#ifndef LD_LAYOUT_SAVE_H
#define LD_LAYOUT_SAVE_H
#include "layout_changes.h"
static void *g_orig_save_batch __attribute__((used));
static void *g_orig_save_single __attribute__((used));
static void *g_orig_delete_item __attribute__((used));
static void ld_capture_saved_items(const struct ld_layout_item *items,size_t count) {
    if(!g_layout_hooks_ready || !g_load_prepared || !count || g_grid_cols<=0 || g_grid_rows<=0) return;
    struct ld_layout_memory *m=calloc(1,sizeof *m); if(!m) return;
    if(ld_layout_load(LD_LAYOUT_FILE,m) && m->columns==g_grid_cols && m->rows==g_grid_rows
            && ld_layout_record_changes(m,items,count) && ld_layout_save(LD_LAYOUT_FILE,m))
        ld_blog("layout save captured %zu HOME changes direction=%d",count,m->active_direction);
    free(m);
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
