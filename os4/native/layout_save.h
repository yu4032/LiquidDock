#ifndef LD_LAYOUT_SAVE_H
#define LD_LAYOUT_SAVE_H
#include "layout_state.h"
#include "layout_changes.h"
static void *g_orig_save_batch __attribute__((used));
static void *g_orig_save_single __attribute__((used));
static void *g_orig_delete_item __attribute__((used));

static inline int ld_save_dir_hint(void) {
    return g_runtime_columns ? g_runtime_direction : (g_load_columns ? g_load_direction : -1);
}

static void ld_capture_saved_items_slot(int slot, const struct ld_layout_item *items, size_t count, int dir_hint) {
    if (slot < 0 || slot >= LD_SLOT_COUNT || !g_layout_hooks_ready ||
        !__atomic_load_n(&g_load_prepared, __ATOMIC_ACQUIRE) ||
        !count || g_grid_cols <= 0 || g_grid_rows <= 0) return;
    const char *path = ld_layout_slot_path(slot);
    pthread_mutex_lock(&g_layout_io_lock);
    if (!g_pending_layout_valid[slot] ||
        g_pending_layout_mem[slot].columns != g_grid_cols ||
        g_pending_layout_mem[slot].rows != g_grid_rows) {
        if (!ld_layout_load(path, &g_pending_layout_mem[slot]) ||
            g_pending_layout_mem[slot].columns != g_grid_cols ||
            g_pending_layout_mem[slot].rows != g_grid_rows) {
            if (dir_hint >= 0 && dir_hint <= 1 && slot != LD_SLOT_NORMAL_HOME) {
                memset(&g_pending_layout_mem[slot], 0, sizeof g_pending_layout_mem[slot]);
                g_pending_layout_mem[slot].columns = g_grid_cols;
                g_pending_layout_mem[slot].rows = g_grid_rows;
                g_pending_layout_mem[slot].active_direction = dir_hint;
            } else {
                g_pending_layout_valid[slot] = 0;
                pthread_mutex_unlock(&g_layout_io_lock);
                return;
            }
        }
    }
    if (dir_hint >= 0 && dir_hint <= 1 && g_pending_layout_mem[slot].active_direction < 0) {
        g_pending_layout_mem[slot].active_direction = dir_hint;
    }
    if (!ld_layout_apply_changes(&g_pending_layout_mem[slot], items, count)) {
        g_pending_layout_valid[slot] = 0;
        ld_blog("layout save ignored %zu changes slot=%d (invalid item)", count, slot);
        pthread_mutex_unlock(&g_layout_io_lock);
        return;
    }
    if (ld_layout_finalize_changes(&g_pending_layout_mem[slot]) &&
        ld_layout_save(path, &g_pending_layout_mem[slot])) {
        g_pending_layout_valid[slot] = 0;
        int d = g_pending_layout_mem[slot].active_direction;
        if (slot == LD_SLOT_NORMAL_HOME && d >= 0 && d <= 1) {
            ld_write_4x2_widget_state(g_pending_layout_mem[slot].items[d],
                                      g_pending_layout_mem[slot].count[d]);
        }
        ld_blog("layout save captured %zu changes slot=%d direction=%d", count, slot, d);
    } else {
        g_pending_layout_valid[slot] = 1;
        int d = g_pending_layout_mem[slot].active_direction;
        int cols = d ? g_pending_layout_mem[slot].rows : g_pending_layout_mem[slot].columns;
        int rows = d ? g_pending_layout_mem[slot].columns : g_pending_layout_mem[slot].rows;
        size_t cnt = (d >= 0 && d <= 1) ? g_pending_layout_mem[slot].count[d] : 0;
        ld_blog("layout save buffered %zu changes slot=%d direction=%d first=(id=%lld scr=%lld (%d,%d) %dx%d)",
                count, slot, d, (long long)items[0].id, (long long)items[0].screen,
                items[0].x, items[0].y, items[0].sx, items[0].sy);
        if (d >= 0 && d <= 1) {
            for (size_t i = 0; i < cnt; i++) {
                const struct ld_layout_item *a = &g_pending_layout_mem[slot].items[d][i];
                if (!ld_layout_bounds(a, cols, rows)) {
                    ld_blog("layout conflict: slot=%d OOB id=%lld scr=%lld (%d,%d) %dx%d in %dx%d",
                            slot, (long long)a->id, (long long)a->screen, a->x, a->y, a->sx, a->sy, cols, rows);
                    break;
                }
                int found = 0;
                for (size_t j = 0; j < i; j++) {
                    const struct ld_layout_item *b = &g_pending_layout_mem[slot].items[d][j];
                    if (a->id == b->id || ld_layout_overlap(a, b)) {
                        ld_blog("layout conflict: slot=%d id=%lld scr=%lld (%d,%d) %dx%d vs id=%lld scr=%lld (%d,%d) %dx%d",
                                slot, (long long)a->id, (long long)a->screen, a->x, a->y, a->sx, a->sy,
                                (long long)b->id, (long long)b->screen, b->x, b->y, b->sx, b->sy);
                        found = 1;
                        break;
                    }
                }
                if (found) break;
            }
        }
    }
    pthread_mutex_unlock(&g_layout_io_lock);
}

static inline void ld_capture_saved_items(const struct ld_layout_item *items, size_t count) {
    ld_capture_saved_items_slot(LD_SLOT_NORMAL_HOME, items, count, -1);
}

__attribute__((used)) static void ld_save_batch(struct ld_dart_context *c) {
    uintptr_t list = c->x[2];
    unsigned cid = ld_heap_u32(list, (size_t)-1) >> 12 & 0xfffff;
    if (cid != 0x5c && cid != 0x59 && cid != 0x5a) {
        ld_blog("save batch skipped list CID=%x", cid);
        return;
    }
    int n = (int32_t)ld_heap_u32(list, 0xb) / 2;
    uintptr_t data = cid == 0x5c ? ld_heap_pointer(c, list, 0xf) : list;
    if (!data || n <= 0 || n > LD_LAYOUT_MAX_ITEMS) return;
    struct ld_layout_item *raw = calloc((size_t)n, sizeof *raw);
    int64_t *conts = calloc((size_t)n, sizeof *conts);
    struct ld_layout_item *home_items = calloc((size_t)n, sizeof *home_items);
    struct ld_layout_item *ws_items = calloc((size_t)n, sizeof *ws_items);
    if (!raw || !conts || !home_items || !ws_items) goto out;

    size_t parsed = 0;
    int has_104 = 0;
    for (int i = 0; i < n; i++) {
        if (ld_read_dto_item_ex(c, ld_heap_pointer(c, data, 0xf + 4 * i), &raw[parsed], &conts[parsed])) {
            if (conts[parsed] == -104 && raw[parsed].screen >= 0) has_104 = 1;
            parsed++;
        }
    }
    int is_ws = has_104 || ld_is_workstation_layout_active();
    int home_slot = is_ws ? LD_SLOT_WS_DESK : LD_SLOT_NORMAL_HOME;
    size_t home_count = 0, ws_count = 0;
    for (size_t i = 0; i < parsed; i++) {
        if (conts[i] == -100 && raw[i].screen >= 0) {
            home_items[home_count++] = raw[i];
        } else if (conts[i] == -104 && raw[i].screen >= 0) {
            (void)is_ws;
        } else {
            struct ld_layout_item del = {.id = raw[i].id, .screen = -1};
            home_items[home_count++] = del;
            if (is_ws) ws_items[ws_count++] = del;
        }
    }
    int dir_hint = ld_save_dir_hint();
    if (home_count > 0) ld_capture_saved_items_slot(home_slot, home_items, home_count, dir_hint);
    if (ws_count > 0) ld_capture_saved_items_slot(LD_SLOT_WS_ALLAPPS, ws_items, ws_count, dir_hint);
out:
    free(raw);
    free(conts);
    free(home_items);
    free(ws_items);
}

__attribute__((used)) static void ld_save_single(struct ld_dart_context *c) {
    struct ld_layout_item item;
    int64_t cont = 0;
    if (!ld_read_dto_item_ex(c, c->x[1], &item, &cont) &&
        !ld_read_dto_item_ex(c, c->x[2], &item, &cont)) return;
    int dir_hint = ld_save_dir_hint();
    int is_ws = (cont == -104) || ld_is_workstation_layout_active();
    if (cont == -104 && item.screen >= 0) {
        return;
    } else if (cont == -100 && item.screen >= 0) {
        ld_capture_saved_items_slot(is_ws ? LD_SLOT_WS_DESK : LD_SLOT_NORMAL_HOME, &item, 1, dir_hint);
    } else {
        struct ld_layout_item del = {.id = item.id, .screen = -1};
        if (is_ws) {
            ld_capture_saved_items_slot(LD_SLOT_WS_DESK, &del, 1, dir_hint);
            ld_capture_saved_items_slot(LD_SLOT_WS_ALLAPPS, &del, 1, dir_hint);
        } else {
            ld_capture_saved_items_slot(LD_SLOT_NORMAL_HOME, &del, 1, dir_hint);
        }
    }
}

__attribute__((used)) static void ld_delete_item(struct ld_dart_context *c) {
    int64_t id = INT64_MIN;
    if (!(c->x[2] & 1)) id = (int64_t)c->x[2] / 2;
    else if ((ld_heap_u32(c->x[2], (size_t)-1) >> 12 & 0xfffff) == 0x3d) id = ld_heap_i64(c->x[2], 7);
    if (id >= 0) {
        struct ld_layout_item item = {.id = id, .screen = -1};
        int dir_hint = ld_save_dir_hint();
        if (ld_is_workstation_layout_active()) {
            ld_capture_saved_items_slot(LD_SLOT_WS_DESK, &item, 1, dir_hint);
            ld_capture_saved_items_slot(LD_SLOT_WS_ALLAPPS, &item, 1, dir_hint);
        } else {
            ld_capture_saved_items_slot(LD_SLOT_NORMAL_HOME, &item, 1, dir_hint);
        }
    }
}
LD_DART_OBSERVER(repl_save_batch, ld_save_batch, g_orig_save_batch)
LD_DART_OBSERVER(repl_save_single, ld_save_single, g_orig_save_single)
LD_DART_OBSERVER(repl_delete_item, ld_delete_item, g_orig_delete_item)
#endif
