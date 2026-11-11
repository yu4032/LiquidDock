#ifndef LD_LAYOUT_CHANGES_H
#define LD_LAYOUT_CHANGES_H
#include "layout_memory.h"
static inline int ld_layout_record_changes(struct ld_layout_memory *memory,const struct ld_layout_item *changes,size_t n) {
    if(!memory || !changes || !n || n>LD_LAYOUT_MAX_ITEMS || !ld_layout_memory_valid(memory) || memory->active_direction<0) return 0;
    int d=memory->active_direction,other=1-d;
    struct ld_layout_item *current=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *current),*target=calloc(LD_LAYOUT_MAX_ITEMS,sizeof *target);
    if(!current || !target) { free(current); free(target); return 0; }
    size_t count=memory->count[d]; memcpy(current,memory->items[d],count*sizeof *current); int ok=0;
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
    int cols=d?memory->rows:memory->columns,rows=d?memory->columns:memory->rows;
    if(!ld_layout_valid(current,count,cols,rows)) goto done;
    if(!ld_layout_plan(current,count,rows,cols,memory->items[other],memory->count[other],target)) goto done;
    memcpy(memory->items[d],current,count*sizeof *current); memory->count[d]=(uint32_t)count;
    memcpy(memory->items[other],target,count*sizeof *target); memory->count[other]=(uint32_t)count;
    ok=1;
done:
    free(current); free(target); return ok;
}
#endif
