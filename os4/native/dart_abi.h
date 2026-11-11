/* dart_abi.h — HyperOS 4 桌面 (libapp.so Dart AOT) hook 用的 wrapper 原语
 *
 * 反汇编实测并交叉验证（libapp.so：DeviceConfig.isPhone / shouldUsePadLayout /
 * calculateDockOffsetY / calHotseatCenterPosition 等）。规则不可凭感觉改：
 *
 *   x15 = Dart 栈指针（SP）        x27 = 对象池 PP（快照常量）
 *   x26 = **线程寄存器**（TLAB top/limit [x26,#0x60]/#0x68；
 *         globals 表 [x26,#0x78]；runtime stubs [x26,#0x250]/[x26,#0x630]）
 *   x28 = **堆基址**（压缩指针展开用它，见下）   x22 = canonical null 对象
 *   x21 = 类/vtable 分发表          x30 = LR，必须原样回到 Dart 调用方
 *
 *   ⚠️ stub 绝不能破坏 x21/x22/x26/x27/x28/x15/x30。
 *
 *   实例方法的 this 在 **x1**，其余实参 x2,x3,…；x0 在实例方法里是 scratch。
 *   部分 static 的第 1 个实参才在 x0。多余实参在 [x29+0x10]… 的栈窗。
 *
 *   对象返回值 -> x0    double 返回值 -> d0    tagged int -> x0
 *   绝不能用 d0 承接对象返回值。
 *
 *   压缩指针展开：add xN, xN, x28, lsl #32
 *   bool：x22 是 canonical null 对象，x22+0x20 = true，x22+0x30 = false
 *         （故 `mov x0, x22` = return null；`eor xN, xN, #0x10` 恰好 true↔false）
 *   堆对象带 kHeapObjectTag(+1)：字段位移 = 真实偏移 − 1
 *         （`stur d0,[x0,#0x7]` = 真实偏移 8）
 *   Dart int：Smi = value<<1；超 31 位装箱成 Mint，64 位值在真实偏移 8
 *   存进对象字段的 bool：bit4 清零 = true，置位 = false
 *   Dart 函数入口指纹：0xa9bf79fd  (= stp x29, x30, [x15, #-16]!)
 *
 * 红线：
 *   1) wrapper 里不许写栈（除非走 DART_FRAME_* 宏，那用的是 x15 约定）
 *   2) wrapper 里不许调 C 函数（会破坏 x15/x22/x27/x28 语义）
 *   3) 只允许用 x16/x17 做暂存
 *   4) 不许 hook 短 thunk，必须 hook 已校验的函数体
 */
#ifndef LIQUIDDOCK_DART_ABI_H
#define LIQUIDDOCK_DART_ABI_H

#include <stdint.h>

/* Dart 函数入口序言首字，用作「确系 Dart 入口」的校验 */
#define DART_ENTRY_WORD 0xa9bf79fdu

/* ---------- 1. 透传（只为拿 hook 点，不改行为） ----------
 * br 而非 bl：x30 不被改写，原函数 ret 时直接回 Dart 调用方。
 * slot 是 g_orig 数组里的字节偏移。 */
#define DART_PASSTHROUGH(fn, orig_ptr)                       \
    __attribute__((naked)) static void fn(void) {            \
        __asm__ volatile(                                    \
            "adrp x16, " #orig_ptr "\n"                      \
            "add  x16, x16, :lo12:" #orig_ptr "\n"           \
            "ldr  x16, [x16]\n"                              \
            "br   x16\n");                                   \
    }

/* ---------- 1b. 带调用计数的透传 ----------
 * 用途：passthrough 语义上是 no-op，装上了也看不出是否被调用。
 * 只有计数器能区分「hook 在死路径上」和「真的在工作」。
 * 只碰 x9/x10（caller-saved），入参与 d0-d7 不动。 */
#define DART_PASSTHROUGH_CNT(fn, orig_ptr, cnt)              \
    __attribute__((naked)) static void fn(void) {            \
        __asm__ volatile(                                    \
            "adrp x9, " #cnt "\n"                            \
            "add  x9, x9, :lo12:" #cnt "\n"                  \
            "ldr  w10, [x9]\n"                               \
            "add  w10, w10, #1\n"                            \
            "str  w10, [x9]\n"                               \
            "adrp x16, " #orig_ptr "\n"                      \
            "add  x16, x16, :lo12:" #orig_ptr "\n"           \
            "ldr  x16, [x16]\n"                              \
            "br   x16\n");                                   \
    }

/* ---------- 2. 常量 bool ----------
 * x22 由 Dart 调用方置好，函数入口即有效。 */
#define DART_RETURN_TRUE(fn)                                 \
    __attribute__((naked)) static void fn(void) {            \
        __asm__ volatile("add x0, x22, #0x20\n"              \
                         "ret\n");                           \
    }

#define DART_RETURN_FALSE(fn)                                \
    __attribute__((naked)) static void fn(void) {            \
        __asm__ volatile("add x0, x22, #0x20\n"              \
                         "eor x0, x0, #0x10\n"               \
                         "ret\n");                           \
    }

/* ---------- 3. 常量 double ----------
 * 返回 double 的 Dart 函数（如 calculateDockOffsetY）改返回值最安全。 */
#define DART_RETURN_DOUBLE(fn, bits)                         \
    __attribute__((naked)) static void fn(void) {            \
        __asm__ volatile("ldr d0, 1f\n"                      \
                         "ret\n"                             \
                         ".align 3\n"                        \
                         "1: .quad " #bits "\n");             \
    }

/* ---------- 4. 对象池常量 ----------
 * 返回快照里的常量对象（枚举值等）。imm12 上限 0x7ff8，超出用 lsl #12 拆。
 * 用前必须反汇编原函数确认它取的确实是这个池偏移。 */
#define DART_RETURN_POOL(fn, hi, lo)                         \
    __attribute__((naked)) static void fn(void) {            \
        __asm__ volatile("add x16, x27, #" #hi ", lsl #12\n" \
                         "ldr x0, [x16, #" #lo "]\n"         \
                         "ret\n");                           \
    }

/* ---------- 5. 原值微调（调原函数后改 double 返回值） ----------
 * 用 Dart 自己的帧约定存 LR，所以不会破坏 x15。
 * 只用 x16 做暂存；入参寄存器与 x22/x26/x27/x28 全部保持不变，
 * 这样 blr 原函数时它的参数完好。
 *
 * 用法：先声明 `static void *g_orig_xxx;`，再展开宏，最后在目标表里绑定。 */
#define DART_ADJUST_DOUBLE(fn, orig_ptr, add_imm_quad)       \
    __attribute__((naked)) static void fn(void) {            \
        __asm__ volatile(                                    \
            "stp x29, x30, [x15, #-0x10]!\n"                 \
            "mov x29, x15\n"                                 \
            "adrp x16, " #orig_ptr "\n"                      \
            "add  x16, x16, :lo12:" #orig_ptr "\n"           \
            "ldr  x16, [x16]\n"                              \
            "blr x16\n"                                      \
            "ldr d1, 2f\n"                                   \
            "fadd d0, d0, d1\n"                              \
            "mov x15, x29\n"                                 \
            "ldp x29, x30, [x15], #0x10\n"                   \
            "ret\n"                                          \
            ".align 3\n"                                     \
            "2: .quad " #add_imm_quad "\n");                  \
    }

/* ---------- 目标校验 ---------- */
static inline int dart_entry_ok(const void *addr, unsigned size,
                                unsigned char *(*map_exec_check)(void))
{
    (void)map_exec_check;
    return addr != 0 && size >= 4 && *(const uint32_t *)addr == DART_ENTRY_WORD;
}

#endif /* LIQUIDDOCK_DART_ABI_H */
