/* Apply indicator Y tuning to the Matrix4 stored by the verified Transform.scale
 * widget. The earlier Offset at 0x15361c8 is only the scale origin; the scale is
 * identity, so changing that origin does not move the indicator. */
#ifndef LD_HOME_INDICATOR_H
#define LD_HOME_INDICATOR_H
#include <math.h>
#include <stdint.h>
#include <string.h>
#include "grid_orientation.h"

static void *g_orig_home_indicator __attribute__((used));
#ifndef LD_HOME_INDICATOR_WRITE_HEAP
#define LD_HOME_INDICATOR_WRITE_HEAP(address, source, size) \
    (memcpy((void *)(address),(source),(size)),1)
#endif

__attribute__((used)) static void ld_observe_home_indicator(struct ld_dart_context *c)
{
    if (g_grid_cols <= 0 || g_count_cols <= 0 || !g_hook_pin_ok) return;
    uintptr_t fields=0, controller=0, sentinel=0, fullscreen=0;
    if (!ld_read_heap(c->x[26]+0x78,&fields,sizeof fields) || !fields ||
        !ld_read_heap(fields+0x27d8,&controller,sizeof controller) ||
        !ld_read_heap(c->x[27]+0x40,&sentinel,sizeof sentinel) ||
        !(controller&1) || controller==c->x[22] || controller==sentinel) return;
    uintptr_t rx=ld_heap_pointer(c,controller,0x2b);
    uintptr_t grid=ld_heap_pointer(c,rx,0x13);
    uintptr_t variables=ld_heap_pointer(c,grid,0x8f);
    uint32_t layout=0;
    /* GridController.isSplit compares variables+0x27 to PP+0xef20.
     * CSEL NE => null+0x20 (true); equality is the fullscreen layout. */
    if (!rx || !grid || !variables ||
        !ld_read_heap(variables+0x27,&layout,sizeof layout) ||
        !ld_read_heap(c->x[27]+0xef20,&fullscreen,sizeof fullscreen) ||
        !(layout&1) || !(fullscreen&1) || layout==(uint32_t)c->x[22] ||
        layout!=(uint32_t)fullscreen) return;
    int direction=ld_grid_direction(c,grid);
    if (direction!=0 && direction!=1) return;
    double delta=direction==0 ? g_home_options.portrait.indicator_y :
                                g_home_options.landscape.indicator_y;
    if (!isfinite(delta) || delta==0.0) return;

    /* At this post-constructor boundary, [fp-0x38] is the fresh Transform.scale
     * widget. Its compressed matrix field points to a Matrix4, whose compressed
     * storage field points to inline Float64List data. Matrix4 translation Y is
     * element 13 (data+0x17+13*8 == data+0x7f). */
    uintptr_t widget=0;
    if (!ld_read_heap(c->x[29]-0x38,&widget,sizeof widget) || !(widget&1)) return;
    uintptr_t matrix=ld_heap_pointer(c,widget,0xf);
    uintptr_t data=ld_heap_pointer(c,matrix,7);
    double original=0;
    if (!matrix || !data || !ld_read_heap(data+0x7f,&original,sizeof original)) return;
    double shifted=original+delta;
    if (!isfinite(original) || !isfinite(shifted)) return;
    (void)LD_HOME_INDICATOR_WRITE_HEAP(data+0x7f,&shifted,sizeof shifted);
}

#ifndef LD_HOME_INDICATOR_NO_BRIDGE
LD_DART_OBSERVER(repl_home_indicator, ld_observe_home_indicator, g_orig_home_indicator)
#endif
#endif
