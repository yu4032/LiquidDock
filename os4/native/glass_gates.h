#ifndef LD_OS4_GLASS_GATES_H
#define LD_OS4_GLASS_GATES_H
#include <stdint.h>

/* These functions return Dart canonical bool objects, not C booleans:
 * x22+0x20 is true, x22+0x30 false.
 * Override is inactive until ALL selected functions are safely hooked.
 * Never replace isAnyMaterialActive / isWidgetMaterialActive:
 * those may register Flutter reactive dependencies. */
static volatile uint8_t g_os4_glass_override __attribute__((used));
static volatile unsigned g_os4_glass_calls[3] __attribute__((used));
static void *g_os4_glass_original[3] __attribute__((used));
static bool g_os4_glass_installed;
static bool g_os4_glass_attempted;

#define LD_OS4_BOOL_GATE(fn, offset, orig_off, retval) \
__attribute__((used,naked)) static void fn(void) { \
    __asm__ volatile( \
        "adrp x9, g_os4_glass_calls\n" \
        "add x9, x9, :lo12:g_os4_glass_calls\n" \
        "ldr w10, [x9, #" #offset "]\n" \
        "add w10, w10, #1\n" \
        "str w10, [x9, #" #offset "]\n" \
        "adrp x16, g_os4_glass_override\n" \
        "ldrb w16, [x16, :lo12:g_os4_glass_override]\n" \
        "cbz w16, 1f\n" \
        "add x0, x22, #" #retval "\n" \
        "ret\n" \
        "1: adrp x16, g_os4_glass_original\n" \
        "add x16, x16, :lo12:g_os4_glass_original\n" \
        "ldr x16, [x16, #" #orig_off "]\n" \
        "br x16\n" \
    ); \
}
LD_OS4_BOOL_GATE(repl_os4_glass_supported, 0, 0, 0x20)
LD_OS4_BOOL_GATE(repl_os4_hyper_supported, 4, 8, 0x20)
LD_OS4_BOOL_GATE(repl_os4_material_enabled, 8, 16, 0x30)
#endif
