/* OS4 工作区边距：正确写入点（后置写）。
 *
 * GridSizeCalRules.correctWorkspaceCellSide @0xa757b4 的结构（capstone 反汇编）：
 *   d0 = [x1 + 0x47]          ; cell 宽
 *   bl alignPixel
 *   d2 = d0 * (double)x2      ; 网格总宽
 *   ... width/2 ...
 *   d1 = (width2 - d2) / 2
 *   stur d1, [x1 + 0x1f]      ; ← 侧边距就写在这里
 *   ret TRUE
 *
 * 因此用「后置写」hook：先按原参数调用原函数，再用入口保存的 x1（rules 对象）
 * 覆写 +0x1f（side）与 +0x57（cell bottom）。Dart SP 是 x15，必须原样保存/恢复，
 * 且调用原函数前不能改动 x0..x7。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-margins
 * 配置：同目录 liquiddock-margins.conf（side=<px> bottom=<px>）
 */
#ifndef LD_LAYOUT_MARGINS_WRITE_H
#define LD_LAYOUT_MARGINS_WRITE_H

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"

#define LD_LMW_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-margins"
#define LD_LMW_CONF "/data/user_de/0/com.miui.home/files/liquiddock-margins.conf"
#define LD_LMW_VADDR 0xa757b4u

static double g_lmw_side __attribute__((used)), g_lmw_bottom __attribute__((used));
static int g_lmw_have_side __attribute__((used)), g_lmw_have_bottom __attribute__((used));
static void *g_lmw_orig __attribute__((used));
static volatile unsigned g_lmw_calls __attribute__((used));

static void ld_lmw_load_conf(void)
{
    FILE *f = fopen(LD_LMW_CONF, "r");
    if (!f) return;
    char line[128];
    while (fgets(line, sizeof line, f)) {
        double v = 0;
        if (sscanf(line, "side=%lf", &v) == 1) { g_lmw_side = v; g_lmw_have_side = 1; }
        else if (sscanf(line, "bottom=%lf", &v) == 1) { g_lmw_bottom = v; g_lmw_have_bottom = 1; }
    }
    fclose(f);
    ld_blog("margins-write: side=%.1f bottom=%.1f", g_lmw_side, g_lmw_bottom);
}

/* 后置写：静态保存区（Dart 入口处硬件 sp 紧邻 guard page，不能压栈）。
 * 保存 x0..x8/x15/x19/x28/x30 与 q0..q7，调原函数后由 C 回调改写 rules 字段。 */
static unsigned char g_lmw_save[0x100] __attribute__((aligned(16), used));

__attribute__((naked, used)) static void ld_lmw_hook(void)
{
    __asm__ volatile(
        "adrp x16, g_lmw_save\n"
        "add  x16, x16, :lo12:g_lmw_save\n"
        "stp x0, x1, [x16, #0]\n"
        "stp x2, x3, [x16, #16]\n"
        "stp x4, x5, [x16, #32]\n"
        "stp x6, x7, [x16, #48]\n"
        "str x8, [x16, #64]\n"
        "str x15, [x16, #72]\n"
        "str x30, [x16, #80]\n"
        "str x28, [x16, #248]\n"
        "stp q0, q1, [x16, #112]\n"
        "stp q2, q3, [x16, #144]\n"
        "ldp x0, x1, [x16, #0]\n"
        "ldp x2, x3, [x16, #16]\n"
        "ldp x4, x5, [x16, #32]\n"
        "ldp x6, x7, [x16, #48]\n"
        "ldr x8, [x16, #64]\n"
        "ldr x15, [x16, #72]\n"
        "adrp x17, g_lmw_orig\n"
        "ldr x17, [x17, :lo12:g_lmw_orig]\n"
        "cbz x17, 9f\n"
        "blr x17\n"
        /* 回调：改写 rules 对象字段（x1 在保存区 +8） */
        "adrp x16, g_lmw_save\n"
        "add  x16, x16, :lo12:g_lmw_save\n"
        "str x0, [x16, #88]\n"
        "str x19, [x16, #96]\n"
        "mov x19, sp\n"
        "sub x17, x15, #1024\n"
        "and x17, x17, #0xfffffffffffffff0\n"
        "mov sp, x17\n"
        "mov x0, x16\n"
        "bl ld_lmw_post\n"
        "mov sp, x19\n"
        "adrp x16, g_lmw_save\n"
        "add  x16, x16, :lo12:g_lmw_save\n"
        "ldr x19, [x16, #96]\n"
        "ldr x0, [x16, #88]\n"
        "9:\n"
        "adrp x16, g_lmw_save\n"
        "add  x16, x16, :lo12:g_lmw_save\n"
        "ldr x28, [x16, #248]\n"
        "ldr x15, [x16, #72]\n"
        "ldr x30, [x16, #80]\n"
        "ret\n");
}

__attribute__((used)) static void ld_lmw_post(unsigned char *save, uintptr_t result)
{
    (void)result;
    uintptr_t rules = 0;
    __builtin_memcpy(&rules, save + 8, sizeof rules);
    if (!rules) return;
    unsigned n = __atomic_fetch_add(&g_lmw_calls, 1u, __ATOMIC_RELAXED);
    if (n < 3u) ld_blog("margins-write: post #%u rules=%p", n + 1u, (void *)rules);
    if (g_lmw_have_side) {
        double v = g_lmw_side;
        __builtin_memcpy((void *)(rules + 0x1f), &v, sizeof v);
    }
    if (g_lmw_have_bottom) {
        double v = g_lmw_bottom;
        __builtin_memcpy((void *)(rules + 0x57), &v, sizeof v);
    }
}

static void ld_layout_margins_write_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_LMW_MARKER, F_OK) != 0) { state = 2; return; }
    ld_lmw_load_conf();
    if (!g_lmw_have_side && !g_lmw_have_bottom) { state = 2; return; }

    uintptr_t fn = 0;
    if (!ld_vaddr_to_runtime(lib, LD_LMW_VADDR, &fn)) return;
    /* stp x29,x30,[x15,#-0x10]! / mov x29,x15 / sub x15,x15,#0x28 */
    static const uint32_t prologue[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd100a1efu};
    if (memcmp((const void *)fn, prologue, sizeof prologue) != 0) {
        state = 2;
        ld_blog("margins-write: prologue mismatch at %p", (void *)fn);
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)fn, (void *)ld_lmw_hook, &backup) == 0 && backup) {
        g_lmw_orig = backup;
        state = 1;
        ld_blog("margins-write: installed at %p orig=%p", (void *)fn, backup);
    } else {
        state = 2;
        ld_blog("margins-write: hook failed");
    }
}

#endif
