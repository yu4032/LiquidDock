#ifndef LIQUIDDOCK_FOLDER_SIZE_H
#define LIQUIDDOCK_FOLDER_SIZE_H

#include "dart_heap.h"
#include "dart_observer.h"
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
            || !ld_read_heap(fp - (vertical ? 0x30 : 0x38), &pitch, sizeof pitch)) return;
    double ws_ratio = ld_ws_icon_ratio(NULL);
    if (ws_ratio != 1.0) icon *= ws_ratio;
    if (!ld_icon_aligned_extent(span, pitch, icon, &extent)) return;
    ld_gse_record_large_folder_dim((int)span_x, (int)span_y, vertical, (float)extent);

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
static void *g_orig_folder_open_title;
static void *g_orig_folder_start_anim_title;
static uintptr_t g_fn_set_is_hide_title;
static size_t g_cling_icon_ctrl_off;
static size_t g_icon_model_off;
static size_t g_icon_hide_rx_off;
static size_t g_icon_opacity_off;
static size_t g_set_hide_arg_pp_off;
static size_t g_sentinel_pp_off;

static inline uintptr_t ld_ft_decode_bl(uintptr_t pc, uint32_t w)
{
    if ((w & 0xfc000000u) != 0x94000000u) return 0;
    int32_t imm26 = (int32_t)(w & 0x03ffffffu);
    if (imm26 & 0x02000000) imm26 |= (int32_t)0xfc000000u;
    return (uintptr_t)((intptr_t)pc + ((intptr_t)imm26 << 2));
}

static bool ld_ft_decode_icon_ctrl_off(const uint32_t *code, size_t n, uintptr_t get_fn,
                                       size_t *out_off)
{
    for (size_t i = 0; i < n; i++) {
        uintptr_t pc = (uintptr_t)(code + i);
        if (ld_ft_decode_bl(pc, code[i]) != get_fn) continue;
        size_t lim = (i + 7 < n) ? i + 7 : n;
        for (size_t j = i + 1; j < lim; j++) {
            uint32_t w = code[j];
            if ((w & 0xffe00c1fu) == 0xb8000000u) {
                uint32_t rn = (w >> 5) & 31u;
                size_t imm9 = (size_t)((w >> 12) & 0x1ffu);
                if (rn != 29u && imm9 >= 0x0fu && imm9 < 0x180u) {
                    *out_off = imm9;
                    return true;
                }
            }
            if ((w & 0xffc0001fu) == 0xb9000000u) {
                uint32_t rn = (w >> 5) & 31u;
                size_t off = (size_t)((w >> 10) & 0xfffu) * 4u;
                if (rn != 29u && off >= 0x0fu && off < 0x180u) {
                    *out_off = off;
                    return true;
                }
            }
            if (j + 1 < lim && (w & 0xffe00000u) == 0xd2800000u) {
                uint32_t rt = w & 31u;
                uint32_t w2 = code[j + 1];
                if ((w2 & 0xffe0cc1fu) == 0xb8204800u) {
                    uint32_t rm = (w2 >> 16) & 31u;
                    uint32_t rn = (w2 >> 5) & 31u;
                    size_t off = (size_t)((w >> 5) & 0xffffu);
                    if (rm == rt && rn != 29u && off >= 0x0fu && off < 0x400u) {
                        *out_off = off;
                        return true;
                    }
                }
            }
        }
    }
    return false;
}

static bool ld_ft_decode_arg_desc_pp_off(const uint32_t *code, size_t n, uintptr_t set_fn,
                                         size_t *out_off)
{
    for (size_t i = 0; i < n; i++) {
        if (ld_ft_decode_bl((uintptr_t)(code + i), code[i]) != set_fn) continue;
        size_t lo = (i >= 6) ? i - 6 : 0;
        for (size_t j = i; j-- > lo;) {
            uint32_t w = code[j];
            if ((w & 0xffc003ffu) == 0xf9400364u) {
                *out_off = (size_t)((w >> 10) & 0xfffu) * 8u;
                return true;
            }
            if (j > lo && (code[j - 1] & 0xffc003e0u) == 0x91400360u
                    && (w & 0xffc0001fu) == 0xf9400004u) {
                uint32_t rd = code[j - 1] & 31u;
                uint32_t rn = (w >> 5) & 31u;
                if (rd == rn) {
                    size_t hi = (size_t)((code[j - 1] >> 10) & 0xfffu) << 12;
                    size_t imm = (size_t)((w >> 10) & 0xfffu) * 8u;
                    *out_off = hi + imm;
                    return true;
                }
            }
        }
    }
    return false;
}

static bool ld_ft_decode_set_hide_fields(uintptr_t set_fn, size_t sz,
                                         size_t *out_rx, size_t *out_op, size_t *out_sentinel)
{
    if (!set_fn || sz < 0x80u || !executable_range(set_fn, sz)) return false;
    const uint32_t *code = (const uint32_t *)set_fn;
    size_t n = sz / 4u;
    size_t rx = 0, op = 0, sentinel = 0;

    /* Find RxBool hide-title offset loaded right after a Dart bool tbz check and passed to BL. */
    for (size_t i = 0; i + 4 < n; i++) {
        uint32_t w = code[i];
        if ((w & 0x7e000000u) != 0x36000000u || ((w >> 19) & 0x1fu) != 4u) continue;
        for (size_t j = i + 1; j < i + 4 && j + 2 < n; j++) {
            uint32_t l = code[j];
            size_t cand = 0;
            if ((l & 0xffe00c00u) == 0xb8400000u) {
                uint32_t rn = (l >> 5) & 31u;
                size_t imm9 = (size_t)((l >> 12) & 0x1ffu);
                if (rn != 29u && imm9 >= 0x20u && imm9 < 0x1ffu) cand = imm9;
            }
            if (!cand) continue;
            for (size_t k = j + 1; k < j + 5 && k < n; k++) {
                if (ld_ft_decode_bl((uintptr_t)(code + k), code[k])) {
                    rx = cand;
                    break;
                }
            }
            if (rx) break;
        }
        if (rx) break;
    }

    /* Find opacity Value field + Dart late sentinel pool offset near cmp wR, w16. */
    for (size_t i = 0; i + 4 < n; i++) {
        size_t cand_op = 0;
        uint32_t reg_v = 32u;
        size_t cmp_scan_start = i + 1;
        uint32_t w0 = code[i];
        if ((w0 & 0xffe00000u) == 0xd2800000u) {
            uint32_t rt = w0 & 31u;
            uint32_t w1 = code[i + 1];
            if ((w1 & 0xffe0cc00u) == 0xb8604800u
                    && ((w1 >> 16) & 31u) == rt && ((w1 >> 5) & 31u) != 29u) {
                cand_op = (size_t)((w0 >> 5) & 0xffffu);
                reg_v = w1 & 31u;
                cmp_scan_start = i + 2;
            }
        } else if ((w0 & 0xffe00c00u) == 0xb8400000u && ((w0 >> 5) & 31u) != 29u) {
            cand_op = (size_t)((w0 >> 12) & 0x1ffu);
            reg_v = w0 & 31u;
        }
        if (cand_op < 0x20u || cand_op >= 0x400u || reg_v >= 31u) continue;
        for (size_t k = cmp_scan_start; k + 1 < cmp_scan_start + 4 && k + 1 < n; k++) {
            uint32_t wl = code[k], wc = code[k + 1];
            if ((wl & 0xffc003ffu) == 0xf9400370u && wc == (0x6b10001fu | (reg_v << 5))) {
                op = cand_op;
                sentinel = (size_t)((wl >> 10) & 0xfffu) * 8u;
                break;
            }
        }
        if (op) break;
    }

    if (!rx || !op || !sentinel) return false;
    *out_rx = rx;
    *out_op = op;
    *out_sentinel = sentinel;
    return true;
}

static bool ld_ft_decode_model_off(uintptr_t vis_site, size_t *out_model_off)
{
    if (!vis_site || !executable_range(vis_site - 0xa0u, 0xa0u)) return false;
    const uint32_t *code = (const uint32_t *)(vis_site - 0xa0u);
    size_t n = 0xa0u / 4u;
    for (size_t i = n; i-- > 1;) {
        uint32_t w_cmp = code[i];
        uint32_t w_ldr = code[i - 1];
        if ((w_ldr & 0xfffffc00u) == 0xf840f000u && (w_cmp & 0xfffffc1fu) == 0xf100081fu
                && (w_ldr & 31u) == ((w_cmp >> 5) & 31u)) {
            uint32_t base_reg = (w_ldr >> 5) & 31u;
            size_t lo = (i >= 9) ? i - 9 : 0;
            for (size_t j = i - 1; j-- > lo;) {
                uint32_t wm = code[j];
                if ((wm & 0xffe00c00u) == 0xb8400000u && (wm & 31u) == base_reg
                        && ((wm >> 5) & 31u) != 29u) {
                    size_t off = (size_t)((wm >> 12) & 0x1ffu);
                    if (off >= 0x10u && off < 0x1ffu) {
                        *out_model_off = off;
                        return true;
                    }
                }
            }
        }
    }
    return false;
}

static void ld_folder_toggle_open_title(struct ld_dart_context *c, bool hide)
{
    if (!__atomic_load_n(&g_show_big_folder_title, __ATOMIC_ACQUIRE)) return;
    if (!g_fn_set_is_hide_title || !g_cling_icon_ctrl_off || !g_icon_model_off
            || !g_icon_hide_rx_off || !g_icon_opacity_off || !g_set_hide_arg_pp_off) return;
    uintptr_t cling = (uintptr_t)c->x[1];
    uintptr_t null_obj = (uintptr_t)c->x[22];
    uintptr_t sentinel = 0;
    if (g_sentinel_pp_off) {
        ld_read_heap((uintptr_t)c->x[27] + g_sentinel_pp_off, &sentinel, sizeof sentinel);
    }
    uintptr_t icon_ctrl = ld_heap_pointer(c, cling, g_cling_icon_ctrl_off);
    if (!icon_ctrl || icon_ctrl == null_obj || (sentinel && icon_ctrl == sentinel)) return;
    uintptr_t model = ld_heap_pointer(c, icon_ctrl, g_icon_model_off);
    if (!model || model == null_obj || (sentinel && model == sentinel)) return;
    if (ld_heap_i64(model, 0x47) != -100) return;
    int64_t sx = ld_heap_i64(model, 0x1f);
    int64_t sy = ld_heap_i64(model, 0x27);
    if (sx < 1 || sx > 10 || sy < 1 || sy > 10 || (sx == 1 && sy == 1)) return;

    uintptr_t rx_hide = ld_heap_pointer(c, icon_ctrl, g_icon_hide_rx_off);
    uintptr_t op_val = ld_heap_pointer(c, icon_ctrl, g_icon_opacity_off);
    if (!rx_hide || rx_hide == null_obj || (sentinel && rx_hide == sentinel)) return;
    if (!op_val || op_val == null_obj || (sentinel && op_val == sentinel)) return;

    uintptr_t arg_desc = 0;
    if (!ld_read_heap((uintptr_t)c->x[27] + g_set_hide_arg_pp_off, &arg_desc, sizeof arg_desc)
            || !arg_desc || arg_desc == null_obj || (sentinel && arg_desc == sentinel)) return;

    uint64_t args[8] = {
        null_obj,
        icon_ctrl,
        null_obj + (hide ? 0x20u : 0x30u),
        0,
        arg_desc,
        0, 0, 0,
    };
    (void)ld_dart_call_raw(c, (void *)g_fn_set_is_hide_title, args, 0);
}

__attribute__((used)) static void ld_observe_folder_open_title(struct ld_dart_context *c)
{
    ld_folder_toggle_open_title(c, true);
}

__attribute__((used)) static void ld_observe_folder_start_anim_title(struct ld_dart_context *c)
{
    /* startAnim(this, isOpen): x2 is Dart false (null + 0x30) when close anim starts. */
    if ((uintptr_t)c->x[2] == (uintptr_t)c->x[22] + 0x30u) {
        ld_folder_toggle_open_title(c, false);
    }
}

LD_DART_OBSERVER(repl_folder_open_title, ld_observe_folder_open_title, g_orig_folder_open_title)
LD_DART_OBSERVER(repl_folder_start_anim_title, ld_observe_folder_start_anim_title,
                 g_orig_folder_start_anim_title)

__attribute__((used)) static void ld_observe_folder_title_visibility(struct ld_dart_context *c)
{
    c->reserved = (uintptr_t)g_orig_folder_title_visibility;
    uintptr_t dart_false = c->x[22] + 0x30;
    uintptr_t context = 0;
    if (!ld_read_heap(c->x[29] - 8, &context, sizeof context)
            || !(context & 1) || context != c->x[0]) return;
    uintptr_t controller = ld_heap_pointer(c, context, 0xf);
    size_t model_off = g_icon_model_off ? g_icon_model_off : 0x83u;
    uintptr_t model = controller ? ld_heap_pointer(c, controller, model_off) : 0;
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

static void ld_folder_size_install(const struct native_api *api, const ld_lib_info *lib,
                                   const struct ld_grid_conf *conf)
{
    if (!conf->big_folder_enabled) return;

    if (conf->big_folder_align_icon && conf->folder_size_targets_present && !g_orig_aligned_width) {
        uintptr_t functions[2];
        const size_t minimum_size[] = {0x40, 0x200};
        int targets_ok = 1;
        for (size_t i = 0; i < 2; ++i) {
            if (conf->folder_size_size[i] < minimum_size[i]
                    || !ld_vaddr_to_runtime(lib, conf->folder_size_vaddr[i], &functions[i])
                    || !executable_range(functions[i], conf->folder_size_size[i])) {
                ld_blog("folder_size skip: bad target[%zu] vaddr=%#llx sz=%zu",
                        i, (unsigned long long)conf->folder_size_vaddr[i], conf->folder_size_size[i]);
                targets_ok = 0;
                break;
            }
        }
        if (targets_ok) {
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
            } else if (!ld_install_alignment_site(api, site_spec, spec_words,
                        repl_aligned_width, &g_orig_aligned_width)
                    || !ld_install_alignment_site(api, site_pw, percent_width_words,
                        repl_percent_width, &g_orig_percent_width)
                    || !ld_install_alignment_site(api, site_ph, percent_height_words,
                        repl_percent_height, &g_orig_percent_height)) {
                ld_blog("folder_size skip: hook install failed spec=%p pw=%p ph=%p",
                        g_orig_aligned_width, g_orig_percent_width, g_orig_percent_height);
            } else {
                __atomic_store_n(&g_align_folders, 1, __ATOMIC_RELEASE);
                ld_blog("icon aligned folder sizing installed");
            }
        }
    }

    if (conf->folder_size_targets_present && conf->folder_size_vaddr[2]) {
        __atomic_store_n(&g_show_big_folder_title, conf->big_folder_title_enabled, __ATOMIC_RELEASE);
        if (!g_orig_folder_title_visibility) {
            uintptr_t builder = 0;
            const uint32_t visibility_words[] = {0xf85e03a1, 0xf85e83a2, 0xf81d83a3, 0xb841f044};
            if (ld_vaddr_to_runtime(lib, conf->folder_size_vaddr[2], &builder)) {
                size_t scan_sz = conf->folder_size_size[2] + 0xa00;
                uintptr_t vis_site = ld_find_words_in_range(builder, scan_sz, visibility_words, 4);
                if (vis_site && ld_install_alignment_site(api, vis_site, visibility_words,
                        repl_folder_title_visibility, &g_orig_folder_title_visibility)) {
                    ld_blog("big folder title visibility installed: show=%d", conf->big_folder_title_enabled);
                    if (conf->folder_title_anim_present && !g_orig_folder_open_title) {
                        uintptr_t fns[5] = {0};
                        int ok = 1;
                        for (size_t i = 0; i < 5; i++) {
                            if (conf->folder_title_anim_size[i] < 0x20u
                                    || !ld_vaddr_to_runtime(lib, conf->folder_title_anim_vaddr[i], &fns[i])
                                    || !executable_range(fns[i], conf->folder_title_anim_size[i])) {
                                ok = 0;
                                break;
                            }
                        }
                        if (ok && ld_ft_decode_icon_ctrl_off((const uint32_t *)fns[2],
                                    conf->folder_title_anim_size[2] / 4u, fns[4], &g_cling_icon_ctrl_off)
                                && ld_ft_decode_arg_desc_pp_off((const uint32_t *)fns[2],
                                    conf->folder_title_anim_size[2] / 4u, fns[3], &g_set_hide_arg_pp_off)
                                && ld_ft_decode_set_hide_fields(fns[3], conf->folder_title_anim_size[3],
                                    &g_icon_hide_rx_off, &g_icon_opacity_off, &g_sentinel_pp_off)
                                && ld_ft_decode_model_off(vis_site, &g_icon_model_off)
                                && ld_hook_site_safe(fns[0], "folder open title")
                                && ld_hook_site_safe(fns[1], "folder close title")) {
                            g_fn_set_is_hide_title = fns[3];
                            if (api->hook((void *)fns[0], repl_folder_open_title, &g_orig_folder_open_title) == 0
                                    && ld_pin_code_pages_soft(fns[0], 16)
                                    && api->hook((void *)fns[1], repl_folder_start_anim_title,
                                                 &g_orig_folder_start_anim_title) == 0
                                    && ld_pin_code_pages_soft(fns[1], 16)) {
                                ld_blog("big folder title open/close sync installed: cling_off=%#zx "
                                        "model=%#zx rx=%#zx op=%#zx arg_pp=%#zx sent_pp=%#zx",
                                        g_cling_icon_ctrl_off, g_icon_model_off, g_icon_hide_rx_off,
                                        g_icon_opacity_off, g_set_hide_arg_pp_off, g_sentinel_pp_off);
                            } else {
                                ld_blog("big folder title open/close hook failed");
                            }
                        } else {
                            ld_blog("big folder title open/close decode miss ok=%d cling=%#zx arg=%#zx "
                                    "rx=%#zx op=%#zx model=%#zx",
                                    ok, g_cling_icon_ctrl_off, g_set_hide_arg_pp_off,
                                    g_icon_hide_rx_off, g_icon_opacity_off, g_icon_model_off);
                        }
                    }
                }
            }
        }
    }
}
#endif
