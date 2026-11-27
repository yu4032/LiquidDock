/* Independent Dock & Workspace/AllApps icon size scaling.
 *
 * GridConfig.icons.iconSize is kept 100% untouched so Workspace icon scaling
 * never leaks into Dock, Dock background, Folders, or Widgets.
 *   - Dock (Normal & Workstation independently):
 *       HotSeatsConstants2.mHotSeatsScale, HotseatLayerGetxControllerPadNew._getItemActualWidth/Height,
 *       and ShortCutCommonMixin.launcherIconWidth/Height Dock branch (`stur d0, [x0, #0x7]`)
 *       scale directly by `dock_pct / 100.0`.
 *   - Workspace & AllApps:
 *       ShortCutCommonMixin.launcherIconWidth/Height non-Dock branch (`stur d1, [x0, #0x7]`)
 *       scales directly by `icon_size_percent / 100.0` (only for container <= 0,
 *       leaving opened-folder items untouched).
 */
#ifndef LD_DOCK_ICON_SIZE_H
#define LD_DOCK_ICON_SIZE_H

#include "bootlog.h"
#include "dart_observer.h"
#include "executable_range.h"
#include "grid_conf.h"
#include "lib_base.h"
#include "native_api.h"
#include "workstation.h"

#include <math.h>
#include <stdint.h>
#include <string.h>

static void *g_orig_dock_icon_scale __attribute__((used));
static void *g_orig_dock_item_w __attribute__((used));
static void *g_orig_dock_item_h __attribute__((used));
static void *g_orig_sc_dim_0 __attribute__((used));
static void *g_orig_sc_dim_1 __attribute__((used));
static void *g_orig_sc_dim_2 __attribute__((used));
static void *g_orig_sc_dim_3 __attribute__((used));
static uint32_t g_ws_sc_info_off[4] __attribute__((used)) = { 0x157u, 0x157u, 0x157u, 0x157u };
static uint32_t g_ws_sc_cont_off[4] __attribute__((used)) = { 0x47u, 0x47u, 0x47u, 0x47u };
static __thread int64_t g_sc_dim_cont[LD_WS_SHADOW_MAX];
static __thread uint8_t g_sc_dim_cont_valid[LD_WS_SHADOW_MAX];
static int g_dock_icon_scale_installed __attribute__((used));

static inline double ld_dock_icon_ratio(int *is_laptop_out, int *dock_pct_out)
{
    if (!g_hook_pin_ok || !g_home_options.icon_size_enabled) return 1.0;
    int is_laptop = __atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED) ? 1 : 0;
    int dock_pct = is_laptop
        ? g_home_options.ws_dock_icon_size_percent
        : g_home_options.dock_icon_size_percent;
    if (dock_pct < 70 || dock_pct > 130) dock_pct = 100;
    if (is_laptop_out) *is_laptop_out = is_laptop;
    if (dock_pct_out) *dock_pct_out = dock_pct;
    if (dock_pct == 100) return 1.0;
    return (double)dock_pct / 100.0;
}

static inline double ld_ws_icon_ratio(int *ws_pct_out)
{
    if (!g_hook_pin_ok || !g_home_options.icon_size_enabled) return 1.0;
    int ws_pct = g_home_options.icon_size_percent;
    if (ws_pct < 70 || ws_pct > 130) ws_pct = 100;
    if (ws_pct_out) *ws_pct_out = ws_pct;
    if (ws_pct == 100) return 1.0;
    return (double)ws_pct / 100.0;
}

static inline int ld_dock_item_is_divider(const struct ld_ws_ctx *c)
{
    uintptr_t item = (uintptr_t)c->x[2];
    if (!(item & 1u) || item < 0x100000u) return 0;
    uint32_t mw = 0;
    if (!ld_read_heap(item + 7u, &mw, sizeof mw) || !(mw & 1u) || mw < 0x100000u) return 0;
    uintptr_t model = (uintptr_t)mw + (c->x[28] << 32);
    int64_t t = 0;
    if (!ld_read_heap(model + g_ld_off.item_kind, &t, sizeof t)) return 0;
    return (t == 0x3eb || t == 0x3ef) ? 1 : 0;
}

__attribute__((used)) static void ld_post_dock_icon_scale(struct ld_ws_ctx *c)
{
    if (!c) return;
    int is_laptop = 0, dock_pct = 100;
    double ratio = ld_dock_icon_ratio(&is_laptop, &dock_pct);
    if (ratio == 1.0) return;

    double orig_scale = 0.0;
    memcpy(&orig_scale, &c->ret_d0, sizeof(double));
    if (!isfinite(orig_scale) || orig_scale <= 0.0 || orig_scale > 4.0) return;

    double adj = orig_scale * ratio;
    if (!isfinite(adj) || adj <= 0.1 || adj > 3.0) return;
    memcpy(&c->ret_d0, &adj, sizeof(double));

    static unsigned logs;
    if (logs++ < 6u) {
        ld_blog("dock_icon_size: laptop=%d dock=%d%% scale=%.3f->%.3f",
                is_laptop, dock_pct, orig_scale, adj);
    }
}

__attribute__((used)) static void ld_post_dock_item_dim(struct ld_ws_ctx *c)
{
    if (!c || ld_dock_item_is_divider(c)) return;
    double ratio = ld_dock_icon_ratio(NULL, NULL);
    if (ratio == 1.0) return;
    double orig = 0.0;
    memcpy(&orig, &c->ret_d0, sizeof(double));
    if (!isfinite(orig) || orig <= 10.0 || orig > 500.0) return;
    double adj = orig * ratio;
    if (!isfinite(adj) || adj <= 10.0 || adj > 500.0) return;
    memcpy(&c->ret_d0, &adj, sizeof(double));
}

static inline int ld_sc_dim_is_dock_container(int64_t container)
{
    return container == -101 || container == -102 || container == -103 ||
           container == -105 || container == -106;
}

__attribute__((used)) static void ld_pre_sc_dim_site(struct ld_ws_ctx *c, size_t idx)
{
    uint32_t slot = (g_ws_shadow_depth - 1u) & (LD_WS_SHADOW_MAX - 1u);
    g_sc_dim_cont_valid[slot] = 0u;
    g_sc_dim_cont[slot] = 0;
    if (!c || idx >= 4u) return;
    uintptr_t self = (uintptr_t)c->x[1];
    if (!(self & 1u) || self <= 0x100000u || self == (uintptr_t)c->x[22]) return;
    uint32_t info_w = 0;
    if (!ld_read_heap(self + g_ws_sc_info_off[idx], &info_w, sizeof info_w) ||
        !(info_w & 1u) || info_w == (uint32_t)c->x[22]) return;
    uintptr_t info = (uintptr_t)info_w + (c->x[28] << 32);
    int64_t container = 0;
    if (!ld_read_heap(info + g_ws_sc_cont_off[idx], &container, sizeof container)) return;
    g_sc_dim_cont[slot] = container;
    g_sc_dim_cont_valid[slot] = 1u;
}

__attribute__((used)) static void ld_post_sc_dim_site(struct ld_ws_ctx *c, size_t idx)
{
    if (!c || idx >= 4u) return;
    uint32_t slot = g_ws_shadow_depth & (LD_WS_SHADOW_MAX - 1u);
    if (!g_sc_dim_cont_valid[slot]) return;
    int64_t container = g_sc_dim_cont[slot];
    g_sc_dim_cont_valid[slot] = 0u;

    int is_dock = ld_sc_dim_is_dock_container(container);
    double ratio = 1.0;
    int is_laptop = 0, pct = 100;
    if (is_dock) {
        ratio = ld_dock_icon_ratio(&is_laptop, &pct);
    } else if (container > 0) {
        static unsigned skip_logs;
        if (skip_logs++ < 8u) {
            ld_blog("dock_icon_size: sc[%zu] skip folder item container=%lld",
                    idx, (long long)container);
        }
        return;
    } else {
        ratio = ld_ws_icon_ratio(&pct);
    }
    if (ratio == 1.0) return;

    double orig = 0.0;
    memcpy(&orig, &c->ret_d0, sizeof(double));
    if (!isfinite(orig) || orig <= 10.0 || orig > 500.0) return;
    double adj = orig * ratio;
    if (!isfinite(adj) || adj <= 10.0 || adj > 500.0) return;
    memcpy(&c->ret_d0, &adj, sizeof(double));

    /* Keep the freshly boxed double cached on self._launcherIconWidth/Height in sync. */
    uintptr_t box = (uintptr_t)c->ret_x0;
    if ((box & 1u) && box > 0x100000u && box != (uintptr_t)c->x[22] &&
        !ld_dart_is_ro_addr(box)) {
        double boxed = 0.0;
        if (ld_read_heap(box + 7u, &boxed, sizeof boxed) && boxed == orig) {
            memcpy((void *)(box + 7u), &adj, sizeof adj);
        }
    }
    static unsigned logs;
    if (logs++ < 8u) {
        ld_blog("dock_icon_size: sc[%zu] dock=%d laptop=%d pct=%d%% icon=%.2f->%.2f",
                idx, is_dock, is_laptop, pct, orig, adj);
    }
}

__attribute__((used)) static void ld_pre_sc_dim_0(struct ld_ws_ctx *c) { ld_pre_sc_dim_site(c, 0u); }
__attribute__((used)) static void ld_pre_sc_dim_1(struct ld_ws_ctx *c) { ld_pre_sc_dim_site(c, 1u); }
__attribute__((used)) static void ld_pre_sc_dim_2(struct ld_ws_ctx *c) { ld_pre_sc_dim_site(c, 2u); }
__attribute__((used)) static void ld_pre_sc_dim_3(struct ld_ws_ctx *c) { ld_pre_sc_dim_site(c, 3u); }
__attribute__((used)) static void ld_post_sc_dim_0(struct ld_ws_ctx *c) { ld_post_sc_dim_site(c, 0u); }
__attribute__((used)) static void ld_post_sc_dim_1(struct ld_ws_ctx *c) { ld_post_sc_dim_site(c, 1u); }
__attribute__((used)) static void ld_post_sc_dim_2(struct ld_ws_ctx *c) { ld_post_sc_dim_site(c, 2u); }
__attribute__((used)) static void ld_post_sc_dim_3(struct ld_ws_ctx *c) { ld_post_sc_dim_site(c, 3u); }

LD_WS_POST_OBSERVER(repl_dock_icon_scale, ld_post_dock_icon_scale, g_orig_dock_icon_scale)
LD_WS_POST_OBSERVER(repl_dock_item_w, ld_post_dock_item_dim, g_orig_dock_item_w)
LD_WS_POST_OBSERVER(repl_dock_item_h, ld_post_dock_item_dim, g_orig_dock_item_h)
LD_WS_PRE_POST_OBSERVER(repl_sc_dim_0, ld_pre_sc_dim_0, ld_post_sc_dim_0, g_orig_sc_dim_0)
LD_WS_PRE_POST_OBSERVER(repl_sc_dim_1, ld_pre_sc_dim_1, ld_post_sc_dim_1, g_orig_sc_dim_1)
LD_WS_PRE_POST_OBSERVER(repl_sc_dim_2, ld_pre_sc_dim_2, ld_post_sc_dim_2, g_orig_sc_dim_2)
LD_WS_PRE_POST_OBSERVER(repl_sc_dim_3, ld_pre_sc_dim_3, ld_post_sc_dim_3, g_orig_sc_dim_3)

/* Verify ShortCutCommonMixin.launcherIconWidth/Height prologue and decode
 * ctrl_info_off & info_container_off (no mid-function site scanning). */
static bool ld_decode_sc_dim_prologue(uintptr_t fn, size_t size,
                                      uint32_t *out_info_off, uint32_t *out_cont_off)
{
    if (!fn || size < 64u || !executable_range(fn, size)) return false;
    const uint32_t *w = (const uint32_t *)fn;
    size_t n = size / 4u;
    for (size_t i = 0; i + 4u < n && i < 16u; i++) {
        if ((w[i] & 0xffe0001fu) == 0xd2800011u && /* mov x17, #ctrl_info_off */
            w[i + 1] == 0xb8716820u && /* ldr w0, [x1, x17] */
            w[i + 2] == 0x8b1c8000u && /* add x0, x0, x28, lsl #32 */
            (w[i + 3] & 0xffe00fffu) == 0xf8400002u) { /* ldur x2, [x0, #container_off] */
            if (out_info_off) *out_info_off = (w[i] >> 5) & 0xffffu;
            if (out_cont_off) *out_cont_off = (w[i + 3] >> 12) & 0x1ffu;
            return true;
        }
    }
    return false;
}

static void ld_dock_icon_size_install(const struct native_api *api, const ld_lib_info *lib)
{
    static int attempted;
    if (attempted || !api || !api->hook || !lib) return;
    if (!g_home_options.icon_size_enabled) return;
    attempted = 1;
    if (!g_home_options.dock_icon_size_target_present || g_home_options.dock_icon_size_target_count < 1) {
        ld_blog("dock_icon_size: target not present, skipped");
        return;
    }
    int ok = 0;
    uintptr_t rt0 = 0;
    if (ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.dock_icon_size_vaddr[0], &rt0) &&
        executable_range(rt0, 16u) &&
        api->hook((void *)rt0, (void *)repl_dock_icon_scale, &g_orig_dock_icon_scale) == 0 &&
        g_orig_dock_icon_scale) {
        pin_installed_hook(rt0);
        ok++;
    }
    void *item_repls[2] = { (void *)repl_dock_item_w, (void *)repl_dock_item_h };
    void **item_origs[2] = { &g_orig_dock_item_w, &g_orig_dock_item_h };
    size_t item_idx = 0;
    void *sc_repls[4] = {
        (void *)repl_sc_dim_0, (void *)repl_sc_dim_1,
        (void *)repl_sc_dim_2, (void *)repl_sc_dim_3,
    };
    void **sc_origs[4] = {
        &g_orig_sc_dim_0, &g_orig_sc_dim_1,
        &g_orig_sc_dim_2, &g_orig_sc_dim_3,
    };
    size_t sc_idx = 0;
    for (size_t i = 1; i < g_home_options.dock_icon_size_target_count && i < 8; i++) {
        uintptr_t fn = 0;
        if (!ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.dock_icon_size_vaddr[i], &fn)) continue;
        uint32_t info_off = 0x157u, cont_off = 0x47u;
        if (ld_decode_sc_dim_prologue(fn, g_home_options.dock_icon_size_size[i],
                                      &info_off, &cont_off) &&
            sc_idx < 4u) {
            if (executable_range(fn, 16u) &&
                api->hook((void *)fn, sc_repls[sc_idx], sc_origs[sc_idx]) == 0 &&
                *sc_origs[sc_idx]) {
                g_ws_sc_info_off[sc_idx] = info_off;
                g_ws_sc_cont_off[sc_idx] = cont_off;
                pin_installed_hook(fn);
                sc_idx++;
                ok++;
            }
        } else if (executable_range(fn, 16u) && item_idx < 2u) {
            if (api->hook((void *)fn, item_repls[item_idx], item_origs[item_idx]) == 0 &&
                *item_origs[item_idx]) {
                pin_installed_hook(fn);
                item_idx++;
                ok++;
            }
        }
    }
    g_dock_icon_scale_installed = ok;
    ld_blog("dock_icon_size: installed %d prologue hooks (item=%zu sc=%zu info=[0x%x,0x%x,0x%x,0x%x] cont=0x%x)",
            ok, item_idx, sc_idx,
            g_ws_sc_info_off[0], g_ws_sc_info_off[1], g_ws_sc_info_off[2], g_ws_sc_info_off[3],
            g_ws_sc_cont_off[0]);
}

#endif /* LD_DOCK_ICON_SIZE_H */
