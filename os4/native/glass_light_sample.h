#ifndef LD_OS4_GLASS_LIGHT_SAMPLE_H
#define LD_OS4_GLASS_LIGHT_SAMPLE_H
#include <math.h>
#include <stddef.h>

/* Diagnostic only. A valid record candidate contains five finite boxed
 * doubles in a narrow physical range. No field is assigned a meaning here.
 * This filter is intentionally conservative to avoid logging garbage memory. */
static inline int ld_os4_plausible_light_record(const double fields[5])
{
    if (!fields) return 0;
    unsigned nonzero=0;
    for(size_t i=0;i<5;i++) {
        if(!isfinite(fields[i]) || fabs(fields[i])>64.0) return 0;
        if(fabs(fields[i])>0.00001) nonzero++;
    }
    return nonzero>=2;
}
#endif
