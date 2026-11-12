/* OS4 布局边距（工作区网格 / hotseat / 屏幕底）—— getter 覆盖实现。
 *
 * 依据 /mnt/extra/outputs/os4_dock_layout_margins.md：
 *   工作区侧边距   GridConfig.workspaceCellSide            @0xa6efa8  返回 d0
 *   单元格底部     GridConfig.workspaceCellPaddingBottom   @0x15048d8 返回 d0
 *   hotseat 底边距 GridConfig.getHotseatMarginBottom       @0xedb4a4  返回 d0
 *   屏幕下边距     GridConfig.getScreenMarginBottom        @0xa76b90  返回 d0
 *
 * 这些 getter 都是「读堆字段 + alignPixel」的纯函数；直接把入口替换为
 * 「返回配置值」的叶子函数即可，不改堆、不触发重算。
 * 顶部内边距走 Rx 对象（返回的不是 double），本版不覆盖。
 *
 * 配置：/data/user_de/0/com.miui.home/files/liquiddock-margins
 *   side=<px> bottom=<px> hotseat=<px> screen=<px>
 * 门控：同目录 marker liquiddock-margins
 */
#ifndef LD_LAYOUT_MARGINS_H
#define LD_LAYOUT_MARGINS_H

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"

#define LD_MARGINS_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-margins"
#define LD_MARGINS_CONF "/data/user_de/0/com.miui.home/files/liquiddock-margins.conf"

#define LD_MARGIN_SIDE_VADDR 0xa6efa8u
#define LD_MARGIN_BOTTOM_VADDR 0x15048d8u
#define LD_MARGIN_HOTSEAT_VADDR 0xedb4a4u
#define LD_MARGIN_SCREEN_VADDR 0xa76b90u
#define LD_MARGIN_CTRL_SIDE_VADDR 0x93334cu
#define LD_MARGIN_CTRL_BOTTOM_VADDR 0x934334u

static double g_margin_side;
static double g_margin_bottom;
static double g_margin_hotseat;
static double g_margin_screen;
static int g_margin_have_side, g_margin_have_bottom, g_margin_have_hotseat, g_margin_have_screen;

static volatile unsigned g_margin_calls[4];
__attribute__((used)) static double ld_margin_side_value(void)
{
    unsigned n = __atomic_add_fetch(&g_margin_calls[0], 1u, __ATOMIC_RELAXED);
    if (n == 1u || (n % 300u) == 0u) ld_blog("margins: side getter calls=%u", n);
    return g_margin_side;
}
__attribute__((used)) static double ld_margin_bottom_value(void)
{
    unsigned n = __atomic_add_fetch(&g_margin_calls[1], 1u, __ATOMIC_RELAXED);
    if (n == 1u || (n % 300u) == 0u) ld_blog("margins: bottom getter calls=%u", n);
    return g_margin_bottom;
}
__attribute__((used)) static double ld_margin_hotseat_value(void)
{
    unsigned n = __atomic_add_fetch(&g_margin_calls[2], 1u, __ATOMIC_RELAXED);
    if (n == 1u || (n % 300u) == 0u) ld_blog("margins: hotseat getter calls=%u", n);
    return g_margin_hotseat;
}
__attribute__((used)) static double ld_margin_screen_value(void)
{
    unsigned n = __atomic_add_fetch(&g_margin_calls[3], 1u, __ATOMIC_RELAXED);
    if (n == 1u || (n % 300u) == 0u) ld_blog("margins: screen getter calls=%u", n);
    return g_margin_screen;
}

__attribute__((naked, used)) static void ld_margin_side_hook(void)
{
    __asm__ volatile("b ld_margin_side_value\n");
}
__attribute__((naked, used)) static void ld_margin_bottom_hook(void)
{
    __asm__ volatile("b ld_margin_bottom_value\n");
}
__attribute__((naked, used)) static void ld_margin_hotseat_hook(void)
{
    __asm__ volatile("b ld_margin_hotseat_value\n");
}
__attribute__((naked, used)) static void ld_margin_screen_hook(void)
{
    __asm__ volatile("b ld_margin_screen_value\n");
}

static void ld_margins_load_conf(void)
{
    FILE *f = fopen(LD_MARGINS_CONF, "r");
    if (!f) return;
    char line[128];
    while (fgets(line, sizeof line, f)) {
        double v = 0;
        if (sscanf(line, "side=%lf", &v) == 1) { g_margin_side = v; g_margin_have_side = 1; }
        else if (sscanf(line, "bottom=%lf", &v) == 1) { g_margin_bottom = v; g_margin_have_bottom = 1; }
        else if (sscanf(line, "hotseat=%lf", &v) == 1) { g_margin_hotseat = v; g_margin_have_hotseat = 1; }
        else if (sscanf(line, "screen=%lf", &v) == 1) { g_margin_screen = v; g_margin_have_screen = 1; }
    }
    fclose(f);
    ld_blog("margins: side=%.1f bottom=%.1f hotseat=%.1f screen=%.1f",
            g_margin_side, g_margin_bottom, g_margin_hotseat, g_margin_screen);
}

static void ld_layout_margins_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_MARGINS_MARKER, F_OK) != 0) { state = 2; return; }
    ld_margins_load_conf();

    static const uint32_t pro_grid[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd10021efu};
    static const uint32_t pro_ctrl[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xaa0103e0u};
    struct { uintptr_t vaddr; void *replace; double *value; int *have; const char *name;
             const uint32_t *prologue; } hooks[] = {
        {LD_MARGIN_SIDE_VADDR, (void *)ld_margin_side_hook, &g_margin_side, &g_margin_have_side, "side", pro_grid},
        {LD_MARGIN_BOTTOM_VADDR, (void *)ld_margin_bottom_hook, &g_margin_bottom, &g_margin_have_bottom, "bottom", pro_grid},
        {LD_MARGIN_HOTSEAT_VADDR, (void *)ld_margin_hotseat_hook, &g_margin_hotseat, &g_margin_have_hotseat, "hotseat", pro_grid},
        {LD_MARGIN_SCREEN_VADDR, (void *)ld_margin_screen_hook, &g_margin_screen, &g_margin_have_screen, "screen", pro_grid},
        {LD_MARGIN_CTRL_SIDE_VADDR, (void *)ld_margin_side_hook, &g_margin_side, &g_margin_have_side, "ctrl_side", pro_ctrl},
        {LD_MARGIN_CTRL_BOTTOM_VADDR, (void *)ld_margin_bottom_hook, &g_margin_bottom, &g_margin_have_bottom, "ctrl_bottom", pro_ctrl},
    };
    for (size_t i = 0; i < sizeof hooks / sizeof *hooks; ++i) {
        if (!*hooks[i].have) continue;
        uintptr_t fn = 0;
        if (!ld_vaddr_to_runtime(lib, hooks[i].vaddr, &fn)) continue;
        if (memcmp((const void *)fn, hooks[i].prologue, 12) != 0) {
            ld_blog("margins: %s prologue mismatch at %p", hooks[i].name, (void *)fn);
            continue;
        }
        void *backup = NULL;
        if (api->hook((void *)fn, hooks[i].replace, &backup) == 0)
            ld_blog("margins: %s hooked at %p = %.1f", hooks[i].name, (void *)fn, *hooks[i].value);
        else
            ld_blog("margins: %s hook failed", hooks[i].name);
    }
    state = 1;
}

#endif
