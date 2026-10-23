#ifndef LD_DOCK_BOTTOM_OFFSET_H
#define LD_DOCK_BOTTOM_OFFSET_H

#include <math.h>
#include <stdint.h>
#include <string.h>

/*
 * The fresh GridConfig Insets.bottom is a signed translation consumed by the
 * vendor HotSeatsMarginBottom calculation. Preserve the original screen-grid
 * dimensions and modify only this newly built inset.
 *
 * Existing LiquidDock convention: positive bottom offset moves Dock upward.
 * Insets.bottom is negative, hence delta = -offset.
 * This is not a post-layout View translation or a Dock background resize.
 */
static int ld_dock_bottom_inset(double original, double offset, double *result)
{
    if(!result || !isfinite(original) || !isfinite(offset)
            || offset < -30 || offset > 40) return 0;
    double next=original-offset;
    if(!isfinite(next)) return 0;
    *result=next;
    return 1;
}

#ifndef LD_DOCK_BOTTOM_TEST_ONLY
static void ld_apply_dock_bottom_offset(struct ld_dart_context *c,uintptr_t grid)
{
    const double offset=g_home_options.dock.bottom;
    if(!g_hook_pin_ok || !g_home_options.dock.enabled || offset==0) return;

    /* Same active fullscreen Dock gate as the other geometry observers.
     * A laptop/workstation Dock is a different placement owner. */
    if(!ld_dock_active(c)) return;

    uintptr_t variables=ld_heap_pointer(c,grid,0x8f),fullscreen=0;
    uint32_t layout=0;
    if(!variables || !ld_read_heap(variables+0x27,&layout,4)
            || !ld_read_heap(c->x[27]+0xef20,&fullscreen,8)
            || !(layout&1) || layout!=(uint32_t)fullscreen) return;

    uintptr_t insets=ld_heap_pointer(c,grid,0x3b);
    double original=0, shifted=0;
    if(!insets || !ld_read_heap(insets+0x1f,&original,8)
            || !ld_dock_bottom_inset(original,offset,&shifted)) return;
    memcpy((void *)(insets+0x1f),&shifted,8);
}
#endif
#endif
