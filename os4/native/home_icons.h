#ifndef LD_HOME_ICONS_H
#define LD_HOME_ICONS_H
#include <math.h>
#include <stdint.h>
#include <string.h>
#include "dart_heap.h"
/* Call once for the newly constructed GridConfig at ComputePipeline.compute
 * +0x33c (8583 vaddr 0x1998494), before AsyncReturn. The parent observer first
 * reads the full variables pointer at saved x29-0x18 and initializes grid+0x8f
 * with its low 32 bits, exactly as the displaced load/store will do again.
 * The 16-byte patch ends before the slow-path entry 0x19984a4, which remains
 * reachable from 0x1998228. From this normal exit, AsyncReturn does not read
 * x16 before invoking its own runtime continuation, so x16 is dead here.
 * The icon record at +0x6b
 * is freshly allocated by IconConfigHandler.handle. Consumers in AppIcon,
 * HotSeatsConstants2 and FolderPreviewIcon read its unboxed double +0x17.
 * No grid-count requirement: icon-only settings use this same pipeline.
 * Keep the cell strides and the separately computed +0x77 value untouched.
 */
static inline void ld_apply_home_icons(struct ld_dart_context *c,uintptr_t grid) {
    int percent=g_home_options.icon_size_percent;
    if(!g_hook_pin_ok || !g_home_options.icon_size_enabled || percent<80 || percent>120 || percent==100
            || !(grid&1) || grid==c->x[22]) return;
    uintptr_t variables=ld_heap_pointer(c,grid,0x8f),icons=ld_heap_pointer(c,grid,0x6b),fullscreen=0;
    uint32_t layout=0;
    /* Verified against GridController.isSplit and List.isEmpty: +0x20 is true.
     * isSplit returns true on NE, so equality with PP+0xef20 is fullscreen. */
    if(!variables || !icons || !ld_read_heap(variables+0x27,&layout,sizeof layout)
            || !ld_read_heap(c->x[27]+0xef20,&fullscreen,sizeof fullscreen)
            || !(layout&1) || !(fullscreen&1) || layout==(uint32_t)c->x[22]
            || layout!=(uint32_t)fullscreen) return;
    double size=0,top=0;
    if(!ld_read_heap(icons+0x17,&size,sizeof size) || !ld_read_heap(icons+0x1f,&top,sizeof top)
            || !isfinite(size) || size<=0 || !isfinite(top)) return;
    double scaled=size*((double)percent/100.0),shifted=top-(scaled-size)/2.0;
    /* calIconTopPadding subtracts iconSize before dividing by two in every
     * branch (fd9240/fd9298/fd92b8); all other inputs remain unchanged here. */
    if(!isfinite(scaled) || scaled<=0 || !isfinite(shifted)) return;
    memcpy((void *)(icons+0x17),&scaled,sizeof scaled);
    memcpy((void *)(icons+0x1f),&shifted,sizeof shifted);
}
#endif
