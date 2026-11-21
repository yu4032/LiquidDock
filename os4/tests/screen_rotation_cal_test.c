#include <assert.h>
#include <stdio.h>
#include "../native/screen_rotation_math.h"

static struct ld_screen_calibration step(struct ld_screen_calibration s, int cand)
{
    return ld_screen_calibration_step(s, cand);
}

int main(void)
{
    struct ld_screen_calibration s = {-1, 0, -1};

    /* Before calibration the orientation is UNKNOWN, even when a size sample
     * exists: that sample can belong to the previous display transaction. */
    s = step(s, 0);
    assert(s.votes == 1 && s.locked == -1);
    assert(ld_screen_land_for_event(3, s.locked) == -1);
    assert(ld_screen_land_for_event(0, s.locked) == -1);

    s = step(s, 0);
    assert(s.locked == 0);

    /* The 53ms regression: natural portrait, ROTATION_270 delivered while
     * getSize() still reports 1880x3008. The calibrated direction must win;
     * the stale measurement may not reopen the window. */
    assert(ld_screen_land_for_event(3, 0) == 1);
    assert(ld_screen_land_for_event(1, 0) == 1);
    assert(ld_screen_land_for_event(0, 0) == 0);
    assert(ld_screen_land_for_event(2, 0) == 0);

    /* Same for a naturally landscape panel. */
    assert(ld_screen_land_for_event(0, 1) == 1);
    assert(ld_screen_land_for_event(2, 1) == 1);
    assert(ld_screen_land_for_event(1, 1) == 0);
    assert(ld_screen_land_for_event(3, 1) == 0);

    /* The margin divisor must follow the corrected direction: native portrait
     * 4x8 becomes landscape 8x4 during the transition. */
    /* configured_cols/rows describe the LANDSCAPE grid (8x4 on turner);
     * the current config is still the portrait 4x8 profile. */
    struct ld_hm_layout_shape shape = ld_hm_layout_shape_for_display(
            0, 4, 8, 1, 8, 4);
    assert(shape.landscape == 1);
    assert(shape.cols == 8 && shape.rows == 4);
    assert(shape.provisional == 1);

    /* A stale run of two samples must not flip an established calibration;
     * overwriting it takes three consecutive contradicting samples. */
    s = step(s, 1);
    assert(s.locked == 0);
    s = step(s, 1);
    assert(s.locked == 0);
    s = step(s, 1);
    assert(s.locked == 1);
    assert(ld_screen_land_for_event(3, s.locked) == 0);

    /* Unknown samples leave the state untouched. */
    struct ld_screen_calibration keep = s;
    s = step(s, -1);
    assert(s.candidate == keep.candidate && s.votes == keep.votes &&
           s.locked == keep.locked);

    /* Without a calibration, resolution is always unknown. */
    assert(ld_screen_land_for_event(0, -1) == -1);
    assert(ld_screen_land_for_event(3, -1) == -1);
    assert(ld_screen_land_for_event(7, -1) == -1);

    printf("screen_rotation_cal_test: ok\n");
    return 0;
}
