#ifndef LD_OS4_GLASS_GATES_H
#define LD_OS4_GLASS_GATES_H
#include <stdint.h>

/* These functions return Dart canonical bool objects, not C booleans:
 * x22+0x20 is true, x22+0x30 false. The extended wrappers call their vendor
 * bodies first so cache initialization and Flutter reactive dependencies stay
 * registered, then remove the capability result as a rendering gate. */
static volatile uint8_t g_os4_glass_override __attribute__((used));
static volatile uint8_t g_os4_widget_glass_override __attribute__((used));
static volatile uint8_t g_os4_maml_blur_override __attribute__((used));
static volatile unsigned g_os4_glass_calls[3] __attribute__((used));
static void *g_os4_glass_original[3] __attribute__((used));
static bool g_os4_glass_installed;
static bool g_os4_glass_attempted;
static void *g_os4_render_gate_original[9] __attribute__((used));
static volatile uint16_t g_os4_render_gate_mask __attribute__((used));
static bool g_os4_render_gates_installed;
static void *g_orig_passblur_supported __attribute__((used));
static bool g_passblur_supported_installed;
static volatile uint32_t g_os4_recents_blur_percent __attribute__((used)) = 100;
static void *g_orig_recents_blur_radius __attribute__((used));
static void *g_orig_recents_home_hold __attribute__((used));
static bool g_recents_blur_installed;
static bool g_recents_blur_attempted;
static void *g_orig_widget_init_miui_attr __attribute__((used));
static bool g_widget_init_miui_attr_installed;

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

#define LD_OS4_RENDER_GATE_VAR(fn, orig_off, var) \
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
        "2: adrp x9, " #var "\n" \
        "ldrb w9, [x9, :lo12:" #var "]\n" \
        "cbz w9, 3f\n" \
        "add x0, x22, #0x20\n" \
        "3: mov x15, x29\n" \
        "ldp x29, x30, [x15], #0x10\n" \
        "ret\n" \
    ); \
}

#define LD_OS4_RENDER_GATE(fn, orig_off) \
    LD_OS4_RENDER_GATE_VAR(fn, orig_off, g_os4_glass_override)
#define LD_OS4_WIDGET_RENDER_GATE(fn, orig_off) \
    LD_OS4_RENDER_GATE_VAR(fn, orig_off, g_os4_widget_glass_override)

LD_OS4_RENDER_GATE(repl_os4_blur_supported, 0)
/* Keep Flutter-side MAML/widget blur backdrop at vendor value so special-shape widgets
 * avoid unclipped square fill; OS4 widget glass is gated by isGlassOs4SupportedSync. */
LD_OS4_RENDER_GATE_VAR(repl_os4_maml_blur_supported, 8, g_os4_maml_blur_override)
LD_OS4_RENDER_GATE(repl_os4_any_material_active, 16)
LD_OS4_WIDGET_RENDER_GATE(repl_os4_widget_material_active, 24)
LD_OS4_RENDER_GATE(repl_os4_three_layer_blur_supported, 32)
LD_OS4_RENDER_GATE(repl_os4_folder_blur_supported, 40)
LD_OS4_WIDGET_RENDER_GATE(repl_os4_widget_glass_supported, 48)
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

/* BlurLayerParams._getRecentBlurRadius returns an unboxed double in d0.
 * Scale it by g_os4_recents_blur_percent / 100.0 when < 100. */
__attribute__((used,naked)) static void repl_os4_recents_blur_radius(void)
{
    __asm__ volatile(
        "stp x29, x30, [x15, #-0x10]!\n"
        "mov x29, x15\n"
        "adrp x16, g_orig_recents_blur_radius\n"
        "add x16, x16, :lo12:g_orig_recents_blur_radius\n"
        "ldr x16, [x16]\n"
        "cbz x16, 1f\n"
        "blr x16\n"
        "1: adrp x9, g_os4_recents_blur_percent\n"
        "add x9, x9, :lo12:g_os4_recents_blur_percent\n"
        "ldr w9, [x9]\n"
        "cmp w9, #100\n"
        "b.hs 2f\n"
        "ucvtf d1, w9\n"
        "movz x10, #0x4059, lsl #48\n"
        "fmov d2, x10\n"
        "fdiv d1, d1, d2\n"
        "fmul d0, d0, d1\n"
        "2: mov x15, x29\n"
        "ldp x29, x30, [x15], #0x10\n"
        "ret\n"
    );
}

/* BlurLayerParams.getHomeHoldParams returns a BlurLayerParams* in x0 with
 * blurRadius (double) at [x0, #7]. The 16-byte hook trampoline clobbers x17
 * across the target's `add x17, x27, ...`, so recompute blurRadius via
 * repl_os4_recents_blur_radius and store it at [x0, #7]. */
__attribute__((used,naked)) static void repl_os4_recents_home_hold(void)
{
    __asm__ volatile(
        "stp x29, x30, [x15, #-0x10]!\n"
        "mov x29, x15\n"
        "sub x15, x15, #0x10\n"
        "adrp x16, g_orig_recents_home_hold\n"
        "add x16, x16, :lo12:g_orig_recents_home_hold\n"
        "ldr x16, [x16]\n"
        "cbz x16, 2f\n"
        "blr x16\n"
        "cbz x0, 2f\n"
        "stur x0, [x29, #-8]\n"
        "bl repl_os4_recents_blur_radius\n"
        "ldur x0, [x29, #-8]\n"
        "stur d0, [x0, #7]\n"
        "2: mov x15, x29\n"
        "ldp x29, x30, [x15], #0x10\n"
        "ret\n"
    );
}

/* Extend OS4 widget glass & rounded-corner clipping to all Android AppWidgets
 * (including third-party providers without MIUI metadata) when widget glass is enabled. */
__attribute__((used,naked)) static void repl_widget_init_miui_attr(void)
{
    __asm__ volatile(
        "stp x29, x30, [x15, #-0x10]!\n"
        "mov x29, x15\n"
        "str x1, [x15, #-0x10]!\n"
        "adrp x16, g_orig_widget_init_miui_attr\n"
        "add x16, x16, :lo12:g_orig_widget_init_miui_attr\n"
        "ldr x16, [x16]\n"
        "cbz x16, 1f\n"
        "blr x16\n"
        "1: ldr x1, [x15], #0x10\n"
        "adrp x9, g_os4_widget_glass_override\n"
        "ldrb w9, [x9, :lo12:g_os4_widget_glass_override]\n"
        "cbz w9, 2f\n"
        "add x9, x22, #0x20\n"
        "stur w9, [x1, #0xfb]\n"
        "mov x10, #0x10f\n"
        "str w9, [x1, x10]\n"
        "2: mov x15, x29\n"
        "ldp x29, x30, [x15], #0x10\n"
        "ret\n"
    );
}

static void *g_orig_pad_widget_container __attribute__((used));
static void *g_orig_phone_widget_container __attribute__((used));
__attribute__((used)) static void ld_observe_pad_widget_container(struct ld_dart_context *c) {
    if (!c || !g_os4_widget_glass_override) return;
    uintptr_t container = c->x[1];
    if (!(container & 1) || container == c->x[22]) return;
    uintptr_t item = ld_heap_pointer(c, container, 0xb);
    if (!item) {
        uintptr_t ctrl = ld_heap_pointer(c, container, 0xf);
        item = ctrl ? ld_heap_pointer(c, ctrl, 0xb) : 0;
    }
    if (!item) return;
    uint32_t dart_true = (uint32_t)(c->x[22] + 0x20);
    memcpy((void *)(item + 0xfb), &dart_true, 4);
    memcpy((void *)(item + 0x10f), &dart_true, 4);
}
LD_DART_OBSERVER(repl_pad_widget_container, ld_observe_pad_widget_container, g_orig_pad_widget_container)
LD_DART_OBSERVER(repl_phone_widget_container, ld_observe_pad_widget_container, g_orig_phone_widget_container)
#endif
