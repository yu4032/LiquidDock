#include "folder_preview_inset.h"
#include "folder_size_policy.h"
#include "grid_conf.h"

#include <math.h>
#include <stdio.h>

int main(void)
{
    struct ld_grid_conf conf = {0};
    const char *targets =
            "big_folder_enabled=1\n"
            "folder_size_targets=0x9000:128,0xa000:24\n"
            "end=1\n";
    if (!ld_grid_conf_parse(targets, strlen(targets), &conf)
            || !conf.folder_size_targets_present
            || conf.folder_size_vaddr[0] != 0x9000
            || conf.folder_size_size[1] != 24) {
        puts("FAIL: fingerprint-resolved folder size targets were not parsed");
        return 1;
    }

    const char *duplicate_targets =
            "folder_size_targets=0x9000:128,0xa000:24\n"
            "folder_size_targets=0x9000:128,0xa000:24\n"
            "end=1\n";
    if (ld_grid_conf_parse(duplicate_targets, strlen(duplicate_targets), &conf)) {
        puts("FAIL: repeated folder size targets were accepted");
        return 1;
    }

    double width, height;
    if (!ld_icon_aligned_extent(4, 100.0, 70.0, &width)
            || !ld_icon_aligned_extent(2, 80.0, 70.0, &height)
            || width != 370.0 || height != 150.0
            || ld_icon_aligned_extent(4, 60.0, 70.0, &width)) {
        puts("FAIL: invalid icon aligned extent");
        return 1;
    }

    struct ld_folder_preview_rect orig_tl = {22.66, 22.66, 59.69, 59.69};
    struct ld_folder_preview_rect orig_mini = {310.59, 87.15, 28.47, 28.47};
    struct ld_folder_preview_rect adj = {0};

    /* Negative inset (outward offset) must never enlarge icons beyond 1.0x. */
    if (!ld_inset_folder_preview(393.08, 169.74, 22.66, 59.69, -10.0, &orig_tl, &adj)
            || fabs(adj.x - 12.66) > 1e-6 || fabs(adj.y - 12.66) > 1e-6
            || fabs(adj.width - 59.69) > 1e-6 || fabs(adj.height - 59.69) > 1e-6) {
        puts("FAIL: negative preview inset enlarged or mispositioned top-left icon");
        return 1;
    }

    /* Extreme negative inset must clamp pad1 >= 0 and still keep icon size unchanged. */
    if (!ld_inset_folder_preview(393.08, 169.74, 22.66, 59.69, -24.0, &orig_tl, &adj)
            || adj.x < 0.0 || adj.y < 0.0
            || fabs(adj.width - 59.69) > 1e-6 || fabs(adj.height - 59.69) > 1e-6) {
        puts("FAIL: extreme negative preview inset clipped or enlarged icon");
        return 1;
    }

    /* 2x2 mini-icons under negative inset must not enlarge and must stay aligned. */
    if (!ld_inset_folder_preview(393.08, 169.74, 22.66, 59.69, -10.0, &orig_mini, &adj)
            || fabs(adj.width - 28.47) > 1e-6 || fabs(adj.height - 28.47) > 1e-6) {
        puts("FAIL: negative preview inset enlarged 2x2 mini icon");
        return 1;
    }

    /* Positive inset (inward) must still shrink icons proportionally to fit. */
    if (!ld_inset_folder_preview(393.08, 169.74, 22.66, 59.69, 10.0, &orig_tl, &adj)
            || fabs(adj.x - 32.66) > 1e-6 || fabs(adj.y - 32.66) > 1e-6
            || !(adj.width < 59.69 && adj.width > 45.0)) {
        puts("FAIL: positive preview inset did not shrink icon cleanly");
        return 1;
    }

    puts("PASS: 4x2 extent and preview inset policy");
    return 0;
}
