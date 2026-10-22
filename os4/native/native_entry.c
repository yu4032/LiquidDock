#include "native_api.h"
#include "process_gate.h"

#include <android/log.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "LiquidDock4", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LiquidDock4", __VA_ARGS__)

#include "bootlog.h"
#include "grid_conf.h"
#include "grid_db_repack.h"
#include "layout_fingerprints.h"
#include "lib_base.h"
#include "pad_counts_wrapper.h"

#define LD_PRIMARY_CONF "/data/user/0/com.miui.home/files/liquiddock-native.conf"
#define LD_EARLY_CONF "/data/user_de/0/com.miui.home/files/liquiddock-native-early.conf"
#define LD_PAD_ROWCOL_VADDR 0x1a88188u
#define LD_FILTER_VADDR 0x8d26c0u

static volatile int g_grid_cols;
static volatile int g_grid_rows;
static volatile unsigned g_pad_trace[5] __attribute__((used));
static volatile uintptr_t g_pad_pool __attribute__((used));
static void *g_orig_pad_counts;
static const struct native_api *g_api;
static bool g_installed;

void ld_blog(const char *fmt, ...)
{
    static int fd = -2;
    if (fd == -2) {
        const char *path = "/data/user_de/0/com.miui.home/files/liquiddock4-boot.log";
        mkdir("/data/user_de/0/com.miui.home/files", 0700);
        fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0600);
    }
    if (fd < 0) return;

    char line[512];
    int n = 0;
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0) {
        n += snprintf(line + n, sizeof line - n, "%ld.%03ld ",
                      (long)ts.tv_sec, (long)(ts.tv_nsec / 1000000));
    }
    va_list ap;
    va_start(ap, fmt);
    n += vsnprintf(line + n, sizeof line - n - 2, fmt, ap);
    va_end(ap);
    if (n < 0) n = 0;
    if (n > (int)sizeof line - 2) n = (int)sizeof line - 2;
    line[n++] = '\n';
    (void)write(fd, line, (size_t)n);
}

LD_PAD_COUNTS_WRAPPER(repl_pad_rowcol_counts, g_grid_rows, g_grid_cols,
                     g_orig_pad_counts, g_pad_pool, g_pad_trace)

static void load_grid_config(void)
{
    struct ld_grid_conf conf;
    const char *source = LD_PRIMARY_CONF;
    int ok = ld_grid_conf_parse_file(LD_PRIMARY_CONF, &conf);
    if (!ok && access(LD_EARLY_CONF, R_OK) == 0) {
        ok = ld_grid_conf_parse_file(LD_EARLY_CONF, &conf);
        source = LD_EARLY_CONF;
    }
    if (!ok) {
        g_grid_cols = 0;
        g_grid_rows = 0;
        LOGI("grid config rejected/missing; stock counts");
        ld_blog("grid config rejected/missing; stock counts");
        return;
    }

    g_grid_cols = conf.cols;
    g_grid_rows = conf.rows;
    LOGI("grid config %dx%d source=%s", conf.cols, conf.rows, source);
    ld_blog("grid config %dx%d source=%s", conf.cols, conf.rows, source);
    if (conf.cols > 0 && conf.rows > 0) {
        int moved = ld_grid_db_repack(conf.cols, conf.rows);
        LOGI("grid repack moved=%d", moved);
        ld_blog("grid repack before load %dx%d moved=%d",
                conf.cols, conf.rows, moved);
    }
}

static bool executable_range(uintptr_t address, size_t size)
{
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return false;
    char line[512];
    bool ok = false;
    while (fgets(line, sizeof line, maps)) {
        unsigned long start, end;
        char permissions[8];
        if (sscanf(line, "%lx-%lx %7s", &start, &end, permissions) == 3 &&
            permissions[0] == 'r' && permissions[2] == 'x' &&
            address >= start && address + size <= end) {
            ok = true;
            break;
        }
    }
    fclose(maps);
    return ok;
}

static void install_grid_hook(const struct native_api *api)
{
    if (g_installed || !api || !api->hook) return;

    static ld_lib_info lib;
    if (!ld_resolve_lib("MiuiHome.apk", "lib/arm64-v8a/libapp.so", &lib)) {
        LOGI("libapp.so not mapped yet");
        return;
    }
    if (!ld_cross_check_strict(&lib, LD_PAD_ROWCOL_VADDR, LD_FILTER_VADDR)) {
        LOGE("libapp.so identity cross-check failed");
        ld_blog("libapp.so identity cross-check failed");
        return;
    }

    uintptr_t entry = 0;
    if (!ld_vaddr_to_runtime(&lib, LD_PAD_ROWCOL_VADDR, &entry) ||
        !executable_range(entry, sizeof ld_pad_rowcol_words) ||
        memcmp((const void *)entry, ld_pad_rowcol_words,
               sizeof ld_pad_rowcol_words) != 0) {
        LOGE("PadRowColHandler 8583 full-body fingerprint mismatch");
        ld_blog("PadRowColHandler 8583 full-body fingerprint mismatch");
        return;
    }

    void *site = (void *)(entry + 0x80);
    void *backup = NULL;
    int result = api->hook(site, (void *)repl_pad_rowcol_counts, &backup);
    if (result != 0 || !backup) {
        LOGE("PadRowColHandler hook failed result=%d backup=%p", result, backup);
        ld_blog("PadRowColHandler hook failed result=%d", result);
        return;
    }

    g_orig_pad_counts = backup;
    g_installed = true;
    LOGI("PadRowColHandler count authority installed");
    ld_blog("PadRowColHandler count authority installed");
}

static void on_library_loaded(const char *name, void *handle)
{
    (void)handle;
    if (name && strstr(name, "libapp.so")) {
        install_grid_hook(g_api);
    }
}

static void *trace_thread(void *unused)
{
    (void)unused;
    unsigned last_calls = 0;
    for (;;) {
        unsigned calls = __atomic_load_n(&g_pad_trace[0], __ATOMIC_RELAXED);
        if (calls != last_calls) {
            last_calls = calls;
            int raw_rows = (int)__atomic_load_n(&g_pad_trace[1], __ATOMIC_RELAXED);
            int raw_cols = (int)__atomic_load_n(&g_pad_trace[2], __ATOMIC_RELAXED);
            int final_rows = (int)__atomic_load_n(&g_pad_trace[3], __ATOMIC_RELAXED);
            int final_cols = (int)__atomic_load_n(&g_pad_trace[4], __ATOMIC_RELAXED);
            LOGI("PadRowCol calls=%u raw=%dx%d final=%dx%d",
                 calls, raw_cols, raw_rows, final_cols, final_rows);
            ld_blog("PadRowCol calls=%u raw(cols=%d,rows=%d) final(cols=%d,rows=%d)",
                    calls, raw_cols, raw_rows, final_cols, final_rows);
        }
        usleep(500000);
    }
    return NULL;
}

__attribute__((visibility("default"), used))
on_library_loaded_t native_init(struct native_api *api)
{
    ld_blog("native_init enter pid=%d", (int)getpid());
    if (!os4_is_launcher_process()) return NULL;
    if (!api || api->version != 2 || !api->hook) {
        LOGE("unknown native injection ABI");
        return NULL;
    }

    g_api = api;
    load_grid_config();
    install_grid_hook(api);
    pthread_t thread;
    if (pthread_create(&thread, NULL, trace_thread, NULL) == 0) {
        pthread_detach(thread);
    }
    return on_library_loaded;
}
