#ifndef LD_LAYOUT_RUNTIME_H
#define LD_LAYOUT_RUNTIME_H
#include <sys/uio.h>
#include <sys/syscall.h>
#include "dart_observer.h"
#include "layout_memory.h"
#include "dart_heap.h"
#include "grid_orientation.h"

#ifndef LD_LAYOUT_FILE
#define LD_LAYOUT_FILE "/data/user_de/0/com.miui.home/files/liquiddock-layout-v3.bin"
#endif
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
__attribute__((used)) static void ld_observe_orientation(struct ld_dart_context *c)
{
    uintptr_t receiver = c->x[1];
    int direction=ld_grid_direction(c,c->x[2]);
    g_rotation_direction=direction;
    int livecols=(int)ld_heap_i64(c->x[2],0x1b),liverows=(int)ld_heap_i64(c->x[2],0x23);
    if(direction>=0 && direction<=1 && livecols==(direction?g_grid_rows:g_grid_cols)
            && liverows==(direction?g_grid_cols:g_grid_rows)) {
        g_runtime_columns=livecols; g_runtime_rows=liverows; g_runtime_direction=direction;
    }
    int square=g_grid_cols>0 && g_grid_cols==g_grid_rows,changed_square=0;
    if(square && direction>=0 && g_layout_hooks_ready) {
        struct ld_layout_memory *m=calloc(1,sizeof *m);
        if(m && ld_layout_load(LD_LAYOUT_FILE,m) && m->columns==g_grid_cols && m->rows==g_grid_rows)
            changed_square=m->active_direction>=0 && m->active_direction!=direction;
        free(m);
    }
    int initial=g_initial_restore==1 && direction==g_initial_direction
            && ld_heap_i64(c->x[2],0x1b)==g_initial_columns
            && ld_heap_i64(c->x[2],0x23)==g_initial_rows;
    if(g_layout_hooks_ready && (initial || changed_square)) {
        /* Reuse the existing orientation transaction and its Dart frames to
         * publish the fully loaded layout through the vendor's save callbacks. */
        int64_t oldcols=square?g_grid_cols+1:ld_heap_i64(c->x[2],0x23);
        int64_t oldrows=square?g_grid_rows:ld_heap_i64(c->x[2],0x1b);
        memcpy((void *)(receiver+7),&oldcols,8); memcpy((void *)(receiver+15),&oldrows,8);
        if(initial) g_initial_restore=2;
        ld_blog("vendor layout transaction requested direction=%d initial=%d",direction,initial);
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
    if(!g_layout_hooks_ready) return;
    ld_blog("process-screen args=%llx,%llx,%lld,%llx,%lld",
            (unsigned long long)c->x[1], (unsigned long long)c->x[2],
            (long long)c->x[3], (unsigned long long)c->x[4], (long long)c->x[5]);
    uintptr_t list = ld_heap_pointer(c, c->x[2], 0xf);
    int n = (int32_t)ld_heap_u32(list, 0xb) >> 1;
    uintptr_t data = ld_heap_pointer(c, list, 0xf);
    ld_blog("process-screen list=%llx n=%d data=%llx", (unsigned long long)list, n, (unsigned long long)data);
    struct ld_layout_item *source=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *source);
    struct ld_layout_item *target=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *target);
    uintptr_t *cells=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *cells), *models=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *models);
    size_t count=0;
    if(!source || !target || !cells || !models || n<=0 || n>=100) goto done;
    for (int i = 0; i < n; i++) {
        uintptr_t screen = ld_heap_pointer(c, data, 0xf + i * 4);
        uintptr_t layout = ld_heap_pointer(c, screen, 0x1b);
        uintptr_t items=ld_heap_pointer(c,layout,0xdb);
        int size=(int32_t)ld_heap_u32(items,0xb)>>1;
        uintptr_t array=ld_heap_pointer(c,items,0xf);
        if(!layout || !array || size<0 || count+(size_t)size>LD_LAYOUT_MAX_ITEMS) goto done;
        for(int j=0;j<size;j++) {
            uintptr_t cell=ld_heap_pointer(c,array,0xf+j*4), model=ld_heap_pointer(c,cell,7);
            if(!cell || !model || ld_heap_i64(model,0x47)!=-100) goto done;
            source[count]=(struct ld_layout_item){ld_heap_i64(model,7),ld_heap_i64(model,0x2f),
                (int)ld_heap_i64(cell,0xb),(int)ld_heap_i64(cell,0x13),
                (int)ld_heap_i64(model,0x1f),(int)ld_heap_i64(model,0x27)};
            cells[count]=cell; models[count]=model; count++;
        }
    }
    int cols=(int)c->x[3], rows=(int)c->x[5];
    int oldcols=(int)ld_heap_i64(c->x[1],7), oldrows=(int)ld_heap_i64(c->x[1],15);
    int initial=g_initial_restore==2;
    int square=g_grid_cols==g_grid_rows;
    if(!count || !ld_layout_valid(source,count,(initial||square)?cols:oldcols,(initial||square)?rows:oldrows)) goto done;
    int direction=square?g_rotation_direction:(cols==g_grid_cols && rows==g_grid_rows?0:1);
    if(direction<0 || direction>1) goto done;
    if((direction && (cols!=g_grid_rows || rows!=g_grid_cols)) || (oldcols==cols && oldrows==rows)) goto done;
    struct ld_layout_memory *memory=calloc(1,sizeof *memory);
    if(!memory) goto done;
    if(!ld_layout_load(LD_LAYOUT_FILE,memory) || memory->columns!=g_grid_cols || memory->rows!=g_grid_rows)
        memset(memory,0,sizeof *memory);
    memory->columns=g_grid_cols; memory->rows=g_grid_rows;
    if(!initial) {
        memcpy(memory->items[1-direction],source,count*sizeof *source);
        memory->count[1-direction]=(uint32_t)count;
    }
    if(!ld_layout_plan(source,count,cols,rows,memory->items[direction],memory->count[direction],target)) {
        ld_blog("layout restore cannot fit same-page items; no changes"); free(memory); goto done;
    }
    memcpy(memory->items[direction],target,count*sizeof *target); memory->count[direction]=(uint32_t)count;
    memory->active_direction=direction;
    /* Let the original Dart continuation publish through the launcher's own
     * database engine. Never open or rewrite its live database here. */
    if(!ld_layout_save(LD_LAYOUT_FILE,memory)) { free(memory); goto done; }
    free(memory);
    for(size_t i=0;i<count;i++) {
        int64_t x=target[i].x,y=target[i].y;
        memcpy((void *)(cells[i]+0xb),&x,8); memcpy((void *)(cells[i]+0x13),&y,8);
        memcpy((void *)(models[i]+0x37),&x,8); memcpy((void *)(models[i]+0x3f),&y,8);
    }
    g_restore_ready=1;
    if(g_initial_restore) g_initial_restore=0;
    ld_blog("layout restored %zu items to %dx%d; vendor occupancy continuation",count,cols,rows);
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
