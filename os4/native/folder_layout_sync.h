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

/* Synchronize FolderLayout4x2Grid10 (5x2 ten-icon) previewItemInfos in place so
 * preview rendering, folder open/close animations, and app launch/return animations
 * all target the exact same adjusted icon rectangles. */
#define LD_FLS_TEN_ICON_COUNT 13u
static int g_fls_ti_valid;
static uint32_t g_fls_ti_cid;
static double g_fls_ti_fw, g_fls_ti_fh, g_fls_ti_pad0, g_fls_ti_big_w;
static double g_fls_ti_applied_inset;
static struct ld_folder_preview_rect g_fls_ti_orig[LD_FLS_TEN_ICON_COUNT];
/* Absolute (cell-relative) rects we last wrote. Used to tell an already-adjusted list
 * (reused across calItemSize calls, e.g. long-press move) from a stock one so the
 * inset is never captured as the new baseline and stacked. */
static struct ld_folder_preview_rect g_fls_ti_written[LD_FLS_TEN_ICON_COUNT];
static int g_fls_ti_written_valid;

static void ld_fls_sync_ten_icon_inset(uintptr_t strat, uintptr_t x28, uint32_t null_low,
                                       uint32_t cid, int span_x, int span_y,
                                       double fw, double fh, double ph, double pv,
                                       int from_cal_item_size)
{
    if (!g_fls_list_off || span_x != 4 || span_y != 2 || fw <= fh * 1.5) return;

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
    if (len != LD_FLS_TEN_ICON_COUNT) {
        return;
    }
    uintptr_t arr = (uintptr_t)aw + (x28 << 32);

    uintptr_t items[LD_FLS_TEN_ICON_COUNT];
    struct ld_folder_preview_rect cur[LD_FLS_TEN_ICON_COUNT];
    for (unsigned i = 0; i < LD_FLS_TEN_ICON_COUNT; i++) {
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

    int same_shape = g_fls_ti_valid && cid == g_fls_ti_cid &&
                     fabs(fw - g_fls_ti_fw) <= 0.5 && fabs(fh - g_fls_ti_fh) <= 0.5;
    if (!same_shape) {
        g_fls_ti_written_valid = 0;
        double pad0 = fmin(cur[0].x, cur[0].y);
        double big_w = cur[0].width;
        if (pad0 <= 1.0 || big_w < 30.0) return;
        for (unsigned i = 0; i < LD_FLS_TEN_ICON_COUNT; i++) g_fls_ti_orig[i] = cur[i];
        g_fls_ti_cid = cid;
        g_fls_ti_fw = fw;
        g_fls_ti_fh = fh;
        g_fls_ti_pad0 = pad0;
        g_fls_ti_big_w = big_w;
        g_fls_ti_applied_inset = 0.0;
        g_fls_ti_valid = 1;
    } else {
        /* Either hook may (re)build the list, possibly only some entries. Per item: an
         * entry still equal to what we wrote is ours (keep its stock baseline);
         * anything else is stock. */
        unsigned fresh = 0;
        for (unsigned i = 0; i < LD_FLS_TEN_ICON_COUNT; i++) {
            int ours = g_fls_ti_written_valid &&
                       fabs(cur[i].x + ph - g_fls_ti_written[i].x) <= 0.05 &&
                       fabs(cur[i].y + pv - g_fls_ti_written[i].y) <= 0.05 &&
                       fabs(cur[i].width - g_fls_ti_written[i].width) <= 0.05 &&
                       fabs(cur[i].height - g_fls_ti_written[i].height) <= 0.05;
            if (!ours) {
                g_fls_ti_orig[i] = cur[i];
                fresh++;
                if (i == 0) {
                    double pad0 = fmin(cur[0].x, cur[0].y);
                    if (pad0 > 1.0 && cur[0].width >= 30.0) {
                        g_fls_ti_pad0 = pad0;
                        g_fls_ti_big_w = cur[0].width;
                    }
                }
            }
        }
        if (fresh) g_fls_ti_applied_inset = 0.0;
        static unsigned fresh_logs;
        if (fresh_logs++ < 12u)
            ld_blog("folder_layout_sync: ten-icon recalc cid=0x%x fresh=%u/%u written=%d", cid,
                    fresh, LD_FLS_TEN_ICON_COUNT, g_fls_ti_written_valid);
    }

    double inset = (double)__atomic_load_n(&g_home_options.folder_preview_inset, __ATOMIC_ACQUIRE);
    struct ld_folder_preview_rect target0 = g_fls_ti_orig[0];
    if (inset != 0.0) {
        if (!ld_inset_folder_preview(fw, fh, g_fls_ti_pad0, g_fls_ti_big_w, inset,
                                     &g_fls_ti_orig[0], &target0))
            return;
    }
    if (!from_cal_item_size && fabs(inset - g_fls_ti_applied_inset) < 0.01 &&
        fabs(cur[0].x - target0.x) < 0.05 && fabs(cur[0].y - target0.y) < 0.05 &&
        fabs(cur[0].width - target0.width) < 0.05) {
        return;
    }

    for (unsigned i = 0; i < LD_FLS_TEN_ICON_COUNT; i++) {
        struct ld_folder_preview_rect adj = g_fls_ti_orig[i];
        if (inset != 0.0 &&
            !ld_inset_folder_preview(fw, fh, g_fls_ti_pad0, g_fls_ti_big_w, inset,
                                     &g_fls_ti_orig[i], &adj)) {
            adj = g_fls_ti_orig[i];
        }
        double nx = adj.x + ph;
        double ny = adj.y + pv;
        memcpy((void *)(items[i] + 7u), &adj.width, sizeof adj.width);
        memcpy((void *)(items[i] + 15u), &adj.height, sizeof adj.height);
        memcpy((void *)(items[i] + 23u), &nx, sizeof nx);
        memcpy((void *)(items[i] + 31u), &ny, sizeof ny);
        g_fls_ti_written[i] = (struct ld_folder_preview_rect){nx, ny, adj.width, adj.height};
    }
    g_fls_ti_written_valid = 1;
    g_fls_ti_applied_inset = inset;
    __atomic_store_n(&g_fls_ten_icon_inset_active, 1, __ATOMIC_RELEASE);
    static unsigned ti_logs;
    if (ti_logs++ < 12u) {
        ld_blog("folder_layout_sync: ten-icon preview inset synced cid=0x%x inset=%.1f pad0=%.2f big_w=%.2f orig0=(%.2f,%.2f,%.2f) adj0=(%.2f,%.2f,%.2f)",
                cid, inset, g_fls_ti_pad0, g_fls_ti_big_w,
                g_fls_ti_orig[0].x, g_fls_ti_orig[0].y, g_fls_ti_orig[0].width,
                target0.x, target0.y, target0.width);
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

    ld_fls_record_cid(cid, span_x, span_y, title_h);
    uint64_t null_obj = 0;
    if (c->x[27]) ld_read_heap((uintptr_t)c->x[27] + 64u, &null_obj, sizeof null_obj);
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

    if (!executable_range(fn_cal, 16u) || !executable_range(fn_put, 16u)) {
        ld_blog("folder_layout_sync: non-executable hook targets");
        return;
    }
    void *bk_cal = NULL, *bk_put = NULL;
    if (api->hook((void *)fn_cal, (void *)repl_fls_cal_item_size, &bk_cal) != 0 || !bk_cal) {
        ld_blog("folder_layout_sync: calItemSize hook failed");
        return;
    }
    g_orig_fls_cal_item_size = bk_cal;

    if (api->hook((void *)fn_put, (void *)repl_fls_put_if_absent, &bk_put) != 0 || !bk_put) {
        ld_blog("folder_layout_sync: putIfAbsent hook failed");
        return;
    }
    g_orig_fls_put_if_absent = bk_put;

    __atomic_store_n(&g_fls_ready, 1, __ATOMIC_RELEASE);
    ld_blog("folder_layout_sync: installed strat=0x%x fw=%u fh=%u pv=%u ph=%u list=%u def=%u",
            g_fls_strat_off, g_fls_fw_off, g_fls_fh_off, g_fls_pv_off, g_fls_ph_off,
            g_fls_list_off, g_fls_def_off);
}

#endif /* LD_FOLDER_LAYOUT_SYNC_H */
