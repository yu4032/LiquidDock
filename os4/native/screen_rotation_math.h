#ifndef LD_SCREEN_ROTATION_MATH_H
#define LD_SCREEN_ROTATION_MATH_H

/* Geometry is the authority for physical landscape. Surface rotation is
 * only used to pair the dimensions with a delivered launcher event. */
struct ld_screen_dimensions {
    int width, height, rotation;
};
static inline int ld_screen_landscape_for_dimensions(int target_rotation,
                                                     struct ld_screen_dimensions size)
{
    if ((unsigned)target_rotation > 3u || size.rotation != target_rotation ||
        size.width < 80 || size.height < 80 || size.width == size.height)
        return -1;
    return size.width > size.height;
}

/* Calibrate once from an actual size + rotation pair. Unlike a raw
 * (rotation & 1) mapping, this supports BOTH natural-portrait and
 * natural-landscape displays. A fixed panel's natural orientation does not
 * change when a rotation callback arrives before its new size is visible. */
static inline int ld_screen_natural_landscape(int rotation,
                                              struct ld_screen_dimensions size)
{
    int measured = ld_screen_landscape_for_dimensions(rotation, size);
    return measured < 0 ? -1 : measured ^ (rotation & 1);
}

static inline int ld_screen_landscape_from_natural(int rotation, int natural)
{
    if ((unsigned)rotation > 3u || (natural != 0 && natural != 1))
        return -1;
    return natural ^ (rotation & 1);
}

/* Natural orientation is a property of the panel, but a sample can still be
 * wrong: a delivered rotation may be paired with a size read that still
 * belongs to the previous display transaction. Locking the first sample would
 * then invert every later event, so require two consecutive agreeing samples
 * and let two consecutive disagreeing samples correct an existing lock. */
struct ld_screen_calibration {
    int candidate;  /* last natural-landscape sample, -1 = none */
    int votes;      /* consecutive samples agreeing with candidate */
    int locked;     /* established natural landscape, -1 = not yet */
};

static inline struct ld_screen_calibration
ld_screen_calibration_step(struct ld_screen_calibration s, int candidate)
{
    if (candidate != 0 && candidate != 1)
        return s;
    if (candidate == s.candidate) {
        if (s.votes < 2) s.votes++;
    } else {
        s.candidate = candidate;
        s.votes = 1;
    }
    if (s.votes >= 2) s.locked = candidate;
    return s;
}

/* A size sample paired with this event is authoritative; calibration only
 * fills the gap left by a lagging size read, so a stale calibration can
 * never overwrite fresh geometry. */
static inline int ld_screen_land_for_event(int measured, int rotation,
                                           int locked_natural)
{
    if (measured == 0 || measured == 1) return measured;
    return ld_screen_landscape_from_natural(rotation, locked_natural);
}

struct ld_hm_layout_shape {
    int landscape, cols, rows, adjusted, provisional;
};

static inline int ld_hm_valid_count(int n)
{
    return n >= 2 && n <= 16;
}

/* Resolve orientation together with the divisor used for cellW/cellH.
 * configured_cols/rows are the LANDSCAPE layout grid; the portrait
 * profile exchanges them (not physical screen pixel dimensions).
 *
 * An inactive/stale currentConfig may still describe the old orientation.
 * Such calls may use the current target geometry, but must not create a
 * permanent native baseline under the new orientation until Dart catches up.
 */
static inline struct ld_hm_layout_shape
ld_hm_layout_shape_for_display(int cfg_dir, int cfg_cols, int cfg_rows,
                               int display_land, int configured_cols,
                               int configured_rows)
{
    struct ld_hm_layout_shape out = {-1, 0, 0, 0, 0};
    int config_valid = ld_hm_valid_count(cfg_cols) && ld_hm_valid_count(cfg_rows);
    int target_counts_valid = ld_hm_valid_count(configured_cols) &&
                              ld_hm_valid_count(configured_rows);

    if (config_valid) {
        out.cols = cfg_cols;
        out.rows = cfg_rows;
    }
    if (cfg_dir == 0 || cfg_dir == 1) out.landscape = cfg_dir;

    if (display_land == 0 || display_land == 1) {
        if (out.landscape != display_land) {
            out.provisional = 1;
            out.adjusted = 1;
            out.landscape = display_land;
            if (target_counts_valid) {
                out.cols = display_land ? configured_cols : configured_rows;
                out.rows = display_land ? configured_rows : configured_cols;
            } else if (config_valid) {
                /* Count pair belongs to the old direction; transpose both. */
                out.cols = cfg_rows;
                out.rows = cfg_cols;
            }
        } else if (target_counts_valid) {
            int target_cols = display_land ? configured_cols : configured_rows;
            int target_rows = display_land ? configured_rows : configured_cols;
            /* Even with the enum updated, columns/rows may lag. Only
             * override them if they EXACTLY match the opposite profile.
             * Avoid guessing about custom AllApps grids. */
            if (config_valid && out.cols == target_rows &&
                out.rows == target_cols &&
                (target_cols != target_rows)) {
                out.cols = target_cols;
                out.rows = target_rows;
                out.adjusted = 1;
                out.provisional = 1;
            }
        }
        if (!config_valid && target_counts_valid) {
            out.cols = display_land ? configured_cols : configured_rows;
            out.rows = display_land ? configured_rows : configured_cols;
            out.adjusted = 1;
            out.provisional = 1;
        }
    }
    return out;
}
#endif
