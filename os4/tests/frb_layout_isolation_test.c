#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "../native/dart_heap.h"
#include "orientation_guard_mock.h"
#undef LD_DART_OBSERVER
#define LD_DART_OBSERVER(...)

#define LD_LAYOUT_FILE "/tmp/ld-frb-normal-fixture.bin"
#define LD_WS_LAYOUT_FILE "/tmp/ld-frb-ws-apps-fixture.bin"
#define LD_WS_DESK_LAYOUT_FILE "/tmp/ld-frb-ws-desk-fixture.bin"

static int g_grid_cols = 4, g_grid_rows = 8, g_layout_hooks_ready = 1;
static volatile int g_ws_is_laptop_mode = 0;
int g_runtime_columns, g_runtime_rows, g_runtime_direction;
static int g_initial_restore, g_initial_columns, g_initial_rows, g_initial_direction;
static void ld_blog(const char *fmt, ...) { (void)fmt; }

#include "../native/layout_load.h"
#include "../native/layout_save.h"
#include "../native/frb_boundary_adapter.h"

struct mock_frb_item {
    uint64_t id_tag;
    int64_t id;
    uint64_t cont_tag;
    int64_t cont;
    uint32_t sx_tag;
    int32_t sx;
    uint32_t sy_tag;
    int32_t sy;
    uint32_t scr_tag;
    int32_t scr;
    uint32_t x_tag;
    int32_t x;
    uint32_t y_tag;
    int32_t y;
};

static struct mock_frb_item make_item(int64_t id, int64_t cont, int32_t scr, int32_t x, int32_t y) {
    struct mock_frb_item it = {
        .id_tag = 1u, .id = id,
        .cont_tag = 1u, .cont = cont,
        .sx_tag = 1u, .sx = 1,
        .sy_tag = 1u, .sy = 1,
        .scr_tag = 1u, .scr = scr,
        .x_tag = 1u, .x = x,
        .y_tag = 1u, .y = y,
    };
    return it;
}

static void *g_mock_decode_vec = NULL;
static int g_mock_encode_calls = 0;

static void mock_vec_encode(void *vec, void *ser) {
    (void)vec;
    (void)ser;
    g_mock_encode_calls++;
}

static void mock_vec_decode(void *out_vec, void *deser) {
    (void)out_vec;
    (void)deser;
}

static void mock_primary_adapter(int32_t func_id, int64_t port, uint8_t *ptr,
                                 int32_t rust_vec_len, int32_t data_len) {
    (void)port;
    (void)ptr;
    (void)rust_vec_len;
    (void)data_len;
    if (func_id == g_frb_ids.update_item_batch && g_mock_decode_vec) {
        repl_frb_vec_item_decode(g_mock_decode_vec, NULL);
    }
}

int main(void) {
    unlink(LD_LAYOUT_FILE);
    unlink(LD_WS_LAYOUT_FILE);
    unlink(LD_WS_DESK_LAYOUT_FILE);

    g_frb_ids = (ld_frb_launcher_ids_t){
        .start_load = 855,
        .bind_items_sub = 848,
        .bind_screens = 850,
        .update_item_batch = 858,
        .update_item = 857,
        .move_item = 834,
    };
    g_frb_item_layout = (ld_frb_item_layout_t){
        .stride = sizeof(struct mock_frb_item),
        .id_off = offsetof(struct mock_frb_item, id_tag),
        .container_off = offsetof(struct mock_frb_item, cont_tag),
        .sx_off = offsetof(struct mock_frb_item, sx_tag),
        .sy_off = offsetof(struct mock_frb_item, sy_tag),
        .screen_off = offsetof(struct mock_frb_item, scr_tag),
        .x_off = offsetof(struct mock_frb_item, x_tag),
        .y_off = offsetof(struct mock_frb_item, y_tag),
        .valid = true,
    };
    g_orig_frb_primary_adapter = mock_primary_adapter;
    g_orig_frb_vec_item_encode = mock_vec_encode;
    g_orig_frb_vec_item_decode = mock_vec_decode;

    /* Seed stale screen ID (99) into remembered files to prove FRB never overwrites screen IDs */
    struct ld_layout_memory normal_mem = {
        .columns = 4, .rows = 8, .active_direction = 0, .count = {2, 2},
        .items = {
            {{10, 99, 0, 1, 1, 1}, {11, 99, 1, 7, 1, 1}},
            {{10, 99, 4, 2, 1, 1}, {11, 99, 7, 3, 1, 1}},
        },
    };
    assert(ld_layout_save(LD_LAYOUT_FILE, &normal_mem));

    struct ld_layout_memory ws_desk_mem = {
        .columns = 4, .rows = 8, .active_direction = 0, .count = {2, 2},
        .items = {
            {{200, 99, 0, 6, 1, 1}, {201, 99, 3, 7, 1, 1}},
            {{200, 99, 5, 1, 1, 1}, {201, 99, 6, 2, 1, 1}},
        },
    };
    assert(ld_layout_save(LD_WS_DESK_LAYOUT_FILE, &ws_desk_mem));

    struct ld_layout_memory ws_apps_mem = {
        .columns = 4, .rows = 8, .active_direction = 0, .count = {2, 2},
        .items = {
            {{300, 99, 0, 7, 1, 1}, {301, 99, 2, 5, 1, 1}},
            {{300, 99, 7, 2, 1, 1}, {301, 99, 6, 3, 1, 1}},
        },
    };
    assert(ld_layout_save(LD_WS_LAYOUT_FILE, &ws_apps_mem));

    /* 1. Boot in Normal Mode (Portrait 4x8) */
    int32_t start_port[4] = {4, 8, 4, 6};
    repl_frb_primary_adapter(g_frb_ids.start_load, 0, (uint8_t *)start_port, 16, 16);
    struct mock_frb_item norm_items[2] = {
        make_item(10, -100, 1, 7, 3), /* OOB for 4x8 -> must replan without changing screen=1 */
        make_item(11, -100, 2, 6, 2),
    };
    uint64_t norm_vec[3] = {0, (uint64_t)(uintptr_t)norm_items, 2};
    repl_frb_vec_item_encode(norm_vec, NULL);
    assert(g_mock_encode_calls == 1);
    assert(__atomic_load_n(&g_load_prepared, __ATOMIC_ACQUIRE) == 1);
    assert(norm_items[0].scr == 1 && norm_items[0].x < 4 && norm_items[0].y < 8);
    assert(norm_items[1].scr == 2 && norm_items[1].x < 4 && norm_items[1].y < 8);
    assert(ld_layout_load(LD_LAYOUT_FILE, &normal_mem));
    struct ld_layout_memory norm_snapshot = normal_mem;

    /* 2. Switch to Workstation Mode (Landscape 8x4): start_load must reset g_load_prepared=0! */
    ld_test_set_framework_orientation(0);
    g_runtime_columns = 8; g_runtime_rows = 4; g_runtime_direction = 1;
    int32_t start_land[4] = {8, 4, 4, 6};
    repl_frb_primary_adapter(g_frb_ids.start_load, 0, (uint8_t *)start_land, 16, 16);
    assert(__atomic_load_n(&g_load_prepared, __ATOMIC_ACQUIRE) == 0);
    assert(__atomic_load_n(&g_initial_restore, __ATOMIC_ACQUIRE) == 0);

    struct mock_frb_item ws_load_items[4] = {
        make_item(200, -100, 5, 0, 7), /* OOB y=7 in 8x4! Must replan to 8x4, screen stays 5 */
        make_item(201, -100, 5, 1, 6),
        make_item(300, -104, 1, 0, 7), /* OOB y=7 in 8x4! Must replan to 8x4, screen stays 1 */
        make_item(301, -104, 2, 2, 5),
    };
    uint64_t ws_vec[3] = {0, (uint64_t)(uintptr_t)ws_load_items, 4};
    repl_frb_vec_item_encode(ws_vec, NULL);
    assert(__atomic_load_n(&g_load_prepared, __ATOMIC_ACQUIRE) == 1);
    assert(__atomic_load_n(&g_loaded_ws_known, __ATOMIC_ACQUIRE) == 1);
    assert(__atomic_load_n(&g_loaded_is_ws, __ATOMIC_ACQUIRE) == 1);
    assert(__atomic_load_n(&g_initial_restore_mask, __ATOMIC_ACQUIRE) == 1);
    assert(ws_load_items[0].scr == 5 && ws_load_items[0].x < 8 && ws_load_items[0].y < 4);
    assert(ws_load_items[1].scr == 5 && ws_load_items[1].x < 8 && ws_load_items[1].y < 4);
    assert(ws_load_items[2].y == 7);
    assert(ws_load_items[3].y == 5);
    /* Normal Home must remain untouched */
    assert(ld_layout_load(LD_LAYOUT_FILE, &normal_mem));
    assert(memcmp(&norm_snapshot, &normal_mem, sizeof normal_mem) == 0);

    /* 3. Secondary update_item_batch aligns -104 and moves item 201 into folder (cont=172):
     *    - Without t_frb_in_update_batch, sse_decode is ignored.
     *    - Via repl_frb_primary_adapter(update_item_batch), BOTH items[1] and items[0] sync atomically! */
    struct mock_frb_item batch_items[3] = {
        make_item(201, 172, -1, 0, 0),  /* moved into folder -> delete from ws_desk & ws_apps */
        make_item(300, -104, 1, 0, 0),  /* aligned to (0,0) */
        make_item(301, -104, 2, 1, 0),  /* aligned to (1,0) */
    };
    uint64_t dec_vec[3] = {0, (uint64_t)(uintptr_t)batch_items, 3};
    g_frb_sse_decode_synced = false;
    repl_frb_vec_item_decode(dec_vec, NULL);
    assert(!g_frb_sse_decode_synced);

    g_mock_decode_vec = dec_vec;
    uint32_t hdr = 3;
    repl_frb_primary_adapter(g_frb_ids.update_item_batch, 0, (uint8_t *)&hdr, 4, 4);
    g_mock_decode_vec = NULL;
    assert(g_frb_sse_decode_synced);

    (void)ws_apps_mem;

    assert(ld_layout_load(LD_WS_DESK_LAYOUT_FILE, &ws_desk_mem));
    assert(ws_desk_mem.count[1] == 1 && ws_desk_mem.items[1][0].id == 200);
    assert(ws_desk_mem.count[0] == 1 && ws_desk_mem.items[0][0].id == 200);

    /* Normal Home STILL untouched */
    assert(ld_layout_load(LD_LAYOUT_FILE, &normal_mem));
    assert(memcmp(&norm_snapshot, &normal_mem, sizeof normal_mem) == 0);

    /* 4. Switch Workstation -> Normal even when stale g_ws_is_laptop_mode == 1 */
    g_ws_is_laptop_mode = 1;
    ld_test_set_framework_orientation(1);
    g_runtime_columns = 4; g_runtime_rows = 8; g_runtime_direction = 0;
    repl_frb_primary_adapter(g_frb_ids.start_load, 0, (uint8_t *)start_port, 16, 16);
    struct mock_frb_item norm_reload[2] = {
        make_item(10, -100, 1, 0, 2),
        make_item(11, -100, 2, 1, 5),
    };
    uint64_t norm_reload_vec[3] = {0, (uint64_t)(uintptr_t)norm_reload, 2};
    repl_frb_vec_item_encode(norm_reload_vec, NULL);
    assert(__atomic_load_n(&g_loaded_ws_known, __ATOMIC_ACQUIRE) == 1);
    assert(__atomic_load_n(&g_loaded_is_ws, __ATOMIC_ACQUIRE) == 0);
    assert(ld_is_workstation_layout_active() == 0);
    assert(norm_reload[0].x == norm_snapshot.items[0][0].x &&
           norm_reload[0].y == norm_snapshot.items[0][0].y);

    /* And saving in Normal mode writes LD_LAYOUT_FILE without touching WS files */
    struct ld_layout_memory ws_desk_before = ws_desk_mem;
    struct mock_frb_item norm_moved[2] = {
        make_item(10, -100, 1, 0, 2),
        make_item(11, -100, 2, 1, 5),
    };
    uint64_t norm_moved_vec[3] = {0, (uint64_t)(uintptr_t)norm_moved, 2};
    g_mock_decode_vec = norm_moved_vec;
    uint32_t norm_hdr = 2;
    repl_frb_primary_adapter(g_frb_ids.update_item_batch, 0, (uint8_t *)&norm_hdr, 4, 4);
    g_mock_decode_vec = NULL;
    assert(ld_layout_load(LD_LAYOUT_FILE, &normal_mem));
    assert(normal_mem.items[0][0].x == 0 && normal_mem.items[0][0].y == 2);
    assert(normal_mem.items[0][1].x == 1 && normal_mem.items[0][1].y == 5);
    assert(ld_layout_load(LD_WS_DESK_LAYOUT_FILE, &ws_desk_mem));
    assert(memcmp(&ws_desk_before, &ws_desk_mem, sizeof ws_desk_before) == 0);

    (void)ld_frb_adapter_ready;
    (void)ld_frb_adapter_init;
    (void)ld_verify_rust_orientation_fn;
    (void)ld_resolve_rust_symbol;
    (void)ld_resolve_lib;
    (void)ld_launcher_apk_substr;
    unlink(LD_LAYOUT_FILE);
    unlink(LD_WS_LAYOUT_FILE);
    unlink(LD_WS_DESK_LAYOUT_FILE);
    puts("PASS FRB layout reset, screen preservation, and dual-orientation cross-mode isolation");
    return 0;
}
