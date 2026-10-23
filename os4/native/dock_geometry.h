#ifndef LD_DOCK_GEOMETRY_H
#define LD_DOCK_GEOMETRY_H
#include <math.h>
#include <string.h>
#include "dart_heap.h"

static int ld_dock_background(const struct ld_dock_conf *o,double *width,double *height,double *corner)
{
    if(!o->enabled || (o->width==0 && o->height==0 && o->corner==0)) return 0;
    double w=*width+o->width,h=*height+o->height,r=*corner+o->corner;
    if(!isfinite(w) || !isfinite(h) || !isfinite(r) || w<1 || h<1) return 0;
    if(o->corner!=0 && r<1) r=1; /* The native GlassOs4Background minimum. */
    *width=w; *height=h; *corner=r; return 1;
}

static int ld_dock_active(struct ld_dart_context *c)
{
    if(!g_hook_pin_ok || !g_home_options.dock.enabled) return 0;
    uintptr_t fields=0,controller=0,fullscreen=0,sentinel=0;
    if(!ld_read_heap(c->x[26]+0x78,&fields,8) || !fields
            || !ld_read_heap(fields+0x27d8,&controller,8)
            || !ld_read_heap(c->x[27]+0x40,&sentinel,8)
            || !(controller&1) || controller==c->x[22] || controller==sentinel) return 0;
    /* HotSeatsConstants2._isLaptopMode: static+25d0 -> Rx+73 -> value+13.
     * Its vendor exception path is false, so an uninitialized owner is normal. */
    uintptr_t laptop_owner=0;
    if(ld_read_heap(fields+0x25d0,&laptop_owner,8) && (laptop_owner&1)
            && laptop_owner!=sentinel && laptop_owner!=c->x[22]) {
        uintptr_t laptop_rx=ld_heap_pointer(c,laptop_owner,0x73);
        if(laptop_rx && ld_heap_u32(laptop_rx,0x13)==(uint32_t)(c->x[22]+0x20)) return 0;
    }
    uintptr_t rx=ld_heap_pointer(c,controller,0x2b),grid=ld_heap_pointer(c,rx,0x13);
    uintptr_t variables=ld_heap_pointer(c,grid,0x8f);
    uintptr_t orientation=ld_heap_pointer(c,variables,0x23);
    int64_t direction=ld_heap_i64(orientation,7);
    uint32_t mode=0;
    return rx && grid && variables && orientation && (direction==0 || direction==1)
        && ld_read_heap(variables+0x27,&mode,4)
        && ld_read_heap(c->x[27]+0xef20,&fullscreen,8)
        && (mode&1) && (fullscreen&1) && mode==(uint32_t)fullscreen;
}

static void *g_orig_dock_background __attribute__((used));
static void *g_orig_dock_spacing __attribute__((used));
__attribute__((used)) static void ld_observe_dock_background(struct ld_dart_context *c)
{
    if(g_home_options.dock.width==0 && g_home_options.dock.height==0 && g_home_options.dock.corner==0) return;
    if(!ld_dock_active(c)) return;
    uintptr_t params=c->x[2],size=ld_heap_pointer(c,params,7);
    double width=0,height=0,corner=0;
    if(!size || !ld_read_heap(size+7,&width,8) || !ld_read_heap(size+0xf,&height,8)
            || !ld_read_heap(params+0xb,&corner,8)
            || !ld_dock_background(&g_home_options.dock,&width,&height,&corner)) return;
    /* Fresh params, before equality comparison and publication to the native
     * background animation. Workspace reserves and icon sizes stay untouched. */
    memcpy((void *)(size+7),&width,8); memcpy((void *)(size+0xf),&height,8);
    memcpy((void *)(params+0xb),&corner,8);
}
__attribute__((used)) static void ld_observe_dock_spacing(struct ld_dart_context *c)
{
    if(!ld_dock_active(c) || g_home_options.dock.spacing==0) return;
    uintptr_t size=c->x[0]; double width=0;
    if(!(size&1) || !ld_read_heap(size+7,&width,8)) return;
    /* Legacy item decoration adds the configured distance to BOTH sides.
     * This Size is newly allocated on every _defaultIconSize invocation. */
    width+=2*g_home_options.dock.spacing;
    if(!isfinite(width) || width<1) return;
    memcpy((void *)(size+7),&width,8);
}
static void *g_orig_dock_icon_padding __attribute__((used));
__attribute__((used)) static void ld_observe_dock_icon_padding(struct ld_dart_context *c)
{
    if(g_home_options.dock.spacing==0 || !ld_dock_active(c)) return;
    double stride,icon,scale;
    memcpy(&stride,c->q[5],8); memcpy(&icon,c->q[6],8); memcpy(&scale,c->q[2],8);
    double baseline=stride-2*g_home_options.dock.spacing*scale;
    if(!isfinite(stride) || !isfinite(icon) || !isfinite(scale) || stride<=0 || baseline<=0) return;
    icon*=baseline/stride;
    memcpy(c->q[6],&icon,8);
}
static void *g_orig_dock_function_size __attribute__((used));
__attribute__((used)) static void ld_observe_dock_function_size(struct ld_dart_context *c)
{
    if(g_home_options.dock.spacing==0 || !ld_dock_active(c)) return;
    double base,cell; memcpy(&base,c->q[4],8);
    double adjusted=base+2*g_home_options.dock.spacing;
    if(!isfinite(base) || base<=0 || adjusted<=0
        || !ld_read_heap(c->x[29]-8,&cell,8) || !isfinite(cell)) return;
    cell*=base/adjusted;
    memcpy((void *)(c->x[29]-8),&cell,8);
}
static void *g_orig_dock_container_height __attribute__((used));
__attribute__((used)) static void ld_observe_dock_container_height(struct ld_dart_context *c)
{
    if(g_home_options.dock.height<=0 || !ld_dock_active(c)) return;
    uintptr_t box=c->x[2]; double height;
    if(!(box&1) || !ld_read_heap(box+7,&height,8)) return;
    height+=g_home_options.dock.height;
    if(isfinite(height)) memcpy((void *)(box+7),&height,8);
}
#ifndef LD_DOCK_NO_BRIDGE
LD_DART_OBSERVER(repl_dock_function_size,ld_observe_dock_function_size,g_orig_dock_function_size)
LD_DART_OBSERVER(repl_dock_icon_padding,ld_observe_dock_icon_padding,g_orig_dock_icon_padding)
LD_DART_OBSERVER(repl_dock_container_height,ld_observe_dock_container_height,g_orig_dock_container_height)
LD_DART_OBSERVER(repl_dock_background,ld_observe_dock_background,g_orig_dock_background)
LD_DART_OBSERVER(repl_dock_spacing,ld_observe_dock_spacing,g_orig_dock_spacing)
#endif
#endif
