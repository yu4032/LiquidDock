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
    if (stretch && span > 1) {
        width = (double)span * current_cell_width;
    } else if (special_phone) {
        width = base->cell_width * ((double)span - 2.0)
                + base->cell_height + base->icon_size;
    } else {
        return 0;
    }
    if (!isfinite(width) || width <= 0) return 0;
    *out = width;
    return 1;
}

__attribute__((used, noinline)) static void ld_observe_home_widget(struct ld_dart_context *c)
{
    if (!c) return;
    c->reserved = (uintptr_t)g_orig_home_widget;
    if (!g_hook_pin_ok || g_count_cols <= 0) return;

    /* Recreate the displaced GC-root store before any heap inspection. */
    uintptr_t item_info = c->x[0];
    if (c->x[29] < 8 || !(item_info & 1) || item_info == c->x[22]) return;
    memcpy((void *)(c->x[29] - 8), &item_info, sizeof item_info);

    uintptr_t globals = 0, controller = 0, sentinel = 0;
    if (!ld_read_heap(c->x[26] + 0x78, &globals, sizeof globals)
            || !globals || (globals & 1)
            || !ld_read_heap(globals + 0x27d8, &controller, sizeof controller)
            || !ld_read_heap(c->x[27] + 0x40, &sentinel, sizeof sentinel)
            || !(controller & 1) || controller == c->x[22] || controller == sentinel) return;
    uintptr_t rx = ld_heap_pointer(c, controller, 0x2b);
    uintptr_t grid = ld_heap_pointer(c, rx, 0x13);
    if (!grid) return;
    int direction = ld_grid_direction(c, grid);
    if (direction < 0 || direction > 1) return;
    int64_t cols = ld_heap_i64(grid, 0x1b), rows = ld_heap_i64(grid, 0x23);
    if (cols != (direction ? g_grid_rows : g_grid_cols)
            || rows != (direction ? g_grid_cols : g_grid_rows)) return;

    uintptr_t variables = ld_heap_pointer(c, grid, 0x8f), fullscreen = 0;
    uint32_t layout = 0;
    if (!variables || !ld_read_heap(variables + 0x27, &layout, 4)
            || !ld_read_heap(c->x[27] + 0xef20, &fullscreen, 8)
            || !(layout & 1) || !(fullscreen & 1) || layout != (uint32_t)fullscreen) return;

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

    /* DeviceConfig static entry at globals+0x2768 marks the phone width branch. */
    uintptr_t device_config = 0;
    uint8_t config_flags = 0, item_flags = 0;
    int special_phone = ld_read_heap(globals + 0x2768, &device_config, sizeof device_config)
            && (device_config & 1) && device_config != c->x[22] && device_config != sentinel
            && ld_read_heap(device_config + 0xe7, &config_flags, 1)
            && ld_read_heap(item_info + 0xfb, &item_flags, 1)
            && (config_flags & 0x10) && (item_flags & 0x10);

    if((!g_home_options.widget_horizontal_stretch || span==1) && special_phone
            && !memcmp(&base->cell_width,&current_width,sizeof current_width)
            && !memcmp(&base->cell_height,&current_height,sizeof current_height)
            && !memcmp(&base->icon_size,&current_icon,sizeof current_icon)) return;

    double desired = 0;
    if (!ld_home_widget_width(base, span, current_width,
                g_home_options.widget_horizontal_stretch, special_phone, &desired)) return;
    if (!g_home_widget_allocate) return;
    memcpy(&c->q[0][0], &desired, sizeof desired);
    c->reserved = g_home_widget_allocate;
}

#ifndef LD_HOME_WIDGET_TEST_NO_BRIDGE
#include "home_widget_bridge.h"
LD_DART_ROUTED_OBSERVER(repl_home_widget, ld_observe_home_widget)
#endif
#endif
