#ifndef LD_HOME_WIDGETS_H
#define LD_HOME_WIDGETS_H
#include <math.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include "dart_heap.h"
#include "grid_orientation.h"
#include "home_widget_fingerprints.h"

struct ld_home_baseline {
    double cell_width, cell_height, icon_size;
    double current_width, current_height, current_icon;
    int direction;
    int valid;
};

/* Parent captures each full-screen/count-matched pipeline variant. */
static struct ld_home_baseline g_home_baseline[128];
static void *g_orig_home_widget;
static uintptr_t g_home_widget_allocate;

static inline const struct ld_home_baseline *ld_home_baseline_find(
        const struct ld_home_baseline *entries, size_t count, int direction,
        double current_width, double current_height, double current_icon)
{
    if (!entries || direction < 0 || direction > 1 || !isfinite(current_width)
            || !isfinite(current_height) || !isfinite(current_icon)
            || current_width <= 0 || current_height <= 0 || current_icon <= 0) return 0;
    const struct ld_home_baseline *found = 0;
    for (size_t i = 0; i < count; ++i) {
        const struct ld_home_baseline *entry = &entries[i];
        if (!entry->valid || entry->direction != direction
                || memcmp(&entry->current_width, &current_width, sizeof current_width)
                || memcmp(&entry->current_height, &current_height, sizeof current_height)
                || memcmp(&entry->current_icon, &current_icon, sizeof current_icon)) continue;
        if (!found) found = entry;
        else if (memcmp(&found->cell_width, &entry->cell_width, sizeof entry->cell_width)
                || memcmp(&found->cell_height, &entry->cell_height, sizeof entry->cell_height)
                || memcmp(&found->icon_size, &entry->icon_size, sizeof entry->icon_size)) return 0;
    }
    if (found) return found;
    /* home_margins.h modifies grid cell_width/cell_height after baseline capture,
     * so fall back to matching (direction, current_icon) when margins are active. */
    for (size_t i = 0; i < count; ++i) {
        const struct ld_home_baseline *entry = &entries[i];
        if (!entry->valid || entry->direction != direction
                || memcmp(&entry->current_icon, &current_icon, sizeof current_icon)) continue;
        if (!found) found = entry;
        else if (memcmp(&found->cell_width, &entry->cell_width, sizeof entry->cell_width)
                || memcmp(&found->cell_height, &entry->cell_height, sizeof entry->cell_height)
                || memcmp(&found->icon_size, &entry->icon_size, sizeof entry->icon_size)) return 0;
    }
    return found;
}

static inline int ld_home_widget_width(const struct ld_home_baseline *base,
        int64_t span, double current_cell_width, int stretch, int special_phone,
        double *out)
{
    if (!base || !out || !base->valid || span < 1 || span > 10
            || !isfinite(base->cell_width) || base->cell_width <= 0
            || !isfinite(base->cell_height) || base->cell_height <= 0
            || !isfinite(base->icon_size) || base->icon_size <= 0
            || !isfinite(current_cell_width) || current_cell_width <= 0) return 0;
    double width;
    if (span > 1) {
        double eff_cell_width = stretch ? current_cell_width : base->cell_width;
        width = (double)span * eff_cell_width
                - (2.0 * base->cell_width - (base->cell_height + base->icon_size));
        (void)special_phone;
    } else {
        return 0;
    }
    if (!isfinite(width) || width <= 0) return 0;
    *out = width;
    return 1;
}

static uintptr_t g_dev_config_offset = 0;

__attribute__((used, noinline)) static void ld_observe_home_widget(struct ld_dart_context *c)
{
    if (!c) return;
    c->reserved = (uintptr_t)g_orig_home_widget;
    if (!g_hook_pin_ok || g_count_cols <= 0) return;

    /* Recreate the displaced GC-root store before any heap inspection. */
    uintptr_t item_info = c->x[0];
    if (c->x[29] < 8 || !(item_info & 1) || item_info == c->x[22]) return;
    memcpy((void *)(c->x[29] - 8), &item_info, sizeof item_info);

    uintptr_t globals = 0, controller = ld_get_grid_controller(c);
    if (!ld_read_heap(c->x[26] + 0x78, &globals, sizeof globals)
            || !globals || (globals & 1) || !controller) return;
    uintptr_t rx = ld_heap_pointer(c, controller, ld_get_grid_rx_offset());
    uintptr_t grid = rx ? ld_heap_pointer(c, rx, 0x13) : 0;
    if (!grid) return;
    int direction = ld_grid_direction(c, grid);
    if (direction < 0 || direction > 1) return;
    int64_t cols = ld_heap_i64(grid, 0x1b), rows = ld_heap_i64(grid, 0x23);
    if (cols != (direction ? g_grid_cols : g_grid_rows)
            || rows != (direction ? g_grid_rows : g_grid_cols)) return;

    uintptr_t variables = ld_heap_pointer(c, grid, 0x8f);
    if (!variables) return;

    int64_t span = ld_heap_i64(item_info, 0x1f);
    double current_width = 0, current_height = 0, current_icon = 0;
    uintptr_t icon_record = ld_heap_pointer(c, grid, 0x6b);
    if (!icon_record || !ld_read_heap(grid + 0x2b, &current_width, sizeof current_width)
            || !ld_read_heap(grid + 0x33, &current_height, sizeof current_height)
            || !ld_read_heap(icon_record + 0x17, &current_icon, sizeof current_icon)) return;
    const struct ld_home_baseline *base = ld_home_baseline_find(g_home_baseline,
            sizeof g_home_baseline / sizeof g_home_baseline[0], direction,
            current_width, current_height, current_icon);
    if (!base) return;

    /* DeviceConfig static entry marks the phone width branch.
     * 9239: globals + 0x27c0, 8584/8604: globals + 0x2768 */
    uintptr_t device_config = 0, sentinel = 0;
    (void)ld_read_heap(c->x[27] + 0x40, &sentinel, sizeof sentinel);
    if (g_dev_config_offset) {
        uintptr_t cand = 0;
        if (ld_read_heap(globals + g_dev_config_offset, &cand, sizeof cand)
                && (cand & 1) && cand != c->x[22] && cand != sentinel) {
            device_config = cand;
        }
    }
    if (!device_config) {
        static const uintptr_t dev_conf_offsets[] = { 0x27c0u, 0x2768u };
        for (size_t i = 0; i < sizeof(dev_conf_offsets)/sizeof(dev_conf_offsets[0]); i++) {
            uintptr_t cand = 0;
            if (ld_read_heap(globals + dev_conf_offsets[i], &cand, sizeof cand)
                    && (cand & 1) && cand != c->x[22] && cand != sentinel) {
                device_config = cand;
                if (!g_dev_config_offset) g_dev_config_offset = dev_conf_offsets[i];
                break;
            }
        }
    }
    uint8_t config_flags = 0, item_flags = 0;
    int special_phone = device_config
            && ld_read_heap(device_config + 0xe7, &config_flags, 1)
            && ld_read_heap(item_info + 0xfb, &item_flags, 1)
            && (config_flags & 0x10) && (item_flags & 0x10);

    int w_changed = memcmp(&base->cell_width, &current_width, sizeof current_width) != 0
            && fabs(current_width - base->cell_width) >= 1e-6;
    int h_changed = g_home_options.widget_prevent_stretch_enabled
            && memcmp(&base->cell_height, &current_height, sizeof current_height) != 0
            && fabs(current_height - base->cell_height) >= 1e-6;
    if (!w_changed && !h_changed) return;

    double desired = 0;
    if (w_changed && !ld_home_widget_width(base, span, current_width,
                g_home_options.widget_prevent_stretch_enabled ? 0 : 1,
                special_phone, &desired)) return;
    if (!g_home_widget_allocate) return;
    if (w_changed) {
        memcpy(&c->q[0][0], &desired, sizeof desired);
    }
    if (h_changed && current_height > 0.0) {
        double cur_h = 0.0;
        memcpy(&cur_h, &c->q[1][0], sizeof cur_h);
        if (isfinite(cur_h) && cur_h > 0.0) {
            double scaled_h = cur_h * (base->cell_height / current_height);
            memcpy(&c->q[1][0], &scaled_h, sizeof scaled_h);
        }
    }
    c->reserved = g_home_widget_allocate;
    static unsigned widget_hits = 0;
    if (__atomic_fetch_add(&widget_hits, 1u, __ATOMIC_RELAXED) < 5u) {
        ld_blog("home widget: span=%lld width=%.2f (base=%.2f)",
                (long long)span, desired, base->cell_width);
    }
}

static void *g_orig_home_widget_height = NULL;
static void *g_orig_home_widget_content = NULL;
static void *g_orig_home_widget_spec = NULL;
static uintptr_t g_home_widget_height_allocate = 0;
static int g_home_widget_stretch_ready = 0;

static int ld_home_widget_extent(int64_t span, double stride, int positive, double *out)
{
    if (!out || span < 1 || span > 10 || !isfinite(stride) || stride <= 0) return 0;
    double value = (double)span * stride;
    if (!isfinite(value) || (positive && value <= 0)) return 0;
    *out = value;
    return 1;
}

static int ld_home_widget_size(struct ld_dart_context *c, uintptr_t item, double *width, double *height)
{
    if (!c || !g_home_options.widget_prevent_stretch_enabled || !g_home_widget_stretch_ready || !g_hook_pin_ok
            || !(item & 1) || item == c->x[22]) return 0;
    uintptr_t controller = ld_get_grid_controller(c);
    uintptr_t rx = controller ? ld_heap_pointer(c, controller, ld_get_grid_rx_offset()) : 0;
    uintptr_t grid = rx ? ld_heap_pointer(c, rx, 0x13) : 0;
    if (!grid) return 0;
    int direction = ld_grid_direction(c, grid);
    if (direction < 0 || direction > 1) return 0;
    uintptr_t variables = ld_heap_pointer(c, grid, 0x8f);
    uint32_t layout = 0;
    if (!variables || !ld_read_heap(variables + 0x27, &layout, sizeof layout)
            || !ld_is_fullscreen_layout(c, layout)) return 0;
    double cell_width = 0, cell_height = 0;
    if (!ld_read_heap(grid + 0x2b, &cell_width, sizeof cell_width)
            || !ld_read_heap(grid + 0x33, &cell_height, sizeof cell_height)) return 0;
    int64_t span_x = ld_heap_i64(item, 0x1f), span_y = ld_heap_i64(item, 0x27);
    if (!ld_home_widget_extent(span_x, cell_width, 1, width)
            || !ld_home_widget_extent(span_y, cell_height, 1, height)) return 0;
    uintptr_t globals = 0, device = 0;
    uint8_t device_flags = 0, item_flags = 0;
    uintptr_t dev_off = g_dev_config_offset ? g_dev_config_offset : 0x2768u;
    if (!ld_read_heap(c->x[26] + 0x78, &globals, sizeof globals) || !globals
            || !ld_read_heap(globals + dev_off, &device, sizeof device)
            || !(device & 1) || device == c->x[22]
            || !ld_read_heap(device + 0xe7, &device_flags, 1)
            || !ld_read_heap(item + 0xfb, &item_flags, 1)
            || !(device_flags & item_flags & 0x10)) return 0;
    uintptr_t icon = ld_heap_pointer(c, grid, 0x6b);
    uintptr_t title = ld_heap_pointer(c, grid, 0x6f);
    double icon_size = 0, title_height = 0;
    if (!icon || !title || !ld_read_heap(icon + 0x17, &icon_size, sizeof icon_size)
            || !ld_read_heap(title + 0x27, &title_height, sizeof title_height)) return 0;
    const struct ld_home_baseline *base = ld_home_baseline_find(g_home_baseline,
        sizeof g_home_baseline / sizeof *g_home_baseline, direction, cell_width, cell_height, icon_size);
    if (!base) return 0;
    double natural_width = base->cell_width * ((double)span_x - 2.0)
        + base->cell_height + base->icon_size;
    double natural_height = (double)span_y * base->cell_height - title_height;
    if (!isfinite(natural_width) || !isfinite(natural_height)
            || natural_width <= 0 || natural_height <= 0) return 0;
    double scale = fmin(1.0, fmin(*width / natural_width, *height / natural_height));
    *width = natural_width * scale;
    *height = natural_height * scale;
    return 1;
}

__attribute__((used, noinline)) static void ld_observe_home_widget_height(struct ld_dart_context *c)
{
    if (!c) return;
    c->reserved = (uintptr_t)g_orig_home_widget_height;
    uintptr_t item = c->x[0];
    if (c->x[29] < 8 || !(item & 1) || item == c->x[22]) return;
    memcpy((void *)(c->x[29] - 8), &item, sizeof item);
    double width = 0, height = 0;
    if (!g_home_widget_height_allocate || !ld_home_widget_size(c, item, &width, &height)) return;
    memcpy(c->q[1], &height, sizeof height);
    c->reserved = g_home_widget_height_allocate;
}

__attribute__((used)) static void ld_observe_home_widget_content(struct ld_dart_context *c)
{
    uintptr_t controller = 0;
    if (!c || c->x[29] < 0x38 || !ld_read_heap(c->x[29] - 8, &controller, sizeof controller)) return;
    uintptr_t item = ld_heap_pointer(c, controller, 0x9b);
    if (!g_home_options.widget_prevent_stretch_enabled ||
            (ld_heap_i64(item, 0x1f) == 1 && ld_heap_i64(item, 0x27) == 1)) return;
    double width = 0, height = 0;
    if (!ld_home_widget_size(c, item, &width, &height)) return;
    memcpy((void *)(c->x[29] - 0x38), &width, sizeof width);
    memcpy((void *)(c->x[29] - 0x30), &height, sizeof height);
    static unsigned logged;
    if (__atomic_fetch_add(&logged, 1u, __ATOMIC_RELAXED) < 8u)
        ld_blog("widget geometry prevention=%d id=%lld size=%.2fx%.2f",
            g_home_options.widget_prevent_stretch_enabled,
            (long long)ld_heap_i64(item, 0xf), width, height);
}

__attribute__((used)) static void ld_observe_home_widget_spec(struct ld_dart_context *c)
{
    if (!c || !g_home_options.widget_prevent_stretch_enabled || !g_home_widget_stretch_ready
            || !g_hook_pin_ok || c->x[29] < 0x38) return;
    uintptr_t arguments = c->x[2], grid = c->x[0];
    uint32_t span_x = ld_heap_u32(arguments, 0xf), span_y = ld_heap_u32(arguments, 0x13);
    if ((span_x & 1) || (span_y & 1) || span_x < 2 || span_y < 2 || span_x > 20 || span_y > 20) return;
    int direction = ld_grid_direction(c, grid);
    uintptr_t variables = ld_heap_pointer(c, grid, 0x8f);
    uintptr_t icon = ld_heap_pointer(c, grid, 0x6b);
    uint32_t layout = variables ? ld_heap_u32(variables, 0x27) : 0;
    if (direction < 0 || direction > 1 || !icon || !ld_is_fullscreen_layout(c, layout)) return;
    double cell_width = 0, cell_height = 0, icon_size = 0;
    if (!ld_read_heap(grid + 0x2b, &cell_width, 8) || !ld_read_heap(grid + 0x33, &cell_height, 8)
            || !ld_read_heap(icon + 0x17, &icon_size, 8)
            || !isfinite(cell_width) || !isfinite(cell_height) || cell_width <= 0 || cell_height <= 0) return;
    const struct ld_home_baseline *base = ld_home_baseline_find(g_home_baseline,
        sizeof g_home_baseline / sizeof *g_home_baseline, direction, cell_width, cell_height, icon_size);
    double native_width = base ? base->cell_width : fmin(cell_width, cell_height);
    double native_height = base ? base->cell_height : native_width;
    memcpy((void *)(c->x[29] - 0x38), &native_width, 8);
    memcpy((void *)(c->x[29] - 0x30), &native_height, 8);
}

#ifndef LD_HOME_WIDGET_TEST_NO_BRIDGE
#include "home_widget_bridge.h"
LD_DART_ROUTED_OBSERVER(repl_home_widget_height, ld_observe_home_widget_height)
LD_DART_OBSERVER(repl_home_widget_content, ld_observe_home_widget_content, g_orig_home_widget_content)
LD_DART_OBSERVER(repl_home_widget_spec, ld_observe_home_widget_spec, g_orig_home_widget_spec)
LD_DART_ROUTED_OBSERVER(repl_home_widget, ld_observe_home_widget)
#endif
#endif
