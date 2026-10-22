/* grid_conf.h — liquiddock-native.conf 的纯 C fail-closed 解析（网格扩展键）
 *
 * 为什么要单独一份解析（而不是用 hooks_dock.c 里的）：
 *   hooks_dock.c 的解析器是编译单元私有的，且本特性不得改它（他人维护）。
 *   这里做**全键集**解析（dock_* / glass / icons / grid_cols / grid_rows / end），
 *   校验规则与 hooks_dock.c 逐条对齐（含未知键/重复键/截断/超长全部拒绝），
 *   但只提取 grid_cols / grid_rows 两个键。这样任一侧发现配置损坏时，
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
 *   end=1                 终止行，必须是最后一行
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

#define LD_GRID_CONF_MAX_BYTES 4096
#define LD_GRID_LINE_MAX       255   /* 含 LF；对齐 hooks_dock.c 的 fgets(line,256) */

#define LD_GRID_COLS_MIN 2            /* 与 app 层 ConfigSchema.Grid.COLUMNS 一致 */
#define LD_GRID_COLS_MAX 10
#define LD_GRID_ROWS_MIN 2            /* 与 app 层 ConfigSchema.Grid.ROWS 一致 */
#define LD_GRID_ROWS_MAX 10

struct ld_grid_conf {
    int cols;   /* 0 = 不改（缺键）；否则 [LD_GRID_COLS_MIN, LD_GRID_COLS_MAX] */
    int rows;   /* 0 = 不改（缺键）；否则 [LD_GRID_ROWS_MIN, LD_GRID_ROWS_MAX] */
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

/* 解析整份配置文本（内存版）。成功 = 1 并写 *out；任何违规 = 0 且 *out 清零。
 * len 含全部字节；文本中每行必须以 LF 结尾（含最后一行）。 */
static inline int ld_grid_conf_parse(const char *buf, size_t len, struct ld_grid_conf *out)
{
    struct ld_grid_conf c = { 0, 0 };
    unsigned seen = 0;  /* bit0=dock_offset_y bit1=dock_offset_x bit2=glass bit3=icons
                           bit4=grid_cols bit5=grid_rows bit6=end */
    int ok = 0;

    if (buf && len > 0 && len <= LD_GRID_CONF_MAX_BYTES) {
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
                } else if (strcmp(key, "end") == 0) {
                    if (seen & 64u) { bad = 1; break; }
                    if (val[0] != '1' || val[1] != '\0') { bad = 1; break; }
                    seen |= 64u;
                    done = 1;                            /* 必须是最后一行 */
                } else {
                    bad = 1; break;                      /* 未知键 = 损坏 */
                }
            }
        }
        if (!bad && done && p == end) ok = 1;
        /* done 后仍有内容 / 未见 end=1（截断）/ 末行无 LF —— 全部保持 ok=0 */
    }

    if (ok) { if (out) *out = c; }
    else    { if (out) { out->cols = 0; out->rows = 0; } }
    return ok;
}

/* 读文件并解析。缺失 / 空 / 超长 / 读不全 = 拒绝（fail-closed）。 */
static inline int ld_grid_conf_parse_file(const char *path, struct ld_grid_conf *out)
{
    FILE *f;
    long sz;
    char buf[LD_GRID_CONF_MAX_BYTES + 1];
    size_t n;

    if (out) { out->cols = 0; out->rows = 0; }
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
