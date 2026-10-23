#include "grid_conf.h"

#include <stdio.h>

static int parse_ok(const char *text, int cols, int rows)
{
    struct ld_grid_conf conf;
    return ld_grid_conf_parse(text, strlen(text), &conf) &&
           conf.cols == cols && conf.rows == rows;
}

int main(void)
{
    if (!parse_ok("grid_cols=10\ngrid_rows=10\nend=1\n", 10, 10)) {
        puts("FAIL: 10x10 rejected");
        return 1;
    }
    if (!parse_ok("grid_cols=8\ngrid_rows=10\nend=1\n", 8, 10)) {
        puts("FAIL: 8x10 rejected");
        return 1;
    }
    if (ld_grid_conf_parse("grid_cols=10\ngrid_rows=11\nend=1\n",
                           strlen("grid_cols=10\ngrid_rows=11\nend=1\n"), NULL)) {
        puts("FAIL: 10x11 accepted");
        return 1;
    }
    const char corrupted[] = "grid_cols=10\0junk\ngrid_rows=10\nend=1\n";
    struct ld_grid_conf reject = {.cols=9, .rows=9};
    if (ld_grid_conf_parse(corrupted, sizeof(corrupted) - 1, &reject) ||
        reject.cols != 0 || reject.rows != 0) {
        puts("FAIL: embedded NUL or partial config accepted");
        return 1;
    }
    const char dock_config[] = "dock_geometry_enabled=1\ndock_geometry_bottom=12.5\nend=1\n";
    struct ld_grid_conf dock = {0};
    if(!ld_grid_conf_parse(dock_config,strlen(dock_config),&dock)
            || !dock.dock.enabled || fabs(dock.dock.bottom-12.5)>0.000001) {
        puts("FAIL: Dock bottom geometry not parsed");
        return 1;
    }
    const char invalid_dock[] = "dock_geometry_enabled=1\ndock_geometry_bottom=40.1\nend=1\n";
    if(ld_grid_conf_parse(invalid_dock,strlen(invalid_dock),&dock)
            || dock.dock.enabled || dock.dock.bottom!=0) {
        puts("FAIL: out-of-range Dock bottom accepted");
        return 1;
    }
    puts("PASS: grid config supports 10x10 and rejects 11 rows");
    return 0;
}
