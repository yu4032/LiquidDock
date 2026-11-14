#ifndef LD_WIDGET_1X1_MENU_H
#define LD_WIDGET_1X1_MENU_H

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "bootlog.h"
#include "dart_heap.h"
#include "executable_range.h"
#include "lib_base.h"
#include "native_api.h"

#define LD_ENLARGED_WIDGETS_FILE \
    "/data/user_de/0/com.miui.home/files/liquiddock-enlarged-widgets.txt"

/* Target indices matching Os4GlassSymbols.widgetResizeSymbolNames */
enum {
    LD_W1X1_IS_VALID = 0,
    LD_W1X1_SUPPORT_SIZE_ADAPT = 1,
    LD_W1X1_RESOLVE_MENU_TITLE = 2,
    LD_W1X1_CHECK_RESIZE_ON_DROP = 3,
    LD_W1X1_IS_LEGAL_XY = 4,
    LD_W1X1_IS_CELL_OCCUPIED_EXCEPT = 5,
    LD_W1X1_SHOW_NO_SPACE_TOAST = 6,
    LD_W1X1_ADD_RESIZE_FRAME = 7,
    LD_W1X1_COMMIT_WIDGET_RESIZE = 8,
    LD_W1X1_RESIZE_WIDGET_IF_NEEDED = 9,
    LD_W1X1_ALLOC_TWO_BYTE_STRING = 10,
    LD_W1X1_ON_ITEM_CLICK = 11,
    LD_W1X1_I_ON_ITEM_CLICK = 12,
    LD_W1X1_MENU_ITEM_BUILD = 13,
    LD_W1X1_UPDATE_OCCUPIED_MARKS = 14,
    LD_W1X1_MAML_WIDGET_COPY = 15,
    LD_W1X1_WS_ON_INIT = 16,
    LD_W1X1_GET_CELL_LAYOUT_BY_ID = 17,
    LD_W1X1_REQUEST_UPDATE_GRID_CELLS = 18,
    LD_W1X1_UPDATE_ITEM_DATABASE = 19,
    LD_W1X1_TARGET_COUNT = 20,
};

static void *g_w1x1_rt[LD_W1X1_TARGET_COUNT];
static void *g_orig_w1x1_is_valid __attribute__((used));
static void *g_orig_w1x1_support __attribute__((used));
static void *g_orig_w1x1_title __attribute__((used));
static void *g_orig_w1x1_drop __attribute__((used));
static void *g_orig_w1x1_resize_needed __attribute__((used));
static void *g_orig_w1x1_item_click __attribute__((used));
static void *g_orig_w1x1_menu_item_build __attribute__((used));
static void *g_orig_w1x1_ws_on_init __attribute__((used));
static bool g_w1x1_menu_installed;

static volatile uintptr_t g_w1x1_last_widget;
static volatile uintptr_t g_w1x1_ws_ctrl;
static volatile uint32_t g_w1x1_size_adapt_cid;
static volatile uint32_t g_w1x1_stock_title_comp;

static struct {
    int active;
    uintptr_t widget;
    int64_t cell_x;
    int64_t cell_y;
    int64_t span_x;
    int64_t span_y;
} g_w1x1_pending;

#define LD_W1X1_MAX_ENLARGED 64
static struct {
    uint64_t key;
    int32_t orig_x;
    int32_t orig_y;
} g_w1x1_enlarged[LD_W1X1_MAX_ENLARGED];
static size_t g_w1x1_enlarged_count;
static int g_w1x1_enlarged_loaded;

/* Interceptor trampoline using hardware SP so Dart stack x15 stays free for Dart calls.
 * callback(struct ld_dart_context *c): return 1 to return c->x[0] directly, 0 to tail-call original.
 */
#define LD_DART_INTERCEPTOR(name, callback, original) \
__attribute__((naked, used)) static void name(void) { \
    __asm__ volatile( \
        "sub sp, sp, #832\n" \
        "stp x0, x1, [sp, #0]\n" \
        "stp x2, x3, [sp, #16]\n" \
        "stp x4, x5, [sp, #32]\n" \
        "stp x6, x7, [sp, #48]\n" \
        "stp x8, x9, [sp, #64]\n" \
        "stp x10, x11, [sp, #80]\n" \
        "stp x12, x13, [sp, #96]\n" \
        "stp x14, x15, [sp, #112]\n" \
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
        "cbnz w0, 1f\n" \
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
        "ldp x18, x19, [sp, #144]\n" \
        "ldp x20, x21, [sp, #160]\n" \
        "ldp x22, x23, [sp, #176]\n" \
        "ldp x24, x25, [sp, #192]\n" \
        "ldp x26, x27, [sp, #208]\n" \
        "ldp x28, x29, [sp, #224]\n" \
        "ldr x30, [sp, #240]\n" \
        "add sp, sp, #832\n" \
        "adrp x16, " #original "\n" \
        "ldr x16, [x16, :lo12:" #original "]\n" \
        "br x16\n" \
        "1:\n" \
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
        "ldr x15, [sp, #120]\n" \
        "ldp x18, x19, [sp, #144]\n" \
        "ldp x20, x21, [sp, #160]\n" \
        "ldp x22, x23, [sp, #176]\n" \
        "ldp x24, x25, [sp, #192]\n" \
        "ldp x26, x27, [sp, #208]\n" \
        "ldp x28, x29, [sp, #224]\n" \
        "ldr x30, [sp, #240]\n" \
        "add sp, sp, #832\n" \
        "ret\n" \
    ); \
}

/* Call a Dart AOT function from inside an interceptor callback.
 * x0=ctx, x1=fn, x2=args[8], x3=push_smi_on_dart_stack (0 or Smi value for allocateTwoByteString).
 */
__attribute__((naked, noinline)) static uint64_t ld_dart_call_raw(
    const struct ld_dart_context *c, void *fn, const uint64_t args[8], uint64_t dart_stack_arg)
{
    __asm__ volatile(
        "stp x29, x30, [sp, #-96]!\n"
        "mov x29, sp\n"
        "stp x19, x20, [sp, #16]\n"
        "stp x21, x22, [sp, #32]\n"
        "stp x23, x24, [sp, #48]\n"
        "stp x25, x26, [sp, #64]\n"
        "stp x27, x28, [sp, #80]\n"
        "mov x9, x1\n"       /* fn */
        "mov x10, x2\n"      /* args */
        "mov x11, x3\n"      /* optional dart stack arg */
        "ldr x15, [x0, #120]\n" /* c->x[15] */
        "ldr x21, [x0, #168]\n" /* c->x[21] */
        "ldr x22, [x0, #176]\n" /* c->x[22] */
        "ldr x26, [x0, #208]\n" /* c->x[26] */
        "ldr x27, [x0, #216]\n" /* c->x[27] */
        "ldr x28, [x0, #224]\n" /* c->x[28] */
        "cbz x11, 1f\n"
        "str x11, [x15, #-8]!\n"
        "1:\n"
        "ldp x0, x1, [x10, #0]\n"
        "ldp x2, x3, [x10, #16]\n"
        "ldp x4, x5, [x10, #32]\n"
        "ldp x6, x7, [x10, #48]\n"
        "blr x9\n"
        "ldp x19, x20, [sp, #16]\n"
        "ldp x21, x22, [sp, #32]\n"
        "ldp x23, x24, [sp, #48]\n"
        "ldp x25, x26, [sp, #64]\n"
        "ldp x27, x28, [sp, #80]\n"
        "ldp x29, x30, [sp], #96\n"
        "ret\n"
    );
}

static inline uint32_t ld_dart_cid(uintptr_t obj)
{
    if (!(obj & 1)) return 0;
    uint64_t hdr = 0;
    if (!ld_read_heap(obj - 1, &hdr, sizeof hdr)) return 0;
    return (uint32_t)((hdr >> 12) & 0xfffffu);
}

static inline uint64_t ld_widget_key(struct ld_dart_context *c, uintptr_t widget)
{
    uint32_t raw = ld_heap_u32(widget, 0x117);
    if (!(raw & 1)) {
        return (uint64_t)(int64_t)((int32_t)raw >> 1);
    }
    uintptr_t obj = (uintptr_t)raw + (c->x[28] << 32);
    if (!obj || obj == c->x[22]) return (uint64_t)widget;
    uint32_t smi_len = ld_heap_u32(obj, 0xb);
    if (!(smi_len & 1)) {
        uint32_t len = smi_len >> 1;
        if (len > 0 && len <= 128) {
            uint8_t buf[128];
            if (ld_read_heap(obj + 0xf, buf, len)) {
                uint64_t h = 14695981039346656037ull;
                for (uint32_t i = 0; i < len; i++) {
                    h ^= buf[i];
                    h *= 1099511628211ull;
                }
                return h ? h : 1;
            }
        }
    }
    return (uint64_t)ld_heap_i64(obj, 0x7);
}

static void ld_w1x1_load_enlarged(void)
{
    if (g_w1x1_enlarged_loaded) return;
    g_w1x1_enlarged_loaded = 1;
    FILE *f = fopen(LD_ENLARGED_WIDGETS_FILE, "r");
    if (!f) return;
    uint64_t k = 0;
    int ox = 0, oy = 0;
    while (g_w1x1_enlarged_count < LD_W1X1_MAX_ENLARGED &&
           fscanf(f, "%llx %d %d", (unsigned long long *)&k, &ox, &oy) == 3) {
        g_w1x1_enlarged[g_w1x1_enlarged_count].key = k;
        g_w1x1_enlarged[g_w1x1_enlarged_count].orig_x = ox;
        g_w1x1_enlarged[g_w1x1_enlarged_count].orig_y = oy;
        g_w1x1_enlarged_count++;
    }
    fclose(f);
}

static void ld_w1x1_save_enlarged(void)
{
    FILE *f = fopen(LD_ENLARGED_WIDGETS_FILE, "w");
    if (!f) return;
    for (size_t i = 0; i < g_w1x1_enlarged_count; i++) {
        fprintf(f, "%llx %d %d\n",
                (unsigned long long)g_w1x1_enlarged[i].key,
                g_w1x1_enlarged[i].orig_x,
                g_w1x1_enlarged[i].orig_y);
    }
    fclose(f);
}

static int ld_w1x1_find_enlarged(uint64_t key, int32_t *ox, int32_t *oy)
{
    ld_w1x1_load_enlarged();
    for (size_t i = 0; i < g_w1x1_enlarged_count; i++) {
        if (g_w1x1_enlarged[i].key == key) {
            if (ox) *ox = g_w1x1_enlarged[i].orig_x;
            if (oy) *oy = g_w1x1_enlarged[i].orig_y;
            return 1;
        }
    }
    return 0;
}

static void ld_w1x1_add_enlarged(uint64_t key, int32_t ox, int32_t oy)
{
    ld_w1x1_load_enlarged();
    for (size_t i = 0; i < g_w1x1_enlarged_count; i++) {
        if (g_w1x1_enlarged[i].key == key) {
            g_w1x1_enlarged[i].orig_x = ox;
            g_w1x1_enlarged[i].orig_y = oy;
            ld_w1x1_save_enlarged();
            return;
        }
    }
    if (g_w1x1_enlarged_count < LD_W1X1_MAX_ENLARGED) {
        g_w1x1_enlarged[g_w1x1_enlarged_count].key = key;
        g_w1x1_enlarged[g_w1x1_enlarged_count].orig_x = ox;
        g_w1x1_enlarged[g_w1x1_enlarged_count].orig_y = oy;
        g_w1x1_enlarged_count++;
        ld_w1x1_save_enlarged();
    }
}

static void ld_w1x1_remove_enlarged(uint64_t key)
{
    ld_w1x1_load_enlarged();
    for (size_t i = 0; i < g_w1x1_enlarged_count; i++) {
        if (g_w1x1_enlarged[i].key == key) {
            g_w1x1_enlarged[i] = g_w1x1_enlarged[g_w1x1_enlarged_count - 1];
            g_w1x1_enlarged_count--;
            ld_w1x1_save_enlarged();
            return;
        }
    }
}

static inline int ld_w1x1_is_toggle_target(struct ld_dart_context *c, uintptr_t widget, int *is_1x1_out)
{
    if (!widget || !(widget & 1) || widget == c->x[22]) return 0;
    int64_t sx = ld_heap_i64(widget, 0x1f);
    int64_t sy = ld_heap_i64(widget, 0x27);
    if (sx == 1 && sy == 1) {
        if (is_1x1_out) *is_1x1_out = 1;
        return 1;
    }
    if (sx == 2 && sy == 2 && ld_w1x1_find_enlarged(ld_widget_key(c, widget), NULL, NULL)) {
        if (is_1x1_out) *is_1x1_out = 0;
        return 1;
    }
    return 0;
}

__attribute__((used)) static int ld_w1x1_on_is_valid(struct ld_dart_context *c)
{
    if (!g_home_options.widget_1x1_to_2x2_enabled) return 0;
    uintptr_t widget = c->x[2];
    uintptr_t item = c->x[3];
    uint32_t cid = ld_dart_cid(item);
    if (cid) g_w1x1_size_adapt_cid = cid;
    if (widget && (widget & 1) && widget != c->x[22]) {
        g_w1x1_last_widget = widget;
    }
    return 0;
}
LD_DART_INTERCEPTOR(repl_w1x1_is_valid, ld_w1x1_on_is_valid, g_orig_w1x1_is_valid)

__attribute__((used)) static int ld_w1x1_on_support(struct ld_dart_context *c)
{
    if (!g_home_options.widget_1x1_to_2x2_enabled) return 0;
    uintptr_t widget = c->x[2];
    int match = ld_w1x1_is_toggle_target(c, widget, NULL);
    if (match) {
        g_w1x1_last_widget = widget;
        c->x[0] = c->x[22] + 0x20; /* Dart TRUE */
        return 1;
    }
    return 0;
}
LD_DART_INTERCEPTOR(repl_w1x1_support, ld_w1x1_on_support, g_orig_w1x1_support)

static uintptr_t ld_w1x1_make_dart_string(struct ld_dart_context *c, int is_1x1)
{
    if (!g_w1x1_rt[LD_W1X1_ALLOC_TWO_BYTE_STRING]) return 0;
    /* UTF-16LE: "放大到 2×2" (7 chars) or "还原为 1×1" (7 chars) */
    static const uint16_t k_expand[7] = { 0x653E, 0x5927, 0x5230, 0x0020, 0x0032, 0x00D7, 0x0032 };
    static const uint16_t k_shrink[7] = { 0x8FD8, 0x539F, 0x4E3A, 0x0020, 0x0031, 0x00D7, 0x0031 };
    const uint16_t *chars = is_1x1 ? k_expand : k_shrink;
    uint64_t args[8] = {0};
    uintptr_t s = (uintptr_t)ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_ALLOC_TWO_BYTE_STRING], args, (uint64_t)(7u << 1));
    if (!s || !(s & 1)) return 0;
    memcpy((void *)(s + 0xf), chars, sizeof k_expand);
    return s;
}

__attribute__((used)) static int ld_w1x1_on_title(struct ld_dart_context *c)
{
    if (!g_home_options.widget_1x1_to_2x2_enabled || !g_w1x1_rt[LD_W1X1_ALLOC_TWO_BYTE_STRING]) return 0;
    uintptr_t item = c->x[1];
    uint32_t cid = ld_dart_cid(item);
    if (!cid || cid != g_w1x1_size_adapt_cid) return 0;
    int is_1x1 = 0;
    if (!ld_w1x1_is_toggle_target(c, g_w1x1_last_widget, &is_1x1)) return 0;

    if (g_orig_w1x1_title) {
        uint64_t args[8] = { 0, c->x[1], c->x[2], 0, 0, 0, 0, 0 };
        uintptr_t orig = (uintptr_t)ld_dart_call_raw(c, g_orig_w1x1_title, args, 0);
        if (orig && (orig & 1) && orig != c->x[22]) {
            g_w1x1_stock_title_comp = (uint32_t)(orig & 0xFFFFFFFFu);
        }
    }

    uintptr_t s = ld_w1x1_make_dart_string(c, is_1x1);
    if (!s) return 0;
    c->x[0] = s;
    return 1;
}
LD_DART_INTERCEPTOR(repl_w1x1_title, ld_w1x1_on_title, g_orig_w1x1_title)

__attribute__((used)) static int ld_w1x1_on_menu_item_build(struct ld_dart_context *c)
{
    if (!g_home_options.widget_1x1_to_2x2_enabled || !g_w1x1_stock_title_comp) return 0;
    uintptr_t widget = ld_heap_pointer(c, c->x[1], 0xb);
    if (!widget || widget == c->x[22]) return 0;
    uint32_t title_comp = ld_heap_u32(widget, 0xf);
    if (title_comp != g_w1x1_stock_title_comp) return 0;
    int is_1x1 = 0;
    if (!ld_w1x1_is_toggle_target(c, g_w1x1_last_widget, &is_1x1)) return 0;
    uintptr_t s = ld_w1x1_make_dart_string(c, is_1x1);
    if (s && (s & 1)) {
        uint32_t s_comp = (uint32_t)(s & 0xFFFFFFFFu);
        memcpy((void *)(widget + 0xf), &s_comp, sizeof s_comp);
    }
    return 0;
}
LD_DART_INTERCEPTOR(repl_w1x1_menu_item_build, ld_w1x1_on_menu_item_build, g_orig_w1x1_menu_item_build)

__attribute__((used)) static int ld_w1x1_on_ws_init(struct ld_dart_context *c)
{
    if (c->x[1] && (c->x[1] & 1) && c->x[1] != c->x[22]) {
        g_w1x1_ws_ctrl = c->x[1];
    }
    return 0;
}
LD_DART_INTERCEPTOR(repl_w1x1_ws_on_init, ld_w1x1_on_ws_init, g_orig_w1x1_ws_on_init)

static void ld_w1x1_execute_toggle(struct ld_dart_context *c, uintptr_t ws_ctrl, uintptr_t widget, int is_1x1);

__attribute__((used)) static int ld_w1x1_on_item_click(struct ld_dart_context *c)
{
    if (!g_home_options.widget_1x1_to_2x2_enabled) return 0;
    uintptr_t item = c->x[3];
    uintptr_t widget = item ? ld_heap_pointer(c, item, 0x23) : 0;
    int is_1x1 = 0;
    if (!ld_w1x1_is_toggle_target(c, widget, &is_1x1)) return 0;
    if (!g_w1x1_ws_ctrl || !g_w1x1_rt[LD_W1X1_I_ON_ITEM_CLICK]) {
        ld_blog("widget 1x1 onItemClick: ws_ctrl=%#lx missing", (unsigned long)g_w1x1_ws_ctrl);
        return 0;
    }

    uint64_t dismiss_args[8] = { 0, c->x[1], c->x[2], c->x[3], 0, 0, 0, 0 };
    (void)ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_I_ON_ITEM_CLICK], dismiss_args, 0);

    ld_w1x1_execute_toggle(c, g_w1x1_ws_ctrl, widget, is_1x1);
    c->x[0] = c->x[22];
    return 1;
}
LD_DART_INTERCEPTOR(repl_w1x1_item_click, ld_w1x1_on_item_click, g_orig_w1x1_item_click)

static uintptr_t ld_w1x1_find_cell_layout(struct ld_dart_context *c, uintptr_t ws_ctrl, uintptr_t widget)
{
    if (!g_w1x1_rt[LD_W1X1_GET_CELL_LAYOUT_BY_ID]) return 0;
    int64_t screen_id = ld_heap_i64(widget, 0x2f);
    uint64_t args[8] = { 0, ws_ctrl, (uint64_t)screen_id, 0, 0, 0, 0, 0 };
    uintptr_t cl = (uintptr_t)ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_GET_CELL_LAYOUT_BY_ID], args, 0);
    if (!cl || !(cl & 1) || cl == c->x[22]) return 0;
    return cl;
}

static int ld_w1x1_list_contains(struct ld_dart_context *c, uintptr_t list, uintptr_t target)
{
    if (!list || !(list & 1) || list == c->x[22]) return 0;
    uint32_t len_smi = ld_heap_u32(list, 0xf);
    if ((len_smi & 1) != 0) return 0;
    uint32_t len = len_smi >> 1;
    if (len == 0 || len > 256) return 0;
    uintptr_t data = ld_heap_pointer(c, list, 0x17);
    if (!data || !(data & 1) || data == c->x[22]) return 0;
    uint32_t cap_smi = ld_heap_u32(data, 0x7);
    if ((cap_smi & 1) != 0 || (cap_smi >> 1) < len) return 0;
    for (uint32_t i = 0; i < len; i++) {
        if (ld_heap_pointer(c, data, 0xf + (size_t)i * 4) == target) return 1;
    }
    return 0;
}

static uintptr_t ld_w1x1_find_cl_items(struct ld_dart_context *c, uintptr_t cl, uintptr_t widget)
{
    for (size_t off = 0x4b; off <= 0x93; off += 4) {
        uintptr_t cand = ld_heap_pointer(c, cl, off);
        if (!cand || !(cand & 1) || cand == c->x[22]) continue;
        if (ld_w1x1_list_contains(c, cand, widget)) return cand;
        /* RxList wraps an inner _GrowableList */
        static const size_t rx_offs[3] = { 0xb, 0xf, 0x13 };
        for (size_t j = 0; j < 3; j++) {
            uintptr_t inner = ld_heap_pointer(c, cand, rx_offs[j]);
            if (ld_w1x1_list_contains(c, inner, widget)) return cand;
        }
    }
    return ld_heap_pointer(c, cl, 0x5f);
}

static const uint8_t ld_w1x1_empty_args_desc[64] __attribute__((aligned(16))) = { 0 };

static void ld_w1x1_execute_toggle(struct ld_dart_context *c, uintptr_t ws_ctrl, uintptr_t widget, int is_1x1)
{
    int64_t cx = ld_heap_i64(widget, 0x37);
    int64_t cy = ld_heap_i64(widget, 0x3f);
    uint64_t wkey = ld_widget_key(c, widget);

    int64_t target_x = cx, target_y = cy, target_sx = 1, target_sy = 1;

    uintptr_t cl = ld_w1x1_find_cell_layout(c, ws_ctrl, widget);

    if (is_1x1) {
        if (!cl || !g_w1x1_rt[LD_W1X1_IS_LEGAL_XY] || !g_w1x1_rt[LD_W1X1_IS_CELL_OCCUPIED_EXCEPT]) {
            ld_blog("widget 1x1->2x2: cellLayout not found");
            return;
        }
        static const int dx[4] = { 0, -1, 0, -1 };
        static const int dy[4] = { 0, 0, -1, -1 };
        int found = 0;
        for (int i = 0; i < 4; i++) {
            int64_t nx = cx + dx[i];
            int64_t ny = cy + dy[i];
            if (nx < 0 || ny < 0) continue;
            uint64_t a_legal[8] = { 0, cl, (uint64_t)nx, (uint64_t)ny, 0, (uint64_t)(2 << 1), (uint64_t)(2 << 1), 0 };
            uint64_t legal = ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_IS_LEGAL_XY], a_legal, 0);
            if (legal & 0x10u) continue; /* FALSE */
            uint64_t a_occ[8] = { 0, cl, (uint64_t)nx, (uint64_t)ny, 0, (uint64_t)(2 << 1), (uint64_t)(2 << 1), widget };
            uint64_t occ = ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_IS_CELL_OCCUPIED_EXCEPT], a_occ, 0);
            if (!(occ & 0x10u)) continue; /* TRUE = occupied */
            target_x = nx;
            target_y = ny;
            found = 1;
            break;
        }
        if (!found) {
            ld_blog("widget 1x1->2x2: no 2x2 space around (%lld,%lld)", (long long)cx, (long long)cy);
            if (g_w1x1_rt[LD_W1X1_SHOW_NO_SPACE_TOAST]) {
                uint64_t a_toast[8] = { 0, cl, widget, 0, 0, 0, 0, 0 };
                (void)ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_SHOW_NO_SPACE_TOAST], a_toast, 0);
            }
            return;
        }
        target_sx = 2;
        target_sy = 2;
        ld_w1x1_add_enlarged(wkey, (int32_t)cx, (int32_t)cy);
    } else {
        int32_t ox = (int32_t)cx, oy = (int32_t)cy;
        (void)ld_w1x1_find_enlarged(wkey, &ox, &oy);
        target_x = ox;
        target_y = oy;
        target_sx = 1;
        target_sy = 1;
        ld_w1x1_remove_enlarged(wkey);
    }

    uint32_t cid = ld_dart_cid(widget);
    if (cid == 0x877) {
        uintptr_t mgr = ld_heap_pointer(c, ws_ctrl, 0x1d7);
        if (!mgr || !g_w1x1_rt[LD_W1X1_ADD_RESIZE_FRAME] || !g_w1x1_rt[LD_W1X1_COMMIT_WIDGET_RESIZE]) {
            ld_blog("widget 1x1->2x2: resize manager unavailable");
            return;
        }

        g_w1x1_pending.active = 1;
        g_w1x1_pending.widget = widget;
        g_w1x1_pending.cell_x = target_x;
        g_w1x1_pending.cell_y = target_y;
        g_w1x1_pending.span_x = target_sx;
        g_w1x1_pending.span_y = target_sy;

        uint64_t a_add[8] = { 0, mgr, widget, c->x[22] + 0x20, 0, 0, 0, 0 };
        (void)ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_ADD_RESIZE_FRAME], a_add, 0);

        uint64_t a_commit[8] = { 0, mgr, 0, 0, 0, 0, 0, 0 };
        (void)ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_COMMIT_WIDGET_RESIZE], a_commit, 0);

        g_w1x1_pending.active = 0;
    } else {
        memcpy((void *)(widget + 0x37), &target_x, sizeof target_x);
        memcpy((void *)(widget + 0x3f), &target_y, sizeof target_y);
        memcpy((void *)(widget + 0x1f), &target_sx, sizeof target_sx);
        memcpy((void *)(widget + 0x27), &target_sy, sizeof target_sy);

        if (cl && g_w1x1_rt[LD_W1X1_UPDATE_OCCUPIED_MARKS]) {
            uintptr_t items = ld_w1x1_find_cl_items(c, cl, widget);
            if (items) {
                uint64_t a_occ[8] = {
                    0, cl, items, 0,
                    (uint64_t)(uintptr_t)(ld_w1x1_empty_args_desc + 1),
                    0, 0, 0
                };
                (void)ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_UPDATE_OCCUPIED_MARKS], a_occ, 0);
            }
        }
        if (cl && g_w1x1_rt[LD_W1X1_REQUEST_UPDATE_GRID_CELLS]) {
            uint64_t a_req[8] = { 0, cl, 0, 0, 0, 0, 0, 0 };
            (void)ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_REQUEST_UPDATE_GRID_CELLS], a_req, 0);
        }
        if (g_w1x1_rt[LD_W1X1_MAML_WIDGET_COPY] && g_w1x1_rt[LD_W1X1_UPDATE_ITEM_DATABASE]) {
            uint64_t a_copy[8] = { 0, widget, 0, 0, 0, 0, 0, 0 };
            uint64_t bundle = ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_MAML_WIDGET_COPY], a_copy, 0);
            if (bundle && (bundle & 1) && bundle != c->x[22]) {
                uint64_t a_db[8] = { 0, widget, bundle, 0, 0, 0, 0, 0 };
                (void)ld_dart_call_raw(c, g_w1x1_rt[LD_W1X1_UPDATE_ITEM_DATABASE], a_db, 0);
            }
        }
    }

    ld_blog("widget 1x1<->2x2 toggled (%lld,%lld) -> (%lld,%lld) span %lldx%lld",
            (long long)cx, (long long)cy, (long long)target_x, (long long)target_y,
            (long long)target_sx, (long long)target_sy);
}

__attribute__((used)) static int ld_w1x1_on_drop(struct ld_dart_context *c)
{
    if (!g_home_options.widget_1x1_to_2x2_enabled) return 0;
    /* Only intercept when invoked from popup menu (fromMenu == TRUE: !(x3 & 0x10)) */
    if (c->x[3] & 0x10u) return 0;

    uintptr_t ws_ctrl = c->x[1];
    uintptr_t widget = c->x[2];
    int is_1x1 = 0;
    if (!ld_w1x1_is_toggle_target(c, widget, &is_1x1)) return 0;

    ld_w1x1_execute_toggle(c, ws_ctrl, widget, is_1x1);
    c->x[0] = c->x[22];
    return 1;
}
LD_DART_INTERCEPTOR(repl_w1x1_drop, ld_w1x1_on_drop, g_orig_w1x1_drop)

__attribute__((used)) static int ld_w1x1_on_resize_needed(struct ld_dart_context *c)
{
    if (!g_w1x1_pending.active) return 0;
    uintptr_t ctrl = c->x[1];
    uintptr_t rx = ld_heap_pointer(c, ctrl, 0x23);
    uintptr_t widget = rx ? ld_heap_pointer(c, rx, 0x7) : 0;
    if (!widget || widget != g_w1x1_pending.widget) return 0;

    int64_t cx = g_w1x1_pending.cell_x;
    int64_t cy = g_w1x1_pending.cell_y;
    int64_t sx = g_w1x1_pending.span_x;
    int64_t sy = g_w1x1_pending.span_y;
    g_w1x1_pending.active = 0;

    memcpy((void *)(widget + 0x37), &cx, sizeof cx);
    memcpy((void *)(widget + 0x3f), &cy, sizeof cy);
    memcpy((void *)(widget + 0x1f), &sx, sizeof sx);
    memcpy((void *)(widget + 0x27), &sy, sizeof sy);

    uint32_t smi_cx = (uint32_t)(cx << 1);
    uint32_t smi_cy = (uint32_t)(cy << 1);
    uint32_t smi_sx = (uint32_t)(sx << 1);
    uint32_t smi_sy = (uint32_t)(sy << 1);
    memcpy((void *)(ctrl + 0x97), &smi_cx, sizeof smi_cx);
    memcpy((void *)(ctrl + 0x9b), &smi_cy, sizeof smi_cy);
    memcpy((void *)(ctrl + 0x9f), &smi_sx, sizeof smi_sx);
    memcpy((void *)(ctrl + 0xa3), &smi_sy, sizeof smi_sy);

    c->x[0] = c->x[22];
    return 1;
}
LD_DART_INTERCEPTOR(repl_w1x1_resize_needed, ld_w1x1_on_resize_needed, g_orig_w1x1_resize_needed)

static void ld_w1x1_menu_install(const struct native_api *api, const ld_lib_info *lib)
{
    if (g_w1x1_menu_installed || !api || !api->hook || !lib) return;
    if (!g_home_options.widget_1x1_to_2x2_enabled || !g_home_options.widget_resize_targets_present) return;

    for (size_t i = 0; i < LD_W1X1_TARGET_COUNT; i++) {
        uintptr_t rt = 0;
        if (!ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.widget_resize_vaddr[i], &rt) ||
            !executable_range(rt, (size_t)g_home_options.widget_resize_size[i])) {
            ld_blog("OS4 widget 1x1<->2x2 menu target %zu unavailable", i);
            return;
        }
        g_w1x1_rt[i] = (void *)rt;
    }

    int ok = 0;
    struct { size_t idx; void *repl; void **orig; } hooks[8] = {
        { LD_W1X1_IS_VALID, (void *)repl_w1x1_is_valid, &g_orig_w1x1_is_valid },
        { LD_W1X1_SUPPORT_SIZE_ADAPT, (void *)repl_w1x1_support, &g_orig_w1x1_support },
        { LD_W1X1_RESOLVE_MENU_TITLE, (void *)repl_w1x1_title, &g_orig_w1x1_title },
        { LD_W1X1_CHECK_RESIZE_ON_DROP, (void *)repl_w1x1_drop, &g_orig_w1x1_drop },
        { LD_W1X1_RESIZE_WIDGET_IF_NEEDED, (void *)repl_w1x1_resize_needed, &g_orig_w1x1_resize_needed },
        { LD_W1X1_ON_ITEM_CLICK, (void *)repl_w1x1_item_click, &g_orig_w1x1_item_click },
        { LD_W1X1_MENU_ITEM_BUILD, (void *)repl_w1x1_menu_item_build, &g_orig_w1x1_menu_item_build },
        { LD_W1X1_WS_ON_INIT, (void *)repl_w1x1_ws_on_init, &g_orig_w1x1_ws_on_init },
    };
    for (size_t i = 0; i < 8; i++) {
        uintptr_t rt = (uintptr_t)g_w1x1_rt[hooks[i].idx];
        if (api->hook((void *)rt, hooks[i].repl, hooks[i].orig) == 0 &&
            *hooks[i].orig && ld_pin_code_pages_soft(rt, 16)) {
            ok++;
        }
    }
    g_w1x1_menu_installed = (ok == 8);
    ld_blog("OS4 widget 1x1<->2x2 menu installed=%d/8", ok);
}

#endif
