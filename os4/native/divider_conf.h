/* 分隔线配置字段的集中定义：名称、整数类型、上下限。
 *
 * 主解析器（grid_conf.h）与绘制 hook（divider_paint.h）共用这一份定义：
 * 同一个键只有在这里校验通过才会被接受，两侧对合法配置的判定完全一致。
 * 字段值单位与 GUI 一致：width / y_offset 为 dp×10，height_scale 为百分比，
 * color_* / alpha 为 0..255，enabled 为 0/1，y_offset 可为负。
 */
#ifndef LD_DIVIDER_CONF_H
#define LD_DIVIDER_CONF_H

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

struct ld_divider_field_def {
    const char *name;
    int min, max;
};

static const struct ld_divider_field_def ld_divider_field_defs[] = {
    { "enabled", 0, 1 },
    { "width", 0, 160 },          /* 线宽 dp×10 */
    { "height_scale", 0, 200 },   /* 高度百分比 */
    { "y_offset", -80, 80 },      /* 上下偏移 dp×10，可负 */
    { "color_r", 0, 255 },
    { "color_g", 0, 255 },
    { "color_b", 0, 255 },
    { "alpha", 0, 255 },
};

enum {
    LD_DIVIDER_F_ENABLED = 0,
    LD_DIVIDER_F_WIDTH,
    LD_DIVIDER_F_HEIGHT_SCALE,
    LD_DIVIDER_F_Y_OFFSET,
    LD_DIVIDER_F_COLOR_R,
    LD_DIVIDER_F_COLOR_G,
    LD_DIVIDER_F_COLOR_B,
    LD_DIVIDER_F_ALPHA,
    LD_DIVIDER_F_COUNT
};

#define LD_DIVIDER_FIELD_COUNT ((int)(sizeof ld_divider_field_defs / sizeof *ld_divider_field_defs))

/* 校验并解析一个 dock_divider_<name>=<十进制整数> 键值对：
 * 字段名必须精确匹配、值必须是纯整数、必须落在该字段的上下限内、同一字段不得重复。
 * seen 为字段查重掩码（可传 NULL 表示不查重）。
 * 返回字段下标（>=0），任何一项不合法返回 -1。 */
static int ld_divider_conf_kv(const char *key, const char *val, uint32_t *seen)
{
    static const char prefix[] = "dock_divider_";
    const size_t plen = sizeof prefix - 1;
    if (!key || !val || strncmp(key, prefix, plen) != 0) return -1;
    const char *name = key + plen;
    int i;
    for (i = 0; i < LD_DIVIDER_FIELD_COUNT; i++)
        if (strcmp(name, ld_divider_field_defs[i].name) == 0) break;
    if (i == LD_DIVIDER_FIELD_COUNT) return -1;
    char *end = NULL;
    long v = strtol(val, &end, 10);
    if (end == val || *end != '\0') return -1;
    if (v < ld_divider_field_defs[i].min || v > ld_divider_field_defs[i].max) return -1;
    if (seen) {
        if (*seen & (1u << i)) return -1;
        *seen |= 1u << i;
    }
    return i;
}

#endif /* LD_DIVIDER_CONF_H */
