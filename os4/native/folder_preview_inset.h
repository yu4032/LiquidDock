#ifndef LIQUIDDOCK_FOLDER_PREVIEW_INSET_H
#define LIQUIDDOCK_FOLDER_PREVIEW_INSET_H

#include <math.h>

struct ld_folder_preview_rect {
    double x, y, width, height;
};

/* Shift all four outer edges equally by `inset` (positive = inset, negative = outset)
 * while preserving icon aspect ratio and keeping the 2x2 mini-grid aligned. */
static inline int ld_inset_folder_preview(double frame_width, double frame_height,
        double pad0, double big_w, double inset,
        const struct ld_folder_preview_rect *original,
        struct ld_folder_preview_rect *out)
{
    if (!isfinite(frame_width) || !isfinite(frame_height) || !isfinite(inset)
            || !isfinite(pad0) || !isfinite(big_w)
            || frame_width <= 0 || frame_height <= 0 || inset == 0.0
            || pad0 <= 0 || big_w <= 0
            || inset < -24.0 || inset > fmin(frame_width, frame_height) / 4.0
            || !isfinite(original->x) || !isfinite(original->y)
            || !isfinite(original->width) || !isfinite(original->height)
            || original->width <= 0 || original->height <= 0) return 0;

    double pad1 = fmax(0.0, pad0 + inset);
    double orig_cw = frame_width - 2.0 * pad0;
    double orig_ch = frame_height - 2.0 * pad0;
    double new_cw = frame_width - 2.0 * pad1;
    double new_ch = frame_height - 2.0 * pad1;
    if (orig_cw <= big_w || orig_ch <= big_w || new_cw <= 10.0 || new_ch <= 10.0) return 0;

    double scale_x = new_cw / orig_cw;
    double scale_y = new_ch / orig_ch;
    /* Only shrink icons when insetting inward; never enlarge them when offsetting outward. */
    double scale_icon = fmin(1.0, fmin(scale_x, scale_y));
    if (scale_icon <= 0.1) return 0;

    double new_big_w = big_w * scale_icon;
    double new_w = original->width * scale_icon;
    double new_h = original->height * scale_icon;

    if (original->width < big_w * 0.7) {
        /* 2x2 mini-icons in the last cell: keep anchored inside the bottom-right cell. */
        double orig_cell_x = frame_width - pad0 - big_w;
        double orig_cell_y = frame_height - pad0 - big_w;
        double new_cell_x = frame_width - pad1 - new_big_w;
        double new_cell_y = frame_height - pad1 - new_big_w;
        *out = (struct ld_folder_preview_rect){
            new_cell_x + (original->x - orig_cell_x) * scale_icon,
            new_cell_y + (original->y - orig_cell_y) * scale_icon,
            new_w,
            new_h,
        };
        return 1;
    }

    double span_x = orig_cw - big_w;
    double span_y = orig_ch - big_w;
    double new_span_x = new_cw - new_big_w;
    double new_span_y = new_ch - new_big_w;
    if (span_x <= 1.0 || span_y <= 1.0 || new_span_x <= 1.0 || new_span_y <= 1.0) return 0;

    double t_x = (original->x - pad0) / span_x;
    double t_y = (original->y - pad0) / span_y;
    *out = (struct ld_folder_preview_rect){
        pad1 + t_x * new_span_x,
        pad1 + t_y * new_span_y,
        new_w,
        new_h,
    };
    return 1;
}

#endif
