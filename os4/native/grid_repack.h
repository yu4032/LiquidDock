#ifndef LIQUIDDOCK_GRID_REPACK_H
#define LIQUIDDOCK_GRID_REPACK_H

#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

struct ld_repack_item {
    long long id;
    int screen;
    int x;
    int y;
    int span_x;
    int span_y;
};

static inline int ld_repack_area(const struct ld_repack_item *item)
{
    return item->span_x * item->span_y;
}

static inline int ld_repack_fits(
    const unsigned char *occupied, int cols, int rows,
    int x, int y, int span_x, int span_y)
{
    if (x < 0 || y < 0 || span_x <= 0 || span_y <= 0 ||
        x + span_x > cols || y + span_y > rows) {
        return 0;
    }
    for (int px = x; px < x + span_x; px++) {
        for (int py = y; py < y + span_y; py++) {
            if (occupied[py * cols + px]) return 0;
        }
    }
    return 1;
}

static inline void ld_repack_occupy(
    unsigned char *occupied, int cols,
    int x, int y, int span_x, int span_y)
{
    for (int px = x; px < x + span_x; px++) {
        for (int py = y; py < y + span_y; py++) {
            occupied[py * cols + px] = 1;
        }
    }
}

static inline int ld_repack_before(
    const struct ld_repack_item *left, const struct ld_repack_item *right)
{
    int left_area = ld_repack_area(left);
    int right_area = ld_repack_area(right);
    if (left_area != right_area) return left_area > right_area;
    if (left->y != right->y) return left->y < right->y;
    if (left->x != right->x) return left->x < right->x;
    return left->id < right->id;
}

/* Packs as many items as possible; unplaced items receive x=-1,y=-1. */
static inline int ld_repack_screen_relaxed(
    struct ld_repack_item *items, int count, int cols, int rows)
{
    if (!items || count < 0 || cols <= 0 || rows <= 0 ||
        (size_t)cols > SIZE_MAX / (size_t)rows) {
        return 0;
    }

    unsigned char *occupied = (unsigned char *)calloc(
        (size_t)cols * (size_t)rows, 1);
    int *invalid = (int *)malloc((size_t)count * sizeof(int));
    if (!occupied || (count > 0 && !invalid)) {
        free(occupied);
        free(invalid);
        return 0;
    }

    int invalid_count = 0;
    for (int i = 0; i < count; i++) {
        struct ld_repack_item *item = &items[i];
        if (item->span_x <= 0) item->span_x = 1;
        if (item->span_y <= 0) item->span_y = 1;
        if (ld_repack_fits(
                occupied, cols, rows,
                item->x, item->y, item->span_x, item->span_y)) {
            ld_repack_occupy(
                occupied, cols, item->x, item->y,
                item->span_x, item->span_y);
        } else {
            invalid[invalid_count++] = i;
        }
    }

    for (int i = 0; i < invalid_count; i++) {
        for (int j = i + 1; j < invalid_count; j++) {
            if (ld_repack_before(&items[invalid[j]], &items[invalid[i]])) {
                int tmp = invalid[i];
                invalid[i] = invalid[j];
                invalid[j] = tmp;
            }
        }
    }

    for (int i = 0; i < invalid_count; i++) {
        struct ld_repack_item *item = &items[invalid[i]];
        if (item->span_x > cols || item->span_y > rows) {
            item->x = -1;
            item->y = -1;
            continue;
        }

        int preferred_x = item->x;
        int preferred_y = item->y;
        if (preferred_x < 0) preferred_x = 0;
        if (preferred_y < 0) preferred_y = 0;
        if (preferred_x > cols - item->span_x) preferred_x = cols - item->span_x;
        if (preferred_y > rows - item->span_y) preferred_y = rows - item->span_y;

        int best_x = -1, best_y = -1;
        int64_t best_distance = INT64_MAX;
        for (int y = 0; y <= rows - item->span_y; y++) {
            for (int x = 0; x <= cols - item->span_x; x++) {
                if (!ld_repack_fits(
                        occupied, cols, rows,
                        x, y, item->span_x, item->span_y)) {
                    continue;
                }
                int64_t dx = (int64_t)x - preferred_x;
                int64_t dy = (int64_t)y - preferred_y;
                int64_t distance = dx * dx + dy * dy;
                if (distance < best_distance) {
                    best_distance = distance;
                    best_x = x;
                    best_y = y;
                }
            }
        }
        if (best_x < 0) {
            item->x = -1;
            item->y = -1;
            continue;
        }
        item->x = best_x;
        item->y = best_y;
        ld_repack_occupy(
            occupied, cols, item->x, item->y,
            item->span_x, item->span_y);
    }

    free(occupied);
    free(invalid);
    int placed = 0;
    for (int i = 0; i < count; i++) {
        if (items[i].x >= 0 && items[i].y >= 0) placed++;
    }
    return placed;
}

/* Mutates item positions only when the whole screen can be packed. */
static inline int ld_repack_screen(
    struct ld_repack_item *items, int count, int cols, int rows)
{
    return ld_repack_screen_relaxed(items, count, cols, rows) == count;
}

#endif
