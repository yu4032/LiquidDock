/* Independent Dock icon size scaling for Normal mode and Workstation mode.
 *
 * Hooks HotSeatsConstants2.mHotSeatsScale (resolved dynamically via ELF symbol)
 * using a CSP-free TLS shadow-stack pre+post observer.
 *
 * Because HotSeatsConstants2._dockGridConfig().icons.iconSize already reflects
 * any workspace icon_size scaling applied by home_icons.h, mHotSeatsScale is
 * multiplied by (dock_pct / ws_pct) for the active mode (Normal vs Workstation)
 * so Dock icon size is 100% independent of workspace icon size.
 */
#ifndef LD_DOCK_ICON_SIZE_H
#define LD_DOCK_ICON_SIZE_H

#include "bootlog.h"
#include "executable_range.h"
#include "grid_conf.h"
#include "lib_base.h"
#include "native_api.h"
#include "workstation.h"

#include <math.h>
#include <stdint.h>
#include <string.h>

static void *g_orig_dock_icon_scale __attribute__((used));
static int g_dock_icon_scale_installed __attribute__((used));

__attribute__((used)) static void ld_post_dock_icon_scale(struct ld_ws_ctx *c)
{
    if (!c || !g_hook_pin_ok || !g_home_options.icon_size_enabled) return;

    double orig_scale = 0.0;
    memcpy(&orig_scale, &c->ret_d0, sizeof(double));
    if (!isfinite(orig_scale) || orig_scale <= 0.0 || orig_scale > 4.0) return;

    int is_laptop = __atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED) ? 1 : 0;
    int dock_pct = is_laptop
        ? g_home_options.ws_dock_icon_size_percent
        : g_home_options.dock_icon_size_percent;
    int ws_pct = is_laptop
        ? g_home_options.ws_icon_size_percent
        : g_home_options.icon_size_percent;
    if (dock_pct < 70 || dock_pct > 130) dock_pct = 100;
    if (ws_pct < 70 || ws_pct > 130) ws_pct = 100;
    if (dock_pct == ws_pct) return;

    double adj = orig_scale * ((double)dock_pct / (double)ws_pct);
    if (!isfinite(adj) || adj <= 0.1 || adj > 3.0) return;
    memcpy(&c->ret_d0, &adj, sizeof(double));

    static unsigned logs;
    if (logs++ < 8u) {
        ld_blog("dock_icon_size: laptop=%d dock=%d%% ws=%d%% scale=%.3f->%.3f",
                is_laptop, dock_pct, ws_pct, orig_scale, adj);
    }
}

LD_WS_POST_OBSERVER(repl_dock_icon_scale, ld_post_dock_icon_scale, g_orig_dock_icon_scale)

static void ld_dock_icon_size_install(const struct native_api *api, const ld_lib_info *lib)
{
    static int attempted;
    if (attempted || !api || !api->hook || !lib) return;
    if (!g_home_options.icon_size_enabled) return;
    attempted = 1;
    if (!g_home_options.dock_icon_size_target_present) {
        ld_blog("dock_icon_size: target not present, skipped");
        return;
    }
    uintptr_t rt = 0;
    if (!ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.dock_icon_size_vaddr, &rt) ||
        !executable_range(rt, 16u)) {
        ld_blog("dock_icon_size: bad target vaddr=0x%llx",
                (unsigned long long)g_home_options.dock_icon_size_vaddr);
        return;
    }
    int rc = api->hook((void *)rt, (void *)repl_dock_icon_scale, &g_orig_dock_icon_scale);
    if (rc == 0 && g_orig_dock_icon_scale) {
        pin_installed_hook(rt);
        g_dock_icon_scale_installed = 1;
        ld_blog("dock_icon_size: installed at %p (vaddr=0x%llx)",
                (void *)rt, (unsigned long long)g_home_options.dock_icon_size_vaddr);
    } else {
        ld_blog("dock_icon_size: hook failed rc=%d", rc);
    }
}

#endif /* LD_DOCK_ICON_SIZE_H */
