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
/* Packed ld_screen_calibration: candidate+1 [0,1], votes [4,5], locked+1 [8,9]. */
static atomic_uint g_ld_screen_calibration = ATOMIC_VAR_INIT(0);

static inline struct ld_screen_calibration ld_screen_cal_unpack(unsigned v)
{
    struct ld_screen_calibration s;
    unsigned c = v & 3u, k = (v >> 4) & 3u, l = (v >> 8) & 3u;
    s.candidate = c ? (int)c - 1 : -1;
    s.votes = (int)k;
    s.locked = l ? (int)l - 1 : -1;
    return s;
}

static inline unsigned ld_screen_cal_pack(struct ld_screen_calibration s)
{
    unsigned c = (unsigned)(s.candidate + 1) & 3u;
    unsigned k = (unsigned)s.votes & 3u;
    unsigned l = (unsigned)(s.locked + 1) & 3u;
    return c | (k << 4) | (l << 8);
}

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
    int candidate = ld_screen_natural_landscape(rotation, dimensions);
    unsigned cal = atomic_load_explicit(&g_ld_screen_calibration, memory_order_acquire);
    for (;;) {
        struct ld_screen_calibration before = ld_screen_cal_unpack(cal);
        struct ld_screen_calibration after = ld_screen_calibration_step(before, candidate);
        unsigned want = ld_screen_cal_pack(after);
        if (want == cal) break;
        if (atomic_compare_exchange_weak_explicit(&g_ld_screen_calibration, &cal, want,
                memory_order_acq_rel, memory_order_acquire)) {
            if (after.locked != before.locked)
                ld_blog("screen natural orientation calibrated land=%d after %d agreeing samples",
                        after.locked, after.votes);
            break;
        }
    }
    int natural = ld_screen_cal_unpack(
            atomic_load_explicit(&g_ld_screen_calibration, memory_order_acquire)).locked;

    /* A size reading may lag a delivered display event by one transaction.
     * Prefer the paired measurement whenever it exists; fall back to the
     * calibrated natural orientation only when the size read is late. */
    int land = ld_screen_land_for_event(measured, rotation, natural);
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
