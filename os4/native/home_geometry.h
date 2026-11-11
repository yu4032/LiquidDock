#ifndef LD_HOME_GEOMETRY_H
#define LD_HOME_GEOMETRY_H
#include <math.h>
#include <string.h>
#include "grid_orientation.h"

/* Insets in this pipeline are signed translations: right/bottom are negative
 * insets. Cell dimensions are the actual coordinate strides, not icon sizes. */
struct ld_home_geometry {
    double width,height,left,top,right,bottom;
};
static int ld_home_geometry_calculate(struct ld_home_geometry *g,int cols,int rows,
        const struct ld_grid_offsets *o)
{
    if(cols<2 || cols>10 || rows<2 || rows>10 || !g || !o) return 0;
    double values[]={g->width,g->height,g->left,g->top,g->right,g->bottom,
        o->horizontal,o->top,o->bottom,o->row_gap};
    for(size_t i=0;i<sizeof values/sizeof *values;i++) if(!isfinite(values[i])) return 0;
    struct ld_home_geometry next=*g;
    double available_width=g->width*cols-2.0*o->horizontal;
    double available_height=g->height*rows-o->top-o->bottom;
    double content_height=(available_height-o->row_gap*(rows-1))/rows;
    double cell=fmin(fmin(g->width,g->height),fmin(available_width/cols,content_height));
    next.width=available_width/cols;
    next.height=cell+o->row_gap;
    next.left+=o->horizontal; next.right-=o->horizontal;
    next.top+=o->top;
    /* The shared bottom inset also drives HotSeatsMarginBottom. Keep it
     * intact: workspace bottom distance and row gap alter the row stride. */
    /* Invalid viewports retain the complete vendor geometry atomically. */
    if(!isfinite(next.width) || !isfinite(next.height) || cell<1 || next.width<1 || next.height<1) return 0;
    *g=next; return 1;
}

static void ld_apply_home_geometry(struct ld_dart_context *c,uintptr_t grid)
{
    if(!g_hook_pin_ok || g_count_cols<=0 || g_grid_cols<=0) return;
    uintptr_t variables=ld_heap_pointer(c,grid,0x8f),fullscreen=0;
    uint32_t layout=0;
    if(!variables || !ld_read_heap(variables+0x27,&layout,4)
            || !ld_read_heap(c->x[27]+0xef20,&fullscreen,8)
            || !(layout&1) || !(fullscreen&1) || layout!=(uint32_t)fullscreen) return;
    int direction=ld_grid_direction(c,grid);
    if(direction<0 || direction>1) return;
    const struct ld_grid_offsets *o=direction?&g_home_options.landscape:&g_home_options.portrait;
    if(o->horizontal==0 && o->top==0 && o->bottom==0 && o->row_gap==0) return;
    int cols=(int)ld_heap_i64(grid,0x1b),rows=(int)ld_heap_i64(grid,0x23);
    if(cols!=(direction?g_grid_rows:g_grid_cols) || rows!=(direction?g_grid_cols:g_grid_rows)) return;
    uintptr_t insets=ld_heap_pointer(c,grid,0x3b);
    struct ld_home_geometry geometry;
    if(!insets || !ld_read_heap(grid+0x2b,&geometry.width,8)
            || !ld_read_heap(grid+0x33,&geometry.height,8)
            || !ld_read_heap(insets+7,&geometry.left,8)
            || !ld_read_heap(insets+0xf,&geometry.top,8)
            || !ld_read_heap(insets+0x17,&geometry.right,8)
            || !ld_read_heap(insets+0x1f,&geometry.bottom,8)) return;
    if(!ld_home_geometry_calculate(&geometry,cols,rows,o)) return;
    memcpy((void *)(grid+0x2b),&geometry.width,8);
    memcpy((void *)(grid+0x33),&geometry.height,8);
    memcpy((void *)(insets+7),&geometry.left,8);
    memcpy((void *)(insets+0xf),&geometry.top,8);
    memcpy((void *)(insets+0x17),&geometry.right,8);
}
#endif
