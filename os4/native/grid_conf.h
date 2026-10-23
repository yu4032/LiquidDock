/* grid_conf.h — liquiddock-native.conf 的纯 C fail-closed 解析（网格扩展键）
 *
 * 为什么要单独一份解析（而不是用 hooks_dock.c 里的）：
 *   hooks_dock.c 的解析器是编译单元私有的，且本特性不得改它（他人维护）。
 *   这里做**全键集**解析（dock_* / glass / icons / grid_cols / grid_rows / end），
 *   校验规则与 hooks_dock.c 逐条对齐（含未知键/重复键/截断/超长全部拒绝），
 *   并提取网格、图标大小和全屏布局偏移。这样任一侧发现配置损坏时，
 *   两侧都会 fail-closed（整份作废），不会出现「一边开一边关」。
 *
 * ⚠ 协议同步：hooks_dock.c 的已知键集目前**不含** grid_cols / grid_rows，
 *   含网格键的配置会被它整体作废（fail-closed）。hooks_dock.c 的键集需要由其
 *   维护者同步加位（见 native/os4/README.md 配置协议一节的注）。
 *
 * 协议（README「配置协议」为准，此处必须一致）：
 *   dock_offset_y=<float px, 有限值>
 *   dock_offset_x=<float px, 有限值>
 *   glass=0|1
 *   icons=0|1
 *   grid_cols=<2..10>     桌面网格列数（缺省 = 不改）
 *   grid_rows=<2..10>     桌面网格行数（缺省 = 不改）
 *   icon_size_enabled=0|1; icon_size_percent=80..120 (缺省100)
 *   glass_enabled=0|1
 *   glass_native_<field>=decimal (逐项原生参数，缺键保留系统值)
 *   widget_horizontal_stretch=0|1
 *   grid_{landscape,portrait}_{horizontal_distance,top_distance,bottom_distance}=-600..600 dp
 *   grid_{landscape,portrait}_row_gap=-200..400 dp
 *   indicator_{landscape,portrait}_y=-160..160 dp
 *   end=1                 终止行，必须是最后一行
 * grid_cols/grid_rows 必须成对出现；无计数键支持独立的图标大小设置。
 * 每行 LF 结尾的 key=value；行序任意；缺键 = 该特性不生效。
 *
 * fail-closed 规则（任一触发 = 整份配置作废，网格不改，绝不沿用旧值）：
 *   空 / 超 4096B / 无 end=1（截断）/ end=1 后仍有内容 /
 *   未知键 / 重复键 / 值非法 / 行不以 LF 结尾 / 单行超长。
 *
 * 纯 C、无 Android 依赖：hooks_grid.c 与 tools/test_grid_config.c 共用，
 * 测试可在无真机环境直接编译运行。
 */
#ifndef LIQUIDDOCK_GRID_CONF_H
#define LIQUIDDOCK_GRID_CONF_H

#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdint.h>
#include <errno.h>
#include "native_glass_schema.h"

#define LD_GRID_CONF_MAX_BYTES 4096
#define LD_GRID_LINE_MAX       255   /* 含 LF；对齐 hooks_dock.c 的 fgets(line,256) */

#define LD_GRID_COLS_MIN 2            /* 与 app 层 ConfigSchema.Grid.COLUMNS 一致 */
#define LD_GRID_COLS_MAX 10
#define LD_GRID_ROWS_MIN 2            /* 与 app 层 ConfigSchema.Grid.ROWS 一致 */
#define LD_GRID_ROWS_MAX 10

struct ld_grid_offsets {
    double horizontal, top, bottom, row_gap, indicator_y;
};

struct ld_dock_conf {
    int enabled;
    double width, height, spacing, bottom, corner;
};

struct ld_grid_conf {
    int cols;   /* 0 = 不改（缺键）；否则 [LD_GRID_COLS_MIN, LD_GRID_COLS_MAX] */
    int rows;   /* 0 = 不改（缺键）；否则 [LD_GRID_ROWS_MIN, LD_GRID_ROWS_MAX] */
    int icon_size_enabled, icon_size_percent, widget_horizontal_stretch;
    struct ld_grid_offsets landscape, portrait;
    struct ld_dock_conf dock;
    int glass_enabled;
    int glass_icons, glass_widgets, widget_overrides;
    double glass_icon_size, glass_icon_corner;
    struct ld_native_glass_options native_glass;
    int glass_targets_present;
    uint64_t glass_vaddr[3];
    size_t glass_size[3];
    int glass_observers_present;
    uint64_t glass_obs_vaddr[4];
    size_t glass_obs_size[4];
};

/* 纯十进制整数（无符号、无空格、无前导零、无多余字符），范围 [lo,hi]；出格 = 非法 */
static inline int ld_grid_parse_int(const char *s, int lo, int hi, int *outv)
{
    int v = 0;
    if (!s || !*s) return 0;
    if (s[0] == '0' && s[1] != '\0') return 0;  /* 非规范写法一律拒绝 */
    for (const char *p = s; *p; p++) {
        if (*p < '0' || *p > '9') return 0;
        if (v > 1000000) return 0;              /* 防溢出（远超合法上限） */
        v = v * 10 + (*p - '0');
    }
    if (v < lo || v > hi) return 0;
    *outv = v;
    return 1;
}

/* 0/1 开关（与 hooks_dock.c 相同：首字节 '0'/'1' 且恰好 2 字节） */
static inline int ld_grid_parse_bool01(const char *s, int *outv)
{
    if (!s || (s[0] != '0' && s[0] != '1') || s[1] != '\0') return 0;
    *outv = s[0] - '0';
    return 1;
}

/* 有限浮点，整串消费（与 hooks_dock.c 相同） */
static inline int ld_grid_parse_double(const char *s, double *outv)
{
    char *end = NULL;
    double v;
    if (!s || !*s) return 0;
    v = strtod(s, &end);
    if (end == s || *end != '\0' || !isfinite(v)) return 0;
    *outv = v;
    return 1;
}

/* Signed decimal accepted by Kotlin Double.toString; hex, whitespace, NaN and infinity rejected. */
static inline int ld_grid_parse_bounded_decimal(const char *s, double lo, double hi, double *outv)
{
    const char *q = s;
    int digits = 0, dots = 0;
    double v;
    if (!q || !*q) return 0;
    if (*q == '-' || *q == '+') ++q;
    for (; *q && *q != 'e' && *q != 'E'; ++q) {
        if (*q >= '0' && *q <= '9') ++digits;
        else if (*q == '.') ++dots;
        else return 0;
    }
    if (digits == 0 || dots > 1) return 0;
    if (*q == 'e' || *q == 'E') {
        int exponent_digits = 0;
        ++q;
        if (*q == '-' || *q == '+') ++q;
        for (; *q >= '0' && *q <= '9'; ++q) ++exponent_digits;
        if (exponent_digits == 0) return 0;
    }
    if (*q != '\0' || !ld_grid_parse_double(s, &v) || v < lo || v > hi) return 0;
    *outv = v;
    return 1;
}

/* Plain decimal only (no exponent, whitespace, NaN or infinity). */
static inline int ld_grid_parse_plain_decimal(const char *s, double lo, double hi, double *outv)
{
    const char *p=s;
    int digits=0, fraction_digits=0, dot=0;
    double v;
    if(!p || !*p) return 0;
    if(*p=='-' || *p=='+') ++p;
    for(;*p;++p) {
        if(*p>='0' && *p<='9') { ++digits; if(dot) ++fraction_digits; }
        else if(*p=='.' && !dot) dot=1;
        else return 0;
    }
    if(digits==0 || (dot && fraction_digits==0) || !ld_grid_parse_double(s,&v)
            || v<lo || v>hi) return 0;
    *outv=v;
    return 1;
}



/* Canonical, device-local AOT addresses extracted from .gnu_debugdata by
 * the module app. The whole 8583 vendor ELF and all live code bodies are
 * independently authenticated in native_entry.c before any hook. */
static inline int ld_grid_parse_aot_targets(const char *s,uint64_t *vaddr,size_t *bytes,size_t count)
{
    if(!s || !*s) return 0;
    const char *p=s;
    if(count<1 || count>16) return 0;
    for(size_t i=0;i<count;i++) {
        if(p[0]!='0' || p[1]!='x') return 0;
        p+=2;
        if(!((*p>='0' && *p<='9') || (*p>='a' && *p<='f'))) return 0;
        const char *start=p;
        errno=0;
        char *end=NULL;
        unsigned long long address=strtoull(p,&end,16);
        if(errno || end==p || *end!=':' || address<0x4000 || address%4
                || (size_t)(end-start)>12) return 0;
        p=end+1;
        int length=0;
        const char *last=p;
        while(*p>='0' && *p<='9') {
            if(length>2048) return 0;
            length=length*10+(*p-'0');
            p++;
        }
        if(p==last || (*last=='0' && p-last>1) || length<16 || length>2048
                || address>=29983600ull || (unsigned long long)length>29983600ull-address)
            return 0;
        vaddr[i]=(uint64_t)address;
        bytes[i]=(size_t)length;
        if(i+1<count) {
            if(*p!=',') return 0;
            p++;
        } else if(*p!='\0') return 0;
    }
    for(size_t i=0;i<count;i++)
        for(size_t j=i+1;j<count;j++)
            if(vaddr[i]==vaddr[j]) return 0;
    return 1;
}

/* 解析整份配置文本（内存版）。成功 = 1 并写 *out；任何违规 = 0 且 *out 清零。
 * len 含全部字节；文本中每行必须以 LF 结尾（含最后一行）。 */
static inline int ld_grid_conf_parse(const char *buf, size_t len, struct ld_grid_conf *out)
{
    struct ld_grid_conf c = {0};
    c.icon_size_percent = 100;
    uint64_t seen = 0;  /* bit0=dock_offset_y bit1=dock_offset_x bit2=glass bit3=icons
                           bit4=grid_cols bit5=grid_rows bit6=end */
    int ok = 0;

    // A binary NUL could make strcmp/strchr accept only a prefix of an
    // otherwise invalid line. The config is a strict LF-delimited text protocol.
    if (buf && len > 0 && len <= LD_GRID_CONF_MAX_BYTES &&
        memchr(buf, '\0', len) == NULL) {
        const char *p = buf, *end = buf + len;
        int done = 0, bad = 0;
        while (p < end && !bad && !done) {
            const char *nl = memchr(p, '\n', (size_t)(end - p));
            size_t linelen;
            if (!nl) { bad = 1; break; }                 /* 行不以 LF 结尾（截断） */
            linelen = (size_t)(nl - p) + 1;
            if (linelen > LD_GRID_LINE_MAX) { bad = 1; break; }   /* 超长行 */
            {
                char line[LD_GRID_LINE_MAX];
                char *eq, *key, *val;
                memcpy(line, p, linelen - 1);
                line[linelen - 1] = '\0';
                p = nl + 1;

                eq = strchr(line, '=');
                if (!eq || eq == line) { bad = 1; break; }
                *eq = '\0';
                key = line;
                val = eq + 1;

                if (strcmp(key, "dock_offset_y") == 0 ||
                    strcmp(key, "dock_offset_x") == 0) {
                    /* 判别字符 'y'/'x' 在下标 12（"dock_offset_" 恰好 12 字符）。
                     * 注：hooks_dock.c 用的是 key[11]，两个 dock 键会撞同一个
                     * 重复键位（同时出现即整份作废）—— 那是它的既有问题，
                     * 本文件不复刻，正确判别放这里。 */
                    unsigned bit = (key[12] == 'y') ? 1u : 2u;
                    double dv;
                    if (seen & bit) { bad = 1; break; }
                    if (!ld_grid_parse_double(val, &dv)) { bad = 1; break; }
                    seen |= bit;
                } else if (strcmp(key, "glass") == 0 || strcmp(key, "icons") == 0) {
                    unsigned bit = (key[0] == 'g') ? 4u : 8u;
                    int bv;
                    if (seen & bit) { bad = 1; break; }
                    if (!ld_grid_parse_bool01(val, &bv)) { bad = 1; break; }
                    seen |= bit;
                } else if (strcmp(key, "grid_cols") == 0) {
                    if (seen & 16u) { bad = 1; break; }
                    if (!ld_grid_parse_int(val, LD_GRID_COLS_MIN, LD_GRID_COLS_MAX,
                                           &c.cols)) { bad = 1; break; }
                    seen |= 16u;
                } else if (strcmp(key, "grid_rows") == 0) {
                    if (seen & 32u) { bad = 1; break; }
                    if (!ld_grid_parse_int(val, LD_GRID_ROWS_MIN, LD_GRID_ROWS_MAX,
                                           &c.rows)) { bad = 1; break; }
                    seen |= 32u;
                } else if (strcmp(key, "glass_observers") == 0) {
                    if ((seen & (1u << 28)) ||
                        !ld_grid_parse_aot_targets(val,c.glass_obs_vaddr,c.glass_obs_size,4)) {
                        bad=1; break;
                    }
                    c.glass_observers_present=1;
                    seen |= 1u << 28;
                } else if (strcmp(key, "glass_targets") == 0) {
                    if ((seen & (1u << 27)) ||
                            !ld_grid_parse_aot_targets(val,c.glass_vaddr,c.glass_size,3)) {
                        bad=1; break;
                    }
                    c.glass_targets_present=1;
                    seen |= 1u << 27;
                } else if (strcmp(key, "glass_enabled") == 0) {
                    int value;
                    if ((seen & (1u << 26)) || !ld_grid_parse_bool01(val,&value)) {
                        bad=1; break;
                    }
                    c.glass_enabled=value;
                    seen |= 1u << 26;
                } else if (strcmp(key, "glass_icons") == 0 ||
                           strcmp(key, "glass_widgets") == 0 ||
                           strcmp(key, "widget_overrides") == 0) {
                    uint64_t bit = !strcmp(key,"glass_icons") ? (1ull<<29) :
                        (!strcmp(key,"glass_widgets") ? (1ull<<30) : (1ull<<31));
                    int value;
                    if ((seen & bit) || !ld_grid_parse_bool01(val,&value)) {
                        bad=1; break;
                    }
                    if (bit==(1ull<<29)) c.glass_icons=value;
                    else if (bit==(1ull<<30)) c.glass_widgets=value;
                    else c.widget_overrides=value;
                    seen |= bit;
                } else if (strcmp(key, "glass_icon_size") == 0 ||
                           strcmp(key, "glass_icon_corner") == 0) {
                    uint64_t bit = !strcmp(key,"glass_icon_size") ? (1ull<<32) : (1ull<<33);
                    double *target = bit==(1ull<<32) ? &c.glass_icon_size : &c.glass_icon_corner;
                    double lo = bit==(1ull<<32) ? -40.0 : 0.0;
                    double hi = bit==(1ull<<32) ? 40.0 : 128.0;
                    if ((seen & bit) || !ld_grid_parse_bounded_decimal(val,lo,hi,target)) {
                        bad=1; break;
                    }
                    seen |= bit;
                } else if (strcmp(key, "dock_geometry_enabled") == 0) {
                    int bv;
                    if (seen & (1u << 20) || !ld_grid_parse_bool01(val, &bv)) {
                        bad = 1; break;
                    }
                    c.dock.enabled = bv;
                    seen |= 1u << 20;
                } else if (strcmp(key, "dock_geometry_width") == 0 ||
                           strcmp(key, "dock_geometry_height") == 0 ||
                           strcmp(key, "dock_geometry_spacing") == 0 ||
                           strcmp(key, "dock_geometry_bottom") == 0 ||
                           strcmp(key, "dock_geometry_corner") == 0) {
                    unsigned bit;
                    double lo, hi, *target;
                    if (strcmp(key, "dock_geometry_width") == 0) {
                        bit = 1u << 21; lo = -80; hi = 80; target = &c.dock.width;
                    } else if (strcmp(key, "dock_geometry_height") == 0) {
                        bit = 1u << 22; lo = -80; hi = 80; target = &c.dock.height;
                    } else if (strcmp(key, "dock_geometry_spacing") == 0) {
                        bit = 1u << 23; lo = -8; hi = 12; target = &c.dock.spacing;
                    } else if (strcmp(key, "dock_geometry_bottom") == 0) {
                        bit = 1u << 24; lo = -30; hi = 40; target = &c.dock.bottom;
                    } else {
                        bit = 1u << 25; lo = -50; hi = 100; target = &c.dock.corner;
                    }
                    if ((seen & bit) || !ld_grid_parse_bounded_decimal(val, lo, hi, target)) {
                        bad = 1; break;
                    }
                    seen |= bit;
                } else if (strncmp(key,"glass_native_",13)==0) {
                    unsigned index=0;
                    while(index<LD_NATIVE_GLASS_COUNT && strcmp(key+13,ld_native_glass_fields[index].name)) index++;
                    if(index==LD_NATIVE_GLASS_COUNT || (c.native_glass.mask&(1ull<<index)) ||
                        !ld_grid_parse_plain_decimal(val,ld_native_glass_fields[index].min,ld_native_glass_fields[index].max,&c.native_glass.values[index])) {bad=1;break;}
                    c.native_glass.mask|=1ull<<index;
                } else if (strcmp(key, "end") == 0) {
                    if (seen & 64u) { bad = 1; break; }
                    if (val[0] != '1' || val[1] != '\0') { bad = 1; break; }
                    seen |= 64u;
                    done = 1;                            /* 必须是最后一行 */
                } else {
                    static const char *const keys[] = {
                        "icon_size_enabled", "icon_size_percent", "widget_horizontal_stretch",
                        "grid_landscape_horizontal_distance", "grid_landscape_top_distance",
                        "grid_landscape_bottom_distance", "grid_landscape_row_gap",
                        "grid_portrait_horizontal_distance", "grid_portrait_top_distance",
                        "grid_portrait_bottom_distance", "grid_portrait_row_gap",
                        "indicator_landscape_y", "indicator_portrait_y"
                    };
                    double *offsets[] = {
                        &c.landscape.horizontal, &c.landscape.top, &c.landscape.bottom,
                        &c.landscape.row_gap, &c.portrait.horizontal, &c.portrait.top,
                        &c.portrait.bottom, &c.portrait.row_gap,
                        &c.landscape.indicator_y, &c.portrait.indicator_y
                    };
                    unsigned i;
                    for (i = 0; i < sizeof(keys) / sizeof(keys[0]); ++i)
                        if (strcmp(key, keys[i]) == 0) break;
                    if (i == sizeof(keys) / sizeof(keys[0]) || (seen & (128u << i))) {
                        bad = 1; break;
                    }
                    int valid;
                    if (i == 0) valid = ld_grid_parse_bool01(val, &c.icon_size_enabled);
                    else if (i == 1) valid = ld_grid_parse_int(val, 80, 120, &c.icon_size_percent);
                    else if (i == 2) valid = ld_grid_parse_bool01(val, &c.widget_horizontal_stretch);
                    else {
                        double v;
                        double lo = -600, hi = 600;
                        if (i == 6 || i == 10) { lo = -200; hi = 400; }
                        if (i >= 11) { lo = -160; hi = 160; }
                        /* Signed decimal (including Double.toString's exponent form).
                         * No whitespace, hex, NaN or infinity. */
                        const char *q = val;
                        if (*q == '-' || *q == '+') ++q;
                        int digits = 0, dots = 0;
                        for (; *q && *q != 'e' && *q != 'E'; ++q) {
                            if (*q >= '0' && *q <= '9') ++digits;
                            else if (*q == '.') ++dots;
                            else { dots = 2; break; }
                        }
                        int syntax = digits > 0 && dots <= 1;
                        if (*q == 'e' || *q == 'E') {
                            ++q;
                            if (*q == '-' || *q == '+') ++q;
                            int exponent_digits = 0;
                            for (; *q >= '0' && *q <= '9'; ++q) ++exponent_digits;
                            syntax = syntax && exponent_digits > 0;
                        }
                        valid = syntax && *q == '\0' && ld_grid_parse_double(val, &v)
                                && v >= lo && v <= hi;
                        if (valid) *offsets[i - 3] = v;
                    }
                    if (!valid) { bad = 1; break; }
                    seen |= 128u << i;
                }
            }
        }
        if (!bad && done && p == end && ((seen & 48u) == 0 || (seen & 48u) == 48u)) ok = 1;
        /* done 后仍有内容 / 未见 end=1（截断）/ 末行无 LF —— 全部保持 ok=0 */
    }

    if (ok) { if (out) *out = c; }
    else    { if (out) memset(out, 0, sizeof(*out)); }
    return ok;
}

/* 读文件并解析。缺失 / 空 / 超长 / 读不全 = 拒绝（fail-closed）。 */
static inline int ld_grid_conf_parse_file(const char *path, struct ld_grid_conf *out)
{
    FILE *f;
    long sz;
    char buf[LD_GRID_CONF_MAX_BYTES + 1];
    size_t n;

    if (out) memset(out, 0, sizeof(*out));
    f = fopen(path, "rb");
    if (!f) return 0;
    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return 0; }
    sz = ftell(f);
    if (sz <= 0 || sz > LD_GRID_CONF_MAX_BYTES) { fclose(f); return 0; }
    rewind(f);
    n = fread(buf, 1, (size_t)sz, f);
    fclose(f);
    if (n != (size_t)sz) return 0;
    return ld_grid_conf_parse(buf, n, out);
}

#endif /* LIQUIDDOCK_GRID_CONF_H */
