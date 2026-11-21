/*
 * This hook already exists at the vendor's PadRowColHandler boundary.
 * Preserve the complete Dart AOT ABI with the shared observer bridge, then
 * read framework orientation ON DEMAND. No orientation hook or periodic poll.
 *
 * Native x0 = column count, x2 = row count. The configured pair is stored
 * landscape first (cols x rows), and transposed in portrait.
 * If framework state cannot be read, preserve the vendor counts unchanged.
 */
#ifndef LD_PAD_COUNTS_WRAPPER_H
#define LD_PAD_COUNTS_WRAPPER_H

#include "dart_observer.h"

#define LD_PAD_COUNTS_WRAPPER(name, cols, rows, orig, pool, trace) \
__attribute__((used)) static void name##_observe(struct ld_dart_context *c) { \
    __atomic_fetch_add(&(trace)[0], 1u, __ATOMIC_RELAXED); \
    __atomic_store_n(&(trace)[1], (unsigned)c->x[0], __ATOMIC_RELAXED); \
    __atomic_store_n(&(trace)[2], (unsigned)c->x[2], __ATOMIC_RELAXED); \
    __atomic_store_n(&(pool), (uintptr_t)c->x[27], __ATOMIC_RELAXED); \
    int physical = ld_orientation_guard_refresh_now(); \
    if (physical == 0) { \
        int cc = __atomic_load_n(&(cols), __ATOMIC_RELAXED); \
        int rr = __atomic_load_n(&(rows), __ATOMIC_RELAXED); \
        if (cc > 0 && rr > 0) { \
            c->x[0] = (uint64_t)rr; \
            c->x[2] = (uint64_t)cc; \
        } \
    } else if (physical == 1) { \
        int cc = __atomic_load_n(&(cols), __ATOMIC_RELAXED); \
        int rr = __atomic_load_n(&(rows), __ATOMIC_RELAXED); \
        if (cc > 0 && rr > 0) { \
            c->x[0] = (uint64_t)cc; \
            c->x[2] = (uint64_t)rr; \
        } \
    } \
    __atomic_store_n(&(trace)[3], (unsigned)c->x[2], __ATOMIC_RELAXED); \
    __atomic_store_n(&(trace)[4], (unsigned)c->x[0], __ATOMIC_RELAXED); \
} \
LD_DART_OBSERVER(name, name##_observe, orig)

#endif
