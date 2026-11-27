/* Opened big-folder Portrait/custom-grid calibration fix.
 *
 * When custom folder_grid_cols causes rawGridW = cols * rawCellW + (cols-1)*crossS
 * to exceed availW - 2*(outerHPad + 1) (e.g. Portrait 6x3), Flutter clamps
 * FolderGridView's SizedBox(width: gridWidth) to availW while FolderCling /
 * FolderAnimController still compute uncalibrated rawCellW/rawCellH and negative
 * gridLeftMargin, producing a linear (dx, dy) snap on open/close.
 *
 * When rawGridW <= availW - 2*(outerHPad + 1) (e.g. Landscape), every hook is a
 * strict no-op so existing Landscape animations stay completely untouched.
 */
#ifndef LD_FOLDER_OPEN_FIX_H
#define LD_FOLDER_OPEN_FIX_H

#include "dart_observer.h"
#include "aot_scanner.h"
#include "bootlog.h"
#include "executable_range.h"
#include "grid_conf.h"
#include "lib_base.h"
#include "native_api.h"
#include "screen_rotation.h"

#include <math.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

_Static_assert(sizeof(struct ld_dart_context) == 0x310u,
               "ld_dart_context layout mismatch");

struct ld_fof_state {
    int ready;
    int installed_count;

    uint32_t fgvc_cross_off;
    uint32_t fgvc_gw_off;

    uint32_t cling_fgvc_off;
    uint32_t cling_left_margin_off;
    int32_t cal_cling_fp_off;

    uint32_t globals_cols_off;
    uint32_t globals_gc_off;
    uint32_t gc_rx_off;
    uint32_t rx_val_off;
    uint32_t rx_cfg_off;
    uint32_t cfg_w_off;
    uint32_t cfg_h_off;

    uint32_t devcfg_globals_off;
    uint32_t devcfg_obj_off;
    uint32_t devcfg_screenw_off;
    uint32_t globals_outer_hpad_off;

    uint32_t anim_cell_w_off;
    uint32_t anim_cell_h_off;
    uint32_t anim_icon_w_off;
    uint32_t anim_icon_h_off;
    uint32_t anim_title_h_off;
    uint32_t anim_pad_left_off;
    uint32_t anim_pad_top_off;

    uintptr_t site_fgvc_cell_w;
    uintptr_t site_fgvc_cell_h;
    uintptr_t site_calibrate_h;
    uintptr_t site_cal_pos_mid;
    uintptr_t site_calc_pos_x;
    uintptr_t site_refresh_cached;

    double last_cross_s;
    double last_avail_w;
    int last_avail_land;
    int last_avail_rot;
    unsigned log_budget;
};

static struct ld_fof_state g_fof = {
    .last_cross_s = -1.0,
    .last_avail_w = -1.0,
    .last_avail_land = -1,
    .last_avail_rot = -1,
    .log_budget = 16u,
};

static void *g_orig_fof_cell_w __attribute__((used));
static void *g_orig_fof_cell_h __attribute__((used));
static void *g_orig_fof_calibrate_h __attribute__((used));
static void *g_orig_fof_cal_pos_mid __attribute__((used));
static void *g_orig_fof_calc_pos_x __attribute__((used));
static void *g_orig_fof_refresh_cached __attribute__((used));

/* ---- ARM64 semantic instruction decoders ---- */

static inline bool ld_fof_is_ldr_x0_x26_78(uint32_t w) {
    return w == 0xf9403f40u;
}

static inline bool ld_fof_decode_ldr_x0_x0_uimm(uint32_t w, uint32_t *off_out) {
    if ((w & 0xffc003ffu) != 0xf9400000u) return false;
    *off_out = ((w >> 10) & 0xfffu) << 3;
    return true;
}

static inline int32_t ld_fof_sign_ext9(uint32_t w) {
    int32_t v = (int32_t)((w >> 12) & 0x1ffu);
    if (v & 0x100) v -= 0x200;
    return v;
}

static inline bool ld_fof_decode_ldur_x(uint32_t w, uint32_t rt, uint32_t rn, int32_t *imm_out) {
    if ((w & 0xffe00fffu) != (0xf8400000u | (rn << 5) | rt)) return false;
    *imm_out = ld_fof_sign_ext9(w);
    return true;
}

static inline bool ld_fof_decode_ldur_w(uint32_t w, uint32_t rt, uint32_t rn, int32_t *imm_out) {
    if ((w & 0xffe00fffu) != (0xb8400000u | (rn << 5) | rt)) return false;
    *imm_out = ld_fof_sign_ext9(w);
    return true;
}

static inline bool ld_fof_decode_ldur_d(uint32_t w, uint32_t rt, uint32_t rn, int32_t *imm_out) {
    if ((w & 0xffe00fffu) != (0xfc400000u | (rn << 5) | rt)) return false;
    *imm_out = ld_fof_sign_ext9(w);
    return true;
}

static inline bool ld_fof_decode_stur_d(uint32_t w, uint32_t rt, uint32_t rn, int32_t *imm_out) {
    if ((w & 0xffe00fffu) != (0xfc000000u | (rn << 5) | rt)) return false;
    *imm_out = ld_fof_sign_ext9(w);
    return true;
}

static inline bool ld_fof_decode_bl(uintptr_t pc, uint32_t w, uintptr_t *tgt_out) {
    if ((w & 0xfc000000u) != 0x94000000u) return false;
    int32_t imm26 = (int32_t)(w & 0x03ffffffu);
    if (imm26 & 0x02000000) imm26 -= 0x04000000;
    *tgt_out = (uintptr_t)((intptr_t)pc + ((intptr_t)imm26 << 2));
    return true;
}

static inline bool ld_fof_decode_branch_target(uintptr_t pc, uint32_t w, uintptr_t *tgt_out) {
    uint32_t top6 = w & 0xfc000000u;
    if (top6 == 0x14000000u || top6 == 0x94000000u) {
        int32_t imm26 = (int32_t)(w & 0x03ffffffu);
        if (imm26 & 0x02000000) imm26 -= 0x04000000;
        *tgt_out = (uintptr_t)((intptr_t)pc + ((intptr_t)imm26 << 2));
        return true;
    }
    if ((w & 0xff000010u) == 0x54000000u || (w & 0x7e000000u) == 0x34000000u) {
        int32_t imm19 = (int32_t)((w >> 5) & 0x7ffffu);
        if (imm19 & 0x40000) imm19 -= 0x80000;
        *tgt_out = (uintptr_t)((intptr_t)pc + ((intptr_t)imm19 << 2));
        return true;
    }
    if ((w & 0x7e000000u) == 0x36000000u) {
        int32_t imm14 = (int32_t)((w >> 5) & 0x3fffu);
        if (imm14 & 0x2000) imm14 -= 0x4000;
        *tgt_out = (uintptr_t)((intptr_t)pc + ((intptr_t)imm14 << 2));
        return true;
    }
    return false;
}

static inline bool ld_fof_is_pcrel_insn(uint32_t w) {
    uintptr_t dummy = 0;
    if (ld_fof_decode_branch_target(0, w, &dummy)) return true;
    if ((w & 0x1f000000u) == 0x10000000u) return true;
    if ((w & 0x3f000000u) == 0x18000000u) return true;
    return false;
}

static bool ld_fof_mid_site_safe(uintptr_t fn_rt, size_t fn_sz, uintptr_t site) {
    if (!fn_rt || fn_sz < 32u || site < fn_rt + 16u || site + 16u > fn_rt + fn_sz)
        return false;
    if (!executable_range(fn_rt, fn_sz)) return false;
    const uint32_t *w = (const uint32_t *)fn_rt;
    size_t n = fn_sz / 4u;
    const uint32_t *sw = (const uint32_t *)site;
    for (size_t k = 0; k < 4u; k++) {
        if (ld_fof_is_pcrel_insn(sw[k])) return false;
    }
    for (size_t i = 0; i < n; i++) {
        uintptr_t pc = fn_rt + i * 4u;
        uintptr_t tgt = 0;
        if (ld_fof_decode_branch_target(pc, w[i], &tgt)) {
            if (tgt > site && tgt < site + 16u) return false;
        }
    }
    return true;
}

struct ld_fof_sym {
    uintptr_t rt;
    size_t sz;
};

static bool ld_fof_resolve_syms(const ld_lib_info *lib, struct ld_fof_sym out[6]) {
    if (!lib || !g_home_options.folder_open_fix_present) return false;
    for (size_t i = 0; i < 6u; i++) {
        uint64_t va = g_home_options.folder_open_fix_vaddr[i];
        size_t sz = g_home_options.folder_open_fix_size[i];
        uintptr_t rt = 0;
        if (!va || sz < 64u || !ld_vaddr_to_runtime(lib, (uintptr_t)va, &rt) ||
            !executable_range(rt, sz)) {
            ld_blog("OS4 folder_open_fix resolve sym[%zu] failed (va=0x%llx sz=%zu)",
                    i, (unsigned long long)va, sz);
            return false;
        }
        const uint32_t *code = (const uint32_t *)rt;
        if (code[0] != 0xa9bf79fdu || code[1] != 0xaa0f03fdu) {
            ld_blog("OS4 folder_open_fix prologue mismatch sym[%zu] @ %p: %08x %08x",
                    i, (void *)rt, code[0], code[1]);
            return false;
        }
        out[i].rt = rt;
        out[i].sz = sz;
    }
    return true;
}

static bool ld_fof_decode_all(const struct ld_fof_sym syms[6], struct ld_fof_state *st) {
    /* sym[0]: FolderGridViewGetxController.folderCellWidth */
    uintptr_t gc_cell_w_fn = 0, gc_cell_h_fn = 0;
    {
        const uint32_t *c = (const uint32_t *)syms[0].rt;
        size_t n = syms[0].sz / 4u;
        uint32_t gc_off = 0;
        for (size_t i = 0; i + 1 < n && i < 12u; i++) {
            if (ld_fof_is_ldr_x0_x26_78(c[i]) && ld_fof_decode_ldr_x0_x0_uimm(c[i + 1], &gc_off)) {
                st->globals_gc_off = gc_off;
                break;
            }
        }
        for (size_t i = n; i > 0; i--) {
            uintptr_t tgt = 0;
            if (ld_fof_decode_bl(syms[0].rt + (i - 1) * 4u, c[i - 1], &tgt) && executable_range(tgt, 0x70u)) {
                gc_cell_w_fn = tgt;
                break;
            }
        }
        if (!st->globals_gc_off || !gc_cell_w_fn) return false;
        const uint32_t *g = (const uint32_t *)gc_cell_w_fn;
        int32_t w_off = -1;
        for (size_t i = 0; i < 0x70u / 4u; i++) {
            int32_t imm = 0;
            if (ld_fof_decode_ldur_d(g[i], 0, 1, &imm) && imm > 0) w_off = imm;
        }
        if (w_off <= 0) return false;
        st->cfg_w_off = (uint32_t)w_off;
        st->site_fgvc_cell_w = syms[0].rt;
    }

    /* sym[1]: FolderGridViewGetxController.folderCellHeight */
    {
        const uint32_t *c = (const uint32_t *)syms[1].rt;
        size_t n = syms[1].sz / 4u;
        for (size_t i = n; i > 0; i--) {
            uintptr_t tgt = 0;
            if (ld_fof_decode_bl(syms[1].rt + (i - 1) * 4u, c[i - 1], &tgt) && executable_range(tgt, 0x70u)) {
                gc_cell_h_fn = tgt;
                break;
            }
        }
        if (!gc_cell_h_fn) return false;
        const uint32_t *g = (const uint32_t *)gc_cell_h_fn;
        int32_t h_off = -1;
        for (size_t i = 0; i < 0x70u / 4u; i++) {
            int32_t imm = 0;
            if (ld_fof_decode_ldur_d(g[i], 0, 1, &imm) && imm > 0) h_off = imm;
        }
        if (h_off <= 0) return false;
        st->cfg_h_off = (uint32_t)h_off;
        st->site_fgvc_cell_h = syms[1].rt;
    }

    /* sym[3]: FolderClingGetxController.calGridItemPosition */
    {
        const uint32_t *c = (const uint32_t *)syms[3].rt;
        size_t n = syms[3].sz / 4u;
        bool got = false;
        for (size_t i = 0; i + 10 < n; i++) {
            int32_t cling_fp = 0, c_fgvc = 0, gw_off = 0, lm_off = 0;
            if (ld_fof_decode_ldur_x(c[i], 0, 29, &cling_fp) && cling_fp < 0 &&
                c[i + 1] == 0x1e601000u && /* fmov d0, #2.0 */
                ld_fof_decode_ldur_w(c[i + 2], 1, 0, &c_fgvc) && c_fgvc > 0 &&
                c[i + 3] == 0x8b1c8021u) { /* add x1, x1, x28, lsl #32 */
                for (size_t j = i + 4; j + 3 < n && j < i + 14; j++) {
                    if (ld_fof_decode_ldur_d(c[j], 2, 1, &gw_off) && gw_off > 0 &&
                        ld_fof_decode_stur_d(c[j + 3], 1, 0, &lm_off) && lm_off > 0) {
                        uintptr_t site = syms[3].rt + i * 4u;
                        if (ld_fof_mid_site_safe(syms[3].rt, syms[3].sz, site)) {
                            st->cal_cling_fp_off = cling_fp;
                            st->cling_fgvc_off = (uint32_t)c_fgvc;
                            st->fgvc_gw_off = (uint32_t)gw_off;
                            st->cling_left_margin_off = (uint32_t)lm_off;
                            st->site_cal_pos_mid = site;
                            got = true;
                        }
                        break;
                    }
                }
                if (got) break;
            }
        }
        if (!got) {
            ld_blog("OS4 folder_open_fix calGridItemPosition decode failed");
            return false;
        }
    }

    /* sym[4]: FolderClingGetxController.calcPositionForCellX */
    {
        const uint32_t *c = (const uint32_t *)syms[4].rt;
        size_t n = syms[4].sz / 4u;
        bool got_cols = false, got_rx = false, got_cross = false;
        for (size_t i = 0; i + 8 < n; i++) {
            uint32_t cols_off = 0;
            if (!got_cols && ld_fof_is_ldr_x0_x26_78(c[i]) &&
                ld_fof_decode_ldr_x0_x0_uimm(c[i + 1], &cols_off) && cols_off != 0) {
                for (size_t j = i + 2; j < i + 12 && j < n; j++) {
                    if (c[j] == 0x93417c01u) { /* sbfx x1, x0, #1, #0x1f */
                        st->globals_cols_off = cols_off;
                        got_cols = true;
                        break;
                    }
                }
            }
            int32_t rx_off = 0, cfg_off = 0, w_chk = 0;
            uintptr_t rx_fn = 0;
            if (!got_rx && i + 5 < n &&
                ld_fof_decode_ldur_w(c[i], 1, 0, &rx_off) && rx_off > 0 &&
                c[i + 1] == 0x8b1c8021u &&
                ld_fof_decode_bl(syms[4].rt + (i + 2) * 4u, c[i + 2], &rx_fn) &&
                ld_fof_decode_ldur_w(c[i + 3], 1, 0, &cfg_off) && cfg_off > 0 &&
                c[i + 4] == 0x8b1c8021u &&
                ld_fof_decode_ldur_d(c[i + 5], 0, 1, &w_chk) && (uint32_t)w_chk == st->cfg_w_off &&
                executable_range(rx_fn, 0x60u)) {
                const uint32_t *rxf = (const uint32_t *)rx_fn;
                int32_t val_off = -1;
                for (size_t k = 0; k + 2 < 0x60u / 4u; k++) {
                    int32_t vo = 0;
                    if (ld_fof_decode_ldur_w(rxf[k], 0, 1, &vo) && vo > 0 &&
                        rxf[k + 1] == 0x8b1c8000u) {
                        val_off = vo;
                    }
                }
                if (val_off > 0) {
                    st->gc_rx_off = (uint32_t)rx_off;
                    st->rx_val_off = (uint32_t)val_off;
                    st->rx_cfg_off = (uint32_t)cfg_off;
                    got_rx = true;
                }
            }
            int32_t cs_off = 0;
            if (!got_cross && i + 1 < n &&
                ld_fof_decode_ldur_d(c[i], 1, 1, &cs_off) && cs_off > 0 &&
                c[i + 1] == 0x1e612802u) { /* fadd d2, d0, d1 */
                st->fgvc_cross_off = (uint32_t)cs_off;
                got_cross = true;
            }
        }
        if (!got_cols || !got_rx || !got_cross) {
            ld_blog("OS4 folder_open_fix calcPositionForCellX decode failed: cols=%d rx=%d cs=%d",
                    got_cols, got_rx, got_cross);
            return false;
        }
        st->site_calc_pos_x = syms[4].rt;
    }

    /* sym[2]: FolderGridViewGetxController._calibrateItemCellHeight */
    {
        const uint32_t *c = (const uint32_t *)syms[2].rt;
        size_t n = syms[2].sz / 4u;
        bool got_dev = false, got_hpad = false;
        for (size_t i = 0; i + 4 < n; i++) {
            int32_t obj_off = -1, sw_off = -1;
            uintptr_t sw_fn = 0;
            if (ld_fof_decode_bl(syms[2].rt + i * 4u, c[i], &sw_fn) && executable_range(sw_fn, 0x30u)) {
                /* Out-of-line helper form (e.g. 8.01.02.8604) */
                const uint32_t *sw = (const uint32_t *)sw_fn;
                if (!(ld_fof_decode_ldur_w(sw[0], 0, 1, &obj_off) && obj_off > 0 &&
                      sw[1] == 0x8b1c8000u &&
                      ld_fof_decode_ldur_d(sw[5], 0, 0, &sw_off) && sw_off > 0)) {
                    obj_off = -1;
                    sw_off = -1;
                }
            } else if ((c[i] & 0xffe00c1fu) == 0xfc400000u) {
                /* Inlined form (e.g. 8.01.03.308):
                 *   ldur wR, [x0, #obj_off]
                 *   add  xR, xR, x28, lsl #32
                 *   ...
                 *   ldur d0, [xR, #sw_off]   <-- c[i]
                 */
                uint32_t rn = (c[i] >> 5) & 0x1fu;
                int32_t imm_d = ld_fof_sign_ext9(c[i]);
                if (rn != 0 && rn != 29 && imm_d > 0) {
                    for (size_t k = i; k > 0 && i - k <= 8u; k--) {
                        int32_t imm_w = 0;
                        uint32_t expected_add = 0x8b1c8000u | (rn << 5) | rn;
                        if (ld_fof_decode_ldur_w(c[k - 1], rn, 0, &imm_w) && imm_w > 0 &&
                            c[k] == expected_add) {
                            obj_off = imm_w;
                            sw_off = imm_d;
                            break;
                        }
                    }
                }
            }
            if (obj_off > 0 && sw_off > 0) {
                int32_t st_d0 = 0;
                uint32_t hpad_off = 0;
                if (ld_fof_decode_stur_d(c[i + 1], 0, 29, &st_d0) &&
                    ld_fof_is_ldr_x0_x26_78(c[i + 2]) &&
                    ld_fof_decode_ldr_x0_x0_uimm(c[i + 3], &hpad_off) && hpad_off != 0) {
                    uint32_t dev_off = 0;
                    for (size_t b = i; b > 0 && i - b < 32u; b--) {
                        uint32_t goff = 0;
                        if (ld_fof_is_ldr_x0_x26_78(c[b - 1]) &&
                            ld_fof_decode_ldr_x0_x0_uimm(c[b], &goff) && goff != 0 &&
                            b + 1 < n && c[b + 1] == 0x6b16001fu) {
                            dev_off = goff;
                            break;
                        }
                    }
                    if (dev_off != 0) {
                        st->devcfg_globals_off = dev_off;
                        st->devcfg_obj_off = (uint32_t)obj_off;
                        st->devcfg_screenw_off = (uint32_t)sw_off;
                        st->globals_outer_hpad_off = hpad_off;
                        got_dev = true;
                        got_hpad = true;
                        break;
                    }
                }
            }
        }
        if (!got_dev || !got_hpad) {
            ld_blog("OS4 folder_open_fix _calibrateItemCellHeight decode failed");
            return false;
        }
        st->site_calibrate_h = syms[2].rt;
    }

    /* sym[5]: FolderAnimController._refreshCachedGridParams */
    {
        const uint32_t *c = (const uint32_t *)syms[5].rt;
        size_t n = syms[5].sz / 4u;
        uint32_t offs[8] = {0};
        size_t count = 0;
        for (size_t i = 0; i + 1 < n && count < 8u; i++) {
            if ((c[i] & 0xffe0001fu) == 0xd2800011u &&
                (c[i + 1] & 0xfffffc1fu) == 0xb8316800u) {
                uint32_t imm = (c[i] >> 5) & 0xffffu;
                if (imm >= 0x80u) offs[count++] = imm;
            }
        }
        if (count < 7u) {
            ld_blog("OS4 folder_open_fix _refreshCachedGridParams decode failed: count=%zu", count);
            return false;
        }
        st->anim_cell_w_off = offs[0];
        st->anim_cell_h_off = offs[1];
        st->anim_icon_w_off = offs[2];
        st->anim_icon_h_off = offs[3];
        st->anim_title_h_off = offs[4];
        st->anim_pad_left_off = offs[5];
        st->anim_pad_top_off = offs[6];
        st->site_refresh_cached = syms[5].rt;
    }

    st->ready = 1;
    ld_blog("OS4 folder_open_fix decoded: gc=0x%x(rx=0x%x val=0x%x cfg=0x%x w=0x%x h=0x%x) cols=0x%x dev=0x%x(0x%x/0x%x) hpad=0x%x fgvc(c=0x%x gw=0x%x) cling(f=0x%x lm=0x%x) anim(0x%x..0x%x)",
            st->globals_gc_off, st->gc_rx_off, st->rx_val_off, st->rx_cfg_off,
            st->cfg_w_off, st->cfg_h_off, st->globals_cols_off,
            st->devcfg_globals_off, st->devcfg_obj_off, st->devcfg_screenw_off,
            st->globals_outer_hpad_off,
            st->fgvc_cross_off, st->fgvc_gw_off,
            st->cling_fgvc_off, st->cling_left_margin_off,
            st->anim_cell_w_off, st->anim_pad_top_off);
    return true;
}

/* ---- Runtime Dart state readers & unified geometry ---- */

static inline bool ld_fof_ptr_ok(uintptr_t p, const struct ld_dart_context *ctx) {
    if (!p || p < 0x10000ULL) return false;
    uint32_t p32 = (uint32_t)p;
    if (p32 == (uint32_t)ctx->x[22]) return false;
    uint32_t sent32 = *(const uint32_t *)(uintptr_t)(ctx->x[27] + 0x40u);
    if (sent32 && p32 == sent32) return false;
    return true;
}

static inline uintptr_t ld_fof_uncompress(uint32_t comp, uint64_t x28) {
    return (uintptr_t)comp + ((x28 & 0xffffffffULL) << 32);
}

static inline double ld_fof_read_q_double(const unsigned char *qreg) {
    double v = 0.0;
    __builtin_memcpy(&v, qreg, sizeof(double));
    return v;
}

static inline void ld_fof_write_q_double(unsigned char *qreg, double v) {
    __builtin_memcpy(qreg, &v, sizeof(double));
}

static inline int ld_fof_read_or_sync_cols(const struct ld_dart_context *ctx, bool allow_sync) {
    int cfg_cols = g_folder_grid_cols;
    uintptr_t globals = *(const uintptr_t *)(uintptr_t)(ctx->x[26] + 0x78u);
    if (globals && g_fof.globals_cols_off) {
        uintptr_t slot = globals + g_fof.globals_cols_off;
        uintptr_t cur = *(const uintptr_t *)slot;
        uint32_t sent32 = *(const uint32_t *)(uintptr_t)(ctx->x[27] + 0x40u);
        uint32_t cur32 = (uint32_t)cur;
        bool initialized = (cur32 != sent32 && cur32 != (uint32_t)ctx->x[22] && cur != 0);
        if (cfg_cols >= 2) {
            if (allow_sync && initialized && (cur & 1u) == 0) {
                uintptr_t desired = (uintptr_t)((intptr_t)cfg_cols << 1);
                if (cur != desired) *(uintptr_t *)slot = desired;
            }
            return cfg_cols;
        }
        if (initialized && (cur & 1u) == 0) {
            int cols = (int)((int32_t)cur32 >> 1);
            if (cols >= 2 && cols <= 16) return cols;
        }
    }
    return cfg_cols >= 2 ? cfg_cols : 0;
}

static bool ld_fof_read_raw_cell(const struct ld_dart_context *ctx, double *raw_w_out, double *raw_h_out) {
    uintptr_t globals = *(const uintptr_t *)(uintptr_t)(ctx->x[26] + 0x78u);
    if (!globals) return false;
    uintptr_t gc = *(const uintptr_t *)(globals + g_fof.globals_gc_off);
    if (!ld_fof_ptr_ok(gc, ctx)) return false;
    uintptr_t rx = ld_fof_uncompress(*(const uint32_t *)(gc + g_fof.gc_rx_off), ctx->x[28]);
    if (!ld_fof_ptr_ok(rx, ctx)) return false;
    uintptr_t rx_val = ld_fof_uncompress(*(const uint32_t *)(rx + g_fof.rx_val_off), ctx->x[28]);
    if (!ld_fof_ptr_ok(rx_val, ctx)) return false;
    uintptr_t cfg = ld_fof_uncompress(*(const uint32_t *)(rx_val + g_fof.rx_cfg_off), ctx->x[28]);
    if (!ld_fof_ptr_ok(cfg, ctx)) return false;
    double rw = *(const double *)(cfg + g_fof.cfg_w_off);
    double rh = *(const double *)(cfg + g_fof.cfg_h_off);
    if (!(rw > 1.0 && rw < 500.0 && rh > 1.0 && rh < 500.0)) return false;
    if (raw_w_out) *raw_w_out = rw;
    if (raw_h_out) *raw_h_out = rh;
    return true;
}

static bool ld_fof_read_device_dims_and_hpad(const struct ld_dart_context *ctx,
                                             double *sw_out, double *sh_out, double *hpad_out) {
    uintptr_t globals = *(const uintptr_t *)(uintptr_t)(ctx->x[26] + 0x78u);
    if (!globals) return false;
    uintptr_t dev = *(const uintptr_t *)(globals + g_fof.devcfg_globals_off);
    if (!ld_fof_ptr_ok(dev, ctx)) return false;
    uintptr_t dobj = ld_fof_uncompress(*(const uint32_t *)(dev + g_fof.devcfg_obj_off), ctx->x[28]);
    if (!ld_fof_ptr_ok(dobj, ctx)) return false;
    double sw = *(const double *)(dobj + g_fof.devcfg_screenw_off);
    double sh = *(const double *)(dobj + g_fof.devcfg_screenw_off + 8u);
    if (!(sw > 100.0 && sw < 4000.0)) return false;
    if (!(sh > 100.0 && sh < 4000.0)) sh = sw;
    double hpad = 4.0;
    uintptr_t hbox = *(const uintptr_t *)(globals + g_fof.globals_outer_hpad_off);
    if (ld_fof_ptr_ok(hbox, ctx)) {
        double hv = *(const double *)(hbox + 7u);
        if (hv >= 0.0 && hv <= 64.0) hpad = hv;
    }
    if (sw_out) *sw_out = sw;
    if (sh_out) *sh_out = sh;
    if (hpad_out) *hpad_out = hpad;
    return true;
}

struct ld_fof_geom {
    int cols;
    double raw_w;
    double raw_h;
    double cross_s;
    double avail_w;
    double eff_cell_w;
    double eff_cell_h;
    double grid_width;
    double grid_left_margin;
    bool scaled;
};

static inline int ld_fof_current_landscape(void) {
    int l = ld_screen_landscape();
    if (l == 0 || l == 1) return l;
    int d = __atomic_load_n(&g_hm_cur_dir, __ATOMIC_ACQUIRE);
    if (d == 0 || d == 1) return d;
    return -1;
}

static bool ld_fof_compute_geom(const struct ld_dart_context *ctx, uintptr_t fgvc,
                                double avail_w_override, double raw_w_hint, double raw_h_hint,
                                struct ld_fof_geom *out) {
    int cols = ld_fof_read_or_sync_cols(ctx, false);
    if (cols < 2) return false;

    double cross_s = g_fof.last_cross_s;
    if (ld_fof_ptr_ok(fgvc, ctx)) {
        double cs = *(const double *)(fgvc + g_fof.fgvc_cross_off);
        if (cs >= 0.0 && cs <= 128.0) {
            cross_s = cs;
            g_fof.last_cross_s = cs;
        }
    }
    if (cross_s < 0.0) return false;

    double rw = raw_w_hint, rh = raw_h_hint;
    if (!(rw > 1.0 && rh > 1.0)) {
        if (!ld_fof_read_raw_cell(ctx, &rw, &rh)) return false;
    }

    double sw = 0.0, sh = 0.0, hpad = 4.0;
    if (!ld_fof_read_device_dims_and_hpad(ctx, &sw, &sh, &hpad)) return false;

    int cur_land = ld_fof_current_landscape(); /* 0=port, 1=land, -1=unknown */
    int cur_rot = ld_screen_rotation();
    double avail_w = avail_w_override;
    if (!(avail_w > 100.0)) {
        bool rot_matches = (cur_rot < 0 || g_fof.last_avail_rot == cur_rot);
        if (g_fof.last_avail_w > 100.0 && rot_matches &&
            cur_land >= 0 && g_fof.last_avail_land == cur_land) {
            avail_w = g_fof.last_avail_w;
        } else if (cur_land == 1) {
            avail_w = fmax(sw, sh);
        } else if (cur_land == 0) {
            avail_w = fmin(sw, sh);
        } else if (g_fof.last_avail_w > 100.0 && rot_matches) {
            avail_w = g_fof.last_avail_w;
        } else {
            avail_w = fmin(sw, sh);
        }
    }

    double max_grid_w = avail_w - 2.0 * (hpad + 1.0);
    double spacing_total = (double)(cols - 1) * cross_s;
    double raw_grid_w = (double)cols * rw + spacing_total;
    double eff_w = rw;
    double eff_h = rh;
    bool scaled = false;
    if (raw_grid_w > max_grid_w && max_grid_w > spacing_total + (double)cols) {
        eff_w = (max_grid_w - spacing_total) / (double)cols;
        eff_h = rh * (eff_w / rw);
        scaled = true;
    }
    double gw = (double)cols * eff_w + spacing_total;
    double lm = (avail_w - gw) * 0.5;
    if (lm < 0.0) lm = 0.0;

    out->cols = cols;
    out->raw_w = rw;
    out->raw_h = rh;
    out->cross_s = cross_s;
    out->avail_w = avail_w;
    out->eff_cell_w = eff_w;
    out->eff_cell_h = eff_h;
    out->grid_width = gw;
    out->grid_left_margin = lm;
    out->scaled = scaled;
    return true;
}

static inline void ld_fof_log_geom_once(const char *tag, const struct ld_fof_geom *g) {
    unsigned b = __atomic_load_n(&g_fof.log_budget, __ATOMIC_RELAXED);
    if (b > 0 && __atomic_compare_exchange_n(&g_fof.log_budget, &b, b - 1u,
                                              true, __ATOMIC_RELAXED, __ATOMIC_RELAXED)) {
        ld_blog("OS4 folder_open_fix [%s]: cols=%d scaled=%d raw=%.1fx%.1f eff=%.1fx%.1f gw=%.1f lm=%.1f avail=%.1f land=%d",
                tag, g->cols, g->scaled ? 1 : 0, g->raw_w, g->raw_h, g->eff_cell_w, g->eff_cell_h,
                g->grid_width, g->grid_left_margin, g->avail_w, ld_fof_current_landscape());
    }
}

/* ---- Hook callbacks ---- */

static inline void ld_fof_ws_to_dart(const struct ld_ws_ctx *w, struct ld_dart_context *d) {
    memset(d, 0, sizeof(*d));
    memcpy(d->x, w->x, sizeof(d->x));
}

__attribute__((used)) static void ld_fof_post_cell_w(struct ld_ws_ctx *w) {
    struct ld_dart_context ctx;
    ld_fof_ws_to_dart(w, &ctx);
    double raw_w = 0.0;
    memcpy(&raw_w, &w->ret_d0, sizeof(raw_w));
    struct ld_fof_geom g;
    if (ld_fof_compute_geom(&ctx, (uintptr_t)w->x[1], -1.0, raw_w, raw_w, &g) && g.scaled) {
        memcpy(&w->ret_d0, &g.eff_cell_w, sizeof(g.eff_cell_w));
    }
}

__attribute__((used)) static void ld_fof_post_cell_h(struct ld_ws_ctx *w) {
    struct ld_dart_context ctx;
    ld_fof_ws_to_dart(w, &ctx);
    double raw_h = 0.0;
    memcpy(&raw_h, &w->ret_d0, sizeof(raw_h));
    double rw = 0.0, rh_unused = 0.0;
    if (ld_fof_read_raw_cell(&ctx, &rw, &rh_unused)) {
        struct ld_fof_geom g;
        if (ld_fof_compute_geom(&ctx, (uintptr_t)w->x[1], -1.0, rw, raw_h, &g) && g.scaled) {
            memcpy(&w->ret_d0, &g.eff_cell_h, sizeof(g.eff_cell_h));
        }
    }
}

__attribute__((used)) static void ld_fof_post_calibrate_h(struct ld_ws_ctx *w) {
    struct ld_dart_context ctx;
    ld_fof_ws_to_dart(w, &ctx);
    struct ld_fof_geom g;
    if (ld_fof_compute_geom(&ctx, (uintptr_t)w->x[1], -1.0, 0.0, 0.0, &g) && g.scaled) {
        memcpy(&w->ret_d0, &g.eff_cell_h, sizeof(g.eff_cell_h));
    }
}

__attribute__((used)) static void ld_fof_on_cal_pos_mid(struct ld_dart_context *ctx) {
    (void)ld_fof_read_or_sync_cols(ctx, true);
    uintptr_t cling = *(const uintptr_t *)(uintptr_t)(ctx->x[29] + (int64_t)g_fof.cal_cling_fp_off);
    if (!ld_fof_ptr_ok(cling, ctx)) return;
    uintptr_t fgvc = ld_fof_uncompress(*(const uint32_t *)(cling + g_fof.cling_fgvc_off), ctx->x[28]);
    if (!ld_fof_ptr_ok(fgvc, ctx) || ld_dart_is_ro_addr(fgvc)) return;

    double avail_w = ld_fof_read_q_double(&ctx->q[1][0]);
    if (!(avail_w > 100.0 && avail_w < 4000.0)) return;

    struct ld_fof_geom g;
    if (ld_fof_compute_geom(ctx, fgvc, avail_w, 0.0, 0.0, &g)) {
        g_fof.last_avail_w = avail_w;
        g_fof.last_avail_land = ld_fof_current_landscape();
        g_fof.last_avail_rot = ld_screen_rotation();
        *(double *)(fgvc + g_fof.fgvc_gw_off) = g.grid_width;
        ld_fof_log_geom_once("calPos", &g);
    }
}

__attribute__((used)) static void ld_fof_pre_calc_pos_x(struct ld_ws_ctx *w) {
    struct ld_dart_context ctx;
    ld_fof_ws_to_dart(w, &ctx);
    (void)ld_fof_read_or_sync_cols(&ctx, true);
}
__attribute__((used)) static void ld_fof_post_calc_pos_x(struct ld_ws_ctx *w) {
    struct ld_dart_context ctx;
    ld_fof_ws_to_dart(w, &ctx);

    uintptr_t off_obj = (uintptr_t)w->ret_x0;
    uintptr_t cling = (uintptr_t)w->x[1];
    int64_t idx = (int64_t)w->x[2]; /* unboxed int64 cell index */
    if (!ld_fof_ptr_ok(off_obj, &ctx) || ld_dart_is_ro_addr(off_obj) ||
        !ld_fof_ptr_ok(cling, &ctx) || idx < 0) return;

    uintptr_t fgvc = ld_fof_uncompress(*(const uint32_t *)(cling + g_fof.cling_fgvc_off), ctx.x[28]);
    if (!ld_fof_ptr_ok(fgvc, &ctx)) return;

    struct ld_fof_geom g;
    if (!ld_fof_compute_geom(&ctx, fgvc, -1.0, 0.0, 0.0, &g) || !g.scaled || g.cols <= 0) return;

    int64_t col = idx % g.cols;
    int64_t row = idx / g.cols;
    double orig_dx = *(const double *)(off_obj + 7u);
    double orig_dy = *(const double *)(off_obj + 0xfu);

    double ltr_ref = g.grid_left_margin + (double)col * (g.raw_w + g.cross_s);
    double rtl_ref = g.grid_left_margin + g.grid_width - g.raw_w - (double)col * (g.raw_w + g.cross_s);
    bool is_rtl = fabs(orig_dx - rtl_ref) + 1e-3 < fabs(orig_dx - ltr_ref);

    double new_dx = is_rtl
        ? (g.grid_left_margin + g.grid_width - g.eff_cell_w - (double)col * (g.eff_cell_w + g.cross_s))
        : (g.grid_left_margin + (double)col * (g.eff_cell_w + g.cross_s));
    double new_dy = orig_dy + (double)row * (g.eff_cell_h - g.raw_h);

    *(double *)(off_obj + 7u) = new_dx;
    *(double *)(off_obj + 0xfu) = new_dy;
}

__attribute__((used)) static void ld_fof_post_refresh_cached(struct ld_ws_ctx *w) {
    struct ld_dart_context ctx;
    ld_fof_ws_to_dart(w, &ctx);

    uintptr_t anim = (uintptr_t)w->x[1];
    if (!ld_fof_ptr_ok(anim, &ctx)) return;

    uintptr_t cw_box = ld_fof_uncompress(*(const uint32_t *)(anim + g_fof.anim_cell_w_off), ctx.x[28]);
    uintptr_t ch_box = ld_fof_uncompress(*(const uint32_t *)(anim + g_fof.anim_cell_h_off), ctx.x[28]);
    uintptr_t iw_box = ld_fof_uncompress(*(const uint32_t *)(anim + g_fof.anim_icon_w_off), ctx.x[28]);
    uintptr_t ih_box = ld_fof_uncompress(*(const uint32_t *)(anim + g_fof.anim_icon_h_off), ctx.x[28]);
    uintptr_t th_box = ld_fof_uncompress(*(const uint32_t *)(anim + g_fof.anim_title_h_off), ctx.x[28]);
    uintptr_t pl_box = ld_fof_uncompress(*(const uint32_t *)(anim + g_fof.anim_pad_left_off), ctx.x[28]);
    uintptr_t pt_box = ld_fof_uncompress(*(const uint32_t *)(anim + g_fof.anim_pad_top_off), ctx.x[28]);

    if (!ld_fof_ptr_ok(cw_box, &ctx) || !ld_fof_ptr_ok(ch_box, &ctx) ||
        !ld_fof_ptr_ok(iw_box, &ctx) || !ld_fof_ptr_ok(ih_box, &ctx) ||
        !ld_fof_ptr_ok(th_box, &ctx) || !ld_fof_ptr_ok(pl_box, &ctx) ||
        !ld_fof_ptr_ok(pt_box, &ctx)) {
        return;
    }

    double raw_w = *(const double *)(cw_box + 7u);
    double raw_h = *(const double *)(ch_box + 7u);
    struct ld_fof_geom g;
    if (!ld_fof_compute_geom(&ctx, 0, -1.0, raw_w, raw_h, &g) || !g.scaled) return;

    double icon_w = *(const double *)(iw_box + 7u);
    double icon_h = *(const double *)(ih_box + 7u);
    double title_h = *(const double *)(th_box + 7u);
    if (!ld_dart_is_ro_addr(cw_box)) *(double *)(cw_box + 7u) = g.eff_cell_w;
    if (!ld_dart_is_ro_addr(ch_box)) *(double *)(ch_box + 7u) = g.eff_cell_h;
    if (!ld_dart_is_ro_addr(pl_box)) *(double *)(pl_box + 7u) = (g.eff_cell_w - icon_w) * 0.5;
    if (!ld_dart_is_ro_addr(pt_box)) *(double *)(pt_box + 7u) = (g.eff_cell_h - icon_h - title_h) * 0.5;
}

LD_WS_POST_OBSERVER(repl_fof_cell_w, ld_fof_post_cell_w, g_orig_fof_cell_w)
LD_WS_POST_OBSERVER(repl_fof_cell_h, ld_fof_post_cell_h, g_orig_fof_cell_h)
LD_WS_POST_OBSERVER(repl_fof_calibrate_h, ld_fof_post_calibrate_h, g_orig_fof_calibrate_h)
LD_DART_OBSERVER(repl_fof_cal_pos_mid, ld_fof_on_cal_pos_mid, g_orig_fof_cal_pos_mid)
LD_WS_PRE_POST_OBSERVER(repl_fof_calc_pos_x, ld_fof_pre_calc_pos_x, ld_fof_post_calc_pos_x, g_orig_fof_calc_pos_x)
LD_WS_POST_OBSERVER(repl_fof_refresh_cached, ld_fof_post_refresh_cached, g_orig_fof_refresh_cached)

static void ld_folder_open_fix_install(const struct native_api *api, const ld_lib_info *lib) {
    static bool s_attempted = false;
    if (s_attempted) return;
    if (!api || !api->hook || !lib) return;
    if (!g_home_options.big_folder_enabled &&
        g_home_options.folder_grid_cols <= 0 &&
        g_home_options.folder_grid_rows <= 0) {
        return;
    }
    if (!g_home_options.folder_open_fix_present) {
        ld_blog("OS4 folder_open_fix: targets not present, skipped");
        s_attempted = true;
        return;
    }

    struct ld_fof_sym syms[6];
    if (!ld_fof_resolve_syms(lib, syms)) {
        s_attempted = true;
        return;
    }
    struct ld_fof_state decoded = {0};
    decoded.last_cross_s = -1.0;
    decoded.last_avail_w = -1.0;
    decoded.last_avail_land = -1;
    decoded.last_avail_rot = -1;
    decoded.log_budget = 16u;
    if (!ld_fof_decode_all(syms, &decoded)) {
        s_attempted = true;
        return;
    }
    g_fof = decoded;
    s_attempted = true;

    struct {
        uintptr_t site;
        void *repl;
        void **orig;
        const char *label;
    } hooks[] = {
        { g_fof.site_fgvc_cell_w,     (void *)repl_fof_cell_w,         &g_orig_fof_cell_w,         "fgvc.cellW" },
        { g_fof.site_fgvc_cell_h,     (void *)repl_fof_cell_h,         &g_orig_fof_cell_h,         "fgvc.cellH" },
        { g_fof.site_calibrate_h,     (void *)repl_fof_calibrate_h,    &g_orig_fof_calibrate_h,    "fgvc._calibrateH" },
        { g_fof.site_cal_pos_mid,     (void *)repl_fof_cal_pos_mid,    &g_orig_fof_cal_pos_mid,    "cling.calPos.mid" },
        { g_fof.site_calc_pos_x,      (void *)repl_fof_calc_pos_x,     &g_orig_fof_calc_pos_x,     "cling.calcPosX" },
        { g_fof.site_refresh_cached,  (void *)repl_fof_refresh_cached, &g_orig_fof_refresh_cached, "anim.refreshCached" },
    };

    int ok = 0;
    for (size_t i = 0; i < sizeof(hooks) / sizeof(hooks[0]); i++) {
        if (!hooks[i].site || !executable_range(hooks[i].site, 16u)) continue;
        int rc = api->hook((void *)hooks[i].site, hooks[i].repl, hooks[i].orig);
        if (rc == 0 && *hooks[i].orig) {
            pin_installed_hook(hooks[i].site);
            ok++;
        } else {
            ld_blog("OS4 folder_open_fix hook failed [%s] @ %p rc=%d",
                    hooks[i].label, (void *)hooks[i].site, rc);
        }
    }
    g_fof.installed_count = ok;
    ld_blog("OS4 folder_open_fix installed %d/6 hooks", ok);
}

#endif /* LD_FOLDER_OPEN_FIX_H */
