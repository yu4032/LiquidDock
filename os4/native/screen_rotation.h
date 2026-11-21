#ifndef LD_SCREEN_ROTATION_H
#define LD_SCREEN_ROTATION_H
#include <stdint.h>

/* The vendor's RotationHelper.updateRotation receives the Surface quarter
 * turn 0/1/2/3. It is an event-backed launcher orientation, not a live
 * DisplayInfo read. UNKNOWN until the first valid startup/event callback. */
static int g_ld_screen_rotation __attribute__((used)) = -1;

static inline int ld_screen_rotation(void)
{
    return __atomic_load_n(&g_ld_screen_rotation, __ATOMIC_ACQUIRE);
}

/* Raw Surface quarter turns are not enough to derive physical landscape:
 * tablets can have a natural landscape orientation. Consumers requiring a
 * physical portrait/landscape profile must normalize installation rotation. */
static void ld_rotation_publish(int rotation)
{
    if ((unsigned)rotation > 3u) return;
    int prev = __atomic_exchange_n(&g_ld_screen_rotation, rotation, __ATOMIC_ACQ_REL);
    if (prev != rotation)
        ld_blog("screen rotation=%d source=RotationHelper", rotation);
}
#endif
