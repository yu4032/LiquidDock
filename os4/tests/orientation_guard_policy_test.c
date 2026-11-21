#include <assert.h>
#include <stdio.h>
#include "../native/orientation_guard_policy.h"

int main(void)
{
    for (int install = 0; install < 4; ++install) {
        for (int raw = 0; raw < 4; ++raw) {
            int normalized = ld_orientation_normalize(raw, install);
            assert(normalized == ((raw + install) & 3));
            assert(ld_orientation_from_rotation(normalized) == (normalized & 1));
        }
    }
    /* 90 and 270 must remain distinguishable despite the same direction. */
    assert(ld_orientation_normalize(1, 0) == 1);
    assert(ld_orientation_normalize(3, 0) == 3);
    assert(ld_orientation_from_rotation(1) == ld_orientation_from_rotation(3));
    assert(ld_orientation_from_rotation(0) == ld_orientation_from_rotation(2));
    for (int i = -3; i <= 7; ++i) {
        if (i >= 0 && i <= 3) continue;
        assert(ld_orientation_normalize(i, 0) == LD_ORIENT_UNKNOWN);
        assert(ld_orientation_normalize(0, i) == LD_ORIENT_UNKNOWN);
        assert(ld_orientation_from_rotation(i) == LD_ORIENT_UNKNOWN);
    }
    puts("PASS normalized Display rotation: quarter turns, install orientation, fail-closed");
    return 0;
}
