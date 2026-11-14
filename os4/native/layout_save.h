#ifndef LD_LAYOUT_SAVE_H
#define LD_LAYOUT_SAVE_H
#include <pthread.h>
#include "layout_changes.h"
static void *g_orig_save_batch __attribute__((used));
static void *g_orig_save_single __attribute__((used));
static void *g_orig_delete_item __attribute__((used));
static pthread_mutex_t g_pending_layout_lock = PTHREAD_MUTEX_INITIALIZER;
static struct ld_layout_memory g_pending_layout_mem;
static int g_pending_layout_valid;
static void ld_capture_saved_items(const struct ld_layout_item *items,size_t count) {
    if(!g_layout_hooks_ready || !g_load_prepared || !count || g_grid_cols<=0 || g_grid_rows<=0) return;
    pthread_mutex_lock(&g_pending_layout_lock);
    if(!g_pending_layout_valid || g_pending_layout_mem.columns!=g_grid_cols || g_pending_layout_mem.rows!=g_grid_rows) {
        if(!ld_layout_load(LD_LAYOUT_FILE,&g_pending_layout_mem)
                || g_pending_layout_mem.columns!=g_grid_cols || g_pending_layout_mem.rows!=g_grid_rows) {
            g_pending_layout_valid=0;
            pthread_mutex_unlock(&g_pending_layout_lock);
            return;
        }
    }
    if(!ld_layout_apply_changes(&g_pending_layout_mem,items,count)) {
        g_pending_layout_valid=0;
        ld_blog("layout save ignored %zu HOME changes (invalid item)",count);
        pthread_mutex_unlock(&g_pending_layout_lock);
        return;
    }
    if(ld_layout_finalize_changes(&g_pending_layout_mem) && ld_layout_save(LD_LAYOUT_FILE,&g_pending_layout_mem)) {
        g_pending_layout_valid=0;
        int d = g_pending_layout_mem.active_direction;
        if (d >= 0 && d <= 1) {
            ld_write_4x2_widget_state(g_pending_layout_mem.items[d], g_pending_layout_mem.count[d]);
        }
        ld_blog("layout save captured %zu HOME changes direction=%d",count,g_pending_layout_mem.active_direction);
    } else {
        g_pending_layout_valid=1;
        int d=g_pending_layout_mem.active_direction;
        int cols=d?g_pending_layout_mem.rows:g_pending_layout_mem.columns;
        int rows=d?g_pending_layout_mem.columns:g_pending_layout_mem.rows;
        size_t cnt=g_pending_layout_mem.count[d];
        ld_blog("layout save buffered %zu HOME changes direction=%d first=(id=%lld scr=%lld (%d,%d) %dx%d)",
            count,d,(long long)items[0].id,(long long)items[0].screen,items[0].x,items[0].y,items[0].sx,items[0].sy);
        for(size_t i=0;i<cnt;i++) {
            const struct ld_layout_item *a=&g_pending_layout_mem.items[d][i];
            if(!ld_layout_bounds(a,cols,rows)) {
                ld_blog("layout conflict: OOB id=%lld scr=%lld (%d,%d) %dx%d in %dx%d",
                    (long long)a->id,(long long)a->screen,a->x,a->y,a->sx,a->sy,cols,rows);
                break;
            }
            int found=0;
            for(size_t j=0;j<i;j++) {
                const struct ld_layout_item *b=&g_pending_layout_mem.items[d][j];
                if(a->id==b->id || ld_layout_overlap(a,b)) {
                    ld_blog("layout conflict: id=%lld scr=%lld (%d,%d) %dx%d vs id=%lld scr=%lld (%d,%d) %dx%d",
                        (long long)a->id,(long long)a->screen,a->x,a->y,a->sx,a->sy,
                        (long long)b->id,(long long)b->screen,b->x,b->y,b->sx,b->sy);
                    found=1; break;
                }
            }
            if(found) break;
        }
    }
    pthread_mutex_unlock(&g_pending_layout_lock);
}
__attribute__((used)) static void ld_save_batch(struct ld_dart_context *c) {
    uintptr_t list=c->x[2]; unsigned cid=ld_heap_u32(list,(size_t)-1)>>12&0xfffff;
    if(cid!=0x5c && cid!=0x59 && cid!=0x5a) { ld_blog("save batch skipped list CID=%x",cid); return; }
    int n=(int32_t)ld_heap_u32(list,0xb)/2;
    uintptr_t data=cid==0x5c?ld_heap_pointer(c,list,0xf):list;
    if(!data || n<0 || n>LD_LAYOUT_MAX_ITEMS) return;
    struct ld_layout_item *items=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *items); if(!items) return;
    size_t count=0;
    for(int i=0;i<n;i++) if(ld_read_dto_item(c,ld_heap_pointer(c,data,0xf+4*i),&items[count])) count++;
    ld_capture_saved_items(items,count); free(items);
}
__attribute__((used)) static void ld_save_single(struct ld_dart_context *c) {
    struct ld_layout_item item;
    if(ld_read_dto_item(c,c->x[1],&item) || ld_read_dto_item(c,c->x[2],&item)) ld_capture_saved_items(&item,1);
}
__attribute__((used)) static void ld_delete_item(struct ld_dart_context *c) {
    int64_t id=INT64_MIN;
    if(!(c->x[2]&1)) id=(int64_t)c->x[2]/2;
    else if((ld_heap_u32(c->x[2],(size_t)-1)>>12&0xfffff)==0x3d) id=ld_heap_i64(c->x[2],7);
    if(id>=0) { struct ld_layout_item item={.id=id,.screen=-1}; ld_capture_saved_items(&item,1); }
}
LD_DART_OBSERVER(repl_save_batch,ld_save_batch,g_orig_save_batch)
LD_DART_OBSERVER(repl_save_single,ld_save_single,g_orig_save_single)
LD_DART_OBSERVER(repl_delete_item,ld_delete_item,g_orig_delete_item)
#endif
