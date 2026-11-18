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

    puts("PASS: 4x2 extent reaches the first and last icon edges");
    return 0;
}
