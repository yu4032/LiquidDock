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

    /* A delivered rotation can be paired with a size read from the previous
     * transaction. One such sample must not become the permanent panel
     * orientation: it is only a candidate until a second sample agrees. */
    s = step(s, 1);                     /* stale: 1880x3008 while rot=3 */
    assert(s.candidate == 1 && s.votes == 1 && s.locked == -1);
    assert(ld_screen_land_for_event(0, 3, s.locked) == 0);   /* measured wins */

    s = step(s, 0);                     /* corrected geometry */
    assert(s.candidate == 0 && s.votes == 1 && s.locked == -1);

    s = step(s, 0);
    assert(s.locked == 0 && s.votes == 2);

    /* Once locked, a lagging size read still resolves through the panel. */
    assert(ld_screen_land_for_event(-1, 3, s.locked) == 1);
    assert(ld_screen_land_for_event(-1, 0, s.locked) == 0);

    /* Fresh geometry stays authoritative even if it disagrees momentarily. */
    assert(ld_screen_land_for_event(0, 3, s.locked) == 0);

    /* Two consecutive contradicting samples correct an existing lock. */
    s = step(s, 1);
    assert(s.locked == 0);
    s = step(s, 1);
    assert(s.locked == 1);
    assert(ld_screen_land_for_event(-1, 3, s.locked) == 0);

    /* Unknown samples leave the state untouched. */
    struct ld_screen_calibration keep = s;
    s = step(s, -1);
    assert(s.candidate == keep.candidate && s.votes == keep.votes && s.locked == keep.locked);

    /* Land resolution without any calibration falls back to the measurement. */
    assert(ld_screen_land_for_event(1, 3, -1) == 1);
    assert(ld_screen_land_for_event(-1, 3, -1) == -1);
    assert(ld_screen_land_for_event(-1, 7, -1) == -1);

    printf("screen_rotation_cal_test: ok\n");
    return 0;
}
