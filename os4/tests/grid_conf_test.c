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
    struct ld_grid_conf logging={0}, logging_default={0};
    const char *log_on="debug_logging=1\nend=1\n", *log_dup="debug_logging=1\ndebug_logging=0\nend=1\n";
    if(!ld_grid_conf_parse(log_on,strlen(log_on),&logging) || !logging.debug_logging
            || !ld_grid_conf_parse("end=1\n",6,&logging_default) || logging_default.debug_logging
            || ld_grid_conf_parse(log_dup,strlen(log_dup),&logging_default)) {
        puts("FAIL: debug_logging switch"); return 1;
    }
    struct ld_grid_conf widget_stretch={0};
    const char *prevent_stretch="widget_prevent_stretch=1\nend=1\n";
    if(!ld_grid_conf_parse(prevent_stretch,strlen(prevent_stretch),&widget_stretch)
            || !widget_stretch.widget_prevent_stretch_enabled) {
        puts("FAIL: native widget stretch-prevention flag"); return 1;
    }
    const char *prevent_stretch_off="widget_prevent_stretch=0\nend=1\n";
    if(!ld_grid_conf_parse(prevent_stretch_off,strlen(prevent_stretch_off),&widget_stretch)
            || widget_stretch.widget_prevent_stretch_enabled) {
        puts("FAIL: disabled widget stretch-prevention flag is not a no-op"); return 1;
    }
    const char *bad_widget_stretch[]={
        "widget_prevent_stretch=2\nend=1\n",
        "widget_prevent_stretch=1\nwidget_prevent_stretch=0\nend=1\n"
    };
    for(size_t i=0;i<sizeof bad_widget_stretch/sizeof *bad_widget_stretch;i++) {
        if(ld_grid_conf_parse(bad_widget_stretch[i],strlen(bad_widget_stretch[i]),&widget_stretch)
                || widget_stretch.widget_prevent_stretch_enabled) {
            puts("FAIL: malformed widget stretch-prevention flag accepted"); return 1;
        }
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
    const char *targets="glass_enabled=1\nglass_targets=0x123400:88,0x128000:140,0x134000:88\nend=1\n";
    if(!ld_grid_conf_parse(targets,strlen(targets),&glass)
            || !glass.glass_targets_present || glass.glass_vaddr[0]!=0x123400
            || glass.glass_size[1]!=140 || glass.glass_size[2]!=88)
        return 1;
    const char *bad_targets[]={
        "glass_enabled=1\nglass_targets=0x123400:88,0x123400:140,0x134000:88\nend=1\n",
        "glass_enabled=1\nglass_targets=0x123401:88,0x128000:140,0x134000:88\nend=1\n",
        "glass_enabled=1\nglass_targets=0x123400:88,0x128000:3,0x134000:88\nend=1\n",
        "glass_enabled=1\nglass_targets=0x123400:88,0x128000:140\nend=1\n",
        "glass_enabled=1\nglass_targets=0x123400:88,0x128000:140,0x134000:88\nglass_targets=0x123400:88,0x128000:140,0x134000:88\nend=1\n"
    };
    for(size_t i=0;i<sizeof bad_targets/sizeof *bad_targets;i++) {
        if(ld_grid_conf_parse(bad_targets[i],strlen(bad_targets[i]),&glass)) {
            puts("FAIL: malformed or repeated glass_targets accepted"); return 1;
        }
    }

    const char *probes="glass_enabled=1\nglass_targets=0x123400:88,0x128000:140,0x134000:88\n"
        "glass_observers=0x140000:660,0x142000:644,0x144000:548,0x146000:292\nend=1\n";
    if(!ld_grid_conf_parse(probes,strlen(probes),&glass)
            || !glass.glass_observers_present || glass.glass_obs_size[3]!=292) return 1;
    const char *truncated="glass_enabled=1\nglass_observers=0x140000:660,0x142000:644\nend=1\n";
    if(ld_grid_conf_parse(truncated,strlen(truncated),&glass)) return 1;

    /* 分隔线字段：GUI 允许 y_offset 为负（dp×10，-80..80），不得拖垮整份配置。 */
    const char *divider="dock_divider_enabled=1\ndock_divider_width=15\n"
        "dock_divider_height_scale=150\ndock_divider_y_offset=-40\n"
        "dock_divider_color_r=255\ndock_divider_color_g=0\ndock_divider_color_b=0\n"
        "dock_divider_alpha=128\nend=1\n";
    if(!ld_grid_conf_parse(divider,strlen(divider),NULL)) {
        puts("FAIL: legal divider config with negative y_offset rejected"); return 1;
    }
    const char *bad_divider[]={
        /* 字段名必须精确匹配 */
        "dock_divider_enabled=1\ndock_divider_w=15\nend=1\n",
        /* 上下限：y_offset 最小 -80，alpha 最大 255 */
        "dock_divider_enabled=1\ndock_divider_y_offset=-81\nend=1\n",
        "dock_divider_enabled=1\ndock_divider_alpha=256\nend=1\n",
        /* 纯整数 */
        "dock_divider_enabled=1\ndock_divider_width=abc\nend=1\n",
        "dock_divider_enabled=1\ndock_divider_width=15x\nend=1\n",
        /* 不得重复 */
        "dock_divider_enabled=1\ndock_divider_width=15\ndock_divider_width=15\nend=1\n",
    };
    for(size_t i=0;i<sizeof bad_divider/sizeof *bad_divider;i++) {
        if(ld_grid_conf_parse(bad_divider[i],strlen(bad_divider[i]),NULL)) {
            puts("FAIL: malformed or repeated divider field accepted"); return 1;
        }
    }

    /* Prismal GUI options: defaults, valid override parsing, out-of-range/duplicate rejection */
    const char *prismal_ok = "glass_enabled=1\nsoft_glass_enabled=0\n"
        "prismal_continuous_corners=1\nprismal_blur=4.5\nprismal_thickness=22.0\n"
        "prismal_ior=1.65\nprismal_dome=1.2\nprismal_chromatic=18.0\n"
        "prismal_tint_a=48.0\nprismal_light_dir_x=-0.65\nprismal_show_normals=1\n"
        "prismal_backlit_enabled=1\nprismal_backlit_width=1.2\nprismal_backlit_strength=0.4\n"
        "end=1\n";
    if (!ld_grid_conf_parse(prismal_ok, strlen(prismal_ok), &glass) ||
        glass.soft_glass_enabled != 0 || glass.prismal.continuous_corners != 1 ||
        fabs(glass.prismal.blur - 4.5) > 1e-6 || fabs(glass.prismal.ior - 1.65) > 1e-6 ||
        glass.prismal.show_normals != 1 || glass.prismal.backlit_enabled != 1 ||
        fabs(glass.prismal.backlit_width - 1.2) > 1e-6) {
        puts("FAIL: valid prismal config rejected or misparsed"); return 1;
    }
    const char *bad_prismal[] = {
        "glass_enabled=1\nprismal_unknown=1.0\nend=1\n",
        "glass_enabled=1\nprismal_ior=2.5\nend=1\n",
        "glass_enabled=1\nprismal_blur=61.0\nend=1\n",
        "glass_enabled=1\nprismal_continuous_corners=2\nend=1\n",
        "glass_enabled=1\nprismal_blur=2.0\nprismal_blur=3.0\nend=1\n",
        "glass_enabled=1\nsoft_glass_enabled=2\nend=1\n",
    };
    for (size_t i = 0; i < sizeof bad_prismal / sizeof *bad_prismal; i++) {
        if (ld_grid_conf_parse(bad_prismal[i], strlen(bad_prismal[i]), NULL)) {
            puts("FAIL: invalid prismal config accepted"); return 1;
        }
    }
    puts("PASS: strict glass gates and four AOT pipeline observers");
    return 0;
}
