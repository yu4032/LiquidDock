#ifndef LD_SCREEN_ROTATION_H
#define LD_SCREEN_ROTATION_H
#include <stdint.h>
#include <stdatomic.h>
#include "screen_rotation_math.h"

/* One atomic publication pairs the delivered raw Surface quarter turn and
 * a framework getSize(width,height) sampled for THAT SAME rotation. It must
 * never combine a new rotation with dimensions from the previous event.
 *
 * bits [0,7] rotation+1; [8,9] landscape+1; [16,31] width; [32,47] height.
 * UNKNOWN landscape=0 (stored as zero); 1=portrait, 2=landscape.
 */
static atomic_uint_fast64_t g_ld_screen_state = ATOMIC_VAR_INIT(0);
static atomic_int g_ld_screen_natural_orientation = ATOMIC_VAR_INIT(-1);

static inline int ld_screen_rotation(void)
{
    unsigned raw = (unsigned)(atomic_load_explicit(&g_ld_screen_state,
                                          memory_order_acquire) & UINT64_C(0xff));
    return raw >= 1u && raw <= 4u ? (int)raw - 1 : -1;
}

static inline int ld_screen_landscape(void)
{
    uint64_t packed = atomic_load_explicit(&g_ld_screen_state, memory_order_acquire);
    unsigned encoded = (unsigned)((packed >> 8) & UINT64_C(3));
    return encoded == 1u || encoded == 2u ? (int)encoded - 1 : -1;
}

static inline void ld_rotation_publish(int rotation, struct ld_screen_dimensions dimensions)
{
    if ((unsigned)rotation > 3u) return;

    int measured = ld_screen_landscape_for_dimensions(rotation, dimensions);
    int natural = atomic_load_explicit(&g_ld_screen_natural_orientation,
                                       memory_order_acquire);
    if (natural < 0 && measured >= 0) {
        int candidate = ld_screen_natural_landscape(rotation, dimensions);
        int expected = -1;
        if (atomic_compare_exchange_strong_explicit(&g_ld_screen_natural_orientation,
                &expected, candidate, memory_order_acq_rel, memory_order_acquire)) {
            natural = candidate;
            ld_blog("screen natural orientation calibrated land=%d from rot=%d size=%dx%d",
                    natural, rotation, dimensions.width, dimensions.height);
        } else {
            natural = expected;
        }
    }

    /* A size reading may lag a delivered display event by one transaction.
     * Once natural orientation has been calibrated from physical dimensions,
     * prefer the target rotation transformed through that calibration; do
     * not publish a stale width/height as the target's current geometry. */
    int land = natural >= 0
             ? ld_screen_landscape_from_natural(rotation, natural)
             : measured;
    int size_matches = measured >= 0 && measured == land;
    unsigned width = size_matches && (unsigned)dimensions.width <= 65535u
                   ? (unsigned)dimensions.width : 0u;
    unsigned height = size_matches && (unsigned)dimensions.height <= 65535u
                    ? (unsigned)dimensions.height : 0u;

    uint64_t packed = ((uint64_t)height << 32) | ((uint64_t)width << 16) |
                      ((uint64_t)(land + 1) << 8) | (uint64_t)(rotation + 1);
    uint64_t previous = atomic_exchange_explicit(&g_ld_screen_state,
                                                packed, memory_order_acq_rel);
    if (previous != packed) {
        ld_blog("screen rotation=%d size=%ux%u land=%d measured_land=%d natural=%d source=RotationHelper+Display.getSize",
                rotation, width, height, land, measured, natural);
    }
}

#endif
