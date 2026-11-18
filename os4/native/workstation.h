#ifndef LD_WORKSTATION_H
#define LD_WORKSTATION_H

#include <math.h>
#include <stdint.h>
#include <string.h>
#include "native_api.h"
#include "lib_base.h"
#include "aot_scanner.h"
#include "executable_range.h"
#include "hook_page_pin.h"
#include "bootlog.h"
#include "dart_heap.h"
#include "dart_observer.h"
#include "grid_orientation.h"

/*
 * Post-call observer on system SP (not Dart SP x15) so nested Dart calls inside
 * `original` never clobber our saved frame. Saves x0..x30 (at [sp, #0..240]),
 * q0..q1 (at [sp, #256..288]), and return x0/d0 at [sp, #288]/[sp, #296].
 */
struct ld_ws_ctx {
    uint64_t x[31];
    uint64_t nzcv;
    unsigned char q0[16];
    unsigned char q1[16];
    uint64_t ret_x0;
    uint64_t ret_d0;
};

#define LD_WS_POST_OBSERVER(name, callback, original) \
__attribute__((naked, used)) static void name(void) { \
    __asm__ volatile( \
        "sub sp, sp, #320\n" \
        "stp x0, x1, [sp, #0]\n" \
        "stp x2, x3, [sp, #16]\n" \
        "stp x4, x5, [sp, #32]\n" \
        "stp x6, x7, [sp, #48]\n" \
        "stp x8, x9, [sp, #64]\n" \
        "stp x10, x11, [sp, #80]\n" \
        "stp x12, x13, [sp, #96]\n" \
        "stp x14, x15, [sp, #112]\n" \
        "stp x16, x17, [sp, #128]\n" \
        "stp x18, x19, [sp, #144]\n" \
        "stp x20, x21, [sp, #160]\n" \
        "stp x22, x23, [sp, #176]\n" \
        "stp x24, x25, [sp, #192]\n" \
        "stp x26, x27, [sp, #208]\n" \
        "stp x28, x29, [sp, #224]\n" \
        "str x30, [sp, #240]\n" \
        "mrs x16, nzcv\n" \
        "str x16, [sp, #248]\n" \
        "stp q0, q1, [sp, #256]\n" \
        "ldp x0, x1, [sp, #0]\n" \
        "ldp x2, x3, [sp, #16]\n" \
        "ldp x4, x5, [sp, #32]\n" \
        "ldp x6, x7, [sp, #48]\n" \
        "adrp x16, " #original "\n" \
        "ldr x16, [x16, :lo12:" #original "]\n" \
        "blr x16\n" \
        "str x0, [sp, #288]\n" \
        "str d0, [sp, #296]\n" \
        "str x15, [sp, #304]\n" \
        "mov x0, sp\n" \
        "bl " #callback "\n" \
        "ldr x0, [sp, #288]\n" \
        "ldr d0, [sp, #296]\n" \
        "ldr x15, [sp, #304]\n" \
        "ldr x30, [sp, #240]\n" \
        "add sp, sp, #320\n" \
        "ret\n"); \
}

#define LD_WS_PRE_POST_OBSERVER(name, pre_cb, post_cb, original) \
__attribute__((naked, used)) static void name(void) { \
    __asm__ volatile( \
        "sub sp, sp, #320\n" \
        "stp x0, x1, [sp, #0]\n" \
        "stp x2, x3, [sp, #16]\n" \
        "stp x4, x5, [sp, #32]\n" \
        "stp x6, x7, [sp, #48]\n" \
        "stp x8, x9, [sp, #64]\n" \
        "stp x10, x11, [sp, #80]\n" \
        "stp x12, x13, [sp, #96]\n" \
        "stp x14, x15, [sp, #112]\n" \
        "stp x16, x17, [sp, #128]\n" \
        "stp x18, x19, [sp, #144]\n" \
        "stp x20, x21, [sp, #160]\n" \
        "stp x22, x23, [sp, #176]\n" \
        "stp x24, x25, [sp, #192]\n" \
        "stp x26, x27, [sp, #208]\n" \
        "stp x28, x29, [sp, #224]\n" \
        "str x30, [sp, #240]\n" \
        "mrs x16, nzcv\n" \
        "str x16, [sp, #248]\n" \
        "stp q0, q1, [sp, #256]\n" \
        "mov x0, sp\n" \
        "bl " #pre_cb "\n" \
        "ldp x0, x1, [sp, #0]\n" \
        "ldp x2, x3, [sp, #16]\n" \
        "ldp x4, x5, [sp, #32]\n" \
        "ldp x6, x7, [sp, #48]\n" \
        "ldp x14, x15, [sp, #112]\n" \
        "ldp x26, x27, [sp, #208]\n" \
        "ldp x28, x29, [sp, #224]\n" \
        "adrp x16, " #original "\n" \
        "ldr x16, [x16, :lo12:" #original "]\n" \
        "blr x16\n" \
        "str x0, [sp, #288]\n" \
        "str d0, [sp, #296]\n" \
        "str x15, [sp, #304]\n" \
        "mov x0, sp\n" \
        "bl " #post_cb "\n" \
        "ldr x0, [sp, #288]\n" \
        "ldr d0, [sp, #296]\n" \
        "ldr x15, [sp, #304]\n" \
        "ldr x30, [sp, #240]\n" \
        "add sp, sp, #320\n" \
        "ret\n"); \
}

static volatile uint64_t g_ws_device_width_bits __attribute__((used));

static void *g_orig_ws_is_laptop __attribute__((used));
static void *g_orig_ws_margin_bottom __attribute__((used));
static void *g_orig_ws_dock_icon_top __attribute__((used));
static void *g_orig_ws_apps_padding __attribute__((used));
static void *g_orig_ws_indicator_pos __attribute__((used));
static void *g_orig_ws_device_width __attribute__((used));
static void *g_orig_ws_bg_strategy __attribute__((used));
static void *g_orig_ws_bg_sync __attribute__((used));
static void *g_orig_ws_build_dock_layout __attribute__((used));
static void *g_orig_ws_allapps_ind __attribute__((used));
static void *g_orig_ws_hotseat_bottom __attribute__((used));
static void *g_orig_ws_cal_offset __attribute__((used));

__attribute__((used,naked)) static void repl_ws_skip_blur_degrade(void)
{
    __asm__ volatile(
        "mov x0, x22\n"
        "ret\n"
    );
}

static inline double ld_ws_device_width(void)
{
    uint64_t bits = __atomic_load_n(&g_ws_device_width_bits, __ATOMIC_RELAXED);
    if (!bits) return 0.0;
    double d = 0.0;
    memcpy(&d, &bits, sizeof d);
    return (isfinite(d) && d > 100.0) ? d : 0.0;
}

static volatile int g_ws_last_dir __attribute__((used)) = -1;

static inline int ld_ws_direction(const struct ld_ws_ctx *c)
{
    struct ld_dart_context dc;
    memcpy(dc.x, c->x, sizeof(dc.x));
    uintptr_t controller = ld_get_grid_controller(&dc);
    if (controller) {
        uintptr_t rx = ld_heap_pointer(&dc, controller, ld_get_grid_rx_offset());
        uintptr_t grid = rx ? ld_heap_pointer(&dc, rx, 0x13) : 0;
        if (grid) {
            int dir = ld_grid_direction(&dc, grid);
            if (dir == 0 || dir == 1) {
                __atomic_store_n(&g_ws_last_dir, dir, __ATOMIC_RELAXED);
                return dir;
            }
        }
    }
    int last = __atomic_load_n(&g_ws_last_dir, __ATOMIC_RELAXED);
    if (last == 0 || last == 1) return last;
    double dw = ld_ws_device_width();
    if (dw > 100.0) return (dw > 800.0) ? 1 : 0;
    return -1;
}

static inline int ld_ws_is_const_ro(uintptr_t addr)
{
    Dl_info di;
    return dladdr((const void *)addr, &di) != 0;
}

static inline int ld_ws_is_laptop(const struct ld_ws_ctx *c)
{
    (void)c;
    return __atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED);
}

__attribute__((used)) static void ld_ws_post_is_laptop(struct ld_ws_ctx *c)
{
    int laptop = ((uintptr_t)c->ret_x0 == (c->x[22] + 0x20)) ? 1 : 0;
    static int s_prev = -1;
    if (laptop != s_prev) {
        s_prev = laptop;
        setenv("LD_WS_LAPTOP", laptop ? "1" : "0", 1);
        ld_blog("ws_is_laptop_ret: laptop=%d", laptop);
    }
    __atomic_store_n(&g_ws_is_laptop_mode, laptop, __ATOMIC_RELAXED);
}

__attribute__((used)) static void ld_ws_post_device_width(struct ld_ws_ctx *c)
{
    double w = 0.0;
    memcpy(&w, &c->ret_d0, sizeof w);
    if (isfinite(w) && w > 100.0 && w < 8192.0) {
        __atomic_store_n(&g_ws_device_width_bits, c->ret_d0, __ATOMIC_RELAXED);
    }
}

static volatile uint64_t g_ws_orig_mb_bits __attribute__((used));

static inline double ld_ws_effective_dock_v_offset(void)
{
    return g_home_options.ws.dock_v_offset;
}

__attribute__((used)) static void ld_ws_post_margin_bottom(struct ld_ws_ctx *c)
{
    if (!ld_ws_is_laptop(c)) return;
    if (!g_home_options.ws.dock_custom) return;
    double mb = 0.0;
    memcpy(&mb, &c->ret_d0, sizeof mb);
    double v_off = ld_ws_effective_dock_v_offset();
    static unsigned logs;
    if (__atomic_fetch_add(&logs, 1u, __ATOMIC_RELAXED) < 3) {
        ld_blog("ws_grid_margin_bottom: orig=%.1f taskbar=%d v_off=%.1f",
                mb, g_home_options.ws.taskbar_mode, v_off);
    }
    if (!isfinite(mb)) return;
    uint64_t orig_bits = 0;
    memcpy(&orig_bits, &mb, sizeof orig_bits);
    __atomic_store_n(&g_ws_orig_mb_bits, orig_bits, __ATOMIC_RELAXED);
    if (g_home_options.ws.taskbar_mode) {
        mb = -v_off;
    } else if (v_off != 0.0) {
        mb -= v_off;
    } else {
        return;
    }
    memcpy(&c->ret_d0, &mb, sizeof mb);
}

__attribute__((used)) static void ld_ws_post_dock_icon_top(struct ld_ws_ctx *c)
{
    (void)c;
    return;
}

__attribute__((used)) static void ld_ws_post_apps_padding(struct ld_ws_ctx *c)
{
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    if (!g_home_options.ws.dock_custom) return;
    uintptr_t root = (uintptr_t)c->ret_x0;
    if (!(root & 1)) return;
    struct ld_dart_context dc;
    memcpy(dc.x, c->x, sizeof(dc.x));
    for (size_t r_off = 0x7; r_off <= 0x17; r_off += 4) {
        uintptr_t pad_widget = ld_heap_pointer(&dc, root, r_off);
        if (!pad_widget) continue;
        for (size_t p_off = 0x7; p_off <= 0x17; p_off += 4) {
            uintptr_t cand = ld_heap_pointer(&dc, pad_widget, p_off);
            double l = 1, tp = 1, r = 1, bt = 0;
            if (cand && !ld_ws_is_const_ro(cand) &&
                ld_read_heap(cand + 7, &l, 8) && ld_read_heap(cand + 0xf, &tp, 8) &&
                ld_read_heap(cand + 0x17, &r, 8) && ld_read_heap(cand + 0x1f, &bt, 8) &&
                l == 0.0 && tp == 0.0 && r == 0.0 && isfinite(bt) && bt < 0.0 && bt > -1000.0) {
                double zero = 0.0;
                memcpy((void *)(cand + 0x1f), &zero, 8);
                return;
            }
        }
    }
}

__attribute__((used)) static void ld_ws_post_indicator_pos(struct ld_ws_ctx *c)
{
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    if (!g_home_options.ws.dock_custom) return;
    uintptr_t offset_obj = (uintptr_t)c->ret_x0;
    if (!(offset_obj & 1)) return;
    int dir = ld_ws_direction(c);
    static unsigned enter_logs;
    if (__atomic_fetch_add(&enter_logs, 1u, __ATOMIC_RELAXED) < 3) {
        ld_blog("ws_indicator_enter: dir=%d ind_y=(%.1f,%.1f)",
                dir, g_home_options.ws.apps_land_ind_y, g_home_options.ws.apps_port_ind_y);
    }
    if (dir < 0) return;
    double ind_y = dir == 1 ? g_home_options.ws.apps_land_ind_y : g_home_options.ws.apps_port_ind_y;
    if (!isfinite(ind_y) || ind_y == 0.0) return;
    double dy = 0.0;
    if (!ld_read_heap(offset_obj + 0xf, &dy, 8) || !isfinite(dy)) return;
    static unsigned logs;
    if (__atomic_fetch_add(&logs, 1u, __ATOMIC_RELAXED) < 3) {
        ld_blog("ws_indicator_pos: dir=%d orig_dy=%.1f ind_y=%.1f", dir, dy, ind_y);
    }
    dy += ind_y;
    if (ld_ws_is_const_ro(offset_obj)) return;
    memcpy((void *)(offset_obj + 0xf), &dy, 8);
}

__attribute__((used)) static void ld_ws_on_dock_build_content(struct ld_ws_ctx *c)
{
    uintptr_t layout = (uintptr_t)c->x[2];
    if (!(layout & 1)) return;
    struct ld_dart_context dc;
    memcpy(dc.x, c->x, sizeof(dc.x));
    double dw = 0.0, mb = 0.0;
    if (ld_read_heap(layout + 7, &dw, sizeof dw) && isfinite(dw) && dw > 100.0 && dw < 8192.0) {
        uint64_t bits = 0;
        memcpy(&bits, &dw, sizeof bits);
        __atomic_store_n(&g_ws_device_width_bits, bits, __ATOMIC_RELAXED);
    }
    if (!ld_read_heap(layout + 0x17, &mb, sizeof mb) || !isfinite(mb) || mb < 0.0) return;
    static uintptr_t last_layout;
    static double last_orig_mb;
    if (layout != last_layout) {
        last_layout = layout;
        last_orig_mb = mb;
    } else {
        mb = last_orig_mb;
    }
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    if (!g_home_options.ws.dock_custom) return;
    if (ld_ws_is_const_ro(layout)) return;
    uint64_t orig_bits = __atomic_load_n(&g_ws_orig_mb_bits, __ATOMIC_RELAXED);
    double orig_mb = mb;
    if (orig_bits) {
        double tmp = 0.0;
        memcpy(&tmp, &orig_bits, sizeof tmp);
        if (isfinite(tmp) && tmp > 1.0) orig_mb = tmp;
    }
    double v_off = ld_ws_effective_dock_v_offset();
    if (g_home_options.ws.taskbar_mode) {
        mb = -v_off;
    } else if (v_off != 0.0) {
        mb -= v_off;
    } else {
        return;
    }
    static double s_prev_new_mb = -9999.0;
    if (mb != s_prev_new_mb) {
        s_prev_new_mb = mb;
        double d43=0, d4b=0, d53=0, d5b=0, d63=0;
        ld_read_heap(layout + 0x43, &d43, 8);
        ld_read_heap(layout + 0x4b, &d4b, 8);
        ld_read_heap(layout + 0x53, &d53, 8);
        ld_read_heap(layout + 0x5b, &d5b, 8);
        ld_read_heap(layout + 0x63, &d63, 8);
        ld_blog("ws_dock_build_content: orig_mb=%.1f new_mb=%.1f taskbar=%d v_off=%.1f ext=(%.1f,%.1f,%.1f,%.1f,%.1f)",
                orig_mb, mb, g_home_options.ws.taskbar_mode, g_home_options.ws.dock_v_offset,
                d43, d4b, d53, d5b, d63);
    }
    memcpy((void *)(layout + 0x17), &mb, sizeof mb);
}

__attribute__((used)) static void ld_ws_post_dock_build_content(struct ld_ws_ctx *c)
{
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    if (!g_home_options.ws.dock_custom) return;
    int dir = ld_ws_direction(c);
    if (dir < 0) return;
    double off = dir == 1 ? g_home_options.ws.dock_icon_land_v_offset
                          : g_home_options.ws.dock_icon_port_v_offset;
    uintptr_t stack = (uintptr_t)c->ret_x0;
    if (!(stack & 1)) return;
    struct ld_dart_context dc;
    memcpy(dc.x, c->x, sizeof(dc.x));
    uintptr_t list = 0;
    for (size_t off_l = 0x7; off_l <= 0x27; off_l += 4) {
        uintptr_t cand = ld_heap_pointer(&dc, stack, off_l);
        if (!cand) continue;
        uint32_t len_smi = ld_heap_u32(cand, 0xb);
        if ((len_smi & 1) == 0 && len_smi >= 2 && len_smi <= 16) {
            list = cand;
            break;
        }
    }
    if (!list) return;
    uintptr_t data = ld_heap_pointer(&dc, list, 0xf);
    if (!data) return;
    uintptr_t pos1 = ld_heap_pointer(&dc, data, 0x13);
    if (g_home_options.ws.dock_custom) {
        uintptr_t pos0 = ld_heap_pointer(&dc, data, 0xf);
        uintptr_t pad0 = pos0 ? ld_heap_pointer(&dc, pos0, 0xb) : 0;
        uintptr_t mb_box = pos1 ? ld_heap_pointer(&dc, pos1, 0x1f) : 0;
        if (pos0 && !ld_ws_is_const_ro(pos0) && mb_box) {
            uint32_t mb_cptr = (uint32_t)mb_box;
            memcpy((void *)(pos0 + 0x1f), &mb_cptr, sizeof mb_cptr);
            uintptr_t insets0 = pad0 ? ld_heap_pointer(&dc, pad0, 0xf) : 0;
            if (insets0 && !ld_ws_is_const_ro(insets0)) {
                double zero = 0.0;
                memcpy((void *)(insets0 + 0x1f), &zero, sizeof zero);
            }
        }
        if (g_home_options.ws.taskbar_mode) {
            uintptr_t align0 = pad0 ? ld_heap_pointer(&dc, pad0, 0xb) : 0;
            uintptr_t sized0 = align0 ? ld_heap_pointer(&dc, align0, 0xb) : 0;
            uintptr_t w_box = sized0 ? ld_heap_pointer(&dc, sized0, 0xf) : 0;
            double w_val = 0.0;
            if (w_box && !ld_ws_is_const_ro(w_box) && !ld_ws_is_const_ro(pos0) &&
                !ld_ws_is_const_ro(sized0) &&
                ld_read_heap(w_box + 7, &w_val, 8) && isfinite(w_val) && w_val > 100.0) {
                double neg8 = -8.0;
                uint32_t w_cptr = (uint32_t)w_box;
                uint32_t null_cptr = (uint32_t)c->x[22];
                memcpy((void *)(w_box + 7), &neg8, sizeof neg8);
                memcpy((void *)(pos0 + 0x13), &w_cptr, sizeof w_cptr);
                memcpy((void *)(pos0 + 0x1b), &w_cptr, sizeof w_cptr);
                memcpy((void *)(sized0 + 0xf), &null_cptr, sizeof null_cptr);
                static unsigned tb_logs;
                if (__atomic_fetch_add(&tb_logs, 1u, __ATOMIC_RELAXED) < 3) {
                    ld_blog("ws_taskbar_overhang: orig_w=%.1f left=right=-8.0", w_val);
                }
            }
        }
    }
    if (!isfinite(off) || off == 0.0) return;
    uintptr_t w1 = pos1 ? ld_heap_pointer(&dc, pos1, 0xb) : 0;
    uintptr_t pad = w1 ? ld_heap_pointer(&dc, w1, 0xb) : 0;
    if (!pad) return;
    uintptr_t insets = 0;
    for (size_t off_p = 0xb; off_p <= 0x13; off_p += 4) {
        uintptr_t cand = ld_heap_pointer(&dc, pad, off_p);
        double l = 1, t = 1, r = 1, b = -1;
        if (cand &&
            ld_read_heap(cand + 7, &l, 8) && ld_read_heap(cand + 0xf, &t, 8) &&
            ld_read_heap(cand + 0x17, &r, 8) && ld_read_heap(cand + 0x1f, &b, 8) &&
            l == 0.0 && t == 0.0 && r == 0.0 && isfinite(b) && b >= -200.0 && b < 400.0) {
            insets = cand;
            break;
        }
    }
    if (!insets || ld_ws_is_const_ro(insets)) return;
    if (off > 0.0) {
        double top = 0.0;
        if (ld_read_heap(insets + 0xf, &top, 8) && isfinite(top)) {
            top += off;
            memcpy((void *)(insets + 0xf), &top, 8);
        }
    } else {
        double bot = 0.0;
        if (ld_read_heap(insets + 0x1f, &bot, 8) && isfinite(bot)) {
            bot += (-off);
            if (bot < 0.0) bot = 0.0;
            memcpy((void *)(insets + 0x1f), &bot, 8);
        }
    }
    static unsigned logs;
    if (__atomic_fetch_add(&logs, 1u, __ATOMIC_RELAXED) < 3) {
        ld_blog("ws_dock_icon_pad: dir=%d off=%.1f insets=%p", dir, off, (void *)insets);
    }
}

__attribute__((used)) static void ld_ws_on_bg_strategy(struct ld_dart_context *c)
{
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    if (!g_home_options.ws.dock_custom || !g_home_options.ws.taskbar_mode) return;
    double zero = 0.0;
    memcpy(c->q[0], &zero, sizeof zero);
}

__attribute__((used)) static void ld_ws_on_bg_sync(struct ld_dart_context *c)
{
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    if (!g_home_options.ws.dock_custom) return;
    uintptr_t params = c->x[2];
    if (!(params & 1)) return;
    uintptr_t size = ld_heap_pointer(c, params, 7);
    double p_b = 0.0, sw = 0.0, sh = 0.0;
    ld_read_heap(params + 0xb, &p_b, sizeof p_b);
    if (size) {
        ld_read_heap(size + 7, &sw, sizeof sw);
        ld_read_heap(size + 0xf, &sh, sizeof sh);
    }
    static unsigned diag_logs;
    if (__atomic_fetch_add(&diag_logs, 1u, __ATOMIC_RELAXED) < 5) {
        ld_blog("ws_bg_sync_diag: taskbar=%d p_b=%.1f size=(%.1f,%.1f)",
                g_home_options.ws.taskbar_mode, p_b, sw, sh);
    }
    if (!g_home_options.ws.taskbar_mode) return;
    double dw = ld_ws_device_width();
    double zero = 0.0;
    if (size && dw > 100.0 && !ld_ws_is_const_ro(size)) {
        double taskbar_w = dw + 16.0;
        memcpy((void *)(size + 7), &taskbar_w, sizeof taskbar_w);
    }
    if (!ld_ws_is_const_ro(params)) {
        memcpy((void *)(params + 0xb), &zero, sizeof zero);
    }
}

__attribute__((used)) static void ld_ws_post_build_dock_layout(struct ld_ws_ctx *c)
{
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    uintptr_t layout = (uintptr_t)c->ret_x0;
    if (!(layout & 1)) return;
    double dw = 0.0;
    if (ld_read_heap(layout + 7, &dw, sizeof dw) && isfinite(dw) && dw > 100.0 && dw < 8192.0) {
        uint64_t bits = 0;
        memcpy(&bits, &dw, sizeof bits);
        __atomic_store_n(&g_ws_device_width_bits, bits, __ATOMIC_RELAXED);
    }
}

LD_WS_PRE_POST_OBSERVER(repl_ws_dock_build_content, ld_ws_on_dock_build_content, ld_ws_post_dock_build_content, g_orig_ws_device_width)
LD_DART_OBSERVER(repl_ws_bg_strategy,        ld_ws_on_bg_strategy,        g_orig_ws_bg_strategy)
LD_DART_OBSERVER(repl_ws_bg_sync,            ld_ws_on_bg_sync,            g_orig_ws_bg_sync)

LD_WS_POST_OBSERVER(repl_ws_is_laptop,        ld_ws_post_is_laptop,        g_orig_ws_is_laptop)
LD_WS_POST_OBSERVER(repl_ws_margin_bottom,    ld_ws_post_margin_bottom,    g_orig_ws_margin_bottom)
LD_WS_POST_OBSERVER(repl_ws_dock_icon_top,    ld_ws_post_dock_icon_top,    g_orig_ws_dock_icon_top)
LD_WS_POST_OBSERVER(repl_ws_apps_padding,     ld_ws_post_apps_padding,     g_orig_ws_apps_padding)
LD_WS_POST_OBSERVER(repl_ws_indicator_pos,    ld_ws_post_indicator_pos,    g_orig_ws_indicator_pos)
__attribute__((used)) static void ld_ws_post_allapps_ind(struct ld_ws_ctx *c)
{
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    if (!g_home_options.ws.dock_custom) return;
    int dir = ld_ws_direction(c);
    if (dir < 0) return;
    double ind_y = dir == 1 ? g_home_options.ws.apps_land_ind_y : g_home_options.ws.apps_port_ind_y;
    if (!isfinite(ind_y) || ind_y == 0.0) return;
    uintptr_t pos = (uintptr_t)c->ret_x0;
    if (!(pos & 1)) return;
    struct ld_dart_context dc;
    memcpy(dc.x, c->x, sizeof(dc.x));
    uintptr_t bot_box = ld_heap_pointer(&dc, pos, 0x1b);
    uintptr_t top_box = ld_heap_pointer(&dc, pos, 0x13);
    double val = 0.0;
    if (bot_box && !ld_ws_is_const_ro(bot_box) && ld_read_heap(bot_box + 7, &val, 8) && isfinite(val)) {
        double next = val - ind_y;
        static unsigned logs;
        if (__atomic_fetch_add(&logs, 1u, __ATOMIC_RELAXED) < 3) {
            ld_blog("ws_allapps_ind: dir=%d bot=%.1f->%.1f ind_y=%.1f", dir, val, next, ind_y);
        }
        memcpy((void *)(bot_box + 7), &next, 8);
    } else if (top_box && !ld_ws_is_const_ro(top_box) && ld_read_heap(top_box + 7, &val, 8) && isfinite(val)) {
        double next = val + ind_y;
        static unsigned logs;
        if (__atomic_fetch_add(&logs, 1u, __ATOMIC_RELAXED) < 3) {
            ld_blog("ws_allapps_ind: dir=%d top=%.1f->%.1f ind_y=%.1f", dir, val, next, ind_y);
        }
        memcpy((void *)(top_box + 7), &next, 8);
    }
}

__attribute__((used)) static void ld_ws_post_hotseat_bottom(struct ld_ws_ctx *c)
{
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    double v_off = ld_ws_effective_dock_v_offset();
    if (!g_home_options.ws.dock_custom || v_off == 0.0) return;
    double cur = 0.0;
    memcpy(&cur, &c->ret_d0, sizeof cur);
    if (!isfinite(cur)) return;
    double next = g_home_options.ws.taskbar_mode
            ? -v_off
            : (cur - v_off);
    static unsigned logs;
    if (__atomic_fetch_add(&logs, 1u, __ATOMIC_RELAXED) < 5) {
        ld_blog("ws_hsc_mb: orig=%.1f next=%.1f taskbar=%d v_off=%.1f",
                cur, next, g_home_options.ws.taskbar_mode, v_off);
    }
    memcpy(&c->ret_d0, &next, sizeof next);
}

LD_WS_POST_OBSERVER(repl_ws_build_dock_layout, ld_ws_post_build_dock_layout, g_orig_ws_build_dock_layout)
LD_WS_POST_OBSERVER(repl_ws_allapps_ind,       ld_ws_post_allapps_ind,       g_orig_ws_allapps_ind)
__attribute__((used)) static void ld_ws_post_cal_offset(struct ld_ws_ctx *c)
{
    if (!__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return;
    double v_off = ld_ws_effective_dock_v_offset();
    if (!g_home_options.ws.dock_custom || v_off == 0.0) return;
    double cur = 0.0;
    memcpy(&cur, &c->ret_d0, sizeof cur);
    if (!isfinite(cur)) return;
    double next = cur + v_off;
    static unsigned logs;
    if (__atomic_fetch_add(&logs, 1u, __ATOMIC_RELAXED) < 5) {
        ld_blog("ws_cal_offset: orig=%.1f next=%.1f taskbar=%d v_off=%.1f",
                cur, next, g_home_options.ws.taskbar_mode, v_off);
    }
    memcpy(&c->ret_d0, &next, sizeof next);
}

LD_WS_POST_OBSERVER(repl_ws_hotseat_bottom,    ld_ws_post_hotseat_bottom,    g_orig_ws_hotseat_bottom)
LD_WS_POST_OBSERVER(repl_ws_cal_offset,        ld_ws_post_cal_offset,        g_orig_ws_cal_offset)

static void ld_workstation_install(const struct native_api *api, ld_lib_info *lib)
{
    static int installed;
    if (installed || !api || !api->hook || !lib) return;
    if (g_home_options.workstation_targets_present < 6) return;

    struct {
        size_t idx;
        void *repl;
        void **orig;
        const char *label;
        int check_safe;
    } specs[9] = {
        { 0, (void *)repl_ws_is_laptop,          &g_orig_ws_is_laptop,     "ws_is_laptop",          1 },
        { 1, (void *)repl_ws_margin_bottom,      &g_orig_ws_margin_bottom, "ws_margin_bottom",      1 },
        { 2, (void *)repl_ws_dock_icon_top,      &g_orig_ws_dock_icon_top, "ws_dock_icon_top",      1 },
        { 3, (void *)repl_ws_apps_padding,       &g_orig_ws_apps_padding,  "ws_apps_padding",       1 },
        { 4, (void *)repl_ws_indicator_pos,      &g_orig_ws_indicator_pos, "ws_indicator_pos",      1 },
        { 5, (void *)repl_ws_dock_build_content, &g_orig_ws_device_width,  "ws_dock_build_content", 1 },
        { 6, (void *)repl_ws_bg_strategy,        &g_orig_ws_bg_strategy,   "ws_bg_strategy",        1 },
        { 7, (void *)repl_ws_bg_sync,            &g_orig_ws_bg_sync,       "ws_bg_sync",            1 },
        { 8, (void *)repl_ws_build_dock_layout,  &g_orig_ws_build_dock_layout, "ws_build_dock_layout", 1 },
    };

    size_t count = (size_t)g_home_options.workstation_targets_present;
    if (count > 9) count = 9;
    int ok_count = 0;
    for (size_t i = 0; i < count; i++) {
        uintptr_t fn = 0;
        size_t sz = g_home_options.workstation_size[specs[i].idx];
        if (!ld_vaddr_to_runtime(lib, g_home_options.workstation_vaddr[specs[i].idx], &fn)) continue;
        if (sz < 24 || !executable_range(fn, sz)) continue;
        if (specs[i].check_safe && !ld_hook_site_safe(fn, specs[i].label)) continue;
        if (api->hook((void *)fn, specs[i].repl, specs[i].orig) == 0) {
            ok_count++;
        }
    }
    if (g_home_options.ws_allapps_ind_vaddr) {
        uintptr_t ind_fn = 0;
        uint64_t target_vaddr = g_home_options.ws_allapps_ind_vaddr + g_home_options.ws_allapps_ind_size;
        if (ld_vaddr_to_runtime(lib, target_vaddr, &ind_fn) &&
            executable_range(ind_fn, 64) && ld_hook_site_safe(ind_fn, "ws_allapps_ind")) {
            if (api->hook((void *)ind_fn, (void *)repl_ws_allapps_ind, &g_orig_ws_allapps_ind) == 0) {
                ok_count++;
            }
        }
    }
    if (g_home_options.ws_hotseat_bottom_vaddr) {
        uintptr_t hb_fn = 0;
        if (ld_vaddr_to_runtime(lib, g_home_options.ws_hotseat_bottom_vaddr, &hb_fn) &&
            executable_range(hb_fn, 64)) {
            if (api->hook((void *)hb_fn, (void *)repl_ws_hotseat_bottom, &g_orig_ws_hotseat_bottom) == 0) {
                ok_count++;
            }
        }
    }
    if (g_home_options.ws_cal_offset_vaddr) {
        uintptr_t co_fn = 0;
        if (ld_vaddr_to_runtime(lib, g_home_options.ws_cal_offset_vaddr, &co_fn) &&
            executable_range(co_fn, 64) && ld_hook_site_safe(co_fn, "ws_cal_offset")) {
            if (api->hook((void *)co_fn, (void *)repl_ws_cal_offset, &g_orig_ws_cal_offset) == 0) {
                ok_count++;
            }
        }
    }
    installed = 1;
    ld_blog("workstation hooks installed=%d/%zu", ok_count, count);
}

#endif /* LD_WORKSTATION_H */
