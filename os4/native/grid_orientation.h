#ifndef LD_GRID_ORIENTATION_H
#define LD_GRID_ORIENTATION_H
#include "dart_heap.h"

static uintptr_t g_grid_ctrl_offset = 0;
static uintptr_t g_grid_rx_offset = 0;

static inline uintptr_t ld_get_grid_rx_offset(void) {
    return g_grid_rx_offset ? g_grid_rx_offset : 0x2bu;
}

static inline int ld_grid_direction(struct ld_dart_context *c,uintptr_t grid) {
    uintptr_t variables=ld_heap_pointer(c,grid,0x8f),value=ld_heap_pointer(c,variables,0x23);
    int64_t raw=ld_heap_i64(value,7);
    return value && (raw==0 || raw==1)?(int)raw:-1;
}

static inline int ld_is_fullscreen_layout(struct ld_dart_context *c, uint32_t layout) {
    if (!c || !(layout & 1) || layout == (uint32_t)c->x[22]) return 0;
    uintptr_t full = (uintptr_t)layout + (c->x[28] << 32);
    return ld_heap_i64(full, 7) == 0;
}

static inline uintptr_t ld_get_grid_controller(struct ld_dart_context *c) {
    if (!c) return 0;
    uintptr_t fields = 0, sentinel = 0;
    if (!ld_read_heap(c->x[26] + 0x78, &fields, 8) || !fields || (fields & 1)) return 0;
    (void)ld_read_heap(c->x[27] + 0x40, &sentinel, 8);

    if (g_grid_ctrl_offset) {
        uintptr_t cand = 0;
        if (ld_read_heap(fields + g_grid_ctrl_offset, &cand, 8) && (cand & 1) && cand != c->x[22] && cand != sentinel) {
            uintptr_t rx_test = ld_heap_pointer(c, cand, ld_get_grid_rx_offset());
            if (rx_test && (rx_test & 1) && rx_test != c->x[22]) return cand;
        }
    }

    static const uintptr_t ctrl_offsets[] = { 0x2828u, 0x2838u, 0x27c8u, 0x27d8u };
    for (size_t i = 0; i < sizeof(ctrl_offsets)/sizeof(ctrl_offsets[0]); i++) {
        uintptr_t cand = 0;
        if (ld_read_heap(fields + ctrl_offsets[i], &cand, 8) && (cand & 1) && cand != c->x[22] && cand != sentinel) {
            uintptr_t rx_test = ld_heap_pointer(c, cand, ld_get_grid_rx_offset());
            if (rx_test && (rx_test & 1) && rx_test != c->x[22]) {
                if (!g_grid_ctrl_offset) g_grid_ctrl_offset = ctrl_offsets[i];
                return cand;
            }
        }
    }
    return 0;
}

#endif
