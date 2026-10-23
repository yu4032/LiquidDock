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
#include "layout_fingerprints.h"
#include "lib_base.h"
#include "pad_counts_wrapper.h"
#include "rotation_fingerprint.h"
#include "layout_runtime_fingerprints.h"
#include "executable_range.h"
#include "hook_page_pin.h"
#include "rust_orientation_fingerprint.h"

#define LD_PRIMARY_CONF "/data/user/0/com.miui.home/files/liquiddock-native.conf"
#define LD_EARLY_CONF "/data/user_de/0/com.miui.home/files/liquiddock-native-early.conf"
#define LD_LAYOUT_MANAGED_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-layout-managed-v1"
#define LD_PAD_ROWCOL_VADDR 0x1a88188u
#define LD_FILTER_VADDR 0x8d26c0u

static volatile int g_grid_cols;
static volatile int g_grid_rows;
static volatile int g_count_cols,g_count_rows;
static volatile unsigned g_pad_trace[5] __attribute__((used));
static volatile uintptr_t g_pad_pool __attribute__((used));
static void *g_orig_pad_counts;
static void *g_orig_orientation __attribute__((used));
static const struct native_api *g_api;
static bool g_installed;
static bool g_trace_started,g_profile_selected;
static pthread_mutex_t g_install_lock=PTHREAD_MUTEX_INITIALIZER;
static void *trace_thread(void *unused);
static int g_hook_pin_ok=1;
static void (*g_orig_rust_orientation)(void *);

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

LD_PAD_COUNTS_WRAPPER(repl_pad_rowcol_counts, g_count_rows, g_count_cols,
                     g_orig_pad_counts, g_pad_pool, g_pad_trace)
#include "layout_runtime.h"
#include "layout_profiles.h"
#include "layout_load.h"
#include "layout_save.h"
#include "free_place_wrapper.h"
static void *g_orig_no_vacant __attribute__((used));
static volatile unsigned g_free_place_calls __attribute__((used));
LD_FREE_PLACE_WRAPPER(repl_free_place, g_count_cols, g_orig_no_vacant, g_free_place_calls)
static void *g_orig_widget_lattice __attribute__((used));
LD_WIDGET_LATTICE_WRAPPER(repl_widget_lattice, g_count_cols, g_orig_widget_lattice)

static void load_grid_config(void)
{
    struct ld_grid_conf conf={0,0};
    const char *source = LD_PRIMARY_CONF;
    int ok = ld_grid_conf_parse_file(LD_PRIMARY_CONF, &conf);
    struct stat ce;
    bool ce_available=stat("/data/user/0/com.miui.home/files",&ce)==0 && S_ISDIR(ce.st_mode);
    if (!ok && !ce_available) {
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
        LOGI("grid DB repack skipped: orientation-aware layout is active");
        ld_blog("grid DB repack skipped: orientation-aware layout is active");
    }
}

static bool executable_range(uintptr_t address, size_t size)
{
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return false;
    bool ok=ld_executable_maps(maps,address,size);
    fclose(maps);
    return ok;
}

/* Authenticate the entire APK entry before any inline patch or sidecar mutation. */
static int attest_layout_elf(const ld_lib_info *lib,size_t size,const unsigned char expected[32]) {
    char apk[256]; unsigned char observed[32];
    return ld_apk_real_path("MiuiHome.apk",apk,sizeof apk)
        && ld_sha256_region(apk,lib->entry_off,size,observed)
        && !memcmp(expected,observed,sizeof observed);
}
static void pin_installed_hook(uintptr_t site) {
    int ok=ld_pin_code_pages(site,16);
    if(!ok) {
        g_hook_pin_ok=0; g_layout_hooks_ready=0;
        g_grid_cols=0; g_grid_rows=0; g_count_cols=0; g_count_rows=0;
        ld_blog("hook pin failed; custom grid and placement disabled");
    }
    ld_blog("hook page pinned site=%llx ok=%d errno=%d",(unsigned long long)site,ok,ok?0:errno);
}
/* The Rust pre-load transpose assumes the stock lattice and can index past
 * a three-column grid before Dart receives any DTOs. Our DTO planner handles
 * orientation and bounds; retain the original path unless the complete native
 * layout pipeline was installed successfully. Rust uses normal AAPCS64. */
static void repl_rust_orientation(void *grid) {
    if(g_layout_hooks_ready && g_grid_cols>0 && g_grid_rows>0) {
        ld_blog("vendor pre-load transpose delegated to DTO planner");
        return;
    }
    g_orig_rust_orientation(grid);
}
static void update_layout_ready(void) {
    g_layout_hooks_ready=g_hook_pin_ok && g_layout_mid_ready && g_orig_orientation && g_orig_filter_return
        && g_orig_load_items && g_orig_save_batch && g_orig_save_single && g_orig_delete_item
        && g_orig_rust_orientation && g_orig_process_screen && g_orig_widget_lattice && g_orig_no_vacant;
    if(g_layout_hooks_ready && !g_profile_selected) {
        if(ld_layout_select_profile(LD_LAYOUT_FILE,g_grid_cols,g_grid_rows)) g_profile_selected=true;
        else {
            g_layout_hooks_ready=0; g_grid_cols=0; g_grid_rows=0;
            ld_blog("layout profile archive failed; custom grid disabled");
        }
    }
    __atomic_store_n(&g_count_rows,g_layout_hooks_ready?g_grid_rows:0,__ATOMIC_RELEASE);
    __atomic_store_n(&g_count_cols,g_layout_hooks_ready?g_grid_cols:0,__ATOMIC_RELEASE);
    ld_blog("complete layout pipeline ready=%d",g_layout_hooks_ready);
}
static void install_grid_hook_unlocked(const struct native_api *api)
{
    if (!api || !api->hook) return;
    static const unsigned char rust_digest[32]={0xf7,0xa,0x2d,0x15,0x50,0x5a,0xbc,0x83,0xbb,0xc2,0xf0,0x3c,0xbe,0x9e,0x90,0xb6,0x35,0xa8,0xad,0xd6,0xd8,0x9a,0xe0,0xfe,0xf7,0x64,0x10,0xa,0xea,0x1c,0x6d,0x8b};
    if(!g_orig_rust_orientation) {
        ld_lib_info rust; uintptr_t target=0;
        if(ld_resolve_lib("MiuiHome.apk","lib/arm64-v8a/libapp_launcher.so",&rust)
                && ld_vaddr_to_runtime(&rust,0x90ffa0u,&target)
                && executable_range(target,sizeof ld_rust_orientation_words)
                && !memcmp((void *)target,ld_rust_orientation_words,sizeof ld_rust_orientation_words)
                && attest_layout_elf(&rust,16242592u,rust_digest)) {
            int result=api->hook((void *)target,(void *)repl_rust_orientation,(void **)&g_orig_rust_orientation);
            if(result==0 && g_orig_rust_orientation) pin_installed_hook(target);
            ld_blog("Rust pre-load orientation guard installed result=%d",result);
        }
    }
    if (g_installed) { update_layout_ready(); return; }

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

    static const unsigned char dart_digest[32]={0x6f,0x52,0x74,0xf4,0x35,0xcc,0x7,0xaa,0xb1,0x65,0xce,0x53,0x15,0xd1,0xd3,0x5a,0xfa,0xb5,0x59,0xa6,0x59,0xaf,0xd6,0x20,0x3,0x79,0xd3,0x9,0xc9,0xd0,0x8b,0x91};
    if(!attest_layout_elf(&lib,29983600u,dart_digest)) {
        ld_blog("8583 whole-ELF SHA-256 mismatch; refusing native layout hooks"); return;
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
    pin_installed_hook((uintptr_t)site);
    g_installed = true;
    LOGI("PadRowColHandler count authority installed");
    ld_blog("PadRowColHandler count authority installed");
    /* Observe active geometry; startup positions use the DTO load boundary. */
    uintptr_t filter=0;
    if(ld_vaddr_to_runtime(&lib,LD_FILTER_VADDR,&filter)
            && executable_range(filter,sizeof ld_filter_words)
            && !memcmp((void *)filter,ld_filter_words,sizeof ld_filter_words)) {
        result=api->hook((void *)(filter+0x2a4),(void *)repl_filter_grid,&g_orig_filter_return);
        if(result==0 && g_orig_filter_return) pin_installed_hook(filter+0x2a4);
        ld_blog("read-only grid selection installed result=%d",result);
    }
    struct { uintptr_t vaddr; const void *words; size_t size; void *replace; void **orig; } observers[] = {
        {0x917d00u, ld_delete_item_words, sizeof ld_delete_item_words, (void *)repl_delete_item, &g_orig_delete_item},
        {0x8eecc0u, ld_save_batch_words, sizeof ld_save_batch_words, (void *)repl_save_batch, &g_orig_save_batch},
        {0x8e2128u, ld_save_single_words, sizeof ld_save_single_words, (void *)repl_save_single, &g_orig_save_single},
        {0x15e6f6cu, ld_load_items_words, sizeof ld_load_items_words, (void *)repl_load_items, &g_orig_load_items},
        {0x18c4568u, ld_widget_lattice_words, sizeof ld_widget_lattice_words, (void *)repl_widget_lattice, &g_orig_widget_lattice},
        {0x8eb92cu, ld_no_vacant_words, sizeof ld_no_vacant_words, (void *)repl_free_place, &g_orig_no_vacant},
        {0x8eefdcu, ld_process_screen_words, sizeof ld_process_screen_words, (void *)repl_process_screen, &g_orig_process_screen},
    };
    for (size_t i = 0; i < sizeof observers / sizeof *observers; i++) {
        uintptr_t target = 0;
        if (ld_vaddr_to_runtime(&lib, observers[i].vaddr, &target)
                && executable_range(target, observers[i].size)
                && !memcmp((void *)target, observers[i].words, observers[i].size)) {
            result = api->hook((void *)target, observers[i].replace, observers[i].orig);
            if(result==0 && *observers[i].orig) pin_installed_hook(target);
            ld_blog("layout observer %lx installed result=%d", (unsigned long)observers[i].vaddr, result);
            if(observers[i].vaddr==0x8eefdcu && result==0) {
                g_process_resume=target+0x384;
                int mid_result=api->hook((void *)(target+0x218),(void *)repl_process_mid,&g_orig_process_mid);
                g_layout_mid_ready=mid_result==0 && g_orig_process_mid!=NULL;
                if(g_layout_mid_ready) pin_installed_hook(target+0x218);
                ld_blog("layout continuation installed result=%d",mid_result);
            }
        } else if(target && executable_range(target,observers[i].size)) {
            for(size_t k=0;k<observers[i].size;k+=4) if(((uint32_t *)target)[k/4]!=((const uint32_t *)observers[i].words)[k/4]) {
                ld_blog("observer mismatch %lx +%zx live=%08x expected=%08x",(unsigned long)observers[i].vaddr,k,((uint32_t *)target)[k/4],((const uint32_t *)observers[i].words)[k/4]); break;
            }
        }
    }
    uintptr_t orientation = 0;
    if (ld_vaddr_to_runtime(&lib, 0x8ee38cu, &orientation)
            && executable_range(orientation, sizeof ld_orientation_words)
            && memcmp((const void *)orientation, ld_orientation_words,
                      sizeof ld_orientation_words) == 0) {
        backup = NULL;
        result = api->hook((void *)orientation, (void *)repl_orientation_observed, &backup);
        if (result == 0 && backup) {
            g_orig_orientation = backup;
            pin_installed_hook(orientation);
            ld_blog("orientation layout transaction installed (whole body verified)");
        }
    }
    update_layout_ready();
}

static void install_grid_hook(const struct native_api *api) {
    if(!g_grid_cols || !g_grid_rows) return;
    pthread_mutex_lock(&g_install_lock);
    install_grid_hook_unlocked(api);
    if(g_installed && !g_trace_started) {
        pthread_t thread;
        if(pthread_create(&thread,NULL,trace_thread,NULL)==0) {
            pthread_detach(thread); g_trace_started=true;
        }
    }
    pthread_mutex_unlock(&g_install_lock);
}
static void on_library_loaded(const char *name, void *handle)
{
    (void)handle;
    if(name) {
        const char *base=strrchr(name,'/'); base=base?base+1:name;
        if(!strcmp(base,"libapp.so") || !strcmp(base,"libapp_launcher.so")) install_grid_hook(g_api);
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
