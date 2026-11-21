#include <assert.h>
#include <stdio.h>
#include "../native/screen_rotation_math.h"

static void shape(int raw_dir, int cfg_cols, int cfg_rows, int display,
                  int configured_cols, int configured_rows,
                  int land, int cols, int rows, int provisional)
{
    struct ld_hm_layout_shape x = ld_hm_layout_shape_for_display(
            raw_dir, cfg_cols, cfg_rows,
            display, configured_cols, configured_rows);
    assert(x.landscape == land);
    assert(x.cols == cols && x.rows == rows);
    assert(x.provisional == provisional);
}

int main(void)
{
    /* Surface ROTATION_0 means opposite physical orientations on the two
     * naturally oriented panel types. Size, not quarter-turn parity, wins. */
    assert(ld_screen_landscape_for_dimensions(0,
        (struct ld_screen_dimensions){1880,3008,0}) == 0);
    assert(ld_screen_landscape_for_dimensions(0,
        (struct ld_screen_dimensions){3008,1880,0}) == 1);
    assert(ld_screen_landscape_for_dimensions(3,
        (struct ld_screen_dimensions){3008,1880,3}) == 1);
    assert(ld_screen_landscape_for_dimensions(2,
        (struct ld_screen_dimensions){1880,3008,2}) == 0);
    assert(ld_screen_landscape_for_dimensions(3,
        (struct ld_screen_dimensions){3008,1880,2}) == -1);
    assert(ld_screen_landscape_for_dimensions(0,
        (struct ld_screen_dimensions){3008,3008,0}) == -1);
    assert(ld_screen_landscape_for_dimensions(4,
        (struct ld_screen_dimensions){3008,1880,4}) == -1);

    /* The ~53ms transition: surface already landscape, currentConfig still
     * portrait. Switch BOTH direction and row/column divisors together. */
    shape(0, 4, 8, 1, 8, 4, 1, 8, 4, 1);
    shape(1, 8, 4, 0, 8, 4, 0, 4, 8, 1);
    shape(0, 4, 6, 1, 0, 0, 1, 6, 4, 1);
    shape(1, 4, 6, 1, 6, 4, 1, 6, 4, 1);

    /* Already committed geometry gets its original baseline/profile. */
    shape(1, 8, 4, 1, 8, 4, 1, 8, 4, 0);
    shape(0, 4, 8, 0, 8, 4, 0, 4, 8, 0);

    /* Native-landscape tablet: orientation is supplied from screen SIZE. */
    shape(0, 4, 8, 1, 8, 4, 1, 8, 4, 1);

    /* Square grid is not transposed spuriously. */
    shape(0, 5, 5, 1, 5, 5, 1, 5, 5, 1);
    shape(1, 5, 5, 1, 5, 5, 1, 5, 5, 0);

    /* If JNI is unavailable/lagging, retain the vendor's GridConfig. */
    shape(0, 4, 8, -1, 8, 4, 0, 4, 8, 0);
    shape(1, 8, 4, -1, 8, 4, 1, 8, 4, 0);

    /* Active AllApps may have a custom non-transposed layout: preserve it
     * when its direction already agrees, rather than forcing global sizes. */
    shape(1, 7, 4, 1, 8, 4, 1, 7, 4, 0);
    shape(0, 4, 7, 0, 8, 4, 0, 4, 7, 0);
    puts("PASS screen rotation size normalization and transitional grid divisors");
}
