#ifndef LD_LAYOUT_RUNTIME_H
#define LD_LAYOUT_RUNTIME_H
#include <sys/uio.h>
#include <sys/syscall.h>
#include "dart_observer.h"
#include "layout_state.h"
#include "dart_heap.h"
#include "grid_orientation.h"
#include "page_fields.h"
static int g_restore_ready;
static int g_initial_restore,g_initial_columns,g_initial_rows,g_initial_direction;
static int g_rotation_direction=-1;
static int g_runtime_columns,g_runtime_rows,g_runtime_direction;
static int g_layout_mid_ready;
static int g_layout_hooks_ready;
static uintptr_t g_process_resume;
static void *g_orig_process_mid __attribute__((used));
static void *g_process_mid_next __attribute__((used));

static void *g_orig_process_screen __attribute__((used));

/* Only rewrite item coordinates after proving every target object is a cell of
 * the same class: a page can briefly expose a non-cell object (matrix/list)
 * while it is rebuilt, and an 8-byte x/y store would clobber its header. */
static unsigned ld_layout_object_cid(uintptr_t object)
{
    uint64_t header = 0;
    if (!object || !ld_read_heap(object - 1, &header, sizeof header)) return 0;
    return (unsigned)((header >> 12) & 0xfffffu);
}

/* Keep each page's visible reading order, even when its Dart list is unordered.
 * Each item's original array index is retained for the verified heap write. */
static int ld_layout_reflow_allapps(const struct ld_layout_item *source, size_t count,
                                    int cols, int rows, struct ld_layout_item *target)
{
    if (cols < 2 || cols > LD_LAYOUT_MAX_DIM || rows < 2 || rows > LD_LAYOUT_MAX_DIM)
        return 0;
    unsigned char placed[LD_LAYOUT_MAX_ITEMS] = {0};
    int next_slot = 0;
    int64_t screen = -1;
    for (size_t n = 0; n < count; n++) {
        size_t i = count;
        for (size_t j = 0; j < count; j++) {
            if (placed[j]) continue;
            if (i == count || source[j].screen < source[i].screen ||
                (source[j].screen == source[i].screen &&
                 (source[j].y < source[i].y ||
                  (source[j].y == source[i].y && source[j].x < source[i].x)))) {
                i = j;
            }
        }
        if (screen != source[i].screen) {
            screen = source[i].screen;
            next_slot = 0;
        }
        target[i] = source[i];
        for (; next_slot < cols * rows; next_slot++) {
            target[i].x = next_slot % cols;
            target[i].y = next_slot / cols;
            if (ld_layout_bounds(&target[i], cols, rows) &&
                ld_layout_free(&target[i], target, placed, count)) break;
        }
        if (next_slot == cols * rows) return 0;
        placed[i] = 1;
        next_slot++;
    }
    return 1;
}

static void ld_layout_write_items(const uintptr_t *cells, const uintptr_t *models,
                                  const struct ld_layout_item *items, size_t n)
{
    for (size_t i = 0; i < n; i++) {
        int64_t x = items[i].x, y = items[i].y;
        if (!ld_dart_is_ro_addr(cells[i])) {
            memcpy((void *)(cells[i] + 0xb), &x, 8);
            memcpy((void *)(cells[i] + 0x13), &y, 8);
        }
        if (!ld_dart_is_ro_addr(models[i])) {
            memcpy((void *)(models[i] + 0x37), &x, 8);
            memcpy((void *)(models[i] + 0x3f), &y, 8);
        }
    }
}

/* Read the fields back so the vendor continuation only ever sees coordinates
 * that are proven to be the ones we computed. */
static int ld_layout_write_verified(const uintptr_t *cells, const uintptr_t *models,
                                    const struct ld_layout_item *want, size_t n)
{
    for (size_t i = 0; i < n; i++) {
        if (ld_heap_i64(cells[i], 0xb) != want[i].x ||
            ld_heap_i64(cells[i], 0x13) != want[i].y ||
            ld_heap_i64(models[i], 0x37) != want[i].x ||
            ld_heap_i64(models[i], 0x3f) != want[i].y) return 0;
    }
    return 1;
}

__attribute__((used)) static void ld_observe_orientation(struct ld_dart_context *c)
{
    uintptr_t receiver = c->x[1];
    int raw_dir=ld_grid_direction(c,c->x[2]);
    /* layout_memory slots: 0=landscape(g_grid_cols x g_grid_rows), 1=portrait; ld_grid_direction is 1=landscape, 0=portrait. */
    int direction=(raw_dir==0 || raw_dir==1)?(1-raw_dir):-1;
    int livecols=(int)ld_heap_i64(c->x[2],0x1b),liverows=(int)ld_heap_i64(c->x[2],0x23);
    if(g_grid_cols>0 && g_grid_rows>0 && g_grid_cols!=g_grid_rows) {
        if(livecols==g_grid_cols && liverows==g_grid_rows) direction=0;
        else if(livecols==g_grid_rows && liverows==g_grid_cols) direction=1;
    }
    int prev_cols = g_runtime_columns;
    int prev_rows = g_runtime_rows;
    g_rotation_direction=direction;
    if(direction>=0 && direction<=1 && livecols==(direction?g_grid_rows:g_grid_cols)
            && liverows==(direction?g_grid_cols:g_grid_rows)) {
        g_runtime_columns=livecols; g_runtime_rows=liverows; g_runtime_direction=direction;
    }
    int square=g_grid_cols>0 && g_grid_cols==g_grid_rows,changed_square=0;
    if(square && direction>=0 && g_layout_hooks_ready) {
        const char *sq_path = ld_is_workstation_layout_active() ? LD_WS_DESK_LAYOUT_FILE : LD_LAYOUT_FILE;
        struct ld_layout_memory *m=calloc(1,sizeof *m);
        if(m) {
            pthread_mutex_lock(&g_layout_io_lock);
            int sq_ok = ld_layout_load(sq_path,m) && m->columns==g_grid_cols && m->rows==g_grid_rows;
            pthread_mutex_unlock(&g_layout_io_lock);
            if(sq_ok) changed_square=m->active_direction>=0 && m->active_direction!=direction;
            free(m);
        }
    }
    int init_state = __atomic_load_n(&g_initial_restore, __ATOMIC_ACQUIRE);
    int initial = init_state==1 && direction==g_initial_direction
            && ld_heap_i64(c->x[2],0x1b)==g_initial_columns
            && ld_heap_i64(c->x[2],0x23)==g_initial_rows;
    if (g_layout_hooks_ready && init_state == 1 && !initial && livecols > 0 && liverows > 0 &&
        (livecols != g_initial_columns || liverows != g_initial_rows || direction != g_initial_direction)) {
        __atomic_store_n(&g_initial_restore, 0, __ATOMIC_RELEASE);
        __atomic_store_n(&g_initial_restore_mask, 0, __ATOMIC_RELEASE);
    }
    int stale_old = (prev_cols > 0 && prev_rows > 0 &&
                     (prev_cols != livecols || prev_rows != liverows) &&
                     ld_heap_i64(receiver, 7) == livecols &&
                     ld_heap_i64(receiver, 15) == liverows);
    if(g_layout_hooks_ready && (initial || changed_square || stale_old)) {
        /* Reuse the existing orientation transaction and its Dart frames to
         * publish the fully loaded layout through the vendor's save callbacks. */
        int64_t oldcols=stale_old ? prev_cols
            : (square ? (g_grid_cols<LD_LAYOUT_MAX_DIM ? g_grid_cols+1 : g_grid_cols-1)
                      : ld_heap_i64(c->x[2],0x23));
        int64_t oldrows=stale_old ? prev_rows : (square?g_grid_rows:ld_heap_i64(c->x[2],0x1b));
        if (!ld_dart_is_ro_addr(receiver)) {
            memcpy((void *)(receiver+7),&oldcols,8); memcpy((void *)(receiver+15),&oldrows,8);
        }
        if(initial) __atomic_store_n(&g_initial_restore, 2, __ATOMIC_RELEASE);
        ld_blog("vendor layout transaction requested direction=%d initial=%d stale_old=%d",direction,initial,stale_old);
    }
    ld_blog("orientation fields old=%lld,%lld new=%lld,%lld late=%x,%x sentinel=%llx",
            (long long)ld_heap_i64(receiver, 7), (long long)ld_heap_i64(receiver, 15),
            (long long)ld_heap_i64(c->x[2], 27), (long long)ld_heap_i64(c->x[2], 35),
            ld_heap_u32(receiver, 27), ld_heap_u32(receiver, 31),
            (unsigned long long)ld_heap_i64(c->x[27], 0x40));
}
__attribute__((used)) static void ld_observe_process(struct ld_dart_context *c)
{
    g_restore_ready=0;
    if(!g_layout_hooks_ready || !g_page_fields.layout) return;
    ld_blog("process-screen args=%llx,%llx,%lld,%llx,%lld",
            (unsigned long long)c->x[1], (unsigned long long)c->x[2],
            (long long)c->x[3], (unsigned long long)c->x[4], (long long)c->x[5]);
    uintptr_t list = ld_heap_pointer(c, c->x[2], g_page_fields.rx_value);
    int n = (int32_t)ld_heap_u32(list, 0xb) >> 1;
    uintptr_t data = ld_heap_pointer(c, list, 0xf);
    ld_blog("process-screen list=%llx n=%d data=%llx", (unsigned long long)list, n, (unsigned long long)data);
    struct ld_layout_item *source=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *source);
    struct ld_layout_item *target=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *target);
    uintptr_t *cells=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *cells), *models=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *models);
    int64_t list_cont=0;
    size_t count=0;
    if(!source || !target || !cells || !models || n<=0 || n>=100) goto done;
    for (int i = 0; i < n; i++) {
        uintptr_t screen = ld_heap_pointer(c, data, 0xf + i * 4);
        uintptr_t layout = ld_heap_pointer(c, screen, g_page_fields.layout);
        uintptr_t items = ld_heap_pointer(c, layout, g_page_fields.items);
        if (!items || (ld_heap_u32(items, (size_t)-1) >> 12 & 0xfffff) != 0x5c) goto done;
        int size=(int32_t)ld_heap_u32(items,0xb)>>1;
        uintptr_t array=ld_heap_pointer(c,items,0xf);
        if(!layout || !array || size<0 || count+(size_t)size>LD_LAYOUT_MAX_ITEMS) goto done;
        for(int j=0;j<size;j++) {
            uintptr_t cell=ld_heap_pointer(c,array,0xf+j*4), model=ld_heap_pointer(c,cell,g_page_fields.model);
            if(!cell || !model) goto done;
            int64_t cont=ld_heap_i64(model,g_page_fields.container);
            if(cont!=-100 && cont!=-104) goto done;
            if(!list_cont) list_cont=cont;
            else if(cont!=list_cont) goto done;
            source[count]=(struct ld_layout_item){ld_heap_i64(model,7),ld_heap_i64(model,0x2f),
                (int)ld_heap_i64(cell,0xb),(int)ld_heap_i64(cell,0x13),
                (int)ld_heap_i64(model,0x1f),(int)ld_heap_i64(model,0x27)};
            cells[count]=cell; models[count]=model; count++;
        }
    }
    int cols=(int)c->x[3], rows=(int)c->x[5];
    int oldcols=(int)ld_heap_i64(c->x[1],7), oldrows=(int)ld_heap_i64(c->x[1],15);
    int square=g_grid_cols==g_grid_rows;
    int is_ws_allapps=(list_cont==-104);
    if(!count) goto done;
    if(is_ws_allapps) {
        int source_grid_changed = cols != oldcols || rows != oldrows;
        int valid_source = source_grid_changed && ld_layout_valid(source,count,oldcols,oldrows);
        int live_positions = source_grid_changed && !valid_source &&
                             ld_layout_valid(source,count,cols,rows);
        if (live_positions) {
            /* After a restart the database can already contain live-grid
             * positions even though the controller still reports the old grid. */
            memcpy(target,source,count*sizeof *target);
        }
        if (live_positions || (valid_source &&
            ld_layout_reflow_allapps(source,count,cols,rows,target) &&
            ld_layout_valid(target,count,cols,rows))) {
            /* Rebuilding screens can briefly expose non-cell objects. Model
             * subclasses may differ, but their position fields are shared. */
            unsigned cell_cid = ld_layout_object_cid(cells[0]);
            int targets_are_cells = cell_cid != 0;
            for (size_t i = 0; targets_are_cells && i < count; i++) {
                targets_are_cells = ld_layout_object_cid(cells[i]) == cell_cid &&
                    ld_layout_object_cid(models[i]) != 0;
            }
            int changed = 0;
            for (size_t i = 0; i < count; i++)
                changed |= target[i].x != source[i].x || target[i].y != source[i].y;
            if (targets_are_cells && (changed || live_positions)) {
                /* With already-live cells, confirm both coordinate owners
                 * agree before bypassing the stock old-grid transform. */
                if (changed) ld_layout_write_items(cells,models,target,count);
                if (ld_layout_write_verified(cells,models,target,count)) {
                    g_restore_ready = 1;
                    ld_blog("allapps %s %zu items from %dx%d to %dx%d (verified)",
                            live_positions ? "retained" : "reflowed",count,oldcols,oldrows,cols,rows);
                } else if (changed) {
                    ld_layout_write_items(cells,models,source,count);
                    ld_blog("allapps transform rolled back: readback mismatch");
                } else {
                    ld_blog("allapps live cell/model coordinates disagree at startup");
                }
            }
        }
        if (!g_restore_ready)
            ld_blog("allapps mid path kept: %zu items live %dx%d config %dx%d source_valid=%d live_positions=%d",
                    count,cols,rows,oldcols,oldrows,valid_source,live_positions);
        goto done;
    }
    int direction=square?g_rotation_direction:(cols==g_grid_cols && rows==g_grid_rows?0:1);
    if(direction<0 || direction>1) goto done;
    if((direction && (cols!=g_grid_rows || rows!=g_grid_cols)) || (oldcols==cols && oldrows==rows)) goto done;
    int bit = is_ws_allapps ? 2 : 1;
    int init_state = __atomic_load_n(&g_initial_restore, __ATOMIC_ACQUIRE);
    int init_mask = __atomic_load_n(&g_initial_restore_mask, __ATOMIC_ACQUIRE);
    int initial = (init_state == 2) && ((init_mask & bit) != 0) &&
                  cols == g_initial_columns && rows == g_initial_rows &&
                  direction == g_initial_direction;
    if (init_state == 2 && !initial &&
        (cols != g_initial_columns || rows != g_initial_rows || direction != g_initial_direction)) {
        __atomic_store_n(&g_initial_restore, 0, __ATOMIC_RELEASE);
        __atomic_store_n(&g_initial_restore_mask, 0, __ATOMIC_RELEASE);
    }
    if(!is_ws_allapps && !ld_layout_valid(source,count,(initial||square)?cols:oldcols,(initial||square)?rows:oldrows)) goto done;
    int is_laptop = ld_is_workstation_layout_active();
    int slot = is_ws_allapps ? LD_SLOT_WS_ALLAPPS
             : (is_laptop ? LD_SLOT_WS_DESK : LD_SLOT_NORMAL_HOME);
    const char *layout_file = ld_layout_slot_path(slot);
    struct ld_layout_memory *memory=calloc(1,sizeof *memory);
    if(!memory) goto done;
    pthread_mutex_lock(&g_layout_io_lock);
    if(!ld_layout_load(layout_file,memory) || memory->columns!=g_grid_cols || memory->rows!=g_grid_rows)
        memset(memory,0,sizeof *memory);
    memory->columns=g_grid_cols; memory->rows=g_grid_rows;
    if(!initial && ld_layout_valid(source,count,square?g_grid_cols:oldcols,square?g_grid_rows:oldrows)) {
        memcpy(memory->items[1-direction],source,count*sizeof *source);
        memory->count[1-direction]=(uint32_t)count;
    }
    if(!ld_layout_plan(source,count,cols,rows,memory->items[direction],memory->count[direction],target)) {
        pthread_mutex_unlock(&g_layout_io_lock);
        ld_blog("layout restore cannot fit same-page items; no changes");
        free(memory);
        goto done;
    }
    memcpy(memory->items[direction],target,count*sizeof *target); memory->count[direction]=(uint32_t)count;
    memory->active_direction=direction;
    /* Let the original Dart continuation publish through the launcher's own
     * database engine. Never open or rewrite its live database here. */
    int saved = ld_layout_save(layout_file,memory);
    if(saved) ld_layout_invalidate_pending_locked(slot);
    pthread_mutex_unlock(&g_layout_io_lock);
    free(memory);
    if(!saved) goto done;
    for(size_t i=0;i<count;i++) {
        int64_t x=target[i].x,y=target[i].y;
        if (!ld_dart_is_ro_addr(cells[i])) {
            memcpy((void *)(cells[i]+0xb),&x,8); memcpy((void *)(cells[i]+0x13),&y,8);
        }
        if (!ld_dart_is_ro_addr(models[i])) {
            memcpy((void *)(models[i]+0x37),&x,8); memcpy((void *)(models[i]+0x3f),&y,8);
        }
    }
    g_restore_ready=1;
    if(initial) {
        int rem = __atomic_and_fetch(&g_initial_restore_mask, ~bit, __ATOMIC_ACQ_REL);
        if(!rem) __atomic_store_n(&g_initial_restore, 0, __ATOMIC_RELEASE);
    }
    ld_blog("layout restored %zu items (cont=%lld slot=%d) to %dx%d; vendor occupancy continuation",
            count,(long long)list_cont,slot,cols,rows);
done:
    free(source); free(target); free(cells); free(models);
}
__attribute__((used)) static void ld_process_mid(struct ld_dart_context *c)
{
    (void)c;
    g_process_mid_next=g_restore_ready ? (void *)g_process_resume : g_orig_process_mid;
}
LD_DART_OBSERVER(repl_orientation_observed, ld_observe_orientation, g_orig_orientation)
LD_DART_OBSERVER(repl_process_screen, ld_observe_process, g_orig_process_screen)
LD_DART_OBSERVER(repl_process_mid, ld_process_mid, g_process_mid_next)
#endif
