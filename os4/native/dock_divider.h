/* OS4 Dock 分隔符：宽度/开关。
 *
 * HotseatLayerGetxControllerPadNew.getItemDividerWidth @0xa8f23c 返回分隔符宽度：
 *   stp x29,x30,[x15,#-0x10]! / mov x29,x15 / sub x15,x15,#0x10
 * 用最小叶函数直接返回模块配置的宽度（逻辑 px = dp），不走原函数，不触碰 x15。
 * 关闭（或宽度 0）时分隔符宽度为 0，视觉上隐藏。
 *
 * 配置键（liquiddock-native.conf，由 GUI 的 dock_divider_* 发布）：
 *   dock_divider_enabled=1
 *   dock_divider_width=<dp>
 * 门控：同目录 marker liquiddock-divider
 */
#ifndef LD_DOCK_DIVIDER_H
#define LD_DOCK_DIVIDER_H

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"
#include "dart_observer.h"
#include "dart_heap.h"

#define LD_DIVIDER_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-divider"
#define LD_DIVIDER_CONF "/data/user/0/com.miui.home/files/liquiddock-native.conf"
#define LD_DIVIDER_VADDR 0xa8f23cu
#define LD_DIVIDER_RATIO_VADDR 0xa8f2a8u
#define LD_DIVIDER_HEIGHT_VADDR 0xa8e768u
#define LD_DIVIDER_PAINT_VADDR 0xe0eff0u
#define LD_DIVIDER_STRETCH_PAINT_VADDR 0xe01a3cu

static double g_divider_width __attribute__((used)) = -1.0;
static double g_divider_ratio __attribute__((used));
static double g_divider_height __attribute__((used)) = 40.0;
static int g_divider_enabled __attribute__((used));
static int g_divider_spacing __attribute__((used));
static int g_divider_configured __attribute__((used));

/* 叶函数：d0 = 配置宽度；不调用任何 C 代码，x15 原样保留。 */
__attribute__((naked, used)) static void ld_divider_width_hook(void)
{
    __asm__ volatile(
        "adrp x16, g_divider_width\n"
        "add x16, x16, :lo12:g_divider_width\n"
        "ldr d0, [x16]\n"
        "ret\n");
}

__attribute__((naked, used)) static void ld_divider_ratio_hook(void)
{
    __asm__ volatile(
        "adrp x16, g_divider_ratio\n"
        "add x16, x16, :lo12:g_divider_ratio\n"
        "ldr d0, [x16]\n"
        "ret\n");
}

__attribute__((naked, used)) static void ld_divider_height_hook(void)
{
    __asm__ volatile(
        "adrp x16, g_divider_height\n"
        "add x16, x16, :lo12:g_divider_height\n"
        "ldr d0, [x16]\n"
        "ret\n");
}

static void *g_orig_divider_paint __attribute__((used));

/* paint(this=x1, canvas=x2, size=x3, double d0) —— d0 是分隔符的绘制尺度。
 * 在入口把 d0 改成配置值，再走原函数。 */
__attribute__((used)) static void ld_observe_divider_paint(struct ld_dart_context *c)
{
    if (!g_divider_enabled) return;
    __builtin_memcpy(c->q[0], &g_divider_height, sizeof(double));
}

LD_DART_OBSERVER(repl_divider_paint, ld_observe_divider_paint, g_orig_divider_paint)

static void *g_orig_stretch_paint __attribute__((used));

/* _StretchDrawableDividerPainter.paint(this=x1, canvas=x2, rect=x3)：
 * rect 是 Dart Rect（left/top/right/bottom 在 +7/+0xf/+0x17/+0x1f），
 * 直接改 top/bottom 即可控制竖线长度。 */
__attribute__((used)) static void ld_observe_stretch_paint(struct ld_dart_context *c)
{
    if (!g_divider_enabled) return;
    uintptr_t rect = c->x[3];
    if (!rect) return;
    double top = 0, bottom = 0;
    if (!ld_read_heap(rect + 0xf, &top, 8) || !ld_read_heap(rect + 0x1f, &bottom, 8))
        return;
    double center = (top + bottom) * 0.5;
    double half = g_divider_height * 0.5;
    double nt = center - half, nb = center + half;
    __builtin_memcpy((void *)(rect + 0xf), &nt, 8);
    __builtin_memcpy((void *)(rect + 0x1f), &nb, 8);
}

LD_DART_OBSERVER(repl_stretch_paint, ld_observe_stretch_paint, g_orig_stretch_paint)

static void ld_divider_load_conf(void)
{
    FILE *f = fopen(LD_DIVIDER_CONF, "r");
    if (!f) return;
    char line[160];
    double width = -1.0;
    int enabled = 0;
    int height_scale = -1;
    int spacing_opt_in = 0;
    while (fgets(line, sizeof line, f)) {
        int v = 0;
        if (sscanf(line, "dock_divider_enabled=%d", &v) == 1) { enabled = v != 0; g_divider_configured = 1; }
        else if (sscanf(line, "dock_divider_width=%d", &v) == 1) width = (double)v;
        else if (sscanf(line, "dock_divider_height_scale=%d", &v) == 1) height_scale = v;
        else if (sscanf(line, "dock_divider_spacing=%d", &v) == 1) spacing_opt_in = v != 0;
    }
    fclose(f);
    if (!g_divider_configured) return;          /* 未配置：保持原样 */
    g_divider_enabled = enabled;
    g_divider_spacing = spacing_opt_in;
    g_divider_width = enabled ? width : 0.0;    /* 关闭 = 宽度 0 */
    double ratio = enabled ? width / 80.0 : 0.0; /* 以 80dp 单元宽估算占比 */
    if (ratio > 1.0) ratio = 1.0;
    if (ratio < 0.0) ratio = 0.0;
    g_divider_ratio = ratio;
    if (height_scale >= 0) g_divider_height = (double)height_scale; /* 0..100 -> 0..100px */
    ld_blog("divider: enabled=%d width=%.1f ratio=%.3f", enabled, width, ratio);
}

static void ld_dock_divider_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_DIVIDER_MARKER, F_OK) != 0) { state = 2; return; }
    ld_divider_load_conf();
    if (!g_divider_configured) { state = 2; return; }

    uintptr_t fn = 0;
    if (!ld_vaddr_to_runtime(lib, LD_DIVIDER_VADDR, &fn)) return;
    /* stp x29,x30,[x15,#-0x10]! / mov x29,x15 / sub x15,x15,#0x10 */
    static const uint32_t prologue[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd10041efu};
    if (memcmp((const void *)fn, prologue, sizeof prologue) != 0) {
        state = 2;
        ld_blog("divider: prologue mismatch at %p", (void *)fn);
        return;
    }
    void *backup = NULL;
    if (!g_divider_spacing) {
        state = 1;                     /* 默认不动宽度/间距，只做外观（paint） */
    } else if (api->hook((void *)fn, (void *)ld_divider_width_hook, &backup) == 0) {
        state = 1;
        ld_blog("divider: width hooked at %p = %.1f (enabled=%d)",
                (void *)fn, g_divider_width, g_divider_enabled);
    } else {
        state = 2;
        ld_blog("divider: hook failed");
        return;
    }
    uintptr_t fn2 = 0;
    if (g_divider_spacing && ld_vaddr_to_runtime(lib, LD_DIVIDER_RATIO_VADDR, &fn2) &&
        memcmp((const void *)fn2, prologue, sizeof prologue) == 0) {
        void *b2 = NULL;
        if (api->hook((void *)fn2, (void *)ld_divider_ratio_hook, &b2) == 0)
            ld_blog("divider: ratio hooked at %p = %.3f", (void *)fn2, g_divider_ratio);
    }
    uintptr_t fn3 = 0;
    if (ld_vaddr_to_runtime(lib, LD_DIVIDER_HEIGHT_VADDR, &fn3) &&
        memcmp((const void *)fn3, prologue, 8) == 0) {
        void *b3 = NULL;
        if (api->hook((void *)fn3, (void *)ld_divider_height_hook, &b3) == 0)
            ld_blog("divider: height hooked at %p = %.1f", (void *)fn3, g_divider_height);
    }
    uintptr_t fn4 = 0;
    static const uint32_t paint_pro[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd100c1efu};
    uintptr_t fn5 = 0;
    if (ld_vaddr_to_runtime(lib, LD_DIVIDER_STRETCH_PAINT_VADDR, &fn5) &&
        memcmp((const void *)fn5, paint_pro, sizeof paint_pro) == 0) {
        void *b5 = NULL;
        if (api->hook((void *)fn5, (void *)repl_stretch_paint, &b5) == 0) {
            g_orig_stretch_paint = b5;
            ld_blog("divider: stretch paint hooked at %p height=%.1f", (void *)fn5, g_divider_height);
        }
    }
    if (ld_vaddr_to_runtime(lib, LD_DIVIDER_PAINT_VADDR, &fn4) &&
        memcmp((const void *)fn4, paint_pro, sizeof paint_pro) == 0) {
        void *b4 = NULL;
        if (api->hook((void *)fn4, (void *)repl_divider_paint, &b4) == 0) {
            g_orig_divider_paint = b4;
            ld_blog("divider: paint hooked at %p", (void *)fn4);
        }
    }
}

#endif
