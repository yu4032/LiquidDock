#ifndef LD_LAYOUT_CHANGES_H
#define LD_LAYOUT_CHANGES_H
#include "layout_memory.h"
static inline int ld_layout_apply_changes(struct ld_layout_memory *memory,const struct ld_layout_item *changes,size_t n) {
    if(!memory || !changes || !n || n>LD_LAYOUT_MAX_ITEMS || memory->active_direction<0 || memory->active_direction>1) return 0;
    int d=memory->active_direction;
    size_t count=memory->count[d];
    if(count>LD_LAYOUT_MAX_ITEMS) return 0;
    struct ld_layout_item *current=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *current);
    if(!current) return 0;
    memcpy(current,memory->items[d],count*sizeof *current);
    int ok=0;
    for(size_t i=0;i<n;i++) {
        size_t j=0; while(j<count && current[j].id!=changes[i].id) j++;
        if(changes[i].id<0) goto done;
        if(changes[i].screen<0) {
            if(j<count) { memmove(current+j,current+j+1,(count-j-1)*sizeof *current); count--; }
            continue;
        }
        if(j==count) { if(count==LD_LAYOUT_MAX_ITEMS) goto done; count++; }
        current[j]=changes[i];
    }
    memcpy(memory->items[d],current,count*sizeof *current);
    memory->count[d]=(uint32_t)count;
    ok=1;
done:
    free(current);
    return ok;
}
static inline int ld_layout_finalize_changes(struct ld_layout_memory *memory) {
    if(!memory || memory->active_direction<0 || memory->active_direction>1) return 0;
    int d=memory->active_direction,other=1-d;
    int cols=d?memory->rows:memory->columns,rows=d?memory->columns:memory->rows;
    size_t count=memory->count[d];
    if(!ld_layout_valid(memory->items[d],count,cols,rows)) return 0;
    struct ld_layout_item *target=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *target);
    if(!target) return 0;
    int ok=ld_layout_plan(memory->items[d],count,rows,cols,memory->items[other],memory->count[other],target);
    if(ok) {
        memcpy(memory->items[other],target,count*sizeof *target);
        memory->count[other]=(uint32_t)count;
    }
    free(target);
    return ok;
}
static inline int ld_layout_record_changes(struct ld_layout_memory *memory,const struct ld_layout_item *changes,size_t n) {
    if(!memory || !changes || !n || n>LD_LAYOUT_MAX_ITEMS || !ld_layout_memory_valid(memory) || memory->active_direction<0) return 0;
    struct ld_layout_memory *copy=calloc(1,sizeof *copy);
    if(!copy) return 0;
    *copy=*memory;
    int ok=ld_layout_apply_changes(copy,changes,n) && ld_layout_finalize_changes(copy);
    if(ok) *memory=*copy;
    free(copy);
    return ok;
}
#endif
