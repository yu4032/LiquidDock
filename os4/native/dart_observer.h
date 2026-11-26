/* Native observer bridge: no Dart calls, allocations or retained heap pointers.
 * Use only at the selected function entry. x15 may be only 8-byte aligned. The temporary
 * C stack grows below the current Dart stack; hardware SP and every live Dart
 * register/vector/flags are restored before tail-calling the original.
 */
#ifndef LD_DART_OBSERVER_H
#define LD_DART_OBSERVER_H
#include <stdint.h>
struct ld_dart_context { uint64_t x[31]; uint64_t reserved; unsigned char q[32][16]; uint64_t nzcv; uint64_t native_sp; };
#define LD_DART_OBSERVER(name, callback, original) \
__attribute__((naked, used)) static void name(void) { \
    __asm__ volatile( \
        "stp x16, x17, [x15, #-16]\n" \
        "mov x16, sp\n" \
        "sub x17, x15, #832\n" \
        "and x17, x17, #0xfffffffffffffff0\n" \
        "mov sp, x17\n" \
        "str x16, [sp, #776]\n" \
        "stp x0, x1, [sp, #0]\n" \
        "stp x2, x3, [sp, #16]\n" \
        "stp x4, x5, [sp, #32]\n" \
        "stp x6, x7, [sp, #48]\n" \
        "stp x8, x9, [sp, #64]\n" \
        "stp x10, x11, [sp, #80]\n" \
        "stp x12, x13, [sp, #96]\n" \
        "stp x14, x15, [sp, #112]\n" \
        "ldur x16, [x15, #-16]\n" \
        "ldur x17, [x15, #-8]\n" \
        "stp x16, x17, [sp, #128]\n" \
        "stp x18, x19, [sp, #144]\n" \
        "stp x20, x21, [sp, #160]\n" \
        "stp x22, x23, [sp, #176]\n" \
        "stp x24, x25, [sp, #192]\n" \
        "stp x26, x27, [sp, #208]\n" \
        "stp x28, x29, [sp, #224]\n" \
        "str x30, [sp, #240]\n" \
        "stp q0, q1, [sp, #256]\n" \
        "stp q2, q3, [sp, #288]\n" \
        "stp q4, q5, [sp, #320]\n" \
        "stp q6, q7, [sp, #352]\n" \
        "stp q8, q9, [sp, #384]\n" \
        "stp q10, q11, [sp, #416]\n" \
        "stp q12, q13, [sp, #448]\n" \
        "stp q14, q15, [sp, #480]\n" \
        "stp q16, q17, [sp, #512]\n" \
        "stp q18, q19, [sp, #544]\n" \
        "stp q20, q21, [sp, #576]\n" \
        "stp q22, q23, [sp, #608]\n" \
        "stp q24, q25, [sp, #640]\n" \
        "stp q26, q27, [sp, #672]\n" \
        "stp q28, q29, [sp, #704]\n" \
        "stp q30, q31, [sp, #736]\n" \
        "mrs x16, nzcv\n" \
        "str x16, [sp, #768]\n" \
        "mov x0, sp\n" \
        "bl " #callback "\n" \
        "ldr x16, [sp, #768]\n" \
        "msr nzcv, x16\n" \
        "ldp q0, q1, [sp, #256]\n" \
        "ldp q2, q3, [sp, #288]\n" \
        "ldp q4, q5, [sp, #320]\n" \
        "ldp q6, q7, [sp, #352]\n" \
        "ldp q8, q9, [sp, #384]\n" \
        "ldp q10, q11, [sp, #416]\n" \
        "ldp q12, q13, [sp, #448]\n" \
        "ldp q14, q15, [sp, #480]\n" \
        "ldp q16, q17, [sp, #512]\n" \
        "ldp q18, q19, [sp, #544]\n" \
        "ldp q20, q21, [sp, #576]\n" \
        "ldp q22, q23, [sp, #608]\n" \
        "ldp q24, q25, [sp, #640]\n" \
        "ldp q26, q27, [sp, #672]\n" \
        "ldp q28, q29, [sp, #704]\n" \
        "ldp q30, q31, [sp, #736]\n" \
        "ldp x0, x1, [sp, #0]\n" \
        "ldp x2, x3, [sp, #16]\n" \
        "ldp x4, x5, [sp, #32]\n" \
        "ldp x6, x7, [sp, #48]\n" \
        "ldp x8, x9, [sp, #64]\n" \
        "ldp x10, x11, [sp, #80]\n" \
        "ldp x12, x13, [sp, #96]\n" \
        "ldp x14, x15, [sp, #112]\n" \
        "ldr x17, [sp, #136]\n" \
        "ldp x18, x19, [sp, #144]\n" \
        "ldp x20, x21, [sp, #160]\n" \
        "ldp x22, x23, [sp, #176]\n" \
        "ldp x24, x25, [sp, #192]\n" \
        "ldp x26, x27, [sp, #208]\n" \
        "ldp x28, x29, [sp, #224]\n" \
        "ldr x30, [sp, #240]\n" \
        "ldr x16, [sp, #776]\n" \
        "mov sp, x16\n" \
        "adrp x16, " #original "\n" \
        "ldr x16, [x16, :lo12:" #original "]\n" \
        "br x16\n"); \
}

/* Call a Dart AOT function from inside an observer/interceptor callback.
 * x0=ctx, x1=fn, x2=args[8], x3=push_smi_on_dart_stack (0 or Smi value).
 * Sets Dart SP (x15) below current C sp so nested Dart frames never collide
 * with temporary C stack, and preserves AAPCS callee-saved d8..d15.
 */
__attribute__((naked, noinline)) static uint64_t ld_dart_call_raw(
    const struct ld_dart_context *c, void *fn, const uint64_t args[8], uint64_t dart_stack_arg)
{
    __asm__ volatile(
        "stp x29, x30, [sp, #-160]!\n"
        "mov x29, sp\n"
        "stp x19, x20, [sp, #16]\n"
        "stp x21, x22, [sp, #32]\n"
        "stp x23, x24, [sp, #48]\n"
        "stp x25, x26, [sp, #64]\n"
        "stp x27, x28, [sp, #80]\n"
        "stp d8,  d9,  [sp, #96]\n"
        "stp d10, d11, [sp, #112]\n"
        "stp d12, d13, [sp, #128]\n"
        "stp d14, d15, [sp, #144]\n"
        "mov x9, x1\n"
        "mov x10, x2\n"
        "mov x11, x3\n"
        "sub x15, sp, #64\n"
        "ldr x21, [x0, #168]\n"
        "ldr x22, [x0, #176]\n"
        "ldr x26, [x0, #208]\n"
        "ldr x27, [x0, #216]\n"
        "ldr x28, [x0, #224]\n"
        "cbz x11, 1f\n"
        "str x11, [x15, #-8]!\n"
        "1:\n"
        "ldp x0, x1, [x10, #0]\n"
        "ldp x2, x3, [x10, #16]\n"
        "ldp x4, x5, [x10, #32]\n"
        "ldp x6, x7, [x10, #48]\n"
        "blr x9\n"
        "ldp d14, d15, [sp, #144]\n"
        "ldp d12, d13, [sp, #128]\n"
        "ldp d10, d11, [sp, #112]\n"
        "ldp d8,  d9,  [sp, #96]\n"
        "ldp x19, x20, [sp, #16]\n"
        "ldp x21, x22, [sp, #32]\n"
        "ldp x23, x24, [sp, #48]\n"
        "ldp x25, x26, [sp, #64]\n"
        "ldp x27, x28, [sp, #80]\n"
        "ldp x29, x30, [sp], #160\n"
        "ret\n"
    );
}
#endif
