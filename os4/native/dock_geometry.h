#ifndef LD_DOCK_GEOMETRY_H
#define LD_DOCK_GEOMETRY_H
#include <math.h>
#include <string.h>
#include "dart_heap.h"

static int ld_dock_background(const struct ld_dock_conf *o,double *width,double *height,double *corner)
{
    if(!o->enabled || (o->width==0 && o->height==0 && o->corner==0)) return 0;
    double w=*width+o->width,h=*height+o->height,r=*corner+o->corner;
    if(!isfinite(w) || !isfinite(h) || !isfinite(r) || w<1 || h<1) return 0;
    if(o->corner!=0 && r<1) r=1; /* The native GlassOs4Background minimum. */
    *width=w; *height=h; *corner=r; return 1;
}

static int ld_dock_active(struct ld_dart_context *c)
{
    if(!g_hook_pin_ok || !g_home_options.dock.enabled) return 0;
    uintptr_t controller = ld_get_grid_controller(c);
    if (!controller) return 0;

    uintptr_t rx = ld_heap_pointer(c, controller, ld_get_grid_rx_offset());
    uintptr_t grid = rx ? ld_heap_pointer(c, rx, g_ld_off.rx_value) : 0;
    uintptr_t variables = grid ? ld_heap_pointer(c, grid, g_ld_off.gc_variables) : 0;
    uintptr_t orientation = variables ? ld_heap_pointer(c, variables, g_ld_off.gv_orientation) : 0;
    int64_t direction = orientation ? ld_heap_i64(orientation, 7) : -1;
    uint32_t mode = 0;
    if (!rx || !grid || !variables || !orientation || (direction != 0 && direction != 1)) return 0;
    if (!ld_read_heap(variables + g_ld_off.gv_layout_mode, &mode, 4) || !(mode & 1)) return 0;

    if (ld_is_fullscreen_layout(c, mode)) return 1;

    if (g_grid_cols > 0 && g_grid_rows > 0) {
        int64_t cl = ld_heap_i64(grid, g_ld_off.gc_cols), r = ld_heap_i64(grid, g_ld_off.gc_rows);
        if ((direction == 1 && cl == g_grid_cols && r == g_grid_rows) ||
            (direction == 0 && cl == g_grid_rows && r == g_grid_cols)) {
            return 1;
        }
    }
    return 1;
}

static void *g_orig_dock_background __attribute__((used));
static void *g_orig_dock_diff_bg __attribute__((used));
static void *g_orig_dock_spacing __attribute__((used));
__attribute__((used)) static void ld_observe_dock_background(struct ld_dart_context *c)
{
    /* 实验 B：只隐藏 Dock 背景（宽高置 0），保留布局与图标。 */
    if (access("/data/user_de/0/com.miui.home/files/liquiddock-dockbg-hide", F_OK) == 0) {
        uintptr_t params=c->x[2],size=ld_heap_pointer(c,params,7);
        if(!size || ld_dart_is_ro_addr(size)) return;
        double zero=0.0;
        memcpy((void *)(size+7),&zero,8); memcpy((void *)(size+0xf),&zero,8);
        static unsigned hide_logs;
        if(__atomic_fetch_add(&hide_logs,1u,__ATOMIC_RELAXED)<3)
            ld_blog("dockbg-hide: params=%p size=%p forced 0x0",(void*)params,(void*)size);
        return;
    }
    if (__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED) &&
        g_home_options.ws.dock_custom) {
        uintptr_t params = c->x[2], size = ld_heap_pointer(c, params, 7);
        if (g_home_options.ws.taskbar_mode) {
            double dw = ld_ws_device_width();
            double corner = 0.0;
            if (size) {
                if (dw > 100.0 && !ld_ws_is_const_ro(size)) {
                    double taskbar_w = dw + 32.0;
                    memcpy((void *)(size + 7), &taskbar_w, 8);
                }
                if (g_home_options.ws.dock_height != 0.0) {
                    ld_ws_apply_dock_height(size);
                }
                if (!ld_ws_is_const_ro(params)) {
                    memcpy((void *)(params + 0xb), &corner, 8);
                }
                return;
            }
        }
    }
    if(g_home_options.dock.width==0 && g_home_options.dock.height==0 && g_home_options.dock.corner==0) return;
    if(!ld_dock_active(c)) return;
    uintptr_t params=c->x[2],size=ld_heap_pointer(c,params,7);
    double width=0,height=0,corner=0;
    if(!size || !ld_read_heap(size+7,&width,8) || !ld_read_heap(size+0xf,&height,8)
            || !ld_read_heap(params+0xb,&corner,8)
            || !ld_dock_background(&g_home_options.dock,&width,&height,&corner)) return;
    /* Fresh params, before equality comparison and publication to the native
     * background animation. Workspace reserves and icon sizes stay untouched. */
    if (!ld_dart_is_ro_addr(size)) {
        memcpy((void *)(size+7),&width,8); memcpy((void *)(size+0xf),&height,8);
    }
    if (!ld_dart_is_ro_addr(params)) {
        memcpy((void *)(params+0xb),&corner,8);
    }
}
__attribute__((used)) static void ld_post_dock_spacing(struct ld_ws_ctx *ws)
{
    if (!ws || g_home_options.dock.spacing == 0) return;
    struct ld_dart_context ctx = {0};
    ctx.x[22] = ws->x[22]; ctx.x[26] = ws->x[26];
    ctx.x[27] = ws->x[27]; ctx.x[28] = ws->x[28];
    if (!ld_dock_active(&ctx)) return;
    uintptr_t size = (uintptr_t)ws->ret_x0;
    double width = 0;
    if (!(size & 1u) || ld_dart_is_ro_addr(size) || !ld_read_heap(size + 7, &width, 8)) return;
    /* Legacy item decoration adds the configured distance to BOTH sides.
     * This Size is newly allocated on every _defaultIconSize invocation. */
    width += 2.0 * g_home_options.dock.spacing;
    if (!isfinite(width) || width < 1.0) return;
    memcpy((void *)(size + 7), &width, 8);
}
static void *g_orig_dock_icon_padding __attribute__((used));
__attribute__((used)) static void ld_observe_dock_icon_padding(struct ld_dart_context *c)
{
    if(g_home_options.dock.spacing==0 || !ld_dock_active(c)) return;
    double stride,icon,scale;
    memcpy(&stride,c->q[5],8); memcpy(&icon,c->q[6],8); memcpy(&scale,c->q[2],8);
    double baseline=stride-2*g_home_options.dock.spacing*scale;
    if(!isfinite(stride) || !isfinite(icon) || !isfinite(scale) || stride<=0 || baseline<=0) return;
    icon*=baseline/stride;
    memcpy(c->q[6],&icon,8);
}
static void *g_orig_dock_function_size __attribute__((used));
__attribute__((used)) static void ld_observe_dock_function_size(struct ld_dart_context *c)
{
    if(g_home_options.dock.spacing==0 || !ld_dock_active(c)) return;
    double base,cell; memcpy(&base,c->q[4],8);
    double adjusted=base+2*g_home_options.dock.spacing;
    if(!isfinite(base) || base<=0 || adjusted<=0
        || !ld_read_heap(c->x[29]-8,&cell,8) || !isfinite(cell)) return;
    cell*=base/adjusted;
    memcpy((void *)(c->x[29]-8),&cell,8);
}
static void *g_orig_dock_bottom __attribute__((used));
__attribute__((used)) static void ld_observe_dock_bottom(struct ld_dart_context *c)
{
    if (__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED) && g_home_options.ws.dock_custom) return;
    if(g_home_options.dock.bottom==0 || !ld_dock_active(c)) return;
    /* GridController.hotSeatsMarginBottom: d1 is the original absolute inset,
     * d0 is the vendor extra. The replayed fadd consumes both values. Keep
     * the shared grid immutable so workspace row geometry is unaffected. The
     * vendor page indicator follows the same hotseat bottom reference. */
    double margin=0; memcpy(&margin,c->q[1],8);
    margin+=g_home_options.dock.bottom;
    if(isfinite(margin)) memcpy(c->q[1],&margin,8);
}
static void *g_orig_dock_container_height __attribute__((used));
static void *g_orig_dock_window_height __attribute__((used));
static inline double ld_dock_effective_height_delta(struct ld_dart_context *c)
{
    if (__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED) &&
        g_home_options.ws.dock_custom && g_home_options.ws.taskbar_mode &&
        g_home_options.ws.dock_height != 0.0) {
        return g_home_options.ws.dock_height;
    }
    if (g_home_options.dock.enabled && ld_dock_active(c)) {
        double dh = g_home_options.dock.height > 0.0 ? g_home_options.dock.height : 0.0;
        if (g_home_options.dock_icon_size_percent > 100) {
            dh += 58.1 * ((double)(g_home_options.dock_icon_size_percent - 100) / 100.0);
        }
        return dh;
    }
    return 0.0;
}
__attribute__((used)) static void ld_observe_dock_container_height(struct ld_dart_context *c)
{
    double dh = ld_dock_effective_height_delta(c);
    if(dh<=0) return;
    uintptr_t box=c->x[2]; double height;
    if(!(box&1) || ld_dart_is_ro_addr(box) || !ld_read_heap(box+7,&height,8)) return;
    height+=dh;
    if(isfinite(height)) memcpy((void *)(box+7),&height,8);
}
__attribute__((used)) static void ld_post_dock_window_height(struct ld_ws_ctx *ws)
{
    if (!ws) return;
    struct ld_dart_context ctx = {0};
    ctx.x[22] = ws->x[22]; ctx.x[26] = ws->x[26];
    ctx.x[27] = ws->x[27]; ctx.x[28] = ws->x[28];
    double dh = ld_dock_effective_height_delta(&ctx);
    if (dh <= 0.0 || !isfinite(dh)) return;
    int64_t h = (int64_t)ws->ret_x0;
    if (h < 10 || h > 2000) return;
    ws->ret_x0 = (uint64_t)(h + (int64_t)ceil(dh));
}
static void *g_orig_dock_bg_sync __attribute__((used));
static void *g_orig_dock_pad_height __attribute__((used));
static uint32_t g_dock_pad_h_reg __attribute__((used)) = 2u;
static volatile uint64_t g_dock_last_norm_bg_h_bits __attribute__((used));

/* _HotseatWidgetPadState.build closure computes SizedBox(height: iconSize * 1.35)
 * (~78.4dp) around Stack(DockBackgroundView, DockItems). In landscape, full-size
 * (or scaled) dock icons + dock.height exceed 78.4dp and get hard-clamped here. */
__attribute__((used)) static void ld_on_dock_pad_height(struct ld_dart_context *c)
{
    uint32_t reg = g_dock_pad_h_reg;
    if (reg >= 8u) return;
    double orig_h = 0.0;
    memcpy(&orig_h, c->q[reg], sizeof orig_h);
    if (!isfinite(orig_h) || orig_h < 16.0 || orig_h > 300.0) return;

    int is_laptop = __atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED);
    double need = orig_h;
    if (is_laptop) {
        if (g_home_options.ws.dock_custom && g_home_options.ws.taskbar_mode &&
            g_home_options.ws.dock_height > 0.0 && isfinite(g_home_options.ws.dock_height)) {
            need = orig_h + g_home_options.ws.dock_height;
        }
    } else if (g_hook_pin_ok && g_home_options.dock.enabled) {
        double dh = g_home_options.dock.height > 0.0 ? g_home_options.dock.height : 0.0;
        double icon_extra = g_home_options.dock_icon_size_percent > 100
            ? 58.1 * ((double)(g_home_options.dock_icon_size_percent - 100) / 100.0)
            : 0.0;
        if (dh > 0.0 || icon_extra > 0.0) {
            need = fmax(need, orig_h + dh + icon_extra);
        }
        uint64_t bg_bits = __atomic_load_n(&g_dock_last_norm_bg_h_bits, __ATOMIC_RELAXED);
        if (bg_bits) {
            double bg_h = 0.0;
            memcpy(&bg_h, &bg_bits, sizeof bg_h);
            if (isfinite(bg_h) && bg_h > need && bg_h < 320.0) {
                need = bg_h;
            }
        }
    }
    if (need > orig_h + 0.01 && need < 320.0) {
        memcpy(c->q[reg], &need, sizeof need);
    }
}

/* Unified prologue sink for _DockBackgroundViewState._syncSpringToTarget:
 * serves both Normal mode (width/height/corner) and Workstation taskbar mode. */
__attribute__((used)) static void ld_on_dock_bg_sync(struct ld_dart_context *c)
{
    uintptr_t params = c->x[2];
    if (!(params & 1u)) return;
    uintptr_t size = ld_heap_pointer(c, params, 7);
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) {
        const struct ld_dock_conf *o = &g_home_options.dock;
        if (!g_hook_pin_ok || !o->enabled || (o->width == 0.0 && o->height == 0.0 && o->corner == 0.0)) return;
        if (!size) return;
        double sw = 0.0, sh = 0.0, cr = 0.0;
        if (!ld_read_heap(size + 7, &sw, sizeof sw) ||
            !ld_read_heap(size + 0xf, &sh, sizeof sh) ||
            !ld_read_heap(params + 0xb, &cr, sizeof cr)) return;
        static uintptr_t s_last_norm_size;
        static double s_last_norm_w, s_last_norm_h;
        if (size == s_last_norm_size && fabs(sw - s_last_norm_w) < 0.01 && fabs(sh - s_last_norm_h) < 0.01) {
            return;
        }
        double nw = sw + o->width, nh = sh + o->height, nr = cr + o->corner;
        if (!isfinite(nw) || !isfinite(nh) || !isfinite(nr) || nw < 1.0 || nh < 1.0) return;
        if (o->corner != 0.0 && nr < 1.0) nr = 1.0;
        if (!ld_ws_is_const_ro(size)) {
            memcpy((void *)(size + 7), &nw, sizeof nw);
            memcpy((void *)(size + 0xf), &nh, sizeof nh);
            s_last_norm_size = size;
            s_last_norm_w = nw;
            s_last_norm_h = nh;
            uint64_t nh_bits = 0;
            memcpy(&nh_bits, &nh, sizeof nh_bits);
            __atomic_store_n(&g_dock_last_norm_bg_h_bits, nh_bits, __ATOMIC_RELAXED);
        }
        if (o->corner != 0.0 && !ld_ws_is_const_ro(params)) {
            memcpy((void *)(params + 0xb), &nr, sizeof nr);
        }
        return;
    }
    if (!g_home_options.ws.dock_custom) return;
    double p_b = 0.0, sw = 0.0, sh = 0.0;
    ld_read_heap(params + 0xb, &p_b, sizeof p_b);
    if (size) {
        ld_read_heap(size + 7, &sw, sizeof sw);
        ld_read_heap(size + 0xf, &sh, sizeof sh);
    }
    static unsigned diag_logs;
    if (__atomic_fetch_add(&diag_logs, 1u, __ATOMIC_RELAXED) < 5) {
        ld_blog("dock_bg_sync_diag: taskbar=%d p_b=%.1f size=(%.1f,%.1f)",
                g_home_options.ws.taskbar_mode, p_b, sw, sh);
    }
    if (!g_home_options.ws.taskbar_mode) return;
    if (size && g_home_options.ws.dock_height != 0.0) {
        ld_ws_apply_dock_height(size);
    }
    double dw = ld_ws_device_width();
    double zero = 0.0;
    if (size && dw > 100.0 && !ld_ws_is_const_ro(size)) {
        double taskbar_w = dw + 32.0;
        memcpy((void *)(size + 7), &taskbar_w, sizeof taskbar_w);
    }
    if (!ld_ws_is_const_ro(params)) {
        memcpy((void *)(params + 0xb), &zero, sizeof zero);
    }
}

#ifndef LD_DOCK_NO_BRIDGE
LD_DART_OBSERVER(repl_dock_pad_height,ld_on_dock_pad_height,g_orig_dock_pad_height)
LD_DART_OBSERVER(repl_dock_bg_sync,ld_on_dock_bg_sync,g_orig_dock_bg_sync)
LD_DART_OBSERVER(repl_dock_function_size,ld_observe_dock_function_size,g_orig_dock_function_size)
LD_DART_OBSERVER(repl_dock_icon_padding,ld_observe_dock_icon_padding,g_orig_dock_icon_padding)
LD_DART_OBSERVER(repl_dock_bottom,ld_observe_dock_bottom,g_orig_dock_bottom)
LD_DART_OBSERVER(repl_dock_container_height,ld_observe_dock_container_height,g_orig_dock_container_height)
LD_WS_POST_OBSERVER(repl_dock_window_height,ld_post_dock_window_height,g_orig_dock_window_height)
LD_DART_OBSERVER(repl_dock_background,ld_observe_dock_background,g_orig_dock_background)
LD_DART_OBSERVER(repl_dock_diff_bg,ld_observe_dock_background,g_orig_dock_diff_bg)
LD_WS_POST_OBSERVER(repl_dock_spacing,ld_post_dock_spacing,g_orig_dock_spacing)

static void ld_dock_geometry_install_shared(const struct native_api *api, const ld_lib_info *lib)
{
    if (!api || !api->hook || !lib) return;
    if (g_home_options.workstation_targets_present > 7 && !g_orig_dock_bg_sync) {
        uintptr_t fn = 0;
        size_t sz = g_home_options.workstation_size[7];
        if (ld_vaddr_to_runtime(lib, g_home_options.workstation_vaddr[7], &fn) &&
            sz >= 24u && executable_range(fn, sz) && ld_hook_site_safe(fn, "dock_bg_sync")) {
            if (api->hook((void *)fn, (void *)repl_dock_bg_sync, &g_orig_dock_bg_sync) == 0 &&
                g_orig_dock_bg_sync) {
                pin_installed_hook(fn);
                ld_blog("dock_bg_sync prologue hook installed at %p", (void *)fn);
            }
        }
    }
    if (g_home_options.home_opt_targets_present &&
        g_home_options.home_opt_vaddr[12] && !g_orig_dock_pad_height) {
        uintptr_t pb_fn = 0;
        if (ld_vaddr_to_runtime(lib, g_home_options.home_opt_vaddr[12], &pb_fn) &&
            executable_range(pb_fn, 0x1000u)) {
            const uint32_t *insns = (const uint32_t *)pb_fn;
            uintptr_t site = 0;
            uint32_t out_reg = 2u;
            uint32_t ic_imm = (uint32_t)(g_ld_off.ic_icon_size & 0x1ffu);
            for (size_t i = 4; i + 16 < 0x400u; i++) {
                /* fmul d_out, d_a, d_b (0x1e600800 mask 0xffe0fc00) */
                if ((insns[i] & 0xffe0fc00u) != 0x1e600800u) continue;
                uint32_t d_out = insns[i] & 31u;
                uint32_t d_rn = (insns[i] >> 5) & 31u;
                uint32_t d_rm = (insns[i] >> 16) & 31u;
                if (d_out >= 8u) continue;
                int has_ldur = 0;
                for (size_t k = 1; k <= 4; k++) {
                    /* ldur d_ic, [Rn, #ic_icon_size] */
                    if ((insns[i - k] & 0xffe00c00u) == 0xfc400000u &&
                        (((insns[i - k] >> 12) & 0x1ffu) == ic_imm)) {
                        uint32_t d_ic = insns[i - k] & 31u;
                        if (d_ic == d_rn || d_ic == d_rm) { has_ldur = 1; break; }
                    }
                }
                if (!has_ldur) continue;
                for (size_t j = 1; j <= 14; j++) {
                    /* stur d_out, [Rn, #7] — register-agnostic on both Rn and d_out */
                    if ((insns[i + j] & 0xfffffc00u) == 0xfc007000u &&
                        (insns[i + j] & 31u) == d_out) {
                        site = (uintptr_t)&insns[i + j];
                        out_reg = d_out;
                        break;
                    }
                }
                if (site) break;
            }
            if (site && ld_hook_site_safe(site, "pad_dock_height")) {
                g_dock_pad_h_reg = out_reg;
                if (api->hook((void *)site, (void *)repl_dock_pad_height, &g_orig_dock_pad_height) == 0 &&
                    g_orig_dock_pad_height) {
                    pin_installed_hook(site);
                    ld_blog("dock pad height ceiling hook installed at %p (+0x%lx d%u)",
                            (void *)site, (unsigned long)(site - pb_fn), out_reg);
                }
            }
        }
    }
}
#endif
#endif
