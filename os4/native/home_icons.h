#ifndef LD_HOME_ICONS_H
#define LD_HOME_ICONS_H
#include <math.h>
#include <stdint.h>
#include <string.h>
#include "dart_heap.h"
/* Call once for the newly constructed GridConfig at ComputePipeline.compute
 * +0x33c (captured 8584 vaddr 0x19978c8), before AsyncReturn. The parent observer first
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
/* Workspace & AllApps icon sizing is now applied precisely in dock_icon_size.h
 * on ShortCutCommonMixin.launcherIconWidth/Height (non-Dock branch), leaving
 * GridConfig.icons.iconSize untouched so Dock, Dock background, Folders, and
 * Widgets are never polluted by Workspace icon scaling. */
static inline void ld_apply_home_icons(struct ld_dart_context *c, uintptr_t grid) {
    (void)c;
    (void)grid;
}
#endif
