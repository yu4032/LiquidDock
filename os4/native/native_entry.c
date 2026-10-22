#include "native_api.h"
#include "process_gate.h"
#include "sha256_region.h"

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
static bool g_trace_started;
static struct ld_grid_conf g_requested_grid;
static pthread_mutex_t g_install_lock = PTHREAD_MUTEX_INITIALIZER;
static void *trace_thread(void *unused);

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
    struct ld_grid_conf conf = {0, 0};
    const char *source = LD_PRIMARY_CONF;
    int ok = ld_grid_conf_parse_file(LD_PRIMARY_CONF, &conf);

    // If CE is available, its file is authoritative even when empty,
    // malformed, or absent. Never silently resurrect an older DE snapshot.
    // The DE mirror is read only before CE storage can be accessed.
    struct stat ce;
    bool ce_available = stat("/data/user/0/com.miui.home/files", &ce) == 0 &&
                        S_ISDIR(ce.st_mode);
    if (!ok && !ce_available) {
        ok = ld_grid_conf_parse_file(LD_EARLY_CONF, &conf);
        source = LD_EARLY_CONF;
    }

    if (!ok || conf.cols == 0 || conf.rows == 0) {
        g_requested_grid = (struct ld_grid_conf){0, 0};
        LOGI("grid config rejected/disabled; stock counts");
        ld_blog("grid config rejected/disabled; stock counts");
        return;
    }
    // Parsing alone has no side effects. Do not touch a Launcher database
    // until the exact, executable Dart function body has been authenticated
    // and its hook has been installed successfully.
    g_requested_grid = conf;
    LOGI("grid request %dx%d source=%s", conf.cols, conf.rows, source);
    ld_blog("grid request %dx%d source=%s", conf.cols, conf.rows, source);
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
    if (!api || !api->hook || !g_requested_grid.cols || !g_requested_grid.rows)
        return;
    pthread_mutex_lock(&g_install_lock);
    if (g_installed) {
        pthread_mutex_unlock(&g_install_lock);
        return;
    }

    static ld_lib_info lib;
    if (!ld_resolve_lib("MiuiHome.apk", "lib/arm64-v8a/libapp.so", &lib)) {
        pthread_mutex_unlock(&g_install_lock);
        return;
    }
    if (!ld_cross_check_strict(&lib, LD_PAD_ROWCOL_VADDR, LD_FILTER_VADDR)) {
        LOGE("libapp.so identity cross-check failed");
        ld_blog("libapp.so identity cross-check failed");
        pthread_mutex_unlock(&g_install_lock);
        return;
    }

    uintptr_t entry = 0;
    if (!ld_vaddr_to_runtime(&lib, LD_PAD_ROWCOL_VADDR, &entry) ||
        !executable_range(entry, sizeof ld_pad_rowcol_words) ||
        memcmp((const void *)entry, ld_pad_rowcol_words,
               sizeof ld_pad_rowcol_words) != 0) {
        LOGE("PadRowColHandler 8583 full-body fingerprint mismatch");
        ld_blog("PadRowColHandler 8583 full-body fingerprint mismatch");
        pthread_mutex_unlock(&g_install_lock);
        return;
    }

    // A function-body match alone does not establish the surrounding ELF or
    // database schema identity. Hash the *whole* 8583 libapp.so before changes.
    static const unsigned char expected_sha256[32] = {
        0x6f,0x52,0x74,0xf4,0x35,0xcc,0x07,0xaa,
        0xb1,0x65,0xce,0x53,0x15,0xd1,0xd3,0x5a,
        0xfa,0xb5,0x59,0xa6,0x59,0xaf,0xd6,0x20,
        0x03,0x79,0xd3,0x09,0xc9,0xd0,0x8b,0x91
    };
    char apk_path[256];
    unsigned char observed_sha256[32];
    if (!ld_apk_real_path("MiuiHome.apk", apk_path, sizeof apk_path) ||
        !ld_sha256_region(apk_path, lib.entry_off, 29983600u, observed_sha256) ||
        memcmp(expected_sha256, observed_sha256, sizeof expected_sha256) != 0) {
        LOGE("8583 whole-ELF SHA-256 mismatch: refusing OS4 hook and DB repack");
        ld_blog("8583 whole-ELF SHA-256 mismatch");
        pthread_mutex_unlock(&g_install_lock);
        return;
    }

    void *site = (void *)(entry + 0x80);
    void *backup = NULL;
    int result = api->hook(site, (void *)repl_pad_rowcol_counts, &backup);
    if (result != 0 || !backup) {
        LOGE("PadRowColHandler hook failed result=%d backup=%p", result, backup);
        ld_blog("PadRowColHandler hook failed result=%d", result);
        pthread_mutex_unlock(&g_install_lock);
        return;
    }

    g_orig_pad_counts = backup;
    g_installed = true;
    LOGI("PadRowColHandler count authority installed");
    ld_blog("PadRowColHandler count authority installed");

    // Only now is it safe to modify Launcher state. The hook is active
    // with both count overrides still at zero (stock pass-through).
    int moved = ld_grid_db_repack(g_requested_grid.cols, g_requested_grid.rows);
    if (moved < 0) {
        LOGE("grid repack failed: keeping stock row/column counts");
        ld_blog("grid repack failed: keeping stock counts");
    } else {
        // Activate atomically for the first layout call after DB consistency.
        __atomic_store_n(&g_grid_rows, g_requested_grid.rows, __ATOMIC_RELEASE);
        __atomic_store_n(&g_grid_cols, g_requested_grid.cols, __ATOMIC_RELEASE);
        LOGI("grid repack %dx%d moved=%d",
             g_requested_grid.cols, g_requested_grid.rows, moved);
        ld_blog("grid repack after attestation %dx%d moved=%d",
                g_requested_grid.cols, g_requested_grid.rows, moved);
    }
    if (!g_trace_started) {
        pthread_t thread;
        if (pthread_create(&thread, NULL, trace_thread, NULL) == 0) {
            pthread_detach(thread);
            g_trace_started = true;
        }
    }
    pthread_mutex_unlock(&g_install_lock);
}

static void on_library_loaded(const char *name, void *handle)
{
    (void)handle;
    if (name) {
        size_t n = strlen(name);
        if (n >= sizeof("libapp.so") - 1 &&
            strcmp(name + n - (sizeof("libapp.so") - 1), "libapp.so") == 0) {
            install_grid_hook(g_api);
        }
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
    // The native module may be injected into other processes. No I/O before
    // the process gate, including persistent boot diagnostics.
    if (!os4_is_launcher_process()) return NULL;
    ld_blog("native_init enter pid=%d", (int)getpid());
    if (!api || api->version != 2 || !api->hook) {
        LOGE("unknown native injection ABI");
        return NULL;
    }

    g_api = api;
    load_grid_config();
    install_grid_hook(api);
    return on_library_loaded;
}
