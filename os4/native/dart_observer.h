/* Native observer bridge: no Dart calls, allocations or retained heap pointers.
 * Use only at a verified function entry. x15 may be only 8-byte aligned. The temporary
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
#endif
