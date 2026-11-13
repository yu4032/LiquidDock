#include "native_api.h"
#include "superellipse.h"
#include "dart_superellipse.h"
#include "superellipse_blur.h"
#include "glass_superellipse.h"
#include "divider_paint.h"
#include "process_gate.h"
#include "sha256_region.h"

#include <android/log.h>
#include <fcntl.h>
#include <sys/mman.h>
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
#include "remote_views_components.h"
#include "rotation_fingerprint.h"
#include "layout_runtime_fingerprints.h"
#include "executable_range.h"
#include "aot_scanner.h"
#include "hook_page_pin.h"
#include "rust_orientation_fingerprint.h"
#include "home_options_fingerprints.h"
#include "home_geometry_fingerprints.h"
#include "dock_geometry_fingerprints.h"

#define LD_PRIMARY_CONF "/data/user/0/com.miui.home/files/liquiddock-native.conf"
#define LD_EARLY_CONF "/data/user_de/0/com.miui.home/files/liquiddock-native-early.conf"
#define LD_LAYOUT_MANAGED_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-layout-managed-v1"

static volatile int g_grid_cols;
static volatile int g_grid_rows;
static volatile int g_count_cols,g_count_rows;
static struct ld_grid_conf g_home_options;
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

#include "icon_glass.h"
#include "maml_widget_materials.h"
#include "widget_resize.h"

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
    if (n < (int)sizeof line - 2) {
        va_list ap;
        va_start(ap, fmt);
        int rem = (int)sizeof line - n - 2;
        int m = vsnprintf(line + n, (size_t)rem, fmt, ap);
        va_end(ap);
        if (m > 0) n += m;
    }
    if (n < 0) n = 0;
    if (n > (int)sizeof line - 2) n = (int)sizeof line - 2;
    line[n++] = '\n';
    (void)write(fd, line, (size_t)n);
}

/* Macro order is (columns, rows): the launcher's x0 receives the column count
 * and x2 the row count. Passing them inverted swapped landscape/portrait. */
LD_PAD_COUNTS_WRAPPER(repl_pad_rowcol_counts, g_count_cols, g_count_rows,
                     g_orig_pad_counts, g_pad_pool, g_pad_trace)
#include "layout_runtime.h"
#include "layout_profiles.h"
#include "layout_load.h"
#include "layout_save.h"
#include "home_icons.h"
#include "home_margins.h"
#include "safe_mode.h"
#include "home_indicator.h"
#include "home_widgets.h"
#include "dock_geometry.h"
#include "glass_gates.h"
#include "glass_trace.h"
#include "glass_filter.h"
#include "glass_filter_fingerprints.h"
#include "glass_filter_hook.h"
static void *g_orig_home_pipeline;
__attribute__((used)) static void ld_observe_home_pipeline(struct ld_dart_context *c)
{
    /* The last field's original load/store is in the relocated instructions.
     * Publish its identical compressed value before inspecting the fresh grid.
     * No allocation or Dart call occurs before the vendor replays that store. */
    uintptr_t variables=0,grid=c->x[1];
    if(!(grid&1) || grid==c->x[22]
            || !ld_read_heap(c->x[29]-0x18,&variables,8)
            || !(variables&1) || variables==c->x[22]) return;
    uint32_t compressed=(uint32_t)variables;
    memcpy((void *)(grid+0x8f),&compressed,4);
    struct ld_home_baseline baseline={0};
    int direction=ld_grid_direction(c,grid);
    if(direction==0 || direction==1)
        ld_home_margins_note(variables,direction,(int)ld_heap_i64(grid,0x1b),(int)ld_heap_i64(grid,0x23));
    uintptr_t icons=ld_heap_pointer(c,grid,0x6b);
    uint32_t layout=0;
    bool is_fs = (ld_read_heap(variables+0x27,&layout,4) && ld_is_fullscreen_layout(c, layout));
    if(g_count_cols>0 && direction>=0 && direction<=1 && icons
            && ld_heap_i64(grid,0x1b)==(direction?g_grid_rows:g_grid_cols)
            && ld_heap_i64(grid,0x23)==(direction?g_grid_cols:g_grid_rows)
            && (is_fs || 1)
            && ld_read_heap(grid+0x2b,&baseline.cell_width,8)
            && ld_read_heap(grid+0x33,&baseline.cell_height,8)
            && ld_read_heap(icons+0x17,&baseline.icon_size,8)) {
        baseline.direction=direction; baseline.valid=1;
    }
    ld_apply_home_icons(c,grid);
    if(baseline.valid && ld_read_heap(grid+0x2b,&baseline.current_width,8)
            && ld_read_heap(grid+0x33,&baseline.current_height,8)
            && ld_read_heap(icons+0x17,&baseline.current_icon,8)) {
        for(size_t i=0;i<sizeof g_home_baseline/sizeof *g_home_baseline;i++) {
            if(!g_home_baseline[i].valid || (g_home_baseline[i].direction==direction
                    && !memcmp(&g_home_baseline[i],&baseline,6*sizeof(double)))) {
                g_home_baseline[i]=baseline; break;
            }
        }
    }
}
LD_DART_OBSERVER(repl_home_pipeline,ld_observe_home_pipeline,g_orig_home_pipeline)
#include "free_place_wrapper.h"
static void *g_orig_no_vacant __attribute__((used));
static volatile unsigned g_free_place_calls __attribute__((used));
LD_FREE_PLACE_WRAPPER(repl_free_place, g_count_cols, g_orig_no_vacant, g_free_place_calls)
static void *g_orig_widget_lattice __attribute__((used));
LD_WIDGET_LATTICE_WRAPPER(repl_widget_lattice, g_count_cols, g_orig_widget_lattice)

static void load_grid_config(void)
{
    struct ld_grid_conf conf={0};
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
    /* 规范为「横屏语义」（列 >= 行）。PadRowCol wrapper 会按方向转置：
     * 竖屏得到 (rows, cols)，横屏得到 (cols, rows)。若 GUI 在竖屏保存了 4x8，
     * 这里补一次转置，避免横屏变成 8 行 4 列被切页。 */
    if (g_grid_cols < g_grid_rows) {
        int swapped = g_grid_cols;
        g_grid_cols = g_grid_rows;
        g_grid_rows = swapped;
        ld_blog("grid config normalized to landscape canonical %dx%d (was %dx%d)",
                g_grid_cols, g_grid_rows, conf.cols, conf.rows);
    }
    g_home_options=conf;
    LOGI("grid config %dx%d source=%s", conf.cols, conf.rows, source);
    ld_blog("grid config %dx%d source=%s", conf.cols, conf.rows, source);
    if (conf.cols > 0 && conf.rows > 0) {
        LOGI("grid DB repack skipped: orientation-aware layout is active");
        ld_blog("grid DB repack skipped: orientation-aware layout is active");
    }
}

/* Optional strict-mode authentication of the entire APK entry. */
static int attest_layout_elf(const ld_lib_info *lib,size_t size,const unsigned char expected[32]) {
    char apk[256]; unsigned char observed[32];
    return ld_apk_real_path(ld_launcher_apk_substr(),apk,sizeof apk)
        && ld_sha256_region(apk,lib->entry_off,size,observed)
        && !memcmp(expected,observed,sizeof observed);
}
static void pin_installed_hook(uintptr_t site) {
    int ok=ld_pin_code_pages_soft(site,16);
    if(!ok) {
        g_hook_pin_ok=0; g_layout_hooks_ready=0;
        g_grid_cols=0; g_grid_rows=0; g_count_cols=0; g_count_rows=0;
        ld_blog("hook pin failed; custom grid and placement disabled");
    }
    ld_blog("hook page pinned site=%llx ok=%d errno=%d",(unsigned long long)site,ok,ok?0:errno);
}

static void ld_install_os4_recents_blur_one(const struct native_api *api,
        const ld_lib_info *lib, uint64_t v, uint64_t len, void *repl, void **orig,
        const char *label)
{
    uintptr_t target = 0;
    if (!ld_vaddr_to_runtime(lib, (uintptr_t)v, &target) ||
            !executable_range(target, (size_t)len) ||
            *(const uint32_t *)target != 0xa9bf79fdu) {
        ld_blog("OS4 Recents %s skipped: target invalid or prologue mismatch", label);
        return;
    }
    if (g_home_options.hook_safety_enabled) {
        char apk[256];
        uint8_t file_bytes[2048];
        bool match = false;
        if (len <= sizeof(file_bytes) && ld_apk_real_path(ld_launcher_apk_substr(), apk, sizeof apk)) {
            int fd = open(apk, O_RDONLY | O_CLOEXEC);
            if (fd >= 0) {
                for (uint16_t j = 0; j < lib->phnum; j++) {
                    uint64_t off, base, sz, mem;
                    if (!ld_phdr_load(lib, j, &off, &base, &sz, &mem)) continue;
                    if (v < base || v - base > sz || len > sz - (v - base)) continue;
                    uint64_t at = lib->entry_off + off + (v - base);
                    ssize_t rd = pread(fd, file_bytes, (size_t)len, (off_t)at);
                    if (rd == (ssize_t)len && memcmp((const void *)target, file_bytes, (size_t)len) == 0) {
                        match = true;
                    }
                    break;
                }
                close(fd);
            }
        }
        if (!match) {
            ld_blog("OS4 Recents %s skipped: strict ELF body mismatch", label);
            return;
        }
    }
    int rc = api->hook((void *)target, repl, orig);
    if (rc == 0 && *orig && ld_pin_code_pages_soft(target, 16)) {
        g_recents_blur_installed = true;
        ld_blog("OS4 Recents %s installed: percent=%u", label,
                (unsigned)__atomic_load_n(&g_os4_recents_blur_percent, __ATOMIC_RELAXED));
    } else {
        ld_blog("OS4 Recents %s install failed rc=%d backup=%d", label, rc, *orig != NULL);
    }
}

static void ld_install_os4_recents_blur(const struct native_api *api, const ld_lib_info *lib)
{
    if (g_recents_blur_installed || g_recents_blur_attempted) return;
    g_recents_blur_attempted = true;
    int percent = g_home_options.recents_blur_percent;
    if (percent < 0 || percent > 100) percent = 100;
    __atomic_store_n(&g_os4_recents_blur_percent, (uint32_t)percent, __ATOMIC_RELEASE);
    if (percent == 100) return;
    if (!g_home_options.recents_blur_target_present || g_home_options.recents_blur_target_count == 0) {
        ld_blog("OS4 Recents blur gate unavailable: recents_blur_target was not published");
        return;
    }
    ld_install_os4_recents_blur_one(api, lib,
            g_home_options.recents_blur_vaddr[0], g_home_options.recents_blur_size[0],
            (void *)repl_os4_recents_blur_radius, &g_orig_recents_blur_radius, "blur gate");
    if (g_home_options.recents_blur_target_count >= 2) {
        ld_install_os4_recents_blur_one(api, lib,
                g_home_options.recents_blur_vaddr[1], g_home_options.recents_blur_size[1],
                (void *)repl_os4_recents_home_hold, &g_orig_recents_home_hold, "home-hold gate");
    }
}

/* Direct Dart AOT gates, resolved by name from the installed Launcher ELF. */
static void ld_install_os4_glass(const struct native_api *api,const ld_lib_info *lib)
{
    if(g_os4_glass_installed || g_os4_glass_attempted) return;
    if(!g_home_options.glass_targets_present) {
        ld_blog("OS4 glass gate unavailable: glass_targets not published; open LiquidDock settings");
        return;
    }
    g_os4_glass_attempted=true;
    const char *symbols[3]={
        "BlurUtilities.isGlassMaterialSupported",
        "BlurUtilities.isHyperMaterialSupported",
        "BlurUtilities.isMaterialDisabled",
    };
    const char *render_symbols[9]={
        "BlurUtilities.isBlurSupported",
        "BlurUtilities.isMamlBlurSupported",
        "BlurUtilities.isAnyMaterialActive",
        "BlurUtilities.isWidgetMaterialActive",
        "BlurUtilities.isThreeLayerBlurSupported",
        "BlurUtilities.isFolderBlurSupported",
        "WidgetMaterialUtils.isGlassOs4SupportedSync",
        "GlassPressHighlightWidget.isSupported",
        "_HyperMaterialWidgetState._computeEffective",
    };
    void *replacements[3]={
        (void *)repl_os4_glass_supported,
        (void *)repl_os4_hyper_supported,
        (void *)repl_os4_material_enabled,
    };
    void *render_replacements[9]={
        (void *)repl_os4_blur_supported,
        (void *)repl_os4_maml_blur_supported,
        (void *)repl_os4_any_material_active,
        (void *)repl_os4_widget_material_active,
        (void *)repl_os4_three_layer_blur_supported,
        (void *)repl_os4_folder_blur_supported,
        (void *)repl_os4_widget_glass_supported,
        (void *)repl_os4_press_light_supported,
        (void *)repl_os4_hyper_material_effective,
    };
    uintptr_t targets[3]={0};
    uintptr_t render_targets[9]={0};
    bool render_ready[9]={0};
    uintptr_t pipeline[4]={0};
    bool pipeline_verified[4]={0};
    uintptr_t passblur_target=0;
    bool passblur_ready=false;
    bool strict=g_home_options.hook_safety_enabled;
    char path[256];
    struct stat st={0};
    void *mapping=MAP_FAILED;
    const unsigned char *apk=NULL;
    int ready=1;
    if(strict) {
        if(!ld_apk_real_path(ld_launcher_apk_substr(),path,sizeof path)) {
            ld_blog("OS4 glass safety check unavailable: APK path"); return;
        }
        int fd=open(path,O_RDONLY|O_CLOEXEC);
        if(fd<0) { ld_blog("OS4 glass safety check unavailable: APK read"); return; }
        if(fstat(fd,&st)==0 && st.st_size>0 && st.st_size<128*1024*1024)
            mapping=mmap(NULL,(size_t)st.st_size,PROT_READ,MAP_PRIVATE,fd,0);
        close(fd);
        if(mapping==MAP_FAILED) { ld_blog("OS4 glass safety check unavailable: mmap"); return; }
        apk=mapping;
        const size_t vendor_size=29983448u;
        ready=lib->entry_off<=(size_t)st.st_size
            && vendor_size<=(size_t)st.st_size-lib->entry_off;
    }
    for(size_t i=0;i<3 && ready;i++) {
        uint64_t vaddr=g_home_options.glass_vaddr[i];
        uint64_t len=g_home_options.glass_size[i];
        if(!ld_vaddr_to_runtime(lib,(uintptr_t)vaddr,&targets[i])
                || !executable_range(targets[i],(size_t)len)) {
            ld_blog("OS4 glass gate unavailable: target %s unmapped at %llx",
                    symbols[i],(unsigned long long)vaddr);
            ready=0; break;
        }
        /* Verify target function prologue; in strict mode compare to APK bytes. */
        int found = (*(const uint32_t *)targets[i] == 0xa9bf79fdu);
        if(strict) {
            for(uint16_t k=0;k<lib->phnum;k++) {
                uint64_t po,pv,pfs,pms;
                if(!ld_phdr_load(lib,k,&po,&pv,&pfs,&pms)) continue;
                if(vaddr<pv || vaddr-pv>pfs || len>pfs-(vaddr-pv)) continue;
                uint64_t off=lib->entry_off+po+(vaddr-pv);
                if(off>(uint64_t)st.st_size || len>(uint64_t)st.st_size-off) continue;
                if(memcmp((const void *)targets[i],apk+off,(size_t)len)==0
                        && *(const uint32_t *)targets[i]==0xa9bf79fdu) found=1;
                break;
            }
        }
        if(!found) {
            ld_blog("OS4 glass gate unavailable: function %s changed",symbols[i]);
            ready=0;
        }
    }
    if(ready && g_home_options.render_targets_present) {
        for(size_t i=0;i<9;i++) {
            uint64_t v=g_home_options.render_vaddr[i];
            uint64_t len=g_home_options.render_size[i];
            if(!ld_vaddr_to_runtime(lib,(uintptr_t)v,&render_targets[i])
                    || !executable_range(render_targets[i],(size_t)len)) {
                ld_blog("OS4 render gate skipped: %s unmapped at %llx",
                        render_symbols[i],(unsigned long long)v);
                continue;
            }
            render_ready[i] = (*(const uint32_t *)render_targets[i] == 0xa9bf79fdu);
            if(strict) {
                for(uint16_t j=0;j<lib->phnum;j++) {
                    uint64_t off,base,sz,mem;
                    if(!ld_phdr_load(lib,j,&off,&base,&sz,&mem)) continue;
                    if(v<base || v-base>sz || len>sz-(v-base)) continue;
                    uint64_t at=lib->entry_off+off+(v-base);
                    if(at<=(uint64_t)st.st_size && len<=(uint64_t)st.st_size-at) {
                        render_ready[i]=!memcmp((const void *)render_targets[i],apk+at,(size_t)len)
                            && *(const uint32_t *)render_targets[i]==0xa9bf79fdu;
                    }
                    break;
                }
            }
            if(!render_ready[i]) ld_blog("OS4 render gate skipped: %s body mismatch",render_symbols[i]);
        }
    } else if(ready) {
        ld_blog("OS4 extended render gates unavailable: render_targets was not published");
    }
    if(ready && g_home_options.passblur_target_present
            ) {
        uint64_t v=g_home_options.passblur_vaddr;
        uint64_t len=g_home_options.passblur_size;
        if(ld_vaddr_to_runtime(lib,(uintptr_t)v,&passblur_target)
                && executable_range(passblur_target,(size_t)len)) {
            passblur_ready = (*(const uint32_t *)passblur_target == 0xa9bf79fdu);
            if(strict) {
                for(uint16_t j=0;j<lib->phnum;j++) {
                    uint64_t off,base,sz,mem;
                    if(!ld_phdr_load(lib,j,&off,&base,&sz,&mem)) continue;
                    if(v<base || v-base>sz || len>sz-(v-base)) continue;
                    uint64_t at=lib->entry_off+off+(v-base);
                    if(at<=(uint64_t)st.st_size && len<=(uint64_t)st.st_size-at) {
                        passblur_ready=!memcmp((const void *)passblur_target,apk+at,(size_t)len)
                            && *(const uint32_t *)passblur_target==0xa9bf79fdu;
                    }
                    break;
                }
            }
            if(!passblur_ready) ld_blog("OS4 PassBlur gate skipped: target body mismatch");
        } else {
            ld_blog("OS4 PassBlur gate skipped: target unmapped at %llx",(unsigned long long)v);
        }
    } else if(ready) {
        ld_blog("OS4 PassBlur gate unavailable: passblur_target was not published");
    }
    if(ready && g_home_options.glass_observers_present) {
        for(size_t i=0;i<4;i++) {
            uint64_t v=g_home_options.glass_obs_vaddr[i];
            uint64_t len=g_home_options.glass_obs_size[i];
            if(!ld_vaddr_to_runtime(lib,(uintptr_t)v,&pipeline[i])
                    || !executable_range(pipeline[i],(size_t)len)) continue;
            if(!strict) {
                pipeline_verified[i] = (*(const uint32_t *)pipeline[i] == 0xa9bf79fdu);
                continue;
            }
            for(uint16_t j=0;j<lib->phnum;j++) {
                uint64_t off,base,sz,mem;
                if(!ld_phdr_load(lib,j,&off,&base,&sz,&mem)) continue;
                if(v<base || v-base>sz || len>sz-(v-base)) continue;
                uint64_t at=lib->entry_off+off+(v-base);
                if(at>(uint64_t)st.st_size || len>(uint64_t)st.st_size-at) continue;
                pipeline_verified[i]=!strict ||
                    (!memcmp((const void *)pipeline[i],apk+at,(size_t)len)
                    && *(const uint32_t *)pipeline[i]==0xa9bf79fdu);
                break;
            }
        }
    }
    if(mapping!=MAP_FAILED) munmap(mapping,(size_t)st.st_size);
    if(!ready) return;
    for(size_t i=0;i<3;i++) {
        int rc=api->hook((void *)targets[i],replacements[i],&g_os4_glass_original[i]);
        if(rc!=0 || !g_os4_glass_original[i]
                || !ld_pin_code_pages_soft(targets[i],16)) {
            /* Any previously installed hooks still tail-call the original. */
            ld_blog("OS4 glass gate unavailable: hook/pin %zu rc=%d",i,rc);
            return;
        }
    }
    __atomic_store_n(&g_os4_glass_override,
            g_home_options.glass_enabled ? 1u : 0u,__ATOMIC_RELEASE);
    __atomic_store_n(&g_os4_widget_glass_override,
            (g_home_options.glass_enabled && g_home_options.glass_widgets) ? 1u : 0u,
            __ATOMIC_RELEASE);
    g_os4_glass_installed=true;
    ld_blog("OS4 glass gates installed: 3 vendor predicates enabled=%d",
            g_home_options.glass_enabled);
    unsigned render_mask=0;
    for(size_t i=0;i<9;i++) {
        if(!render_ready[i]) continue;
        int rc=api->hook((void *)render_targets[i],render_replacements[i],
                &g_os4_render_gate_original[i]);
        if(rc==0 && g_os4_render_gate_original[i]
                && ld_pin_code_pages_soft(render_targets[i],16)) {
            g_os4_render_gate_mask|=(uint16_t)(1u<<i);
            render_mask|=1u<<i;
            ld_blog("OS4 render gate installed: %s enabled=%d",
                    render_symbols[i],g_home_options.glass_enabled);
        } else {
            ld_blog("OS4 render gate install failed: %s rc=%d backup=%d",
                    render_symbols[i],rc,g_os4_render_gate_original[i]!=NULL);
        }
    }
    g_os4_render_gates_installed=render_mask==((1u<<9)-1u);
    if(passblur_ready && !g_passblur_supported_installed) {
        int rc=api->hook((void *)passblur_target,(void *)repl_os4_passblur_supported,
                &g_orig_passblur_supported);
        if(rc==0 && g_orig_passblur_supported && ld_pin_code_pages_soft(passblur_target,16)) {
            g_passblur_supported_installed=true;
            ld_blog("OS4 PassBlur support gate installed: enabled=%d",
                    g_home_options.glass_enabled);
        } else {
            ld_blog("OS4 PassBlur gate install failed rc=%d backup=%d",rc,
                    g_orig_passblur_supported!=NULL);
        }
    }
    const char *label[4]={"Dock.light","Dock.dark","ImageFilter.glass","LightConfig.applyTo"};
    void *wrapper[4]={(void *)repl_os4_trace_light,(void *)repl_os4_trace_dark,
        (void *)repl_os4_trace_filter,(void *)repl_os4_trace_apply};
    void **original[4]={&g_os4_trace_light_original,&g_os4_trace_dark_original,
        &g_os4_trace_filter_original,&g_os4_trace_apply_original};
    if(!g_home_options.glass_observers_present)
        ld_blog("OS4 glass pipeline unavailable: observer addresses not published");
    for(size_t i=0;i<4 && g_home_options.glass_observers_present;i++) {
        if(!pipeline_verified[i]) {
            ld_blog("OS4 glass pipeline skipped %s: complete AOT body mismatch",label[i]);
            continue;
        }
        int rc=api->hook((void *)pipeline[i],wrapper[i],original[i]);
        if(rc==0 && *original[i] && ld_pin_code_pages_soft(pipeline[i],16)) {
            g_os4_pipeline_mask|=1u<<i;
            ld_blog("OS4 glass pipeline installed %s",label[i]);
        } else ld_blog("OS4 glass pipeline skipped %s: hook/pin rc=%d",label[i],rc);
    }
    if(!g_widget_init_miui_attr_installed) {
        static const uint32_t init_miui_attr_pat[16]={
            0xa9bf79fdu, 0xaa0f03fdu, 0xd10061efu, 0xf81f83b6u,
            0xf81f03a1u, 0xaa0203f0u, 0xaa0103e2u, 0xaa1003e1u,
            0xf81e83a1u, 0xf9402f60u, 0x97fdf66eu, 0xf85e83a1u,
            0x94000040u, 0xaa0003e1u, 0xf81e83a1u, 0x97fdf5dau,
        };
        uintptr_t init_fn=ld_find_aot_pattern(lib,0x19f399cu,init_miui_attr_pat,16);
        if(init_fn) {
            int rc=api->hook((void *)init_fn,(void *)repl_widget_init_miui_attr,
                    &g_orig_widget_init_miui_attr);
            if(rc==0 && g_orig_widget_init_miui_attr && ld_pin_code_pages_soft(init_fn,16)) {
                g_widget_init_miui_attr_installed=true;
                ld_blog("OS4 widget glass scope hook installed at %p",(void *)init_fn);
            } else {
                ld_blog("OS4 widget glass scope hook failed rc=%d",(int)rc);
            }
        } else {
            ld_blog("OS4 widget glass scope target not found");
        }
    }
    static bool g_rv_hook_installed = false;
    if(!g_rv_hook_installed) {
        static const uint32_t flutter_view_pat[4]={
            0xa9bf79fdu, 0xaa0f03fdu, 0xd10581efu, 0xf81f83a1u,
        };
        uintptr_t fv_fn=ld_find_aot_pattern(lib,0x1059398u,flutter_view_pat,4);
        if(fv_fn) {
            int rc=api->hook((void *)fv_fn,(void *)repl_flutter_view_apply,
                    &g_orig_flutter_view_apply);
            if(rc==0 && g_orig_flutter_view_apply && ld_pin_code_pages_soft(fv_fn,16)) {
                g_rv_hook_installed=true;
                ld_blog("OS4 RemoteViews component hook installed at %p",(void *)fv_fn);
            } else {
                ld_blog("OS4 RemoteViews component hook failed rc=%d",(int)rc);
            }
        } else {
            ld_blog("OS4 RemoteViews component target not found");
        }
    }
    static bool g_pad_widget_glass_installed = false;
    if(!g_pad_widget_glass_installed) {
        static const uint32_t pad_wc_pat[4]={
            0xa9bf79fdu, 0xaa0f03fdu, 0xd10081efu, 0xf81f83a1u,
        };
        uintptr_t wc_fn=ld_find_aot_pattern(lib,0x1492d24u,pad_wc_pat,4);
        if(wc_fn) {
            int rc=api->hook((void *)wc_fn,(void *)repl_pad_widget_container,
                    &g_orig_pad_widget_container);
            if(rc==0 && g_orig_pad_widget_container && ld_pin_code_pages_soft(wc_fn,16)) {
                g_pad_widget_glass_installed=true;
                ld_blog("OS4 Pad widget container glass hook installed at %p",(void *)wc_fn);
            } else {
                ld_blog("OS4 Pad widget container glass hook failed rc=%d",(int)rc);
            }
        } else {
            ld_blog("OS4 Pad widget container glass target not found");
        }
        uintptr_t phone_wc_fn=ld_find_aot_pattern(lib,0x148f9f8u,pad_wc_pat,4);
        if(phone_wc_fn) {
            int rc=api->hook((void *)phone_wc_fn,(void *)repl_phone_widget_container,
                    &g_orig_phone_widget_container);
            if(rc==0 && g_orig_phone_widget_container && ld_pin_code_pages_soft(phone_wc_fn,16)) {
                ld_blog("OS4 Phone widget container glass hook installed at %p",(void *)phone_wc_fn);
            }
        }
    }
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

/* The icon-glass hook windows are not symbol-addressable; they are pinned per
 * Launcher build. Enable only after re-pinning the windows for this build. */
#define LD_PIN_ICON_GLASS 1

static const uint32_t ld_pat_bdi[16] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10081efu, 0xaa0103e2u, 0xf81f03a1u, 0xb842b040u,
    0x8b1c8000u, 0xf9402370u, 0x6b10001fu, 0x54000d80u, 0xf81f83a0u, 0x94106f9cu,
    0xaa0003e3u, 0xf85f83a0u, 0xf81e83a3u, 0xb800b060u,
};
static const uint32_t ld_pat_wrap[20] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10061efu, 0xaa0203e0u, 0xaa0103e3u, 0xf81f83a1u,
    0xb800b060u, 0x385ff070u, 0x385ff011u, 0x8a500a30u, 0xea5c821fu, 0x54000040u,
    0x941134ccu, 0xaa0303e1u, 0x97ffed15u, 0xf85f83a0u, 0xb842b001u, 0x8b1c8021u,
    0xf9402370u, 0x6b10003fu,
};
static const uint32_t ld_pat_common[16] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10121efu, 0xf9400ba0u, 0xb8417002u, 0x8b1c8042u,
    0xf81f83a2u, 0xb8413040u, 0x8b1c8000u, 0xd28020f1u, 0xb8716801u, 0x8b1c8021u,
    0x97ea078au, 0xaa0003e2u, 0xf85f83a0u, 0xf81f03a2u,
};
static const uint32_t ld_pat_body[48] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10121efu, 0xf9400ba0u, 0xb8417002u, 0x8b1c8042u,
    0xf81f83a2u, 0xb840f040u, 0x8b1c8000u, 0xb842b001u, 0x8b1c8021u, 0xf9402370u,
    0x6b10003fu, 0x540012e0u, 0xd2802171u, 0xb8716820u, 0x8b1c8000u, 0xaa0003e1u,
    0x97df6dcbu, 0xaa0003e2u, 0xf85f83a0u, 0xf81f03a2u, 0xb840f001u, 0x8b1c8021u,
    0xb842b023u, 0x8b1c8063u, 0xf9402370u, 0x6b10007fu, 0x54001160u, 0xd2802271u,
    0xb8716861u, 0x8b1c8021u, 0x97df6dbdu, 0xaa0003e2u, 0xf85f83a0u, 0xf81e83a2u,
    0xb840f001u, 0x8b1c8021u, 0xb842b023u, 0x8b1c8063u, 0xf9402370u, 0x6b10007fu,
    0x54001000u, 0xd28021f1u, 0xb8716861u, 0x8b1c8021u, 0x97df6dafu, 0xaa0003e2u,
};
static const uint32_t ld_pat_sys_radius[48] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd100a1efu, 0xf94013a0u, 0xb8417001u, 0x8b1c8021u,
    0xf81f83a1u, 0xd2800021u, 0x94114a95u, 0xaa0003e1u, 0xf85f83a0u, 0xf81f03a1u,
    0xb800b020u, 0xf9403f40u, 0xf952bc00u, 0xf9402370u, 0x6b10001fu, 0x54000081u,
    0x9140ab62u, 0xf9453c42u, 0x941146ffu, 0xaa0003e1u, 0x97df6ce7u, 0xaa0003e1u,
    0xf9400ba0u, 0xf81f83a1u, 0x372000c0u, 0x97c6791fu, 0x37200080u, 0xf85f83a0u,
    0x7100081fu, 0x54000160u, 0xf9400ba0u, 0x37200080u, 0xd2800041u, 0x97c678d3u,
    0x362000c0u, 0x9142b760u, 0xf941a400u, 0xaa1d03efu, 0xa8c179fdu, 0xd65f03c0u,
    0xf85f03a2u, 0xf9400fb0u, 0xf90001f0u, 0xf9405364u, 0x97c67889u, 0xf81f83a0u,
};
static const uint32_t ld_pat_child_orig[9] = {
    0x940c68ecu, 0xf85f03a2u, 0x9142b761u, 0xf941a821u, 0xf81f83a0u,
    0x94114b35u, 0xaa0003e1u, 0xf85f83a0u, 0xb800b001u,
};

static uintptr_t ld_find_sysa(const ld_lib_info *lib, uintptr_t sysb_rt)
{
    const size_t word_count = sizeof ld_pat_sys_radius / sizeof(uint32_t);
    uintptr_t hint_rt = 0;
    if (ld_vaddr_to_runtime(lib, 0x1789ad8u, &hint_rt) && hint_rt != sysb_rt &&
        executable_range(hint_rt, sizeof ld_pat_sys_radius)) {
        if (ld_aot_match_words((const uint32_t *)hint_rt, ld_pat_sys_radius, word_count))
            return hint_rt;
    }
    uint32_t m0 = ld_aot_inst_mask(ld_pat_sys_radius[0]);
    uint32_t exp0 = ld_pat_sys_radius[0];
    for (uint16_t i = 0; i < lib->phnum; i++) {
        const unsigned char *ph = ld_phdr_at(lib, i);
        uint32_t p_type, p_flags;
        uint64_t p_vaddr, p_filesz;
        memcpy(&p_type, ph + 0, 4);
        memcpy(&p_flags, ph + 4, 4);
        if (p_type != 1 || !(p_flags & 1)) continue;
        memcpy(&p_vaddr, ph + 16, 8);
        memcpy(&p_filesz, ph + 32, 8);
        uintptr_t seg_start = lib->elf_base + (uintptr_t)p_vaddr;
        size_t seg_len = (size_t)p_filesz;
        if (seg_len < sizeof ld_pat_sys_radius) continue;
        size_t max_scan = seg_len - sizeof ld_pat_sys_radius;
        for (size_t off = 0; off <= max_scan; off += 4) {
            const uint32_t *live = (const uint32_t *)(seg_start + off);
            if (((live[0] ^ exp0) & m0) != 0) continue;
            if ((uintptr_t)live != sysb_rt && ld_aot_match_words(live, ld_pat_sys_radius, word_count)) {
                return (uintptr_t)live;
            }
        }
    }
    return 0;
}

static void ld_install_icon_materials(const struct native_api *api,const ld_lib_info *lib)
{
    if(!LD_PIN_ICON_GLASS) { (void)api; (void)lib; return; }
    if(!g_hook_pin_ok || !api || !api->hook || !lib)
        return;

    uintptr_t bdi_rt = ld_find_aot_pattern(lib, 0x1789520u, ld_pat_bdi, 16);
    uintptr_t wrap_rt = ld_find_aot_pattern(lib, 0x178e9acu, ld_pat_wrap, 20);
    uintptr_t common_rt = ld_find_aot_pattern(lib, 0x14e3074u, ld_pat_common, 16);
    uintptr_t body_rt = ld_find_aot_pattern(lib, 0x1789758u, ld_pat_body, sizeof ld_pat_body / sizeof(uint32_t));
    if (!body_rt && bdi_rt) {
        uintptr_t cand = bdi_rt + 0x238u;
        if (executable_range(cand, sizeof ld_pat_body) &&
            ld_aot_match_words((const uint32_t *)cand, ld_pat_body, 48)) {
            body_rt = cand;
            ld_blog("AOT pattern relocated (bdi+0x238): hint 0x1789758 -> live 0x%lx (vaddr 0x%lx)",
                    (unsigned long)body_rt, (unsigned long)(body_rt - lib->elf_base));
        }
    }
    uintptr_t sysb_rt = common_rt ? (common_rt + 0x2c0u) : 0;
    uintptr_t sysa_rt = sysb_rt ? ld_find_sysa(lib, sysb_rt) : 0;

    if (!body_rt || !common_rt || !sysb_rt || !sysa_rt) {
        ld_blog("icon glass: core functions unavailable: bdi=%p wrap=%p common=%p body=%p sysb=%p sysa=%p",
                (void *)bdi_rt, (void *)wrap_rt, (void *)common_rt, (void *)body_rt, (void *)sysb_rt, (void *)sysa_rt);
        return;
    }

    if(!g_icon_glass_installed) {
        uintptr_t sites[2] = { body_rt + 0x4cu, body_rt + 0x178u };
        void *const replacements[] = { (void*)repl_icon_glass_enabled, (void*)repl_icon_glass_size };
        void **const originals[] = { &g_orig_icon_glass_enabled, &g_orig_icon_glass_size };
        for (size_t i = 0; i < 2; i++) {
            if (*originals[i]) continue;
            void *backup = NULL;
            int rc = ld_pin_code_pages_soft(sites[i], 16)
                    ? api->hook((void*)sites[i], replacements[i], &backup) : -1;
            if (rc != 0 || !backup) {
                ld_blog("icon glass hook install failed at %p rc=%d backup=%d", (void *)sites[i], rc, backup != NULL);
                return;
            }
            *originals[i] = backup;
        }
        if (g_orig_icon_glass_enabled && g_orig_icon_glass_size) {
            g_icon_glass_installed = 1;
            ld_blog("icon glass hooks installed dynamically at body=%p", (void *)body_rt);
        }
    }
    if(g_icon_glass_installed && g_home_options.glass_enabled && g_home_options.glass_icons
            && !g_orig_icon_glass_flight) {
        uintptr_t flight_rt = ld_find_aot_pattern(lib, LD_ICON_GLASS_FLIGHT_START,
                ld_pat_flight, sizeof(ld_pat_flight) / sizeof(ld_pat_flight[0]));
        if(flight_rt && ld_pin_code_pages_soft(flight_rt, 16)) {
            void *backup = NULL;
            int rc = api->hook((void *)flight_rt, (void *)repl_icon_glass_local_flight, &backup);
            if(rc == 0 && backup) {
                g_orig_icon_glass_flight = backup;
                ld_blog("icon glass local flight hook installed at %p (vaddr 0x%lx)",
                        (void *)flight_rt, (unsigned long)(flight_rt - lib->elf_base));
            } else {
                ld_blog("icon glass local flight hook failed at %p rc=%d backup=%d",
                        (void *)flight_rt, rc, backup != NULL);
            }
        } else {
            ld_blog("icon glass local flight target unavailable: %p", (void *)flight_rt);
        }
    }
    if(g_icon_glass_installed && g_home_options.glass_icon_corner > 0.0 && !g_icon_glass_radius_installed) {
        struct { uintptr_t site; void *replace; void **orig; } rad[] = {
            { sysb_rt + 0x118u, (void*)repl_icon_glass_radius, &g_orig_icon_glass_radius },
            { sysa_rt + 0x118u, (void*)repl_icon_glass_radius_b, &g_orig_icon_glass_radius_b },
        };
        for (size_t i = 0; i < 2; i++) {
            if (*rad[i].orig) continue;
            void *backup = NULL;
            if (ld_pin_code_pages_soft(rad[i].site, 16) &&
                api->hook((void*)rad[i].site, rad[i].replace, &backup) == 0 && backup) {
                *rad[i].orig = backup;
                g_icon_glass_radius_installed = 1;
                ld_blog("icon glass radius hook %zu installed at %p corner=%.1f",
                        i, (void *)rad[i].site, g_home_options.glass_icon_corner);
            }
        }
    }
    if(g_home_options.glass_enabled && g_home_options.glass_icons && !g_icon_glass_container_patched) {
        struct {
            uintptr_t target;
            uint32_t expected;
            uint32_t patch;
            const char *name;
        } container_bypass[] = {
            { bdi_rt ? (bdi_rt + 0xd0u) : 0, LD_ICON_GLASS_CONTAINER_A_OLD, LD_ICON_GLASS_CONTAINER_A_NEW, "buildDesktopIcon" },
            { wrap_rt ? (wrap_rt + 0x68u) : 0, LD_ICON_GLASS_CONTAINER_B_OLD, LD_ICON_GLASS_CONTAINER_B_NEW, "wrapWithFolderCreateAnim" },
            { body_rt + 0x130u, LD_ICON_GLASS_BODY_RADIUS_BRANCH_OLD, LD_ICON_GLASS_BODY_RADIUS_BRANCH_NEW, "body_radius_branch" },
            { body_rt + 0x200u, LD_ICON_GLASS_BODY_SCALE_OLD, LD_ICON_GLASS_BODY_SCALE_NEW, "body_scale_1.0" },
            { body_rt + 0x220u, LD_ICON_GLASS_BODY_OPACITY_OLD, LD_ICON_GLASS_BODY_OPACITY_NEW, "body_opacity_1.0" },
            { common_rt + 0xc8u, LD_ICON_GLASS_COMMON_RADIUS_BRANCH_OLD, LD_ICON_GLASS_COMMON_RADIUS_BRANCH_NEW, "common_radius_branch" },
            { common_rt + 0x184u, LD_ICON_GLASS_COMMON_SCALE_OLD, LD_ICON_GLASS_COMMON_SCALE_NEW, "common_scale_1.0" },
            { common_rt + 0x1a4u, LD_ICON_GLASS_COMMON_OPACITY_OLD, LD_ICON_GLASS_COMMON_OPACITY_NEW, "common_opacity_1.0" },
            { sysa_rt + 0x68u, LD_ICON_GLASS_A_RADIUS_CHECK_OLD, LD_ICON_GLASS_A_RADIUS_CHECK_NEW, "a_radius_check_bypass" },
            { sysb_rt + 0x68u, LD_ICON_GLASS_B_RADIUS_CHECK_OLD, LD_ICON_GLASS_B_RADIUS_CHECK_NEW, "b_radius_check_bypass" },
        };
        int patched_count = 0;
        for (size_t i = 0; i < sizeof container_bypass / sizeof *container_bypass; i++) {
            uintptr_t target = container_bypass[i].target;
            if (!target || !executable_range(target, 4)) {
                ld_blog("icon glass container bypass site unavailable: %s", container_bypass[i].name);
                continue;
            }
            uint32_t curr = *(volatile uint32_t *)target;
            if (curr == container_bypass[i].patch) {
                patched_count++;
                continue;
            }
            if (curr != container_bypass[i].expected) {
                ld_blog("icon glass container bypass mismatch at %s (%p): curr=%08x expected=%08x",
                        container_bypass[i].name, (void *)target, curr, container_bypass[i].expected);
                continue;
            }
            int rc = ld_patch_text_word(target, container_bypass[i].expected, container_bypass[i].patch);
            if (rc == 0) {
                ld_pin_code_pages_soft(target, 4);
                patched_count++;
                ld_blog("icon glass container bypass patched at %s (%p) now=%08x",
                        container_bypass[i].name, (void *)target, *(volatile uint32_t *)target);
            }
        }

        /* Dynamically extract buildFolderGlass from sysb closure */
        uintptr_t closure = sysb_rt + 0x1a8u;
        if (executable_range(closure, 0x60)) {
            uint32_t pool_add = *(volatile uint32_t *)(closure + 0x54u);
            uint32_t pool_ldr = *(volatile uint32_t *)(closure + 0x58u);
            uint32_t bl_raw = *(volatile uint32_t *)(closure + 0x5cu);
            int32_t imm26 = (int32_t)(bl_raw & 0x03ffffffu);
            if (imm26 & 0x02000000) imm26 |= (int32_t)0xfc000000;
            uintptr_t build_folder_glass = (closure + 0x5cu) + ((uintptr_t)imm26 << 2);

            struct {
                uintptr_t site;
                const char *sys_name;
            } child_sites[] = {
                { sysa_rt + 0x150u, "SysA" },
                { sysb_rt + 0x150u, "SysB" },
            };
            for (size_t s = 0; s < 2; s++) {
                uintptr_t site = child_sites[s].site;
                if (!executable_range(site, LD_ICON_GLASS_CHILD_WORDS * 4)) continue;
                bool already_patched = (*(volatile uint32_t *)site == pool_add);
                if (!already_patched && !ld_aot_match_words((const uint32_t *)site, ld_pat_child_orig, 9)) {
                    ld_blog("icon glass %s child orig mismatch at %p; skipping", child_sites[s].sys_name, (void *)site);
                    continue;
                }
                int32_t delta = (int32_t)((intptr_t)build_folder_glass - (intptr_t)(site + 2 * 4)) >> 2;
                uint32_t bl_call = 0x94000000u | ((uint32_t)delta & 0x03ffffffu);
                uint32_t new_words[LD_ICON_GLASS_CHILD_WORDS] = {
                    pool_add, pool_ldr, bl_call,
                    0xf81f83a0u, /* stur x0, [x29, #-8] */
                    0x14000005u, /* b #+0x14 */
                    0xd503201fu, 0xd503201fu, 0xd503201fu, 0xd503201fu
                };
                for (size_t w = 0; w < LD_ICON_GLASS_CHILD_WORDS; w++) {
                    uintptr_t word_target = site + w * 4;
                    uint32_t curr = *(volatile uint32_t *)word_target;
                    if (curr == new_words[w]) {
                        patched_count++;
                        continue;
                    }
                    int rc = ld_patch_text_word(word_target, curr, new_words[w]);
                    if (rc == 0) {
                        ld_pin_code_pages_soft(word_target, 4);
                        patched_count++;
                    } else {
                        ld_blog("icon glass %s child word %zu patch failed rc=%d", child_sites[s].sys_name, w, rc);
                    }
                }
            }
        }

        int total_expected = (int)(sizeof container_bypass / sizeof *container_bypass + 2 * LD_ICON_GLASS_CHILD_WORDS);
        if (patched_count == total_expected) {
            g_icon_glass_container_patched = 1;
            ld_blog("icon glass material & container patches all %d sites OK", patched_count);
        } else {
            ld_blog("icon glass material & container patches partial: %d/%d", patched_count, total_expected);
        }
    }
    if(!g_icon_glass_state_logged) {
        g_icon_glass_state_logged=1;
        ld_blog("icon glass state enabled=%d icons=%d installed=%d radius=%d container=%d size=%.1f corner=%.1f",
                g_home_options.glass_enabled,g_home_options.glass_icons,g_icon_glass_installed,
                g_icon_glass_radius_installed,g_icon_glass_container_patched,
                g_home_options.glass_icon_size,g_home_options.glass_icon_corner);
    }
}

static void ld_widget_inventory_reset_once(void)
{
    if(g_widget_inventory_reset) return;
    int fd=open(LD_WIDGET_INVENTORY,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,0600);
    if(fd<0) return;
    struct stat st; int ok=fstat(fd,&st)==0 && S_ISREG(st.st_mode);
    if(close(fd)!=0) ok=0;
    if(ok) g_widget_inventory_reset=1;
}

static void install_grid_hook_unlocked(const struct native_api *api)
{
    if (!api || !api->hook) return;
    bool strict=g_home_options.hook_safety_enabled;
    if(g_grid_cols>0 && g_grid_rows>0 && !g_orig_rust_orientation) {
        ld_lib_info rust; uintptr_t target=0;
        if(ld_resolve_lib(ld_launcher_apk_substr(),"lib/arm64-v8a/libapp_launcher.so",&rust)) {
            target = ld_find_aot_pattern(&rust, LD_RUST_ORIENTATION_VADDR,
                                         ld_rust_orientation_words,
                                         sizeof ld_rust_orientation_words / sizeof(uint32_t));
            if (!target) {
                target = ld_find_aot_pattern(&rust, LD_RUST_ORIENTATION_VADDR,
                                             ld_rust_orientation_words, 24);
            }
            if(target && executable_range(target,16)) {
                int result=api->hook((void *)target,(void *)repl_rust_orientation,(void **)&g_orig_rust_orientation);
                if(result==0 && g_orig_rust_orientation) pin_installed_hook(target);
                ld_blog("Rust pre-load orientation guard installed at %p result=%d", (void *)target, result);
            }
        }
    }
    if (g_installed) { update_layout_ready(); return; }

    static ld_lib_info lib;
    if (!ld_resolve_lib(ld_launcher_apk_substr(), "lib/arm64-v8a/libapp.so", &lib)) {
        LOGI("libapp.so not mapped yet");
        return;
    }
    /* Rendering hooks use their own resolved AOT symbols and safety checks.
     * Install them before the unrelated grid-layout address checks below. */
    ld_install_icon_materials(api,&lib);
    ld_widget_inventory_reset_once();
    (void)ld_maml_install(api);
    ld_install_os4_glass(api,&lib);
    ld_install_os4_recents_blur(api,&lib);
    ld_widget_resize_install(api,&lib);
    ld_superellipse_blur_install();
    ld_glass_superellipse_install(api);
    ld_home_margins_install(api,&lib);
    ld_divider_paint_install(api,&lib);
    ld_dart_superellipse_install(api,&lib);
    if (strict && !ld_cross_check_strict(&lib, LD_PAD_ROWCOL_VADDR, LD_FILTER_VADDR)) {
        ld_blog("ELF identity cross-check failed in strict mode; aborting layout hooks");
        return;
    }

    uintptr_t entry = ld_find_aot_pattern(&lib, LD_PAD_ROWCOL_VADDR,
                                          ld_pad_rowcol_words,
                                          sizeof ld_pad_rowcol_words / sizeof(uint32_t));
    if (!entry || !executable_range(entry, 16)) {
        LOGE("PadRowColHandler address unavailable or safety check failed");
        ld_blog("PadRowColHandler address unavailable or safety check failed");
        return;
    }

    static const unsigned char dart_digest[32]={0x1a,0xda,0xa4,0x46,0x6d,0x85,0x16,0xc1,0x41,0x74,0xa3,0x68,0x14,0x94,0x8c,0x03,0xf7,0x05,0x79,0x09,0x04,0x68,0xf7,0xd4,0x09,0x37,0x15,0x6e,0x05,0xb8,0x7b,0x20};
    if(strict && !attest_layout_elf(&lib,29983448u,dart_digest)) {
        ld_blog("ELF SHA-256 attestation failed in strict mode; aborting layout hooks");
        return;
    }

    uintptr_t pipe_fn = ld_find_aot_pattern(&lib, LD_HOME_PIPELINE_FUNCTION_OFFSET,
                                           ld_home_pipeline_words,
                                           sizeof ld_home_pipeline_words / sizeof(uint32_t));
    if (pipe_fn && !g_orig_home_pipeline) {
        uintptr_t pipe_hook = 0;
        for (size_t off = 0; off < 0x400; off += 4) {
            const uint32_t *p = (const uint32_t *)(pipe_fn + off);
            if (p[0] == 0xf85e83a0u && p[1] == 0xb808f020u) {
                pipe_hook = pipe_fn + off;
                break;
            }
        }
        if (pipe_hook) {
            int rc = api->hook((void *)pipe_hook, (void *)repl_home_pipeline, &g_orig_home_pipeline);
            if (rc == 0 && g_orig_home_pipeline) pin_installed_hook(pipe_hook);
            ld_blog("home pipeline observer installed at %p result=%d", (void *)pipe_hook, rc);
        }
    }

    uintptr_t hw_fn = ld_find_aot_pattern(&lib, LD_HOME_WIDGET_FUNCTION_OFFSET,
                                         ld_home_widget_words,
                                         sizeof ld_home_widget_words / sizeof(uint32_t));
    if (hw_fn && !g_orig_home_widget) {
        uintptr_t hw_hook = hw_fn + 0x4cu;
        uintptr_t hw_alloc = hw_fn + 0x1c4u;
        const uint32_t *hw_w = (const uint32_t *)hw_fn;
        if ((hw_w[0x54/4] & 0xffc00000u) == 0xf9400000u) {
            g_dev_config_offset = ((hw_w[0x54/4] >> 10) & 0xfffu) << 3;
            ld_blog("home widget: dynamically decoded DeviceConfig offset=0x%lx", (unsigned long)g_dev_config_offset);
        }
        int rc = api->hook((void *)hw_hook, (void *)repl_home_widget, &g_orig_home_widget);
        if (rc == 0 && g_orig_home_widget) {
            pin_installed_hook(hw_hook);
            g_home_widget_allocate = hw_alloc;
        }
        ld_blog("home widget observer installed at %p (alloc=%p) result=%d", (void *)hw_hook, (void *)hw_alloc, rc);
    }

    struct { uintptr_t start,site; const void *words; size_t size; void *replace; void **orig; } home_hooks[] = {
        {LD_GLASS_FILTER_START,LD_GLASS_FILTER_SITE,ld_glass_filter_words,sizeof ld_glass_filter_words,(void*)repl_glass_filter,&g_orig_glass_filter},
        {LD_HOME_INDICATOR_FUNCTION_OFFSET,LD_HOME_INDICATOR_HOOK_OFFSET,
            ld_home_indicator_words,sizeof ld_home_indicator_words,(void *)repl_home_indicator,&g_orig_home_indicator},
        {LD_DOCK_FUNCTION_SIZE_START,LD_DOCK_FUNCTION_SIZE_SITE,
            ld_dock_function_size_body,sizeof ld_dock_function_size_body,(void *)repl_dock_function_size,&g_orig_dock_function_size},
        {LD_DOCK_ICON_PADDING_START,LD_DOCK_ICON_PADDING_SITE,
            ld_dock_icon_padding_body,sizeof ld_dock_icon_padding_body,(void *)repl_dock_icon_padding,&g_orig_dock_icon_padding},
        {LD_DOCK_BOTTOM_START,LD_DOCK_BOTTOM_SITE,
            ld_dock_bottom_body,sizeof ld_dock_bottom_body,(void *)repl_dock_bottom,&g_orig_dock_bottom},
        {LD_DOCK_CONTAINER_HEIGHT_START,LD_DOCK_CONTAINER_HEIGHT_SITE,
            ld_dock_container_height_body,sizeof ld_dock_container_height_body,(void *)repl_dock_container_height,&g_orig_dock_container_height},
        {LD_DOCK_STABLE_HEIGHT_START,LD_DOCK_STABLE_HEIGHT_SITE,
            ld_dock_stable_height_words,sizeof ld_dock_stable_height_words,(void *)repl_dock_stable_height,&g_orig_dock_stable_height},
        {LD_DOCK_WINDOW_HEIGHT_START,LD_DOCK_WINDOW_HEIGHT_SITE,
            ld_dock_window_height_words,sizeof ld_dock_window_height_words,(void *)repl_dock_window_height,&g_orig_dock_window_height},
        {LD_DOCK_BACKGROUND_START,LD_DOCK_BACKGROUND_SITE,
            ld_dock_background_words,sizeof ld_dock_background_words,(void *)repl_dock_background,&g_orig_dock_background},
        {LD_DOCK_DIFF_BG_START,LD_DOCK_DIFF_BG_SITE,
            ld_dock_diff_bg_words,sizeof ld_dock_diff_bg_words,(void *)repl_dock_diff_bg,&g_orig_dock_diff_bg},
        {LD_DOCK_SPACING_START,LD_DOCK_SPACING_SITE,
            ld_dock_spacing_words,sizeof ld_dock_spacing_words,(void *)repl_dock_spacing,&g_orig_dock_spacing},
    };
    for(size_t i=0;i<sizeof home_hooks/sizeof *home_hooks;i++) {
        if(*home_hooks[i].orig) continue;
        if(home_hooks[i].size < 16) continue;
        uintptr_t fn = ld_find_aot_pattern(&lib, home_hooks[i].start, (const uint32_t *)home_hooks[i].words, home_hooks[i].size / 4);
        if (!fn) continue;
        uintptr_t site = 0;
        if (home_hooks[i].site >= home_hooks[i].start) {
            uintptr_t orig_delta = home_hooks[i].site - home_hooks[i].start;
            const uint32_t *orig_words = (const uint32_t *)home_hooks[i].words;
            if (orig_delta + 16 <= home_hooks[i].size) {
                const uint32_t *site_pat = (const uint32_t *)((const char *)orig_words + orig_delta);
                for (size_t off = 0; off < 0x1000 && executable_range(fn + off, 16); off += 4) {
                    if (ld_aot_match_words((const uint32_t *)(fn + off), site_pat, 4)) {
                        site = fn + off;
                        break;
                    }
                }
            }
        }
        if (!site) site = fn + (home_hooks[i].site - home_hooks[i].start);
        if (!executable_range(site, 16)) continue;
        int status = api->hook((void *)site, home_hooks[i].replace, home_hooks[i].orig);
        if (status == 0 && *home_hooks[i].orig) pin_installed_hook(site);
        ld_blog("home options observer %lx installed at %p result=%d", (unsigned long)home_hooks[i].site, (void *)site, status);
    }
    if(!g_grid_cols || !g_grid_rows) return;
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
    uintptr_t filter = ld_find_aot_pattern(&lib, LD_FILTER_VADDR,
                                          ld_filter_words,
                                          sizeof ld_filter_words / sizeof(uint32_t));
    if (filter && !g_orig_filter_return) {
        uintptr_t fsite = filter + 0x2a4u;
        if (executable_range(fsite, 16)) {
            result = api->hook((void *)fsite, (void *)repl_filter_grid, &g_orig_filter_return);
            if (result == 0 && g_orig_filter_return) pin_installed_hook(fsite);
            ld_blog("read-only grid selection installed at %p result=%d", (void *)fsite, result);
        }
    }
    struct { uintptr_t vaddr; const void *words; size_t size; void *replace; void **orig; } observers[] = {
        {LD_DELETE_ITEM_VADDR, ld_delete_item_words, sizeof ld_delete_item_words, (void *)repl_delete_item, &g_orig_delete_item},
        {LD_SAVE_BATCH_VADDR, ld_save_batch_words, sizeof ld_save_batch_words, (void *)repl_save_batch, &g_orig_save_batch},
        {LD_SAVE_SINGLE_VADDR, ld_save_single_words, sizeof ld_save_single_words, (void *)repl_save_single, &g_orig_save_single},
        {LD_LOAD_ITEMS_VADDR, ld_load_items_words, sizeof ld_load_items_words, (void *)repl_load_items, &g_orig_load_items},
        {LD_WIDGET_LATTICE_VADDR, ld_widget_lattice_words, sizeof ld_widget_lattice_words, (void *)repl_widget_lattice, &g_orig_widget_lattice},
        {LD_NO_VACANT_VADDR, ld_no_vacant_words, sizeof ld_no_vacant_words, (void *)repl_free_place, &g_orig_no_vacant},
        {LD_PROCESS_SCREEN_VADDR, ld_process_screen_words, sizeof ld_process_screen_words, (void *)repl_process_screen, &g_orig_process_screen},
    };
    for (size_t i = 0; i < sizeof observers / sizeof *observers; i++) {
        if (*observers[i].orig) continue;
        uintptr_t target = ld_find_aot_pattern(&lib, observers[i].vaddr,
                                              (const uint32_t *)observers[i].words,
                                              observers[i].size / sizeof(uint32_t));
        if (!target) {
            ld_blog("layout observer %zu (%lx) not found/ambiguous; safely skipped",
                    i, (unsigned long)observers[i].vaddr);
            continue;
        }
        if (!executable_range(target, 16)) {
            ld_blog("layout observer %zu (%lx) not executable at %p; safely skipped",
                    i, (unsigned long)observers[i].vaddr, (void *)target);
            continue;
        }
        result = api->hook((void *)target, observers[i].replace, observers[i].orig);
        if (result == 0 && *observers[i].orig) pin_installed_hook(target);
        ld_blog("layout observer %lx installed at %p result=%d", (unsigned long)observers[i].vaddr, (void *)target, result);
        if (observers[i].vaddr == LD_PROCESS_SCREEN_VADDR && result == 0) {
            g_process_resume = target + 0x384u;
            int mid_result = api->hook((void *)(target + 0x218u), (void *)repl_process_mid, &g_orig_process_mid);
            g_layout_mid_ready = mid_result == 0 && g_orig_process_mid != NULL;
            if (g_layout_mid_ready) pin_installed_hook(target + 0x218u);
            ld_blog("layout continuation installed at %p result=%d", (void *)(target + 0x218u), mid_result);
        }
    }
    uintptr_t orientation = ld_find_aot_pattern(&lib, LD_ORIENTATION_VADDR,
                                               ld_orientation_words,
                                               sizeof ld_orientation_words / sizeof(uint32_t));
    if (orientation && !g_orig_orientation && *(const uint32_t *)orientation == 0xa9bf79fdu) {
        backup = NULL;
        result = api->hook((void *)orientation, (void *)repl_orientation_observed, &backup);
        if (result == 0 && backup) {
            g_orig_orientation = backup;
            pin_installed_hook(orientation);
            ld_blog("orientation layout transaction installed at %p safety=%d", (void *)orientation, strict);
        }
    }
    update_layout_ready();
}

static void install_grid_hook(const struct native_api *api) {
    if((!g_grid_cols || !g_grid_rows) && !g_home_options.icon_size_enabled
            && !g_home_options.dock.enabled && !g_home_options.glass_enabled
            && !g_home_options.widget_overrides
            && g_home_options.recents_blur_percent == 100) return;
    pthread_mutex_lock(&g_install_lock);
    install_grid_hook_unlocked(api);
    if((g_installed || g_os4_glass_installed) && !g_trace_started) {
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
        else if(!strcmp(base,"libhyper_os_flutter.so")) {
            ld_glass_superellipse_install(g_api);
            ld_glass_filter_hook_install(g_api);
        }
        else if(!strcmp(base,"librust_maml_sdk.so")) {
            pthread_mutex_lock(&g_install_lock);
            ld_widget_inventory_reset_once();
            (void)ld_maml_install(g_api);
            pthread_mutex_unlock(&g_install_lock);
        }
    }
}

static void *trace_thread(void *unused)
{
    (void)unused;
    unsigned last_calls = 0, last_glass_calls = 0;
    unsigned last_pipeline[4]={0};
    int light_sample_reported=0;
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
        unsigned gc=__atomic_load_n(&g_os4_glass_calls[0],__ATOMIC_RELAXED)
            + __atomic_load_n(&g_os4_glass_calls[1],__ATOMIC_RELAXED)
            + __atomic_load_n(&g_os4_glass_calls[2],__ATOMIC_RELAXED);
        if(gc!=last_glass_calls) {
            last_glass_calls=gc;
            ld_blog("OS4 glass gate calls=%u",gc);
        }
        const char *labels[4]={"Dock.light","Dock.dark","ImageFilter.glass","LightConfig.applyTo"};
        for(size_t i=0;i<4;i++) {
            if(!(g_os4_pipeline_mask & (1u<<i))) continue;
            unsigned now=__atomic_load_n(&g_os4_pipeline_counts[i],__ATOMIC_RELAXED);
            if(now!=last_pipeline[i]) {
                last_pipeline[i]=now;
                ld_blog("OS4 glass pipeline %s calls=%u",labels[i],now);
            }
        }
        if(!light_sample_reported && (g_os4_pipeline_mask & (1u<<3))) {
            unsigned state=__atomic_load_n(&g_os4_light_sample_status,__ATOMIC_ACQUIRE);
            if(state==2) {
                light_sample_reported=1;
                ld_blog("OS4 glass light sample register=x%d doubles=%.4f,%.4f,%.4f,%.4f,%.4f (diagnostic, semantic unknown)",
                    g_os4_light_sample_register,g_os4_light_sample_fields[0],
                    g_os4_light_sample_fields[1],g_os4_light_sample_fields[2],
                    g_os4_light_sample_fields[3],g_os4_light_sample_fields[4]);
            } else if(__atomic_load_n(&g_os4_light_sample_attempts,__ATOMIC_RELAXED)>=12) {
                light_sample_reported=1;
                ld_blog("OS4 glass light sample unavailable: 12 applyTo calls inspected; no plausible 5-double record");
            }
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
    ld_sm_start_watchdog();
    if (!api || api->version != 2 || !api->hook) {
        LOGE("unknown native injection ABI");
        return NULL;
    }
    /* 连续崩溃保护：安全模式下不装任何 hook，直接返回。 */
    if (ld_safe_mode_tick()) return NULL;

    g_api = api;
    load_grid_config();
    ld_prepare_native_glass();
    install_grid_hook(api);
    ld_superellipse_install(api);
    return on_library_loaded;
}
