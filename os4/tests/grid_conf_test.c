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
    struct ld_grid_conf glass={0};
    const char *glass_only="glass_enabled=1\nend=1\n";
    if(!ld_grid_conf_parse(glass_only,strlen(glass_only),&glass)
            || !glass.glass_enabled || glass.cols || glass.rows) {
        puts("FAIL: independent glass-only configuration"); return 1;
    }
    const char *bad_glass[]={
        "glass_enabled=2\nend=1\n",
        "glass_enabled=1\nglass_enabled=0\nend=1\n",
        "glass_enabled=1\nend=1\nextra=1\n"
    };
    for(size_t i=0;i<sizeof bad_glass/sizeof *bad_glass;i++) {
        glass.glass_enabled=1;
        if(ld_grid_conf_parse(bad_glass[i],strlen(bad_glass[i]),&glass)
                || glass.glass_enabled) {
            puts("FAIL: malformed glass option accepted"); return 1;
        }
    }
    puts("PASS: grid and opt-in glass config fail closed");
    return 0;
}
