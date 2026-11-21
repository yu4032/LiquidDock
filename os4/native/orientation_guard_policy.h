#ifndef LD_ORIENTATION_GUARD_POLICY_H
#define LD_ORIENTATION_GUARD_POLICY_H

#define LD_ORIENT_UNKNOWN (-1)

/* Same normalization used by Java's FrameworkDisplayRotation. */
static inline int ld_orientation_normalize(int rotation, int install)
{
    if ((unsigned)rotation > 3u || (unsigned)install > 3u)
        return LD_ORIENT_UNKNOWN;
    return (rotation + install) & 3;
}

static inline int ld_orientation_from_rotation(int normalized)
{
    return (unsigned)normalized <= 3u ? (normalized & 1) : LD_ORIENT_UNKNOWN;
}
#endif
