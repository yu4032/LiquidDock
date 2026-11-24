#ifndef LIQUIDDOCK_FOLDER_SIZE_H
#define LIQUIDDOCK_FOLDER_SIZE_H

#include "dart_heap.h"
#include "folder_size_policy.h"
#include "folder_preview_inset.h"
#include "home_widget_bridge.h"

static void *g_orig_percent_width, *g_orig_percent_height, *g_orig_aligned_width;
static uintptr_t g_folder_measure_start, g_folder_measure_end;
static int g_align_folders;

/* The validated calls pass spans as Dart small integers. */
static int64_t ld_size_spec_span(uint32_t value)
{
    return (value & 1u) ? 0 : ((int32_t)value >> 1);
}

static void ld_align_size_spec(struct ld_dart_context *c, void *original,
                               int percent, int vertical)
{
    c->reserved = (uintptr_t)original;
    uintptr_t fp = c->x[29], return_pc = 0;
    if (!ld_read_heap(fp + 8, &return_pc, sizeof return_pc)) return;
    if (return_pc < g_folder_measure_start || return_pc >= g_folder_measure_end
            || !__atomic_load_n(&g_align_folders, __ATOMIC_ACQUIRE)) return;

    uintptr_t args = 0, grid = 0;
    if (!ld_read_heap(fp - 0x28, &args, sizeof args) || !(args & 1)
            || !ld_read_heap(fp - 8, &grid, sizeof grid) || !(grid & 1)) return;
    int64_t span_x = ld_size_spec_span(ld_heap_u32(args, 0xf));
    int64_t span_y = ld_size_spec_span(ld_heap_u32(args, 0x13));
    if (span_x < 1 || span_x > 10 || span_y < 1 || span_y > 10
            || (span_x == 1 && span_y == 1)) return;

    double pitch, icon, extent;
    int64_t span = vertical ? span_y : span_x;
    uintptr_t icon_record = ld_heap_pointer(c, grid, 0x6b);
    if (!icon_record || !ld_read_heap(icon_record + 0x17, &icon, sizeof icon)
            || !ld_read_heap(fp - (vertical ? 0x30 : 0x38), &pitch, sizeof pitch)
            || !ld_icon_aligned_extent(span, pitch, icon, &extent)) return;

    if (percent) {
        // Original code evaluates (span - percentage) * pitch. Keep the
        // percentage register for its later uses and replace the minuend.
        double percentage;
        memcpy(&percentage, c->q[0], sizeof percentage);
        if (!isfinite(percentage)) return;
        double adjusted_span = extent / pitch + percentage;
        memcpy(c->q[vertical ? 1 : 2], &adjusted_span, sizeof adjusted_span);
        // The vertical path reads its original content height from this
        // calculation, then adds its own top inset.
    } else {
        // The other branch already uses icon height. Match it on X.
        double inset = pitch * (double)span - extent;
        memcpy(c->q[2], &inset, sizeof inset);
    }

    static unsigned reports;
    if (__atomic_fetch_add(&reports, 1u, __ATOMIC_RELAXED) < 12u)
        ld_blog("icon aligned folder: %lldx%lld %s=%.2f",
                (long long)span_x, (long long)span_y,
                vertical ? "height" : "width", extent);
}

__attribute__((used)) static void ld_observe_aligned_width(struct ld_dart_context *c)
{
    ld_align_size_spec(c, g_orig_aligned_width, 0, 0);
}
__attribute__((used)) static void ld_observe_percent_width(struct ld_dart_context *c)
{
    ld_align_size_spec(c, g_orig_percent_width, 1, 0);
}
__attribute__((used)) static void ld_observe_percent_height(struct ld_dart_context *c)
{
    ld_align_size_spec(c, g_orig_percent_height, 1, 1);
}

LD_DART_ROUTED_OBSERVER(repl_aligned_width, ld_observe_aligned_width)
LD_DART_ROUTED_OBSERVER(repl_percent_width, ld_observe_percent_width)
LD_DART_ROUTED_OBSERVER(repl_percent_height, ld_observe_percent_height)

static void *g_orig_folder_title_visibility;
static int g_show_big_folder_title;

__attribute__((used)) static void ld_observe_folder_title_visibility(struct ld_dart_context *c)
{
    c->reserved = (uintptr_t)g_orig_folder_title_visibility;
    uintptr_t dart_false = c->x[22] + 0x30;
    uintptr_t context = 0;
    if (!ld_read_heap(c->x[29] - 8, &context, sizeof context)
            || !(context & 1) || context != c->x[0]) return;
    uintptr_t controller = ld_heap_pointer(c, context, 0xf);
    uintptr_t model = controller ? ld_heap_pointer(c, controller, 0x83) : 0;
    if (!model || ld_heap_i64(model, 0x47) != -100) return;

    int64_t sx = ld_heap_i64(model, 0x1f);
    int64_t sy = ld_heap_i64(model, 0x27);
    if (sx < 1 || sx > 10 || sy < 1 || sy > 10 || (sx == 1 && sy == 1)) return;
    int show = __atomic_load_n(&g_show_big_folder_title, __ATOMIC_ACQUIRE);
    if (!show) {
        c->x[3] = dart_false;
    } else if (c->x[2] == dart_false && c->x[3] == dart_false
            && ld_heap_i64(model, 0xf) == (int64_t)c->x[1]) {
        // Only the type check reaches the join with false in both registers.
        // Other paths still obey Launcher animation and title visibility.
        c->x[3] = c->x[22] + 0x20;
    }
}

LD_DART_ROUTED_OBSERVER(repl_folder_title_visibility, ld_observe_folder_title_visibility)

static int ld_install_alignment_site(const struct native_api *api, uintptr_t site,
                                     const uint32_t words[4], void *replacement, void **original)
{
    if (*original || !executable_range(site, 16)
            || memcmp((const void *)site, words, 16) != 0
            || !ld_hook_site_safe(site, "icon aligned sizing")) return 0;
    return api->hook((void *)site, replacement, original) == 0 && *original
            && ld_pin_code_pages_soft(site, 16);
}

static uintptr_t ld_find_words_in_range(uintptr_t start, size_t size,
                                        const uint32_t *words, size_t count)
{
    if (!start || size < count * 4 || !executable_range(start, size)) return 0;
    const uint32_t *p = (const uint32_t *)start;
    size_t n = size / 4;
    for (size_t i = 0; i + count <= n; i++) {
        if (memcmp(p + i, words, count * 4) == 0) return (uintptr_t)(p + i);
    }
    return 0;
}

static void ld_install_folder_preview_inset(const struct native_api *api, const ld_lib_info *lib);
static void ld_folder_size_install(const struct native_api *api, const ld_lib_info *lib,
                                   const struct ld_grid_conf *conf)
{
    if (!conf->big_folder_enabled) return;

    ld_install_folder_preview_inset(api, lib);

    if (!conf->big_folder_align_icon || !conf->folder_size_targets_present
            || g_orig_aligned_width) {
        static int logged_skip;
        if (!g_orig_aligned_width && !logged_skip) {
            logged_skip = 1;
            ld_blog("folder_size skip: align=%d present=%d",
                    conf->big_folder_align_icon, conf->folder_size_targets_present);
        }
        return;
    }

    uintptr_t functions[2];
    const size_t minimum_size[] = {0x40, 0x200};
    for (size_t i = 0; i < 2; ++i) {
        if (conf->folder_size_size[i] < minimum_size[i]
                || !ld_vaddr_to_runtime(lib, conf->folder_size_vaddr[i], &functions[i])
                || !executable_range(functions[i], conf->folder_size_size[i])) {
            ld_blog("folder_size skip: bad target[%zu] vaddr=%#llx sz=%zu",
                    i, (unsigned long long)conf->folder_size_vaddr[i], conf->folder_size_size[i]);
            return;
        }
    }
    uintptr_t spec = functions[1];
    size_t spec_size = conf->folder_size_size[1];
    g_folder_measure_start = functions[0];
    g_folder_measure_end = functions[0] + conf->folder_size_size[0];
    const uint32_t percent_width_words[] = {0x1e603844, 0xfc5c83a0, 0x1e640802, 0xa9460740};
    const uint32_t percent_height_words[] = {0x1e603822, 0xfc5d03a1, 0x1e620823, 0xfc1c03a3};
    const uint32_t spec_words[] = {0x9e620020, 0xfc5c83a1, 0x1e600823, 0x1e623860};
    uintptr_t site_spec = ld_find_words_in_range(spec, spec_size, spec_words, 4);
    uintptr_t site_pw = ld_find_words_in_range(spec, spec_size, percent_width_words, 4);
    uintptr_t site_ph = ld_find_words_in_range(spec, spec_size, percent_height_words, 4);
    if (!site_spec || !site_pw || !site_ph) {
        ld_blog("icon aligned folder sizing pattern miss: spec=%#lx pw=%#lx ph=%#lx",
                (unsigned long)site_spec, (unsigned long)site_pw, (unsigned long)site_ph);
        return;
    }
    if (!ld_install_alignment_site(api, site_spec, spec_words,
                repl_aligned_width, &g_orig_aligned_width)
            || !ld_install_alignment_site(api, site_pw, percent_width_words,
                repl_percent_width, &g_orig_percent_width)
            || !ld_install_alignment_site(api, site_ph, percent_height_words,
                repl_percent_height, &g_orig_percent_height)) {
        ld_blog("folder_size skip: hook install failed spec=%p pw=%p ph=%p",
                g_orig_aligned_width, g_orig_percent_width, g_orig_percent_height);
        return;
    }

    __atomic_store_n(&g_align_folders, 1, __ATOMIC_RELEASE);
    ld_blog("icon aligned folder sizing installed");
    if (conf->folder_size_vaddr[2]) {
        __atomic_store_n(&g_show_big_folder_title, conf->big_folder_title_enabled, __ATOMIC_RELEASE);
        uintptr_t builder = 0;
        const uint32_t visibility_words[] = {0xf85e03a1, 0xf85e83a2, 0xf81d83a3, 0xb841f044};
        if (ld_vaddr_to_runtime(lib, conf->folder_size_vaddr[2], &builder)) {
            size_t scan_sz = conf->folder_size_size[2] + 0xa00;
            uintptr_t vis_site = ld_find_words_in_range(builder, scan_sz, visibility_words, 4);
            if (vis_site && ld_install_alignment_site(api, vis_site, visibility_words,
                    repl_folder_title_visibility, &g_orig_folder_title_visibility)) {
                ld_blog("big folder title visibility installed: show=%d", conf->big_folder_title_enabled);
            }
        }
    }
}


static void *g_orig_preview_inset;
static uintptr_t g_preview_inset_resume;
static volatile int g_fls_ten_icon_inset_active __attribute__((used));

__attribute__((used)) static void ld_observe_folder_preview_inset(struct ld_dart_context *c)
{
    c->reserved = (uintptr_t)g_orig_preview_inset;
    /* When folder_layout_sync directly updates FolderLayout4x2Grid10.previewItemInfos,
     * both preview rendering and open/close/return animations read the adjusted rects
     * directly; skip the render-only fallback to avoid double-applying the inset. */
    if (__atomic_load_n(&g_fls_ten_icon_inset_active, __ATOMIC_ACQUIRE)) return;
    double inset = (double)__atomic_load_n(&g_home_options.folder_preview_inset, __ATOMIC_ACQUIRE);
    if (inset == 0.0) return;

    // At this point the preview builder has the controller, strategy and
    // original icon rectangle in registers. The strategy may be shared.
    uintptr_t controller = c->x[24], strategy = c->x[25], icon = c->x[20];
    uintptr_t model = ld_heap_pointer(c, controller, 0x83);
    if (!model || ld_heap_i64(model, 0x47) != -100) return;
    if (ld_heap_i64(model, 0xf) != 53
            || ld_heap_i64(model, 0x1f) != 4
            || ld_heap_i64(model, 0x27) != 2) return;
    if (!(strategy & 1) || !(icon & 1)) return;

    uintptr_t fp = c->x[29];
    double frame_width, frame_height;
    struct ld_folder_preview_rect original, adjusted;
    memcpy(&original.y, c->q[2], sizeof original.y);
    if (!ld_read_heap(fp - 0x78, &original.x, sizeof original.x)
            || !ld_read_heap(icon + 7, &original.width, sizeof original.width)
            || !ld_read_heap(icon + 0xf, &original.height, sizeof original.height)
            || !ld_read_heap(strategy + 0xf, &frame_width, sizeof frame_width)
            || !ld_read_heap(strategy + 0x17, &frame_height, sizeof frame_height)
            || frame_width <= frame_height * 1.5) return;

    static double cached_pad0 = 0.0, cached_big_w = 0.0;
    static double cached_fw = 0.0, cached_fh = 0.0;
    if (original.x < frame_width * 0.25 && original.y < frame_height * 0.4 && original.width > 35.0) {
        cached_pad0 = fmin(original.x, original.y);
        cached_big_w = original.width;
        cached_fw = frame_width;
        cached_fh = frame_height;
    }
    int cache_matches = cached_pad0 > 1.0 && cached_big_w > 1.0
            && fabs(frame_width - cached_fw) < 1.0 && fabs(frame_height - cached_fh) < 1.0;
    double pad0 = cache_matches ? cached_pad0 : fmin(frame_width, frame_height) * 0.1338;
    double big_w = cache_matches ? cached_big_w : original.width;
    if (!ld_inset_folder_preview(frame_width, frame_height, pad0, big_w, inset, &original, &adjusted)) return;

    // Write only this build's transient frame. Leave shared preview data intact.
    double locals[] = {adjusted.y, adjusted.width, adjusted.x, adjusted.height};
    struct iovec src = {locals, sizeof locals}, dest = {(void *)(fp - 0x88), sizeof locals};
    if (syscall(SYS_process_vm_writev, getpid(), &src, 1, &dest, 1, 0) != (ssize_t)sizeof locals)
        return;
    memcpy(c->q[0], &adjusted.width, sizeof adjusted.width);
    memcpy(c->q[1], &adjusted.height, sizeof adjusted.height);
    memcpy(c->q[2], &adjusted.y, sizeof adjusted.y);
    c->reserved = g_preview_inset_resume;
}

LD_DART_ROUTED_OBSERVER(repl_folder_preview_inset, ld_observe_folder_preview_inset)

static void ld_install_folder_preview_inset(const struct native_api *api, const ld_lib_info *lib)
{
    if (g_orig_preview_inset) return;
    const uint32_t render_words[] = {0xfc1783a2, 0xfc407280, 0xfc1803a0, 0xfc40f281};
    uintptr_t site = ld_find_aot_pattern(lib, 0x14868e8, render_words, 4);
    if (!site) return;
    g_preview_inset_resume = site + 16;
    if (ld_install_alignment_site(api, site, render_words,
                repl_folder_preview_inset, &g_orig_preview_inset)) {
        ld_blog("ten-icon folder preview inset observer installed at %#lx", (unsigned long)site);
    }
}
#endif
