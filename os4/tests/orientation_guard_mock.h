#ifndef LD_TEST_ORIENTATION_GUARD_MOCK_H
#define LD_TEST_ORIENTATION_GUARD_MOCK_H
/* Native host tests do not load ART. Model only the published framework state.
 * Production uses orientation_guard.h via native_entry.c. */
#include <stdint.h>
#define LD_ORIENT_UNKNOWN (-1)
static int ld_test_framework_direction = 1; /* 0 portrait / 1 landscape */
static uint64_t ld_test_framework_generation = 1;
static inline int ld_orientation_guard_direction(void) {
    return ld_test_framework_direction;
}
static inline int ld_orientation_guard_rotation(void) {
    return ld_test_framework_direction;
}
static inline int ld_orientation_guard_for_grid(int grid_enum) {
    return grid_enum == ld_test_framework_direction ? grid_enum : LD_ORIENT_UNKNOWN;
}
static inline int ld_orientation_guard_refresh_now(void) {
    return ld_test_framework_direction;
}
static inline uint64_t ld_orientation_generation(void) {
    return ld_test_framework_generation;
}
static inline int ld_orientation_guard_generation_matches(uint64_t generation) {
    return generation == ld_test_framework_generation &&
           ld_test_framework_direction >= 0;
}
static inline void ld_test_set_framework_orientation(int direction) {
    if (ld_test_framework_direction != direction) {
        ld_test_framework_direction = direction;
        ++ld_test_framework_generation;
    }
}
#endif
