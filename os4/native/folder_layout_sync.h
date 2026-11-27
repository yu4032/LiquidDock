/* Keep cached FolderLayoutStrategy padding fields (newPaddingHorizontal /
 * newPaddingVertical) synchronized with the active GridConfig cellWidth /
 * cellHeight from home_margins.h.
 *
 * Root cause:
 *   FolderHelper._initLayoutStrategyCache() caches global FolderLayoutStrategy
 *   singletons (including FolderLayout1x1) and computes:
 *     newPaddingVertical   = (cellHeight * spanY - folderHeight - titleHeight) / 2
 *     newPaddingHorizontal = (cellWidth  * spanX - folderWidth) / 2
 *   When entering Workstation AllApps (scope 2) with custom grid offsets,
 *   home_margins.h updates GridConfig.cellWidth/cellHeight in place, but the
 *   cached FolderLayout1x1 singleton still holds Workspace (scope 1) padding
 *   until a screen rotation forces _initLayoutStrategyCache() to re-run.
 *
 * Fix (100% GC-safe, zero cached Dart object pointers, dynamic field decode):
 *   1. Decode field offsets semantically from newPaddingHorizontal,
 *      newPaddingVertical, and FolderLayoutStrategy.calItemSize.
 *   2. On FolderLayoutStrategy.calItemSize exit, record {cid, spanX, spanY,
 *      titleHeight} keyed by immutable Dart class ID (cid).
 *   3. On FolderIconGetxController.putIfAbsent exit (called immediately before
 *      FolderIconWidget._buildPreviewContainer reads strategy padding), sync
 *      the live strategy object's padding fields to (g_hm_active_cw, g_hm_active_ch).
 */
#ifndef LD_FOLDER_LAYOUT_SYNC_H
#define LD_FOLDER_LAYOUT_SYNC_H

#include <math.h>
#include <stdint.h>
#include <string.h>
#include "bootlog.h"
#include "executable_range.h"
#include "folder_preview_inset.h"
#include "home_margins.h"
#include "workstation.h"

#define LD_FLS_MAX_CIDS 16

struct ld_fls_cid_entry {
    uint32_t cid;
    int span_x;
    int span_y;
    double title_h;
    int valid;
};

static struct ld_fls_cid_entry g_fls_cids[LD_FLS_MAX_CIDS] __attribute__((used));
static unsigned g_fls_strat_off __attribute__((used));
static unsigned g_fls_fw_off __attribute__((used));
static unsigned g_fls_fh_off __attribute__((used));
static unsigned g_fls_pv_off __attribute__((used));
static unsigned g_fls_ph_off __attribute__((used));
static unsigned g_fls_list_off __attribute__((used));
static unsigned g_fls_def_off __attribute__((used));
static unsigned g_fls_piw_off __attribute__((used));
static unsigned g_fls_pih_off __attribute__((used));
static volatile int g_fls_ready __attribute__((used));

static void *g_orig_fls_cal_item_size __attribute__((used));
static void *g_orig_fls_put_if_absent __attribute__((used));

/* Decode FolderIconGetxController.newPaddingHorizontal / newPaddingVertical:
 *   movz x17, #strat_off ; ldr w0, [x1, x17]   (or ldur w0, [x1, #strat_off])
 *   add  x0, x0, x28, lsl #32
 *   ...
 *   ldur d0, [x0, #pad_off]
 *   ret
 */
static int ld_fls_decode_padding_getter(uintptr_t fn, size_t size,
                                        unsigned *out_strat_off,
                                        unsigned *out_pad_off)
{
    if (!fn || !out_strat_off || !out_pad_off) return 0;
    if (size < 20u || size > 256u) size = 64u;
    if (!executable_range(fn, size)) return 0;
    const uint32_t *w = (const uint32_t *)fn;
    size_t n = size / 4u;
    if (n > 20u) n = 20u;
    unsigned strat_off = 0, pad_off = 0;
    for (size_t i = 0; i < n; i++) {
        uint32_t ins = w[i];
        if (!strat_off) {
            if ((ins & 0xffe0001fu) == 0xd2800011u) {
                /* movz x17, #imm16 */
                strat_off = (ins >> 5) & 0xffffu;
            } else if ((ins & 0xffe00fffu) == 0xb8400020u) {
                /* ldur w0, [x1, #imm9] */
                unsigned imm9 = (ins >> 12) & 0x1ffu;
                if (imm9 < 256u) strat_off = imm9;
            }
        }
        if (strat_off && !pad_off && (ins & 0xffe00fffu) == 0xfc400000u) {
            /* ldur d0, [x0, #imm9] */
            unsigned imm9 = (ins >> 12) & 0x1ffu;
            if (imm9 < 256u) pad_off = imm9;
        }
        if (ins == 0xd65f03c0u) break; /* ret */
    }
    if (strat_off < 7u || strat_off > 4096u || pad_off < 7u || pad_off > 255u)
        return 0;
    *out_strat_off = strat_off;
    *out_pad_off = pad_off;
    return 1;
}

/* Decode FolderLayoutStrategy.calItemSize to find fh_off and fw_off and
 * cross-check against pv_off and ph_off:
 *   ldur d*, [xN, #fh_off] ; fsub ... ; ... ; stur d*, [xM, #pv_off]
 *   ...
 *   ldur d*, [xN, #fw_off] ; fsub ... ; ... ; stur d*, [xM, #ph_off]
 */
static int ld_fls_decode_cal_item_size(uintptr_t fn, size_t size,
                                       unsigned pv_off, unsigned ph_off,
                                       unsigned *out_fw_off, unsigned *out_fh_off)
{
    if (!fn || !out_fw_off || !out_fh_off) return 0;
    if (size < 64u || size > 4096u) size = 1024u;
    if (!executable_range(fn, size)) return 0;
    const uint32_t *w = (const uint32_t *)fn;
    size_t n = size / 4u;
    unsigned fh_off = 0, fw_off = 0;
    int saw_pv = 0, saw_ph = 0;
    unsigned last_ldur_before_fsub = 0;
    for (size_t i = 0; i < n; i++) {
        uint32_t ins = w[i];
        /* ldur d*, [xRn, #imm9] with Rn != 29 and positive imm9 */
        if ((ins & 0xffe00c00u) == 0xfc400000u) {
            unsigned rn = (ins >> 5) & 0x1fu;
            unsigned imm9 = (ins >> 12) & 0x1ffu;
            if (rn != 29u && imm9 >= 7u && imm9 < 256u) {
                /* Check if followed within 4 instructions by fsub d*, d*, d* */
                for (size_t j = 1; j <= 4u && i + j < n; j++) {
                    if ((w[i + j] & 0xffe0fc00u) == 0x1e603800u) {
                        last_ldur_before_fsub = imm9;
                        break;
                    }
                }
            }
        }
        /* stur d*, [xRn, #imm9] with Rn != 29 */
        if ((ins & 0xffe00c00u) == 0xfc000000u) {
            unsigned rn = (ins >> 5) & 0x1fu;
            unsigned imm9 = (ins >> 12) & 0x1ffu;
            if (rn != 29u && imm9 < 256u) {
                if (imm9 == pv_off && last_ldur_before_fsub) {
                    fh_off = last_ldur_before_fsub;
                    saw_pv = 1;
                    last_ldur_before_fsub = 0;
                } else if (imm9 == ph_off && last_ldur_before_fsub) {
                    fw_off = last_ldur_before_fsub;
                    saw_ph = 1;
                    last_ldur_before_fsub = 0;
                }
            }
        }
    }
    if (!saw_pv || !saw_ph || !fw_off || !fh_off) return 0;
    *out_fw_off = fw_off;
    *out_fh_off = fh_off;
    return 1;
}

/* Decode FolderLayoutStrategy.getPreviewItemInfo to locate previewItemInfos
 * (_GrowableList field) and defaultPreviewItemInfo field on FolderLayoutStrategy:
 *   ldur w2, [x0, #list_off] ; add x2, x2, x28, lsl #32 ; ldur w3, [x2, #11]
 *   ...
 *   ldur w0, [x3, #def_off]  ; add x0, x0, x28, lsl #32
 */
static int ld_fls_decode_preview_fields(uintptr_t fn, size_t size,
                                        unsigned *out_list_off,
                                        unsigned *out_def_off)
{
    if (!fn || !out_list_off || !out_def_off) return 0;
    if (size < 64u || size > 4096u) size = 256u;
    if (!executable_range(fn, size)) return 0;
    const uint32_t *w = (const uint32_t *)fn;
    size_t n = size / 4u;
    unsigned list_off = 0, def_off = 0;
    for (size_t i = 0; i + 1u < n; i++) {
        uint32_t ins = w[i];
        uint32_t next = w[i + 1u];
        /* ldur wRt, [xRn, #imm9] with Rn != 29, followed by add xRt, xRt, x28, lsl #32 */
        if ((ins & 0xffe00c00u) == 0xb8400000u && (next & 0xfffffc00u) == 0x8b1c8000u) {
            unsigned rn = (ins >> 5) & 0x1fu;
            unsigned imm9 = (ins >> 12) & 0x1ffu;
            if (rn == 29u || imm9 < 40u || imm9 >= 256u) continue;
            if (!list_off && i + 2u < n && (w[i + 2u] & 0xffe00c00u) == 0xb8400000u) {
                unsigned len_imm = (w[i + 2u] >> 12) & 0x1ffu;
                if (len_imm == 11u) {
                    list_off = imm9;
                    continue;
                }
            }
            if (list_off && !def_off && imm9 != list_off) {
                def_off = imm9;
                break;
            }
        }
    }
    if (!list_off || !def_off) return 0;
    *out_list_off = list_off;
    *out_def_off = def_off;
    return 1;
}

static int ld_fls_shift_preview_item(uintptr_t item, uint32_t null_low,
                                     double dph, double dpv)
{
    if (!(item & 1u) || item < 0x100000u) return 0;
    if (null_low && (uint32_t)item == null_low) return 0;
    if (ld_ws_is_const_ro(item)) return 0;
    double w = 0.0, h = 0.0, x = 0.0, y = 0.0;
    if (!ld_read_heap(item + 7u, &w, sizeof w) ||
        !ld_read_heap(item + 15u, &h, sizeof h) ||
        !ld_read_heap(item + 23u, &x, sizeof x) ||
        !ld_read_heap(item + 31u, &y, sizeof y))
        return 0;
    if (!isfinite(w) || !isfinite(h) || !isfinite(x) || !isfinite(y)) return 0;
    if (w < 2.0 || w > 500.0 || h < 2.0 || h > 500.0) return 0;
    double nx = x + dph;
    double ny = y + dpv;
    if (!isfinite(nx) || !isfinite(ny)) return 0;
    memcpy((void *)(item + 23u), &nx, sizeof nx);
    memcpy((void *)(item + 31u), &ny, sizeof ny);
    return 1;
}

static unsigned ld_fls_shift_preview_items(uintptr_t strat, uintptr_t x28,
                                           uint32_t null_low,
                                           double dph, double dpv)
{
    unsigned shifted = 0;
    if (g_fls_def_off) {
        uint32_t dw = 0;
        if (ld_read_heap(strat + g_fls_def_off, &dw, sizeof dw) && (dw & 1u) && dw >= 0x100000u) {
            shifted += (unsigned)ld_fls_shift_preview_item(
                (uintptr_t)dw + (x28 << 32), null_low, dph, dpv);
        }
    }
    if (g_fls_list_off) {
        uint32_t lw = 0;
        if (ld_read_heap(strat + g_fls_list_off, &lw, sizeof lw) && (lw & 1u) && lw >= 0x100000u) {
            uintptr_t list = (uintptr_t)lw + (x28 << 32);
            uint32_t lhdr = 0, len_smi = 0, aw = 0;
            if ((!null_low || (uint32_t)list != null_low) &&
                ld_read_heap(list - 1u, &lhdr, sizeof lhdr) &&
                ((lhdr >> 12) & 0xfffffu) == 0x5cu &&
                ld_read_heap(list + 0xbu, &len_smi, sizeof len_smi) &&
                !(len_smi & 1u) &&
                ld_read_heap(list + 0xfu, &aw, sizeof aw) &&
                (aw & 1u) && aw >= 0x100000u) {
                unsigned len = len_smi >> 1;
                if (len > 64u) len = 64u;
                uintptr_t arr = (uintptr_t)aw + (x28 << 32);
                for (unsigned i = 0; i < len; i++) {
                    uint32_t ew = 0;
                    if (!ld_read_heap(arr + 0xfu + (size_t)i * 4u, &ew, sizeof ew)) break;
                    if (!(ew & 1u) || ew < 0x100000u) continue;
                    shifted += (unsigned)ld_fls_shift_preview_item(
                        (uintptr_t)ew + (x28 << 32), null_low, dph, dpv);
                }
            }
        }
    }
    return shifted;
}

/* Synchronize big-folder previewItemInfos in place (5x2 ten-icon inset +
 * per-type big folder icon size scaling for 2x2 / 1x2 / 2x1 / 4x2) so
 * preview rendering, folder open/close animations, and app launch/return
 * animations all target the exact same adjusted icon rectangles. */
#define LD_FLS_TEN_ICON_COUNT 13u
#define LD_FLS_MAX_PREVIEW_ITEMS 32u
#define LD_FLS_PREVIEW_SLOTS 8u

struct ld_fls_preview_slot {
    int valid;
    uint32_t cid;
    int span_x, span_y;
    unsigned len;
    double fw, fh, pad0, big_w;
    double applied_inset;
    int applied_pct;
    int written_valid;
    struct ld_folder_preview_rect orig[LD_FLS_MAX_PREVIEW_ITEMS];
    struct ld_folder_preview_rect written[LD_FLS_MAX_PREVIEW_ITEMS];
};

static struct ld_fls_preview_slot g_fls_preview_slots[LD_FLS_PREVIEW_SLOTS];

static int ld_fls_folder_icon_pct_for_span(int span_x, int span_y, unsigned len)
{
    if (!__atomic_load_n(&g_home_options.icon_size_enabled, __ATOMIC_ACQUIRE)) return 100;
    int pct = 100;
    if (span_x == 2 && span_y == 2) {
        pct = (len > 9u)
            ? __atomic_load_n(&g_home_options.folder_icon_size_2x2_9, __ATOMIC_ACQUIRE)
            : __atomic_load_n(&g_home_options.folder_icon_size_2x2, __ATOMIC_ACQUIRE);
    } else if (span_x == 1 && span_y == 2) {
        pct = __atomic_load_n(&g_home_options.folder_icon_size_1x2, __ATOMIC_ACQUIRE);
    } else if (span_x == 2 && span_y == 1) {
        pct = __atomic_load_n(&g_home_options.folder_icon_size_2x1, __ATOMIC_ACQUIRE);
    } else if (span_x >= 4 && span_y == 2) {
        pct = (len > 16u)
            ? __atomic_load_n(&g_home_options.folder_icon_size_4x2_6x3, __ATOMIC_ACQUIRE)
            : __atomic_load_n(&g_home_options.folder_icon_size_4x2, __ATOMIC_ACQUIRE);
    }
    return (pct >= 70 && pct <= 130) ? pct : 100;
}

static struct ld_fls_preview_slot *ld_fls_get_preview_slot(uint32_t cid)
{
    struct ld_fls_preview_slot *free_slot = NULL;
    for (unsigned i = 0; i < LD_FLS_PREVIEW_SLOTS; i++) {
        if (g_fls_preview_slots[i].valid && g_fls_preview_slots[i].cid == cid)
            return &g_fls_preview_slots[i];
        if (!g_fls_preview_slots[i].valid && !free_slot)
            free_slot = &g_fls_preview_slots[i];
    }
    return free_slot ? free_slot : &g_fls_preview_slots[0];
}

static void ld_fls_sync_ten_icon_inset(uintptr_t strat, uintptr_t x28, uint32_t null_low,
                                       uint32_t cid, int span_x, int span_y,
                                       double fw, double fh, double ph, double pv,
                                       int from_cal_item_size)
{
    if (!g_fls_list_off || (span_x == 1 && span_y == 1)) return;

    int is_ten_icon_shape = (span_x == 4 && span_y == 2 && fw > fh * 1.5);
    double inset = is_ten_icon_shape
        ? (double)__atomic_load_n(&g_home_options.folder_preview_inset, __ATOMIC_ACQUIRE)
        : 0.0;

    uint32_t lw = 0;
    if (!ld_read_heap(strat + g_fls_list_off, &lw, sizeof lw) || !(lw & 1u) || lw < 0x100000u) {
        return;
    }
    uintptr_t list = (uintptr_t)lw + (x28 << 32);
    uint32_t lhdr = 0, len_smi = 0, aw = 0;
    if ((null_low && (uint32_t)list == null_low) ||
        !ld_read_heap(list - 1u, &lhdr, sizeof lhdr) ||
        ((lhdr >> 12) & 0xfffffu) != 0x5cu ||
        !ld_read_heap(list + 0xbu, &len_smi, sizeof len_smi) ||
        (len_smi & 1u) ||
        !ld_read_heap(list + 0xfu, &aw, sizeof aw) ||
        !(aw & 1u) || aw < 0x100000u) {
        return;
    }
    unsigned len = len_smi >> 1;
    if (len == 0 || len > LD_FLS_MAX_PREVIEW_ITEMS) return;
    int pct = ld_fls_folder_icon_pct_for_span(span_x, span_y, len);
    int is_ten_icon = is_ten_icon_shape && (len == LD_FLS_TEN_ICON_COUNT);
    if (!is_ten_icon) inset = 0.0;

    struct ld_fls_preview_slot *slot = ld_fls_get_preview_slot(cid);
    if (pct == 100 && inset == 0.0 && (!slot->valid || (slot->applied_pct == 100 && slot->applied_inset == 0.0))) {
        return;
    }

    uintptr_t arr = (uintptr_t)aw + (x28 << 32);
    uintptr_t items[LD_FLS_MAX_PREVIEW_ITEMS];
    struct ld_folder_preview_rect cur[LD_FLS_MAX_PREVIEW_ITEMS];
    for (unsigned i = 0; i < len; i++) {
        uint32_t ew = 0;
        if (!ld_read_heap(arr + 0xfu + (size_t)i * 4u, &ew, sizeof ew) ||
            !(ew & 1u) || ew < 0x100000u)
            return;
        uintptr_t item = (uintptr_t)ew + (x28 << 32);
        if ((null_low && (uint32_t)item == null_low) || ld_ws_is_const_ro(item)) return;
        double w = 0.0, h = 0.0, x = 0.0, y = 0.0;
        if (!ld_read_heap(item + 7u, &w, sizeof w) ||
            !ld_read_heap(item + 15u, &h, sizeof h) ||
            !ld_read_heap(item + 23u, &x, sizeof x) ||
            !ld_read_heap(item + 31u, &y, sizeof y) ||
            !isfinite(w) || !isfinite(h) || !isfinite(x) || !isfinite(y) ||
            w < 2.0 || w > 500.0 || h < 2.0 || h > 500.0)
            return;
        items[i] = item;
        cur[i] = (struct ld_folder_preview_rect){x - ph, y - pv, w, h};
    }

    int same_shape = slot->valid && slot->cid == cid && slot->len == len &&
                     fabs(fw - slot->fw) <= 0.5 && fabs(fh - slot->fh) <= 0.5;
    if (!same_shape) {
        slot->written_valid = 0;
        double pad0 = fmin(cur[0].x, cur[0].y);
        double big_w = cur[0].width;
        if (big_w < 10.0) return;
        for (unsigned i = 0; i < len; i++) slot->orig[i] = cur[i];
        slot->cid = cid;
        slot->span_x = span_x;
        slot->span_y = span_y;
        slot->len = len;
        slot->fw = fw;
        slot->fh = fh;
        slot->pad0 = pad0;
        slot->big_w = big_w;
        slot->applied_inset = 0.0;
        slot->applied_pct = 100;
        slot->valid = 1;
    } else {
        unsigned fresh = 0;
        for (unsigned i = 0; i < len; i++) {
            int ours = slot->written_valid &&
                       fabs(cur[i].x - slot->written[i].x) <= 0.05 &&
                       fabs(cur[i].y - slot->written[i].y) <= 0.05 &&
                       fabs(cur[i].width - slot->written[i].width) <= 0.05 &&
                       fabs(cur[i].height - slot->written[i].height) <= 0.05;
            if (!ours) {
                slot->orig[i] = cur[i];
                fresh++;
                if (i == 0) {
                    double pad0 = fmin(cur[0].x, cur[0].y);
                    if (cur[0].width >= 10.0) {
                        slot->pad0 = pad0;
                        slot->big_w = cur[0].width;
                    }
                }
            }
        }
        if (fresh) {
            slot->applied_inset = 0.0;
            slot->applied_pct = 100;
        }
    }

    struct ld_folder_preview_rect adj[LD_FLS_MAX_PREVIEW_ITEMS];
    for (unsigned i = 0; i < len; i++) {
        adj[i] = slot->orig[i];
        if (is_ten_icon && inset != 0.0 && slot->pad0 > 1.0 && slot->big_w >= 30.0) {
            struct ld_folder_preview_rect tmp;
            if (ld_inset_folder_preview(fw, fh, slot->pad0, slot->big_w, inset,
                                        &slot->orig[i], &tmp)) {
                adj[i] = tmp;
            }
        }
    }

    if (pct != 100) {
        double scale = (double)pct / 100.0;
        double ref_big_w = adj[0].width;
        double mini_min_x = 1e9, mini_min_y = 1e9, mini_max_x = -1e9, mini_max_y = -1e9;
        int has_mini = 0;
        for (unsigned i = 0; i < len; i++) {
            if (adj[i].width < ref_big_w * 0.7) {
                has_mini = 1;
                if (adj[i].x < mini_min_x) mini_min_x = adj[i].x;
                if (adj[i].y < mini_min_y) mini_min_y = adj[i].y;
                if (adj[i].x + adj[i].width > mini_max_x) mini_max_x = adj[i].x + adj[i].width;
                if (adj[i].y + adj[i].height > mini_max_y) mini_max_y = adj[i].y + adj[i].height;
            }
        }
        double mini_cx = has_mini ? (mini_min_x + mini_max_x) * 0.5 : 0.0;
        double mini_cy = has_mini ? (mini_min_y + mini_max_y) * 0.5 : 0.0;
        for (unsigned i = 0; i < len; i++) {
            double nw = adj[i].width * scale;
            double nh = adj[i].height * scale;
            if (has_mini && adj[i].width < ref_big_w * 0.7) {
                double cx = adj[i].x + adj[i].width * 0.5;
                double cy = adj[i].y + adj[i].height * 0.5;
                double ncx = mini_cx + (cx - mini_cx) * scale;
                double ncy = mini_cy + (cy - mini_cy) * scale;
                adj[i].x = ncx - nw * 0.5;
                adj[i].y = ncy - nh * 0.5;
            } else {
                adj[i].x -= (nw - adj[i].width) * 0.5;
                adj[i].y -= (nh - adj[i].height) * 0.5;
            }
            adj[i].width = nw;
            adj[i].height = nh;
        }
    }

    if (!from_cal_item_size && fabs(inset - slot->applied_inset) < 0.01 &&
        pct == slot->applied_pct &&
        fabs(cur[0].x - adj[0].x) < 0.05 && fabs(cur[0].y - adj[0].y) < 0.05 &&
        fabs(cur[0].width - adj[0].width) < 0.05) {
        return;
    }

    for (unsigned i = 0; i < len; i++) {
        if (ld_dart_is_ro_addr(items[i])) continue;
        double nx = adj[i].x + ph;
        double ny = adj[i].y + pv;
        memcpy((void *)(items[i] + 7u), &adj[i].width, sizeof adj[i].width);
        memcpy((void *)(items[i] + 15u), &adj[i].height, sizeof adj[i].height);
        memcpy((void *)(items[i] + 23u), &nx, sizeof nx);
        memcpy((void *)(items[i] + 31u), &ny, sizeof ny);
        /* Store strategy-local (x, y) without ph/pv so subsequent padding syncs
         * do not invalidate the idempotency check and compound scale/inset. */
        slot->written[i] = (struct ld_folder_preview_rect){adj[i].x, adj[i].y, adj[i].width, adj[i].height};
    }
    if (g_fls_piw_off && g_fls_pih_off && !ld_ws_is_const_ro(strat)) {
        memcpy((void *)(strat + g_fls_piw_off), &adj[0].width, sizeof adj[0].width);
        memcpy((void *)(strat + g_fls_pih_off), &adj[0].height, sizeof adj[0].height);
    }
    slot->written_valid = 1;
    slot->applied_inset = inset;
    slot->applied_pct = pct;
    static unsigned ti_logs;
    if (ti_logs++ < 16u) {
        ld_blog("folder_layout_sync: preview synced cid=0x%x span=%dx%d len=%u pct=%d%% inset=%.1f orig0=(%.2f,%.2f,%.2f) adj0=(%.2f,%.2f,%.2f)",
                cid, span_x, span_y, len, pct, inset,
                slot->orig[0].x, slot->orig[0].y, slot->orig[0].width,
                adj[0].x, adj[0].y, adj[0].width);
    }
}

static struct ld_fls_cid_entry *ld_fls_find_cid(uint32_t cid)
{
    for (int i = 0; i < LD_FLS_MAX_CIDS; i++) {
        if (g_fls_cids[i].valid && g_fls_cids[i].cid == cid)
            return &g_fls_cids[i];
    }
    return NULL;
}

static void ld_fls_record_cid(uint32_t cid, int span_x, int span_y, double title_h)
{
    struct ld_fls_cid_entry *slot = ld_fls_find_cid(cid);
    if (!slot) {
        for (int i = 0; i < LD_FLS_MAX_CIDS; i++) {
            if (!g_fls_cids[i].valid) {
                slot = &g_fls_cids[i];
                break;
            }
        }
    }
    if (!slot) return;
    int changed = !slot->valid || slot->span_x != span_x || slot->span_y != span_y ||
                  fabs(slot->title_h - title_h) > 0.05;
    slot->cid = cid;
    slot->span_x = span_x;
    slot->span_y = span_y;
    slot->title_h = title_h;
    slot->valid = 1;
    if (changed) {
        static unsigned logs;
        if (logs++ < 16u) {
            ld_blog("folder_layout_sync: learned cid=0x%x span=%dx%d titleH=%.2f cw=%.2f ch=%.2f",
                    cid, span_x, span_y, title_h, g_hm_active_cw, g_hm_active_ch);
        }
    }
}

__attribute__((used)) static void ld_fls_post_cal_item_size(struct ld_ws_ctx *c)
{
    if (!__atomic_load_n(&g_fls_ready, __ATOMIC_RELAXED) || !c) return;
    double cw = g_hm_active_cw;
    double ch = g_hm_active_ch;
    if (!isfinite(cw) || !isfinite(ch) || cw <= 10.0 || ch <= 10.0) return;

    uintptr_t strat = (uintptr_t)c->x[1];
    if (!(strat & 1u) || strat < 0x100000u) return;
    uint32_t hdr = 0;
    if (!ld_read_heap(strat - 1u, &hdr, sizeof hdr)) return;
    uint32_t cid = (hdr >> 12) & 0xfffffu;
    if (cid < 0x20u) return;

    double fw = 0.0, fh = 0.0, pv = 0.0, ph = 0.0;
    if (!ld_read_heap(strat + g_fls_fw_off, &fw, sizeof fw) ||
        !ld_read_heap(strat + g_fls_fh_off, &fh, sizeof fh) ||
        !ld_read_heap(strat + g_fls_pv_off, &pv, sizeof pv) ||
        !ld_read_heap(strat + g_fls_ph_off, &ph, sizeof ph))
        return;
    if (!isfinite(fw) || !isfinite(fh) || !isfinite(pv) || !isfinite(ph)) return;
    if (fw < 10.0 || fw > 1500.0 || fh < 10.0 || fh > 1500.0) return;

    double span_x_f = (2.0 * ph + fw) / cw;
    int span_x = (int)llround(span_x_f);
    if (span_x < 1 || span_x > 6 || fabs(span_x_f - (double)span_x) > 0.05) return;

    double span_y_f = (2.0 * pv + fh) / ch;
    int span_y = (int)ceil(span_y_f - 0.02);
    if (span_y < 1 || span_y > 6) return;

    double title_h = ch * (double)span_y - fh - 2.0 * pv;
    if (!isfinite(title_h) || title_h < -1.0 || title_h > ch * 0.85) return;

    uint64_t null_obj = 0;
    if (c->x[27]) ld_read_heap((uintptr_t)c->x[27] + 64u, &null_obj, sizeof null_obj);
    uint32_t hot_w = 0;
    int in_hotseat = (span_x == 1 && span_y == 1) &&
                     ld_read_heap(strat + 0x43u, &hot_w, sizeof hot_w) &&
                     hot_w == (uint32_t)(null_obj + 0x20u);
    if (in_hotseat) return;
    ld_fls_record_cid(cid, span_x, span_y, title_h);
    if (span_x == 1 && span_y == 1 && !ld_ws_is_const_ro(strat)) {
        double ws_ratio = ld_ws_icon_ratio(NULL);
        if (ws_ratio != 1.0) {
            double nfw = fw * ws_ratio;
            double nfh = fh * ws_ratio;
            if (isfinite(nfw) && isfinite(nfh) && nfw >= 10.0 && nfw <= 400.0 && nfh >= 10.0 && nfh <= 400.0) {
                double nph = ph - 0.5 * (nfw - fw);
                double npv = pv - 0.5 * (nfh - fh);
                if (isfinite(nph) && isfinite(npv)) {
                    memcpy((void *)(strat + g_fls_fw_off), &nfw, sizeof nfw);
                    memcpy((void *)(strat + g_fls_fh_off), &nfh, sizeof nfh);
                    memcpy((void *)(strat + g_fls_ph_off), &nph, sizeof nph);
                    memcpy((void *)(strat + g_fls_pv_off), &npv, sizeof npv);
                    fw = nfw;
                    fh = nfh;
                    ph = nph;
                    pv = npv;
                }
            }
            double pw = 0.0, ph_box = 0.0;
            if (ld_read_heap(strat + 0x27u, &pw, sizeof pw) &&
                ld_read_heap(strat + 0x1fu, &ph_box, sizeof ph_box) &&
                isfinite(pw) && isfinite(ph_box) &&
                pw >= 10.0 && pw <= 400.0 && ph_box >= 10.0 && ph_box <= 400.0) {
                double npw = pw * ws_ratio;
                double nph_box = ph_box * ws_ratio;
                memcpy((void *)(strat + 0x27u), &npw, sizeof npw);
                memcpy((void *)(strat + 0x1fu), &nph_box, sizeof nph_box);
            }
        }
    }
    ld_fls_sync_ten_icon_inset(strat, c->x[28], (uint32_t)null_obj,
                               cid, span_x, span_y, fw, fh, ph, pv, 1);
}

__attribute__((used)) static void ld_fls_post_put_if_absent(struct ld_ws_ctx *c)
{
    if (!__atomic_load_n(&g_fls_ready, __ATOMIC_RELAXED) || !c) return;
    double cw = g_hm_active_cw;
    double ch = g_hm_active_ch;
    if (!isfinite(cw) || !isfinite(ch) || cw <= 10.0 || ch <= 10.0) return;

    uintptr_t ctrl = (uintptr_t)c->ret_x0;
    if (!(ctrl & 1u) || ctrl < 0x100000u) return;

    uint32_t sw = 0;
    if (!ld_read_heap(ctrl + g_fls_strat_off, &sw, sizeof sw)) return;
    if (!(sw & 1u) || sw < 0x100000u) return;

    uintptr_t strat = (uintptr_t)sw + (c->x[28] << 32);
    /* Compare against Dart null object stored in pool [x27 + 64] */
    uint64_t null_obj = 0;
    if (c->x[27] && ld_read_heap((uintptr_t)c->x[27] + 64u, &null_obj, sizeof null_obj)) {
        if ((uint32_t)strat == (uint32_t)null_obj) return;
    }

    uint32_t hdr = 0;
    if (!ld_read_heap(strat - 1u, &hdr, sizeof hdr)) return;
    uint32_t cid = (hdr >> 12) & 0xfffffu;
    struct ld_fls_cid_entry *e = ld_fls_find_cid(cid);
    if (!e) return;
    if (e->span_x == 1 && e->span_y == 1) {
        uint32_t hot_w = 0;
        if (ld_read_heap(strat + 0x43u, &hot_w, sizeof hot_w) &&
            hot_w == (uint32_t)(null_obj + 0x20u)) {
            return;
        }
    }

    double fw = 0.0, fh = 0.0, cur_pv = 0.0, cur_ph = 0.0;
    if (!ld_read_heap(strat + g_fls_fw_off, &fw, sizeof fw) ||
        !ld_read_heap(strat + g_fls_fh_off, &fh, sizeof fh) ||
        !ld_read_heap(strat + g_fls_pv_off, &cur_pv, sizeof cur_pv) ||
        !ld_read_heap(strat + g_fls_ph_off, &cur_ph, sizeof cur_ph))
        return;
    if (!isfinite(fw) || !isfinite(fh) || fw < 10.0 || fh < 10.0) return;

    double new_ph = (cw * (double)e->span_x - fw) * 0.5;
    double new_pv = (ch * (double)e->span_y - fh - e->title_h) * 0.5;
    if (!isfinite(new_ph) || !isfinite(new_pv)) return;
    double active_ph = cur_ph, active_pv = cur_pv;
    if ((fabs(cur_ph - new_ph) >= 0.01 || fabs(cur_pv - new_pv) >= 0.01) && !ld_ws_is_const_ro(strat)) {
        double dph = new_ph - cur_ph;
        double dpv = new_pv - cur_pv;
        memcpy((void *)(strat + g_fls_ph_off), &new_ph, sizeof new_ph);
        memcpy((void *)(strat + g_fls_pv_off), &new_pv, sizeof new_pv);
        unsigned shifted = ld_fls_shift_preview_items(strat, c->x[28], (uint32_t)null_obj, dph, dpv);
        active_ph = new_ph;
        active_pv = new_pv;
        static unsigned sync_logs;
        if (sync_logs++ < 16u) {
            ld_blog("folder_layout_sync: synced cid=0x%x span=%dx%d cw=%.2f ch=%.2f ph=%.2f->%.2f pv=%.2f->%.2f items=%u",
                    cid, e->span_x, e->span_y, cw, ch, cur_ph, new_ph, cur_pv, new_pv, shifted);
        }
    }
    ld_fls_sync_ten_icon_inset(strat, c->x[28], (uint32_t)null_obj,
                               cid, e->span_x, e->span_y, fw, fh, active_ph, active_pv, 0);
}

LD_WS_POST_OBSERVER(repl_fls_cal_item_size, ld_fls_post_cal_item_size, g_orig_fls_cal_item_size)
LD_WS_POST_OBSERVER(repl_fls_put_if_absent, ld_fls_post_put_if_absent, g_orig_fls_put_if_absent)

static void ld_folder_layout_sync_install(const struct native_api *api, ld_lib_info *lib)
{
    static int installed;
    if (installed || !api || !api->hook || !lib) return;
    installed = 1;
    if (!g_home_options.folder_layout_sync_present) {
        ld_blog("folder_layout_sync: targets not present");
        return;
    }

    uintptr_t fn_cal = 0, fn_put = 0, fn_ph = 0, fn_pv = 0, fn_prev = 0;
    if (!ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.folder_layout_sync_vaddr[0], &fn_cal) ||
        !ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.folder_layout_sync_vaddr[1], &fn_put) ||
        !ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.folder_layout_sync_vaddr[2], &fn_ph) ||
        !ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.folder_layout_sync_vaddr[3], &fn_pv) ||
        !ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.folder_layout_sync_vaddr[4], &fn_prev)) {
        ld_blog("folder_layout_sync: vaddr translation failed");
        return;
    }

    unsigned strat_h = 0, strat_v = 0, ph_off = 0, pv_off = 0, fw_off = 0, fh_off = 0;
    unsigned list_off = 0, def_off = 0;
    if (!ld_fls_decode_padding_getter(fn_ph, g_home_options.folder_layout_sync_size[2], &strat_h, &ph_off) ||
        !ld_fls_decode_padding_getter(fn_pv, g_home_options.folder_layout_sync_size[3], &strat_v, &pv_off) ||
        strat_h != strat_v) {
        ld_blog("folder_layout_sync: padding getter decode failed (sh=0x%x sv=0x%x ph=%u pv=%u)",
                strat_h, strat_v, ph_off, pv_off);
        return;
    }
    if (!ld_fls_decode_cal_item_size(fn_cal, g_home_options.folder_layout_sync_size[0],
                                     pv_off, ph_off, &fw_off, &fh_off)) {
        ld_blog("folder_layout_sync: calItemSize decode failed (pv=%u ph=%u)", pv_off, ph_off);
        return;
    }
    if (!ld_fls_decode_preview_fields(fn_prev, g_home_options.folder_layout_sync_size[4],
                                      &list_off, &def_off)) {
        ld_blog("folder_layout_sync: preview fields decode failed");
        return;
    }

    g_fls_strat_off = strat_h;
    g_fls_fw_off = fw_off;
    g_fls_fh_off = fh_off;
    g_fls_pv_off = pv_off;
    g_fls_ph_off = ph_off;
    g_fls_list_off = list_off;
    g_fls_def_off = def_off;
    if (g_home_options.folder_preview_dim_present) {
        uintptr_t piw_rt = 0, pih_rt = 0;
        if (ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.folder_preview_dim_vaddr[0], &piw_rt) &&
            ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.folder_preview_dim_vaddr[1], &pih_rt) &&
            executable_range(piw_rt, 8u) && executable_range(pih_rt, 8u)) {
            const uint32_t *w0 = (const uint32_t *)piw_rt;
            const uint32_t *w1 = (const uint32_t *)pih_rt;
            if ((w0[0] & 0xffe00fffu) == 0xfc400020u && w0[1] == 0xd65f03c0u &&
                (w1[0] & 0xffe00fffu) == 0xfc400020u && w1[1] == 0xd65f03c0u) {
                unsigned o0 = (w0[0] >> 12) & 0x1ffu;
                unsigned o1 = (w1[0] >> 12) & 0x1ffu;
                if (o0 >= 7u && o0 < 256u && o1 >= 7u && o1 < 256u) {
                    g_fls_piw_off = o0;
                    g_fls_pih_off = o1;
                }
            }
        }
    }

    if (!executable_range(fn_cal, 16u) || !executable_range(fn_put, 16u)) {
        ld_blog("folder_layout_sync: non-executable hook targets");
        return;
    }
    void *bk_cal = NULL, *bk_put = NULL;
    if (!g_orig_fls_cal_item_size) {
        if (api->hook((void *)fn_cal, (void *)repl_fls_cal_item_size, &bk_cal) != 0 || !bk_cal) {
            ld_blog("folder_layout_sync: calItemSize hook failed");
            return;
        }
        g_orig_fls_cal_item_size = bk_cal;
    }

    if (!g_orig_fls_put_if_absent) {
        if (api->hook((void *)fn_put, (void *)repl_fls_put_if_absent, &bk_put) != 0 || !bk_put) {
            ld_blog("folder_layout_sync: putIfAbsent hook failed");
            return;
        }
        g_orig_fls_put_if_absent = bk_put;
    }

    __atomic_store_n(&g_fls_ready, 1, __ATOMIC_RELEASE);
    ld_blog("folder_layout_sync: installed strat=0x%x fw=%u fh=%u pv=%u ph=%u list=%u def=%u piw=0x%x pih=0x%x",
            g_fls_strat_off, g_fls_fw_off, g_fls_fh_off, g_fls_pv_off, g_fls_ph_off,
            g_fls_list_off, g_fls_def_off, g_fls_piw_off, g_fls_pih_off);
}

#endif /* LD_FOLDER_LAYOUT_SYNC_H */
