#ifndef LIQUIDDOCK_FOLDER_SIZE_POLICY_H
#define LIQUIDDOCK_FOLDER_SIZE_POLICY_H

#include <math.h>
#include <stdint.h>

/* Same edge-to-edge geometry as WidgetGridSizing: the last icon starts
 * (span - 1) pitches after the first, and contributes its own width. */
static inline int ld_icon_aligned_extent(int64_t span, double pitch, double icon,
                                         double *extent)
{
    if (span < 1 || span > 10 || !isfinite(pitch) || !isfinite(icon)
            || icon <= 0.0 || pitch < icon) return 0;
    double width = (double)(span - 1) * pitch + icon;
    if (!isfinite(width) || width > 2400.0) return 0;
    *extent = width;
    return 1;
}
#endif
