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
static volatile unsigned g_geom_calls, g_geom_ok;
/* 真值点（GridSizeCalRules / cell-config 子对象，GridConfig+0x1f 压缩指针）：
 *   +0x1f 侧边距（左右共用单值 -> 偏移即同时改左右两侧，天然居中）
 *   +0x3f -> boxed double +7 顶部内边距
 *   +0x57 单元格底部内边距
 * 布局经 GridConfig.workspaceCellSide / workspacePaddingTop / workspaceCellPaddingBottom
 * 读取这三个字段，写这里才真正生效（grid+0x2b/insets 那组会被厂商重算覆盖）。 */
static void ld_apply_home_geometry(struct ld_dart_context *c,uintptr_t grid)
{
    unsigned diag=__atomic_fetch_add(&g_geom_calls,1u,__ATOMIC_RELAXED);
    #define GEOM_BAIL(code) do{ if(diag<20u) ld_blog("home geometry bail "#code); return; }while(0)
    if(diag<20u) ld_blog("home geometry call #%u",diag+1u);
    if(!g_hook_pin_ok || g_count_cols<=0 || g_grid_cols<=0) GEOM_BAIL(counts);
    uintptr_t variables=ld_heap_pointer(c,grid,0x8f),fullscreen=0;
    uint32_t layout=0;
    if(!variables) GEOM_BAIL(variables);
    if(!ld_read_heap(variables+0x27,&layout,4)
            || !ld_read_heap(c->x[27]+0xef60,&fullscreen,8)) GEOM_BAIL(read);
    if(!(layout&1) || !(fullscreen&1) || layout!=(uint32_t)fullscreen) {
        if(diag<20u) ld_blog("home geometry flags layout=%#x fullscreen=%#lx",layout,fullscreen);
        return;
    }
    int direction=ld_grid_direction(c,grid);
    if(direction<0 || direction>1) GEOM_BAIL(direction);
    /* direction 0=横屏（layout_runtime/layout_load 同约定）：横屏取 landscape 值。 */
    const struct ld_grid_offsets *o=direction?&g_home_options.portrait:&g_home_options.landscape;
    if(diag<20u) ld_blog("home geometry dir=%d off h=%.1f t=%.1f b=%.1f gap=%.1f",
        direction,o->horizontal,o->top,o->bottom,o->row_gap);
    if(o->horizontal==0 && o->top==0 && o->bottom==0) GEOM_BAIL(offsets-zero);
    int cols=(int)ld_heap_i64(grid,0x23),rows=(int)ld_heap_i64(grid,0x1b);   /* 该对象 0x23=列 0x1b=行 */
    if(diag<20u) ld_blog("home geometry cols=%d rows=%d want %dx%d",cols,rows,g_grid_cols,g_grid_rows);
    if(cols!=(direction?g_grid_rows:g_grid_cols) || rows!=(direction?g_grid_cols:g_grid_rows)) GEOM_BAIL(grid-size);
    if(diag==0u) {
        /* 扫描对象里的压缩指针，找类 ID == 0x80b（GridConfig getter 的哨兵类）的子对象 */
        unsigned char buf[0x120];
        if(ld_read_heap(grid,buf,sizeof buf)) {
            for(unsigned off=7; off+4<=sizeof buf; off+=4){
                uint32_t cp=0; memcpy(&cp,buf+off,4);
                if(!(cp&1) || cp==0xffffffffu) continue;
                uintptr_t obj=0;
                if(!ld_heap_pointer(c,grid,off)) continue;
                obj=ld_heap_pointer(c,grid,off);
                uint64_t hdr=0; if(!ld_read_heap(obj-1,&hdr,8)) continue;
                unsigned cid=(unsigned)((hdr>>12)&0xfffff);
                if(cid==0x80b){
                    double side=0,pad=0; uintptr_t box=0;
                    ld_read_heap(obj+0x1f,&side,8);
                    ld_read_heap(obj+0x57,&pad,8);
                    box=ld_heap_pointer(c,obj,0x3f);
                    double top=0; if(box) ld_read_heap(box+7,&top,8);
                    ld_blog("home geometry SUB found at grid+%02x obj=%p side=%.1f padbottom=%.1f top=%.1f",
                        off,(void*)obj,side,pad,top);
                }
            }
        }
    }
    uintptr_t sub=ld_heap_pointer(c,grid,0x1f),topbox=0;
    double side=0,top=0,bottom=0;
    if(!sub) GEOM_BAIL(sub-object);
    topbox=ld_heap_pointer(c,sub,0x3f);
    if(!ld_read_heap(sub+0x1f,&side,8) || !topbox
            || !ld_read_heap(topbox+7,&top,8) || !ld_read_heap(sub+0x57,&bottom,8)) GEOM_BAIL(fields);
    /* 水平偏移已由 GridController.currentConfig 后置 hook 处理（左右共用格宽，天然居中），
     * 这里只做顶部/底部，避免两套写入互相覆盖。 */
    double nside=side, ntop=top+o->top, nbottom=bottom+o->bottom;
    if(diag<20u) ld_blog("home geometry side %.1f->%.1f top %.1f->%.1f bottom %.1f->%.1f",
        side,nside,top,ntop,bottom,nbottom);
    if(!isfinite(nside)||!isfinite(ntop)||!isfinite(nbottom)
            || nside<-400||nside>800||ntop<-400||ntop>800||nbottom<-400||nbottom>800) GEOM_BAIL(insane);
    memcpy((void *)(sub+0x1f),&nside,8);
    memcpy((void *)(topbox+7),&ntop,8);
    memcpy((void *)(sub+0x57),&nbottom,8);
    __atomic_fetch_add(&g_geom_ok,1u,__ATOMIC_RELAXED);
    if(diag<20u) ld_blog("home geometry applied #%u", __atomic_load_n(&g_geom_ok,__ATOMIC_RELAXED));
    #undef GEOM_BAIL
}

#endif /* LD_HOME_GEOMETRY_H */
