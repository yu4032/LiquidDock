/* OS4 平板：1×1 小组件「尺寸调节」（放大到 2×2）启用。
 *
 * 平板禁用点（libapp.so，Launcher 8.01.02.8604）：
 *   WidgetResizeFrameManager.checkIfCanShowResizeFrame   @ 0x19dc714
 *     0x19dc7b4  tbz w1, #4        ; isPad 时跳去 min/max span 检查
 *     0x19dc7b8  add x0, x22, #0x20 ; 手机路径：直接 return TRUE
 *   平板分支才做 min/max span 判断，因此长按菜单的「尺寸调节」项不展示、
 *   拖角 resize frame 也不创建。
 *
 * 处理：把该谓词整体替换为 return TRUE（手机本来就返回 TRUE），
 * 同时解禁菜单项与 resize frame，不触碰 span 计算与占位检查。
 * Dart AOT 布尔单例：TRUE = x22 + 0x20，x22 为 NULL 寄存器。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-widget-resize
 */
#ifndef LD_WIDGET_RESIZE_H
#define LD_WIDGET_RESIZE_H

#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"
#include "executable_range.h"

#define LD_WIDGET_RESIZE_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-widget-resize"

/* Returning the Dart TRUE singleton is a leaf: no stack frame, no saved regs. */
__attribute__((naked, used)) static void ld_widget_can_resize_always(void)
{
    __asm__ volatile(
        "add x0, x22, #0x20\n"
        "ret\n"
    );
}

static void ld_widget_resize_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_WIDGET_RESIZE_MARKER, F_OK) != 0 &&
        !g_home_options.widget_overrides &&
        !(g_home_options.glass_enabled && g_home_options.glass_widgets)) {
        state = 2;
        return;
    }
    uintptr_t fn = 0;
    if (g_home_options.home_opt_targets_present && g_home_options.home_opt_vaddr[13]) {
        uintptr_t cand = 0;
        if (ld_vaddr_to_runtime(lib, g_home_options.home_opt_vaddr[13], &cand) &&
            executable_range(cand, 16) && *(const uint32_t *)cand == 0xa9bf79fdu) {
            fn = cand;
        }
    }
    if (!fn) {
        static const uint32_t resize_pat[16] = {
            0xa9bf79fdu, 0xaa0f03fdu, 0xd10241efu, 0xd2800000u,
            0xf81a03a0u, 0xf9403f41u, 0xf954f021u, 0x6b16003fu,
            0x540001c1u, 0xf9403f40u, 0xf94e8000u, 0xf9402370u,
            0x6b10001fu, 0x54000061u, 0xf97bf362u, 0x9406a80bu,
        };
        fn = ld_resolve_sym_or_aot(
            lib, g_home_options.widget_resize_clear_target_present,
            g_home_options.widget_resize_clear_vaddr, g_home_options.widget_resize_clear_size,
            0x1a30504u, resize_pat, 16, "widget resize");
    }
    if (!fn) return;
    if (*(const uint32_t *)fn != 0xa9bf79fdu) {
        state = 2;
        ld_blog("widget resize: prologue mismatch at %p", (void *)fn);
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)fn, (void *)ld_widget_can_resize_always, &backup) == 0) {
        state = 1;
        ld_blog("widget resize: 1x1 enlarge enabled at %p", (void *)fn);
    } else {
        state = 2;
        ld_blog("widget resize: hook failed");
    }

    /* Corner handles: resolve ResizeFrameController.canResizeVertically/Horizontally
     * dynamically by symbol from home_opt_targets[14..15]. */
    uintptr_t fv = 0, fh = 0;
    if (g_home_options.home_opt_targets_present && g_home_options.home_opt_vaddr[14]) {
        uintptr_t cand = 0;
        if (ld_vaddr_to_runtime(lib, g_home_options.home_opt_vaddr[14], &cand) &&
            executable_range(cand, 16)) {
            fv = cand;
        }
    }
    if (g_home_options.home_opt_targets_present && g_home_options.home_opt_vaddr[15]) {
        uintptr_t cand = 0;
        if (ld_vaddr_to_runtime(lib, g_home_options.home_opt_vaddr[15], &cand) &&
            executable_range(cand, 16)) {
            fh = cand;
        }
    }
    if (fv) {
        void *b = NULL;
        if (api->hook((void *)fv, (void *)ld_widget_can_resize_always, &b) == 0)
            ld_blog("widget resize: vertical axis predicate forced at %p", (void *)fv);
    }
    if (fh) {
        void *b = NULL;
        if (api->hook((void *)fh, (void *)ld_widget_can_resize_always, &b) == 0)
            ld_blog("widget resize: horizontal axis predicate forced at %p", (void *)fh);
    }
}

#endif
