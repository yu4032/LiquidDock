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

static inline int ld_screen_landscape(void)
{
    int rotation = ld_screen_rotation();
    return rotation < 0 ? -1 : (rotation & 1);
}

static void ld_rotation_publish(int rotation)
{
    if ((unsigned)rotation > 3u) return;
    int prev = __atomic_exchange_n(&g_ld_screen_rotation, rotation, __ATOMIC_ACQ_REL);
    if (prev != rotation)
        ld_blog("screen rotation=%d landscape=%d source=RotationHelper", rotation, rotation & 1);
}
#endif
