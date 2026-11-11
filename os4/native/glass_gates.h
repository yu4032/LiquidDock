#ifndef LD_OS4_GLASS_GATES_H
#define LD_OS4_GLASS_GATES_H
#include <stdint.h>

/* These functions return Dart canonical bool objects, not C booleans:
 * x22+0x20 is true, x22+0x30 false. The extended wrappers call their vendor
 * bodies first so cache initialization and Flutter reactive dependencies stay
 * registered, then remove the capability result as a rendering gate. */
static volatile uint8_t g_os4_glass_override __attribute__((used));
static volatile unsigned g_os4_glass_calls[3] __attribute__((used));
static void *g_os4_glass_original[3] __attribute__((used));
static bool g_os4_glass_installed;
static bool g_os4_glass_attempted;
static void *g_os4_render_gate_original[9] __attribute__((used));
static volatile uint16_t g_os4_render_gate_mask __attribute__((used));
static bool g_os4_render_gates_installed;
static void *g_orig_passblur_supported __attribute__((used));
static bool g_passblur_supported_installed;

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
        "cbz x16, 2f\n" \
        "br x16\n" \
        "2: add x0, x22, #" #retval "\n" \
        "ret\n" \
    ); \
}
LD_OS4_BOOL_GATE(repl_os4_glass_supported, 0, 0, 0x20)
LD_OS4_BOOL_GATE(repl_os4_hyper_supported, 4, 8, 0x20)
LD_OS4_BOOL_GATE(repl_os4_material_enabled, 8, 16, 0x30)

#define LD_OS4_RENDER_GATE(fn, orig_off) \
__attribute__((used,naked)) static void fn(void) { \
    __asm__ volatile( \
        "stp x29, x30, [x15, #-0x10]!\n" \
        "mov x29, x15\n" \
        "adrp x16, g_os4_render_gate_original\n" \
        "add x16, x16, :lo12:g_os4_render_gate_original\n" \
        "ldr x16, [x16, #" #orig_off "]\n" \
        "cbz x16, 1f\n" \
        "blr x16\n" \
        "b 2f\n" \
        "1: add x0, x22, #0x20\n" \
        "2: adrp x9, g_os4_glass_override\n" \
        "ldrb w9, [x9, :lo12:g_os4_glass_override]\n" \
        "cbz w9, 3f\n" \
        "add x0, x22, #0x20\n" \
        "3: mov x15, x29\n" \
        "ldp x29, x30, [x15], #0x10\n" \
        "ret\n" \
    ); \
}

LD_OS4_RENDER_GATE(repl_os4_blur_supported, 0)
LD_OS4_RENDER_GATE(repl_os4_maml_blur_supported, 8)
LD_OS4_RENDER_GATE(repl_os4_any_material_active, 16)
LD_OS4_RENDER_GATE(repl_os4_widget_material_active, 24)
LD_OS4_RENDER_GATE(repl_os4_three_layer_blur_supported, 32)
LD_OS4_RENDER_GATE(repl_os4_folder_blur_supported, 40)
LD_OS4_RENDER_GATE(repl_os4_widget_glass_supported, 48)
LD_OS4_RENDER_GATE(repl_os4_press_light_supported, 56)
LD_OS4_RENDER_GATE(repl_os4_hyper_material_effective, 64)

/* PassBlurBackgroundLight.supported is a separate renderer-version gate.
 * Keep its original initialization, then enable the native PassBlur path. */
__attribute__((used,naked)) static void repl_os4_passblur_supported(void)
{
    __asm__ volatile(
        "stp x29, x30, [x15, #-0x10]!\n"
        "mov x29, x15\n"
        "adrp x16, g_orig_passblur_supported\n"
        "add x16, x16, :lo12:g_orig_passblur_supported\n"
        "ldr x16, [x16]\n"
        "cbz x16, 1f\n"
        "blr x16\n"
        "b 2f\n"
        "1: add x0, x22, #0x20\n"
        "2: adrp x9, g_os4_glass_override\n"
        "ldrb w9, [x9, :lo12:g_os4_glass_override]\n"
        "cbz w9, 3f\n"
        "add x0, x22, #0x20\n"
        "3: mov x15, x29\n"
        "ldp x29, x30, [x15], #0x10\n"
        "ret\n"
    );
}
#endif
