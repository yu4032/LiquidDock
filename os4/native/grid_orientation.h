#ifndef LD_GRID_ORIENTATION_H
#define LD_GRID_ORIENTATION_H
#include "dart_heap.h"
static int ld_grid_direction(struct ld_dart_context *c,uintptr_t grid) {
    uintptr_t variables=ld_heap_pointer(c,grid,0x8f),value=ld_heap_pointer(c,variables,0x23);
    int64_t raw=ld_heap_i64(value,7);
    return value && (raw==0 || raw==1)?(int)raw:-1;
}
#endif
