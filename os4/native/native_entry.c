#include "native_api.h"
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
#include "rotation_fingerprint.h"
#include "layout_runtime_fingerprints.h"
#include "executable_range.h"
#include "hook_page_pin.h"
#include "rust_orientation_fingerprint.h"
#include "home_options_fingerprints.h"
#include "home_geometry_fingerprints.h"
#include "dock_geometry_fingerprints.h"

#define LD_PRIMARY_CONF "/data/user/0/com.miui.home/files/liquiddock-native.conf"
#define LD_EARLY_CONF "/data/user_de/0/com.miui.home/files/liquiddock-native-early.conf"
#define LD_LAYOUT_MANAGED_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-layout-managed-v1"
#define LD_PAD_ROWCOL_VADDR 0x1a84cfcu
#define LD_FILTER_VADDR 0x8d39a4u

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
static bool executable_range(uintptr_t address, size_t size);
#include "maml_widget_materials.h"

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
#include "home_icons.h"
#include "home_geometry.h"
#include "home_indicator.h"
#include "home_widgets.h"
#include "dock_geometry.h"
#include "glass_gates.h"
#include "glass_trace.h"
#include "glass_filter.h"
#include "glass_filter_fingerprints.h"
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
    uintptr_t fullscreen=0,icons=ld_heap_pointer(c,grid,0x6b);
    uint32_t layout=0;
    if(g_count_cols>0 && direction>=0 && direction<=1 && icons
            && ld_heap_i64(grid,0x1b)==(direction?g_grid_rows:g_grid_cols)
            && ld_heap_i64(grid,0x23)==(direction?g_grid_cols:g_grid_rows)
            && ld_read_heap(variables+0x27,&layout,4)
            && ld_read_heap(c->x[27]+0xef20,&fullscreen,8)
            && (layout&1) && layout==(uint32_t)fullscreen
            && ld_read_heap(grid+0x2b,&baseline.cell_width,8)
            && ld_read_heap(grid+0x33,&baseline.cell_height,8)
            && ld_read_heap(icons+0x17,&baseline.icon_size,8)) {
        baseline.direction=direction; baseline.valid=1;
    }
    ld_apply_home_geometry(c,grid);
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
    g_home_options=conf;
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

/* Optional strict-mode authentication of the entire APK entry. */
static int attest_layout_elf(const ld_lib_info *lib,size_t size,const unsigned char expected[32]) {
    char apk[256]; unsigned char observed[32];
    return ld_apk_real_path(ld_launcher_apk_substr(),apk,sizeof apk)
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
        const size_t vendor_size=29980112u;
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
        /* Optional strict mode compares the live body to the APK bytes. */
        int found=!strict;
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
            render_ready[i]=!strict;
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
            passblur_ready=!strict;
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
                pipeline_verified[i]=true;
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
                || !ld_pin_code_pages(targets[i],16)) {
            /* Any previously installed hooks still tail-call the original. */
            ld_blog("OS4 glass gate unavailable: hook/pin %zu rc=%d",i,rc);
            return;
        }
    }
    __atomic_store_n(&g_os4_glass_override,
            g_home_options.glass_enabled ? 1u : 0u,__ATOMIC_RELEASE);
    g_os4_glass_installed=true;
    ld_blog("OS4 glass gates installed: 3 vendor predicates enabled=%d",
            g_home_options.glass_enabled);
    unsigned render_mask=0;
    for(size_t i=0;i<9;i++) {
        if(!render_ready[i]) continue;
        int rc=api->hook((void *)render_targets[i],render_replacements[i],
                &g_os4_render_gate_original[i]);
        if(rc==0 && g_os4_render_gate_original[i]
                && ld_pin_code_pages(render_targets[i],16)) {
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
        if(rc==0 && g_orig_passblur_supported && ld_pin_code_pages(passblur_target,16)) {
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
        if(rc==0 && *original[i] && ld_pin_code_pages(pipeline[i],16)) {
            g_os4_pipeline_mask|=1u<<i;
            ld_blog("OS4 glass pipeline installed %s",label[i]);
        } else ld_blog("OS4 glass pipeline skipped %s: hook/pin rc=%d",label[i],rc);
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

static void ld_install_icon_materials(const struct native_api *api,const ld_lib_info *lib)
{
    if(!g_hook_pin_ok || !api || !api->hook || !lib)
        return;
    if(!g_icon_glass_installed) {
        if(g_home_options.hook_safety_enabled &&
                !ld_icon_body_matches(lib->elf_base,LD_ICON_GLASS_BODY_START,
                    LD_ICON_GLASS_BODY_SIZE,ld_icon_glass_body_sha)) {
            ld_blog("icon glass safety check failed for ShortcutIconWidget closure");
            return;
        }
        static const uintptr_t sites[]={0x1786e64u,0x1786f90u};
        void *const replacements[]={(void*)repl_icon_glass_enabled,(void*)repl_icon_glass_size};
        void **const originals[]={&g_orig_icon_glass_enabled,&g_orig_icon_glass_size};
        uintptr_t targets[2]={0};
        for(size_t i=0;i<2;i++)
            if(!ld_vaddr_to_runtime(lib,sites[i],&targets[i]) || !executable_range(targets[i],16)) {
                ld_blog("icon glass hook site unavailable vaddr=%lx",(unsigned long)sites[i]);
                return;
            }
        for(size_t i=0;i<2;i++) {
            if(*originals[i]) continue;
            void *backup=NULL;
            int rc=ld_pin_code_pages(targets[i],16)
                    ? api->hook((void*)targets[i],replacements[i],&backup) : -1;
            if(rc!=0 || !backup) {
                ld_blog("icon glass hook install failed vaddr=%lx rc=%d backup=%d",
                        (unsigned long)sites[i],rc,backup!=NULL);
                return;
            }
            *originals[i]=backup;
        }
        if(g_orig_icon_glass_enabled && g_orig_icon_glass_size) {
            g_icon_glass_installed=1;
            ld_blog("icon glass hooks installed safety=%d",g_home_options.hook_safety_enabled);
        }
    }
    if(g_icon_glass_installed && !g_orig_icon_glass_flight
            && (!g_home_options.hook_safety_enabled ||
                ld_icon_body_matches(lib->elf_base,LD_ICON_GLASS_FLIGHT_START,
                    LD_ICON_GLASS_FLIGHT_SIZE,ld_icon_glass_flight_sha))) {
        uintptr_t target=0;
        if(ld_vaddr_to_runtime(lib,LD_ICON_GLASS_FLIGHT_START,&target)
                && executable_range(target,16) && ld_pin_code_pages(target,16)) {
            void *backup=NULL;
            if(api->hook((void*)target,(void*)repl_icon_glass_local_flight,&backup)==0 && backup) {
                g_orig_icon_glass_flight=backup;
                ld_blog("icon glass uses native local Hero flights");
            }
        }
    }
    if(g_icon_glass_installed && g_home_options.glass_icon_corner>0.0
            && !g_icon_glass_radius_installed
            && (!g_home_options.hook_safety_enabled ||
                ld_icon_body_matches(lib->elf_base,LD_ICON_GLASS_RADIUS_START,
                    LD_ICON_GLASS_RADIUS_SIZE,ld_icon_glass_radius_sha))) {
        uintptr_t target=0;
        if(ld_vaddr_to_runtime(lib,0x14e9c90u,&target) && executable_range(target,16)
                && ld_pin_code_pages(target,16)) {
            void *backup=NULL;
            if(api->hook((void*)target,(void*)repl_icon_glass_radius,&backup)==0 && backup) {
                g_orig_icon_glass_radius=backup;
                g_icon_glass_radius_installed=1;
                ld_blog("icon glass radius hook installed corner=%.1f safety=%d",
                        g_home_options.glass_icon_corner,g_home_options.hook_safety_enabled);
            }
        }
    }
    if(!g_icon_glass_state_logged) {
        g_icon_glass_state_logged=1;
        ld_blog("icon glass state enabled=%d icons=%d installed=%d radius=%d size=%.1f corner=%.1f",
                g_home_options.glass_enabled,g_home_options.glass_icons,g_icon_glass_installed,
                g_icon_glass_radius_installed,g_home_options.glass_icon_size,g_home_options.glass_icon_corner);
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
    static const unsigned char rust_digest[32]={0xf7,0xa,0x2d,0x15,0x50,0x5a,0xbc,0x83,0xbb,0xc2,0xf0,0x3c,0xbe,0x9e,0x90,0xb6,0x35,0xa8,0xad,0xd6,0xd8,0x9a,0xe0,0xfe,0xf7,0x64,0x10,0xa,0xea,0x1c,0x6d,0x8b};
    if(g_grid_cols>0 && g_grid_rows>0 && !g_orig_rust_orientation) {
        ld_lib_info rust; uintptr_t target=0;
        if(ld_resolve_lib(ld_launcher_apk_substr(),"lib/arm64-v8a/libapp_launcher.so",&rust)
                && ld_vaddr_to_runtime(&rust,0x90ffa0u,&target)
                && executable_range(target,strict?sizeof ld_rust_orientation_words:16)
                && (!strict || (!memcmp((void *)target,ld_rust_orientation_words,sizeof ld_rust_orientation_words)
                    && attest_layout_elf(&rust,16242592u,rust_digest)))) {
            int result=api->hook((void *)target,(void *)repl_rust_orientation,(void **)&g_orig_rust_orientation);
            if(result==0 && g_orig_rust_orientation) pin_installed_hook(target);
            ld_blog("Rust pre-load orientation guard installed result=%d",result);
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
    if (strict && !ld_cross_check_strict(&lib, LD_PAD_ROWCOL_VADDR, LD_FILTER_VADDR)) {
        LOGE("libapp.so identity cross-check failed");
        ld_blog("libapp.so identity cross-check failed");
        return;
    }

    uintptr_t entry = 0;
    if (!ld_vaddr_to_runtime(&lib, LD_PAD_ROWCOL_VADDR, &entry) ||
        !executable_range(entry+0x80, 16) ||
        (strict && (!executable_range(entry, sizeof ld_pad_rowcol_words) ||
         memcmp((const void *)entry, ld_pad_rowcol_words,
                sizeof ld_pad_rowcol_words) != 0))) {
        LOGE("PadRowColHandler address unavailable or safety check failed");
        ld_blog("PadRowColHandler address unavailable or safety check failed");
        return;
    }

    static const unsigned char dart_digest[32]={0x93,0xe5,0x11,0x98,0x92,0x02,0xa7,0x0d,0x38,0xb8,0x23,0x1c,0x31,0x33,0x16,0x7b,0x83,0x0c,0xa7,0x60,0x9b,0x61,0xba,0x6a,0xd1,0x33,0x89,0x87,0x37,0xd0,0xe0,0xef};
    if(strict && !attest_layout_elf(&lib,29980112u,dart_digest)) {
        ld_blog("8584 whole-ELF SHA-256 mismatch; safety switch is enabled"); return;
    }
    struct { uintptr_t start,site; const void *words; size_t size; void *replace; void **orig; } home_hooks[] = {
        {LD_GLASS_FILTER_START,LD_GLASS_FILTER_SITE,ld_glass_filter_words,sizeof ld_glass_filter_words,(void*)repl_glass_filter,&g_orig_glass_filter},
        {LD_HOME_PIPELINE_FUNCTION_OFFSET,LD_HOME_PIPELINE_HOOK_OFFSET,
            ld_home_pipeline_words,sizeof ld_home_pipeline_words,(void *)repl_home_pipeline,&g_orig_home_pipeline},
        {LD_HOME_INDICATOR_FUNCTION_OFFSET,LD_HOME_INDICATOR_HOOK_OFFSET,
            ld_home_indicator_words,sizeof ld_home_indicator_words,(void *)repl_home_indicator,&g_orig_home_indicator},
        {LD_HOME_WIDGET_FUNCTION_OFFSET,LD_HOME_WIDGET_HOOK_OFFSET,
            ld_home_widget_words,sizeof ld_home_widget_words,(void *)repl_home_widget,&g_orig_home_widget},
        {LD_DOCK_FUNCTION_SIZE_START,LD_DOCK_FUNCTION_SIZE_SITE,
            ld_dock_function_size_body,sizeof ld_dock_function_size_body,(void *)repl_dock_function_size,&g_orig_dock_function_size},
        {LD_DOCK_ICON_PADDING_START,LD_DOCK_ICON_PADDING_SITE,
            ld_dock_icon_padding_body,sizeof ld_dock_icon_padding_body,(void *)repl_dock_icon_padding,&g_orig_dock_icon_padding},
        {LD_DOCK_BOTTOM_START,LD_DOCK_BOTTOM_SITE,
            ld_dock_bottom_body,sizeof ld_dock_bottom_body,(void *)repl_dock_bottom,&g_orig_dock_bottom},
        {LD_DOCK_CONTAINER_HEIGHT_START,LD_DOCK_CONTAINER_HEIGHT_SITE,
            ld_dock_container_height_body,sizeof ld_dock_container_height_body,(void *)repl_dock_container_height,&g_orig_dock_container_height},
        {LD_DOCK_BACKGROUND_START,LD_DOCK_BACKGROUND_SITE,
            ld_dock_background_words,sizeof ld_dock_background_words,(void *)repl_dock_background,&g_orig_dock_background},
        {LD_DOCK_SPACING_START,LD_DOCK_SPACING_SITE,
            ld_dock_spacing_words,sizeof ld_dock_spacing_words,(void *)repl_dock_spacing,&g_orig_dock_spacing},
    };
    for(size_t i=0;i<sizeof home_hooks/sizeof *home_hooks;i++) {
        if(*home_hooks[i].orig) continue;
        uintptr_t start=0,target=0;
        if(ld_vaddr_to_runtime(&lib,home_hooks[i].start,&start)
                && ld_vaddr_to_runtime(&lib,home_hooks[i].site,&target)
                && executable_range(start,strict?home_hooks[i].size:16)
                && executable_range(target,16)
                && (!strict || !memcmp((void *)start,home_hooks[i].words,home_hooks[i].size))) {
            int status=api->hook((void *)target,home_hooks[i].replace,home_hooks[i].orig);
            if(status==0 && *home_hooks[i].orig) pin_installed_hook(target);
            if(status==0 && *home_hooks[i].orig && home_hooks[i].site==LD_HOME_WIDGET_HOOK_OFFSET)
                ld_vaddr_to_runtime(&lib,LD_HOME_WIDGET_ALLOC_OFFSET,&g_home_widget_allocate);
            ld_blog("home options observer %lx installed result=%d",(unsigned long)home_hooks[i].site,status);
        }
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
    uintptr_t filter=0;
    if(ld_vaddr_to_runtime(&lib,LD_FILTER_VADDR,&filter)
            && executable_range(filter+(strict?0:0x2a4),strict?sizeof ld_filter_words:16)
            && (!strict || !memcmp((void *)filter,ld_filter_words,sizeof ld_filter_words))) {
        result=api->hook((void *)(filter+0x2a4),(void *)repl_filter_grid,&g_orig_filter_return);
        if(result==0 && g_orig_filter_return) pin_installed_hook(filter+0x2a4);
        ld_blog("read-only grid selection installed result=%d",result);
    }
    struct { uintptr_t vaddr; const void *words; size_t size; void *replace; void **orig; } observers[] = {
        {0x918fe0u, ld_delete_item_words, sizeof ld_delete_item_words, (void *)repl_delete_item, &g_orig_delete_item},
        {0x8effa4u, ld_save_batch_words, sizeof ld_save_batch_words, (void *)repl_save_batch, &g_orig_save_batch},
        {0x8e340cu, ld_save_single_words, sizeof ld_save_single_words, (void *)repl_save_single, &g_orig_save_single},
        {0x15e91b4u, ld_load_items_words, sizeof ld_load_items_words, (void *)repl_load_items, &g_orig_load_items},
        {0x18c8f44u, ld_widget_lattice_words, sizeof ld_widget_lattice_words, (void *)repl_widget_lattice, &g_orig_widget_lattice},
        {0x8ecc10u, ld_no_vacant_words, sizeof ld_no_vacant_words, (void *)repl_free_place, &g_orig_no_vacant},
        {0x8f02c0u, ld_process_screen_words, sizeof ld_process_screen_words, (void *)repl_process_screen, &g_orig_process_screen},
    };
    for (size_t i = 0; i < sizeof observers / sizeof *observers; i++) {
        uintptr_t target = 0;
        if (ld_vaddr_to_runtime(&lib, observers[i].vaddr, &target)
                && executable_range(target, strict?observers[i].size:16)
                && (!strict || !memcmp((void *)target, observers[i].words, observers[i].size))) {
            result = api->hook((void *)target, observers[i].replace, observers[i].orig);
            if(result==0 && *observers[i].orig) pin_installed_hook(target);
            ld_blog("layout observer %lx installed result=%d", (unsigned long)observers[i].vaddr, result);
            if(observers[i].vaddr==0x8f02c0u && result==0) {
                g_process_resume=target+0x384;
                int mid_result=api->hook((void *)(target+0x218),(void *)repl_process_mid,&g_orig_process_mid);
                g_layout_mid_ready=mid_result==0 && g_orig_process_mid!=NULL;
                if(g_layout_mid_ready) pin_installed_hook(target+0x218);
                ld_blog("layout continuation installed result=%d",mid_result);
            }
        } else if(target && strict && executable_range(target,observers[i].size)) {
            for(size_t k=0;k<observers[i].size;k+=4) if(((uint32_t *)target)[k/4]!=((const uint32_t *)observers[i].words)[k/4]) {
                ld_blog("observer mismatch %lx +%zx live=%08x expected=%08x",(unsigned long)observers[i].vaddr,k,((uint32_t *)target)[k/4],((const uint32_t *)observers[i].words)[k/4]); break;
            }
        }
    }
    uintptr_t orientation = 0;
    if (ld_vaddr_to_runtime(&lib, 0x8ef670u, &orientation)
            && executable_range(orientation, strict?sizeof ld_orientation_words:16)
            && (!strict || memcmp((const void *)orientation, ld_orientation_words,
                      sizeof ld_orientation_words) == 0)) {
        backup = NULL;
        result = api->hook((void *)orientation, (void *)repl_orientation_observed, &backup);
        if (result == 0 && backup) {
            g_orig_orientation = backup;
            pin_installed_hook(orientation);
            ld_blog("orientation layout transaction installed safety=%d",strict);
        }
    }
    update_layout_ready();
}

static void install_grid_hook(const struct native_api *api) {
    if((!g_grid_cols || !g_grid_rows) && !g_home_options.icon_size_enabled
            && !g_home_options.dock.enabled && !g_home_options.glass_enabled
            && !g_home_options.widget_overrides) return;
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
    if (!api || api->version != 2 || !api->hook) {
        LOGE("unknown native injection ABI");
        return NULL;
    }

    g_api = api;
    load_grid_config();
    ld_prepare_native_glass();
    install_grid_hook(api);
    return on_library_loaded;
}
