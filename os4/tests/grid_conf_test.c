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
    struct ld_grid_conf reject = {9, 9};
    if (ld_grid_conf_parse(corrupted, sizeof(corrupted) - 1, &reject) ||
        reject.cols != 0 || reject.rows != 0) {
        puts("FAIL: embedded NUL or partial config accepted");
        return 1;
    }
    puts("PASS: grid config supports 10x10 and rejects 11 rows");
    return 0;
}
