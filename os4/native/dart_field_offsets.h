#ifndef LD_DART_FIELD_OFFSETS_H
#define LD_DART_FIELD_OFFSETS_H

#include <stddef.h>
#include <stdint.h>
#include "bootlog.h"
#include "executable_range.h"
#include "lib_base.h"

/* Dart VM core class IDs (stable within a Flutter/Dart VM build). */
#define LD_CID_MINT            0x3du
#define LD_CID_ARRAY           0x59u
#define LD_CID_IMMUTABLE_ARRAY 0x5au
#define LD_CID_GROWABLE_LIST   0x5cu

/* Single source of truth for launcher Dart business-class field offsets
 * (GridConfig, GridVariables, IconConfig, ItemInfoModel, controllers, Rx).
 * Initialized with verified OS4 defaults and refined dynamically by decoding
 * resolved AOT getter/method instructions at startup. */
struct ld_dart_offsets {
    /* GridConfig */
    size_t gc_cols;
    size_t gc_rows;
    size_t gc_cell_w;
    size_t gc_cell_h;
    size_t gc_origin;
    size_t gc_dock_h;
    size_t gc_icons;
    size_t gc_title;
    size_t gc_variables;
    /* GridVariables */
    size_t gv_orientation;
    size_t gv_layout_mode;
    /* IconConfig / TitleConfig */
    size_t ic_icon_size;
    size_t tc_title_h;
    /* ItemInfoModel / MiuiAppWidgetInfoModel */
    size_t item_id;
    size_t item_type_id;
    size_t item_span_x;
    size_t item_span_y;
    size_t item_screen;
    size_t item_cell_x;
    size_t item_cell_y;
    size_t item_container;
    size_t item_min_span_x;
    size_t item_min_span_y;
    size_t item_kind;
    size_t item_widget_flags;
    size_t item_widget_clip;
    /* FolderLayoutStrategy (1x1) & FolderClingBlurController */
    size_t fls_in_hotseat;
    size_t fls_preview_h;
    size_t fls_preview_w;
    size_t folder_blur_in_laptop;
    size_t blur_layer_radius;
    /* Widget / Resize controllers */
    size_t devcfg_is_pad;
    size_t widget_ctrl_item;
    size_t ws_ctrl_resize_mgr;
    size_t rfc_widget_rx;
    size_t rfc_cell_x;
    size_t rfc_cell_y;
    size_t rfc_span_x;
    size_t rfc_span_y;
    size_t menu_item_widget;
    /* Rx */
    size_t rx_value;
    /* Dynamically decoded static global offset for GridController (if found) */
    uintptr_t grid_ctrl_global_off;
};

static struct ld_dart_offsets g_ld_off = {
    .gc_cols = 0x1bu,
    .gc_rows = 0x23u,
    .gc_cell_w = 0x2bu,
    .gc_cell_h = 0x33u,
    .gc_origin = 0x3bu,
    .gc_dock_h = 0x4bu,
    .gc_icons = 0x6bu,
    .gc_title = 0x6fu,
    .gc_variables = 0x8fu,
    .gv_orientation = 0x23u,
    .gv_layout_mode = 0x27u,
    .ic_icon_size = 0x17u,
    .tc_title_h = 0x27u,
    .item_id = 0x07u,
    .item_type_id = 0x0fu,
    .item_span_x = 0x1fu,
    .item_span_y = 0x27u,
    .item_screen = 0x2fu,
    .item_cell_x = 0x37u,
    .item_cell_y = 0x3fu,
    .item_container = 0x47u,
    .item_min_span_x = 0x4fu,
    .item_min_span_y = 0x57u,
    .item_kind = 0x6bu,
    .item_widget_flags = 0xfbu,
    .item_widget_clip = 0x10fu,
    .fls_in_hotseat = 0x43u,
    .fls_preview_h = 0x1fu,
    .fls_preview_w = 0x27u,
    .folder_blur_in_laptop = 0x2fu,
    .blur_layer_radius = 0x07u,
    .devcfg_is_pad = 0xe7u,
    .widget_ctrl_item = 0x9bu,
    .ws_ctrl_resize_mgr = 0x1d7u,
    .rfc_widget_rx = 0x23u,
    .rfc_cell_x = 0x97u,
    .rfc_cell_y = 0x9bu,
    .rfc_span_x = 0x9fu,
    .rfc_span_y = 0xa3u,
    .menu_item_widget = 0x23u,
    .rx_value = 0x13u,
    .grid_ctrl_global_off = 0u,
};

static inline int ld_dfo_decode_ldur_d(uint32_t insn, uint32_t *rt, uint32_t *rn, size_t *imm)
{
    if ((insn & 0xffe00c00u) != 0xfc400000u) return 0;
    int32_t s9 = (int32_t)((insn >> 12) & 0x1ffu);
    if (s9 & 0x100) return 0;
    if (rt) *rt = insn & 31u;
    if (rn) *rn = (insn >> 5) & 31u;
    if (imm) *imm = (size_t)s9;
    return 1;
}

static inline int ld_dfo_decode_ldur_w(uint32_t insn, uint32_t *rt, uint32_t *rn, size_t *imm)
{
    if ((insn & 0xffe00c00u) != 0xb8400000u) return 0;
    int32_t s9 = (int32_t)((insn >> 12) & 0x1ffu);
    if (s9 & 0x100) return 0;
    if (rt) *rt = insn & 31u;
    if (rn) *rn = (insn >> 5) & 31u;
    if (imm) *imm = (size_t)s9;
    return 1;
}

static inline int ld_dfo_decode_ldur_x(uint32_t insn, uint32_t *rt, uint32_t *rn, size_t *imm)
{
    if ((insn & 0xffe00c00u) != 0xf8400000u) return 0;
    int32_t s9 = (int32_t)((insn >> 12) & 0x1ffu);
    if (s9 & 0x100) return 0;
    if (rt) *rt = insn & 31u;
    if (rn) *rn = (insn >> 5) & 31u;
    if (imm) *imm = (size_t)s9;
    return 1;
}

static void ld_dart_offsets_decode(const ld_lib_info *lib,
                                   int home_opt_present,
                                   const uint64_t *home_opt_vaddr,
                                   const size_t *home_opt_size,
                                   int layout_extra_present,
                                   const uint64_t *layout_extra_vaddr,
                                   const size_t *layout_extra_size)
{
    static int decoded;
    if (decoded || !lib) return;
    decoded = 1;

    /* 1. From GridController.hotSeatsHeight ([11]): decode GridController static
     *    global offset (ldr x0,[x26,#0x78]; ldr x0,[x0,#uimm]) and GridConfig.dockHeightPx (+0x4b). */
    if (home_opt_present && home_opt_vaddr && home_opt_size &&
        home_opt_vaddr[11] && home_opt_size[11] >= 16u) {
        uintptr_t fn = 0;
        if (ld_vaddr_to_runtime(lib, (uintptr_t)home_opt_vaddr[11], &fn) &&
            executable_range(fn, home_opt_size[11])) {
            const uint32_t *w = (const uint32_t *)fn;
            size_t n = home_opt_size[11] / 4u;
            for (size_t i = 0; i + 1 < n; i++) {
                /* ldr xR, [x26, #0x78]; ldr xR2, [xR, #global_off] (register-agnostic) */
                if ((w[i] & 0xffffffe0u) == 0xf9403f40u) {
                    uint32_t r = w[i] & 31u;
                    if ((w[i + 1] & 0xffc003e0u) == (0xf9400000u | (r << 5))) {
                        uintptr_t goff = (uintptr_t)(((w[i + 1] >> 10) & 0xfffu) << 3);
                        if (goff >= 0x100u && goff < 0x8000u) {
                            g_ld_off.grid_ctrl_global_off = goff;
                            break;
                        }
                    }
                }
            }
            for (size_t i = n; i > 0; i--) {
                uint32_t rt = 0, rn = 0;
                size_t imm = 0;
                if (ld_dfo_decode_ldur_d(w[i - 1], &rt, &rn, &imm) &&
                    rt == 0u && rn == 0u && imm >= 0x1fu && imm <= 0x9fu) {
                    g_ld_off.gc_dock_h = imm;
                    break;
                }
            }
        }
    }

    /* 2. From _HotseatWidgetPadState.build closure ([12]): decode GridConfig.icons
     *    (+0x6b) & IconConfig.iconSize (+0x17). */
    if (home_opt_present && home_opt_vaddr && home_opt_vaddr[12]) {
        uintptr_t fn = 0;
        if (ld_vaddr_to_runtime(lib, (uintptr_t)home_opt_vaddr[12], &fn) &&
            executable_range(fn, 0x1000u)) {
            const uint32_t *w = (const uint32_t *)fn;
            for (size_t i = 5; i < 0x3f0u; i++) {
                if (w[i] != 0x1e610802u) continue; /* fmul d2, d0, d1 */
                for (size_t k = 1; k <= 4; k++) {
                    uint32_t drt = 0, drn = 0;
                    size_t dimm = 0;
                    if (!ld_dfo_decode_ldur_d(w[i - k], &drt, &drn, &dimm) || drt != 0u) continue;
                    if (i - k >= 2u) {
                        uint32_t wrt = 0, wrn = 0;
                        size_t wimm = 0;
                        if (ld_dfo_decode_ldur_w(w[i - k - 2], &wrt, &wrn, &wimm) &&
                            wrt == drn && wrn == 0u && wimm >= 0x3fu && wimm <= 0xbfu &&
                            dimm >= 0x07u && dimm <= 0x4fu) {
                            g_ld_off.gc_icons = wimm;
                            g_ld_off.ic_icon_size = dimm;
                            goto done_icons;
                        }
                    }
                }
            }
        }
    }
done_icons:

    /* 3. From GridController._filterActiveGridConfig (layout_extra[0]): decode
     *    GridConfig.cols / rows consecutive 8-byte int64 loads in 0x13..0x2f. */
    if (layout_extra_present && layout_extra_vaddr && layout_extra_size &&
        layout_extra_vaddr[0] && layout_extra_size[0] >= 0x40u) {
        uintptr_t fn = 0;
        if (ld_vaddr_to_runtime(lib, (uintptr_t)layout_extra_vaddr[0], &fn) &&
            executable_range(fn, layout_extra_size[0])) {
            const uint32_t *w = (const uint32_t *)fn;
            size_t n = layout_extra_size[0] / 4u;
            for (size_t i = 0; i + 4 < n; i++) {
                uint32_t rt1 = 0, rn1 = 0;
                size_t imm1 = 0;
                if (!ld_dfo_decode_ldur_x(w[i], &rt1, &rn1, &imm1)) continue;
                if (rn1 == 29u || imm1 < 0x13u || imm1 > 0x2bu) continue;
                for (size_t j = 1; j <= 4 && i + j < n; j++) {
                    uint32_t rt2 = 0, rn2 = 0;
                    size_t imm2 = 0;
                    if (ld_dfo_decode_ldur_x(w[i + j], &rt2, &rn2, &imm2) &&
                        rn2 == rn1 && imm2 == imm1 + 8u) {
                        g_ld_off.gc_cols = imm1;
                        g_ld_off.gc_rows = imm2;
                        goto done_cols;
                    }
                }
            }
        }
    }
done_cols:

    /* 4. From WidgetContainerWidget.widgetContainerWidth (home_opt[2]): decode
     *    GridConfig.cell_w / cell_h consecutive 8-byte double loads in 0x23..0x3f. */
    if (home_opt_present && home_opt_vaddr && home_opt_size &&
        home_opt_vaddr[2] && home_opt_size[2] >= 0x40u) {
        uintptr_t fn = 0;
        if (ld_vaddr_to_runtime(lib, (uintptr_t)home_opt_vaddr[2], &fn) &&
            executable_range(fn, home_opt_size[2])) {
            const uint32_t *w = (const uint32_t *)fn;
            size_t n = home_opt_size[2] / 4u;
            for (size_t i = 0; i + 4 < n; i++) {
                uint32_t rt1 = 0, rn1 = 0;
                size_t imm1 = 0;
                if (!ld_dfo_decode_ldur_d(w[i], &rt1, &rn1, &imm1)) continue;
                if (rn1 == 29u || imm1 < 0x23u || imm1 > 0x3bu) continue;
                for (size_t j = 1; j <= 4 && i + j < n; j++) {
                    uint32_t rt2 = 0, rn2 = 0;
                    size_t imm2 = 0;
                    if (ld_dfo_decode_ldur_d(w[i + j], &rt2, &rn2, &imm2) &&
                        rn2 == rn1 && imm2 == imm1 + 8u) {
                        g_ld_off.gc_cell_w = imm1;
                        g_ld_off.gc_cell_h = imm2;
                        goto done_cells;
                    }
                }
            }
        }
    }
done_cells:
    ld_blog("dart_field_offsets: gc(cols=%#zx rows=%#zx cw=%#zx ch=%#zx dock_h=%#zx icons=%#zx vars=%#zx) ic=%#zx gctrl=%#lx",
            g_ld_off.gc_cols, g_ld_off.gc_rows, g_ld_off.gc_cell_w, g_ld_off.gc_cell_h,
            g_ld_off.gc_dock_h, g_ld_off.gc_icons, g_ld_off.gc_variables, g_ld_off.ic_icon_size,
            (unsigned long)g_ld_off.grid_ctrl_global_off);
}

#endif /* LD_DART_FIELD_OFFSETS_H */
