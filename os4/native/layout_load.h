#ifndef LD_LAYOUT_LOAD_H
#define LD_LAYOUT_LOAD_H
#include "grid_orientation.h"
static void *g_orig_load_items __attribute__((used));
static void *g_orig_filter_return __attribute__((used));
static int g_load_columns,g_load_rows,g_load_direction;
static int g_load_prepared;
__attribute__((used)) static void ld_filter_grid(struct ld_dart_context *c) {
    g_load_columns=(int)ld_heap_i64(c->x[0],0x1b);
    g_load_rows=(int)ld_heap_i64(c->x[0],0x23);
    g_load_direction=ld_grid_direction(c,c->x[0]);
}
static int64_t ld_dto_number(struct ld_dart_context *c,uintptr_t object,size_t offset) {
    uint32_t word=ld_heap_u32(object,offset);
    if(!(word&1)) return (int32_t)word/2;
    uintptr_t ptr=ld_heap_pointer(c,object,offset);
    if(ptr && (ld_heap_u32(ptr,(size_t)-1)>>12 & 0xfffff)==0x3d) return ld_heap_i64(ptr,7);
    return INT64_MIN;
}
static int ld_read_dto_item(struct ld_dart_context *c,uintptr_t dto,struct ld_layout_item *item) {
    if((ld_heap_u32(dto,(size_t)-1)>>12&0xfffff)!=0xf26) return 0;
    int64_t id=ld_dto_number(c,dto,7),container=ld_dto_number(c,dto,0x2b);
    if(id<0 || container==INT64_MIN) return 0;
    if(container!=-100) { *item=(struct ld_layout_item){.id=id,.screen=-1}; return 1; }
    int64_t page=ld_dto_number(c,dto,0x1f),x=ld_dto_number(c,dto,0x23),y=ld_dto_number(c,dto,0x27);
    int64_t sx=ld_dto_number(c,dto,0x17),sy=ld_dto_number(c,dto,0x1b);
    if(page<0 || page>INT32_MAX/2 || x<INT32_MIN+1LL || x>INT32_MAX
            || y<INT32_MIN+1LL || y>INT32_MAX || sx<1 || sx>10 || sy<1 || sy>10) return 0;
    *item=(struct ld_layout_item){id,page,(int)x,(int)y,(int)sx,(int)sy}; return 1;
}
__attribute__((used)) static void ld_load_items(struct ld_dart_context *c) {
    if(g_load_prepared || !g_layout_hooks_ready || g_grid_cols<=0 || g_grid_rows<=0) return;
    uintptr_t list=c->x[2];
    int n=(int32_t)ld_heap_u32(list,0xb)/2;
    uintptr_t data=ld_heap_pointer(c,list,0xf);
    ld_blog("load-items list cid=%x n=%d data=%llx grid=%dx%d",ld_heap_u32(list,(size_t)-1)>>12&0xfffff,n,(unsigned long long)data,g_load_columns,g_load_rows);
    if(n<0 || n>LD_LAYOUT_MAX_ITEMS || !data || (ld_heap_u32(list,(size_t)-1)>>12&0xfffff)!=0x5c) return;
    /* Active GOC geometry wins over filter calls for inactive configurations.
     * During cold boot both orientations may be enumerated after GOC is ready. */
    int cols=g_runtime_columns?g_runtime_columns:g_load_columns;
    int rows=g_runtime_rows?g_runtime_rows:g_load_rows;
    int direction=g_grid_cols==g_grid_rows?(g_runtime_columns?g_runtime_direction:g_load_direction):(cols==g_grid_cols && rows==g_grid_rows?0:1);
    if(direction<0 || direction>1) return;
    if(direction && (cols!=g_grid_rows || rows!=g_grid_cols)) return;
    struct ld_layout_item *source=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *source),*target=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *target);
    uintptr_t *objects=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *objects);
    struct ld_layout_memory *memory=calloc(1,sizeof *memory);
    size_t count=0;
    if(!source || !target || !objects || !memory) goto done;
    for(int i=0;i<n;i++) {
        uintptr_t dto=ld_heap_pointer(c,data,0xf+4*i);
        if(!ld_read_dto_item(c,dto,&source[count])) goto done;
        if(source[count].screen<0) continue;
        objects[count++]=dto;
    }
    int known=ld_layout_load(LD_LAYOUT_FILE,memory) && memory->columns==g_grid_cols && memory->rows==g_grid_rows;
    if(!known) { memset(memory,0,sizeof *memory); memory->active_direction=-1; }
    int source_direction=memory->active_direction;
    if(source_direction<0) {
        int p=ld_layout_valid(source,count,g_grid_cols,g_grid_rows),l=ld_layout_valid(source,count,g_grid_rows,g_grid_cols);
        source_direction=p&&!l?0:l&&!p?1:direction;
    }
    int sc=source_direction?g_grid_rows:g_grid_cols,sr=source_direction?g_grid_cols:g_grid_rows;
    memory->columns=g_grid_cols; memory->rows=g_grid_rows;
    if(known && source_direction>=0) {
        /* Rust may have already scaled or transposed DTO positions. The save
         * boundary snapshot is authoritative; the load batch supplies identity
         * and footprint, not a second snapshot of the previous orientation. */
        for(size_t i=0;i<count;i++) for(size_t j=0;j<memory->count[source_direction];j++)
            if(source[i].id==memory->items[source_direction][j].id) {
                source[i].screen=memory->items[source_direction][j].screen; break;
            }
    } else if(ld_layout_valid(source,count,sc,sr)) {
        memcpy(memory->items[source_direction],source,count*sizeof *source);
        memory->count[source_direction]=(uint32_t)count;
    }
    if(!ld_layout_plan(source,count,cols,rows,memory->items[direction],memory->count[direction],target)) goto done;
    for(size_t i=0;i<count;i++) if(target[i].screen>INT32_MAX/2) goto done;
    memcpy(memory->items[direction],target,count*sizeof *target); memory->count[direction]=(uint32_t)count;
    memory->active_direction=direction;
    if(!ld_layout_save(LD_LAYOUT_FILE,memory)) goto done;
    for(size_t i=0;i<count;i++) {
        /* ItemInfo DTO fields are compressed nullable ints, unlike the unboxed
         * ItemInfoModel. Small coordinates are immediate Smis, never pointers. */
        int32_t x=target[i].x*2,y=target[i].y*2;
        memcpy((void *)(objects[i]+0x23),&x,4); memcpy((void *)(objects[i]+0x27),&y,4);
        int32_t page=(int32_t)target[i].screen*2;
        memcpy((void *)(objects[i]+0x1f),&page,4);
    }
    g_load_prepared=1; g_initial_restore=1;
    g_initial_columns=cols; g_initial_rows=rows;
    g_initial_direction=direction;
    ld_blog("startup DTO restored %zu HOME items %dx%d source-direction=%d",count,cols,rows,source_direction);
done:
    free(source); free(target); free(objects); free(memory);
}
LD_DART_OBSERVER(repl_load_items,ld_load_items,g_orig_load_items)
LD_DART_OBSERVER(repl_filter_grid,ld_filter_grid,g_orig_filter_return)
#endif
