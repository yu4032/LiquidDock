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

#define LD_WIDGET_RESIZE_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-widget-resize"
#define LD_WIDGET_RESIZE_VADDR 0x19dc714u
/* ResizeFrameController 的可调方向谓词：1×1 时平板路径会拒绝，导致手柄拖不动。 */
#define LD_WIDGET_CAN_RESIZE_V_VADDR 0x11d0c5cu
#define LD_WIDGET_CAN_RESIZE_H_VADDR 0x11d0cc4u

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
    if (access(LD_WIDGET_RESIZE_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t fn = 0;
    if (!ld_vaddr_to_runtime(lib, LD_WIDGET_RESIZE_VADDR, &fn)) return;
    static const uint32_t prologue[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd10241efu};
    if (memcmp((const void *)fn, prologue, sizeof prologue) != 0) {
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

    /* Corner handles: force the two axis predicates to TRUE so a 1x1 widget can
     * grow. Same Dart TRUE singleton, same leaf ABI. */
    static const uint32_t pro_v[3] = {0xb8423022u, 0x8b1c8042u, 0xf9402370u};
    uintptr_t fv = 0, fh = 0;
    if (ld_vaddr_to_runtime(lib, LD_WIDGET_CAN_RESIZE_V_VADDR, &fv) &&
        memcmp((const void *)fv, pro_v, sizeof pro_v) == 0) {
        void *b = NULL;
        if (api->hook((void *)fv, (void *)ld_widget_can_resize_always, &b) == 0)
            ld_blog("widget resize: vertical axis predicate forced at %p", (void *)fv);
    }
    if (ld_vaddr_to_runtime(lib, LD_WIDGET_CAN_RESIZE_H_VADDR, &fh) &&
        memcmp((const void *)fh, pro_v, sizeof pro_v) == 0) {
        void *b = NULL;
        if (api->hook((void *)fh, (void *)ld_widget_can_resize_always, &b) == 0)
            ld_blog("widget resize: horizontal axis predicate forced at %p", (void *)fh);
    }
}

#endif
