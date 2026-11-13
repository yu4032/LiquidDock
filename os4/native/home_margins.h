/* OS4 工作区网格水平边距 —— 在布局真正消费的 config 上做增量。
 *
 * 依据（turner 8.01.02.8604 libapp.so，逐条反汇编确认）：
 *   CellLayoutGetxController.cellToCenterOffset @0x8cae48
 *     cw = [cfg+0x2b]；originX = box([cfg+0x3b])+7；x = originX + col*cw
 *   GridController.currentConfig @0xb47388（736 个调用点）返回的就是该 cfg。
 *   DeviceConfig.isScreenOrientationLandscape @0x8cb3d0 判定方式：
 *     GridController.getOrientationType 取 cfg+0x8f -> variables+0x23 -> 值对象，
 *     再与 object pool 项（x27+0x9b10）比较；相等即横屏。
 *
 * 水平偏移 dh（dp，与 Dart 布局同单位）语义 = 左右同时增大 dh：
 *   左右共用侧边距，所以 originX += dh，同时 cw -= 2*dh/cols（网格仍居中）。
 * 竖直方向：top/bottom 相互独立。originY = 网格顶边（cfg+0xeb -> Rx+0x13 -> 值对象+7），
 *   bottom = 区域高 - originY - rows*cellH。要 top += dt、bottom += db：
 *   originY += dt，cellH -= (dt+db)/rows。
 *
 * 实现：后置 hook currentConfig。每个方向第一次见到 config 时记录原始 cellW/originX，
 * 之后每次返回前都把 cfg 写成“原始值 + 偏移”的**绝对**目标值——厂商会拿上一份
 * config 复制出新对象，绝对值写入才不会累积漂移。水平偏移全为 0 时不装 hook。
 */
#ifndef LD_HOME_MARGINS_H
#define LD_HOME_MARGINS_H
#include <math.h>
#include <stdint.h>
#include <string.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"
#include "aot_scanner.h"

#define LD_HM_CC_VADDR 0xb47388u   /* GridController.currentConfig */
#define LD_HM_VERTICAL 1           /* 竖直：cellH 收缩 + GridController+0xeb 的 originY */

static unsigned char g_hm_cc_save[64] __attribute__((aligned(16), used));
static void *g_hm_cc_orig __attribute__((used));
static volatile unsigned g_hm_cc_calls __attribute__((used));

struct ld_hm_base { double cw, ox, ch, oy, ind; int valid; int cols, rows; int oy_valid; };
static struct ld_hm_base g_hm_base[2] __attribute__((used));
/* 诊断：记录我们上一次写进去的 ox/cw，用来判断厂商下一帧是复制还是重算。 */
static double g_hm_wrote_ox[2] __attribute__((used));
static double g_hm_wrote_cw[2] __attribute__((used));
/* pipeline 观察器按 config 指针记方向：同一份 config 只对应一个方向。 */
struct ld_hm_note { uintptr_t cfg; int dir, cols, rows; };
static struct ld_hm_note g_hm_notes[8] __attribute__((used));
static volatile unsigned g_hm_note_n __attribute__((used));
static volatile int g_hm_last_dir __attribute__((used)) = -1;
/* 当前活动网格的方向/行列（由 pipeline 观察器写入，来自 GridController 的
 * ld_grid_direction + grid+0x1b/0x23），post-hook 不再自己猜方向。
 * ld_grid_direction: 1=横屏 0=竖屏（与旧代码的假设相反）。 */
static volatile int g_hm_cur_dir __attribute__((used)) = -1;
static volatile int g_hm_cur_cols __attribute__((used));
static volatile int g_hm_cur_rows __attribute__((used));

__attribute__((used)) static void ld_home_margins_note(uintptr_t cfg, int dir, int cols, int rows)
{
    if (!cfg || (dir != 0 && dir != 1) || cols <= 0 || rows <= 0) return;
    g_hm_cur_dir = dir; g_hm_cur_cols = cols; g_hm_cur_rows = rows;
    unsigned used = __atomic_load_n(&g_hm_note_n, __ATOMIC_RELAXED);
    for (unsigned i = 0; i < used && i < 8u; i++)
        if (g_hm_notes[i].cfg == cfg) {
            g_hm_notes[i].dir = dir; g_hm_notes[i].cols = cols; g_hm_notes[i].rows = rows;
            return;
        }
    if (used >= 8u) used = 0;                 /* 满了就覆盖第一槽，config 数很少 */
    g_hm_notes[used].cfg = cfg;
    g_hm_notes[used].dir = dir;
    g_hm_notes[used].cols = cols;
    g_hm_notes[used].rows = rows;
    __atomic_store_n(&g_hm_note_n, used + 1u < 8u ? used + 1u : 8u, __ATOMIC_RELAXED);
}

/* 找到这份 config 的 (cols, rows)；先精确匹配，再按 cfg+0x8f 间接匹配。 */
static uintptr_t ld_hm_ptr(uintptr_t obj, size_t off, uintptr_t x28);

static const struct ld_hm_note *ld_hm_lookup(uintptr_t cfg, uintptr_t x28)
{
    unsigned used = __atomic_load_n(&g_hm_note_n, __ATOMIC_RELAXED);
    if (used > 8u) used = 8u;
    for (unsigned i = 0; i < used; i++)
        if (g_hm_notes[i].cfg == cfg) return &g_hm_notes[i];
    uintptr_t vars = ld_hm_ptr(cfg, 0x8f, x28);
    if (vars)
        for (unsigned i = 0; i < used; i++)
            if (g_hm_notes[i].cfg == vars) return &g_hm_notes[i];
    return 0;
}

static uintptr_t ld_hm_ptr(uintptr_t obj, size_t off, uintptr_t x28)
{
    uint32_t v = 0;
    if (!obj) return 0;
    __builtin_memcpy(&v, (const void *)(obj + off), sizeof v);
    /* Dart 堆对象偏移都在 MB 级；Smi 也带奇最低位，靠这个下限挡掉（0x14ef 那种）。 */
    if (!(v & 1u) || v < 0x100000u) return 0;
    return (uintptr_t)v + (x28 << 32);
}

static uintptr_t g_hm_ctrl_offset = 0;

/* originY 的真值不在 cfg 上：cellToCenterOffset 用的是 GridController 单例的
 * controller+0xeb -> Rx -> (+0x13) 值对象 -> +7。控制器取自 [x26+0x78]+0x27c8 (v8604) / +0x2828 (v9239)。 */
static uintptr_t ld_hm_oy_box(uintptr_t x28, uintptr_t x26)
{
    uintptr_t fields = 0, ctrl = 0;
    if (!x26) return 0;
    __builtin_memcpy(&fields, (const void *)(x26 + 0x78), sizeof fields);
    if (!fields) return 0;
    uintptr_t off = g_hm_ctrl_offset ? g_hm_ctrl_offset : 0x27c8u;
    __builtin_memcpy(&ctrl, (const void *)(fields + off), sizeof ctrl);
    if (!ctrl && off != 0x2828u) {
        __builtin_memcpy(&ctrl, (const void *)(fields + 0x2828u), sizeof ctrl);
    }
    if (!ctrl) return 0;
    uintptr_t rx = ld_hm_ptr(ctrl, 0xef, x28);
    if (!rx) rx = ld_hm_ptr(ctrl, 0xeb, x28);
    return rx ? ld_hm_ptr(rx, 0x13, x28) : 0;
}

/* 只对“和左侧 originX 同一种对象”的 box 动手：左侧 box 已验证可写。 */
static unsigned ld_hm_cid(uintptr_t obj)
{
    uint64_t hdr = 0;
    if (!obj) return ~0u;
    __builtin_memcpy(&hdr, (const void *)(obj - 1), sizeof hdr);
    return (unsigned)((hdr >> 12) & 0xfffffu);
}

/* 由 trampoline 调用：save[24]=返回值(cfg)，save[40]=x28，save[48]=x27。 */
__attribute__((used)) static void *ld_hm_cc_post(unsigned char *save)
{
    uintptr_t cfg = 0, x28 = 0, pp = 0, x26 = 0;
    __builtin_memcpy(&cfg, save + 24, sizeof cfg);
    __builtin_memcpy(&x28, save + 40, sizeof x28);
    __builtin_memcpy(&pp, save + 48, sizeof pp);
    __builtin_memcpy(&x26, save + 56, sizeof x26);
    if (!cfg) return (void *)cfg;

    unsigned n = __atomic_fetch_add(&g_hm_cc_calls, 1u, __ATOMIC_RELAXED);

    /* 方向：优先从 GridConfig (cfg + 0x1b / 0x23) 读取 cols / rows，
     * 避免写死的全局常量导致竖屏被强行判定为横屏。 */
    uintptr_t variables = ld_hm_ptr(cfg, 0x8f, x28);
    uintptr_t vo = ld_hm_ptr(variables, 0x23, x28);
    int64_t cfg_cols = 0, cfg_rows = 0, dir_raw = -1;
    __builtin_memcpy(&cfg_cols, (const void *)(cfg + 0x1b), sizeof cfg_cols);
    __builtin_memcpy(&cfg_rows, (const void *)(cfg + 0x23), sizeof cfg_rows);
    if (vo) __builtin_memcpy(&dir_raw, (const void *)(vo + 7), sizeof dir_raw);

    int landscape = -1, cols = 0, rows = 0;
    const struct ld_hm_note *note = ld_hm_lookup(cfg, x28);
    if (cfg_cols >= 2 && cfg_cols <= 16 && cfg_rows >= 2 && cfg_rows <= 16) {
        cols = (int)cfg_cols;
        rows = (int)cfg_rows;
        if (cols != rows) landscape = (cols > rows);
        else if (dir_raw == 0 || dir_raw == 1) landscape = (dir_raw == 0);
    }
    if (landscape < 0 && note) {
        landscape = (note->dir == 0);   /* ld_grid_direction: 0=横屏 1=竖屏 */
        if (cols <= 0) { cols = note->cols; rows = note->rows; }
    } else if (landscape < 0 && (g_hm_cur_dir == 0 || g_hm_cur_dir == 1)) {
        landscape = (g_hm_cur_dir == 0);
        if (cols <= 0) { cols = g_hm_cur_cols; rows = g_hm_cur_rows; }
    }
    if (landscape < 0 && (dir_raw == 0 || dir_raw == 1)) {
        landscape = (dir_raw == 0);
        if (cols <= 0) {
            cols = landscape ? g_grid_cols : g_grid_rows;
            rows = landscape ? g_grid_rows : g_grid_cols;
        }
    }
    if (landscape < 0 || cols <= 0 || rows <= 0) return (void *)cfg;
    double cell_w_now = 0;
    __builtin_memcpy(&cell_w_now, (const void *)(cfg + 0x2b), sizeof cell_w_now);
    if ((n % 500u) == 0u)
        ld_blog("home margin orient land=%d cols=%d rows=%d note=%d cellW=%.2f",
                landscape, cols, rows, note ? 1 : 0, cell_w_now);

    const struct ld_grid_offsets *o = landscape ? &g_home_options.landscape : &g_home_options.portrait;
    double dh = o->horizontal;
    if (cols <= 0) return (void *)cfg;

    struct ld_hm_base *base = &g_hm_base[landscape ? 0 : 1];
    /* config 的几何（列/行）变了说明换页/分屏/文件夹网格，旧基准作废重采。 */
    if (base->valid && (base->cols != cols || base->rows != rows)) base->valid = 0;
    if (!base->valid) {
        double cw_now = 0, ch_now = 0;
        __builtin_memcpy(&cw_now, (const void *)(cfg + 0x2b), sizeof cw_now);
        __builtin_memcpy(&ch_now, (const void *)(cfg + 0x33), sizeof ch_now);
        /* 启动早期会拿到还没填值的空 config（0.00）；文件夹/搜索等容器的格子只有
         * 40 上下，也不是主页网格。两者都不配当基准，否则偏移会被写进别的容器。 */
        if (!(cw_now > 80.0) || !(ch_now > 80.0)) return (void *)cfg;
        __builtin_memcpy(&base->cw, (const void *)(cfg + 0x2b), sizeof base->cw);
        __builtin_memcpy(&base->ch, (const void *)(cfg + 0x33), sizeof base->ch);
        uintptr_t box0 = ld_hm_ptr(cfg, 0x3b, x28);
        base->ox = 0;
        if (box0) __builtin_memcpy(&base->ox, (const void *)(box0 + 7), sizeof base->ox);
        uintptr_t oybox0 = ld_hm_oy_box(x28, x26);
        base->oy = 0;
        if (oybox0) __builtin_memcpy(&base->oy, (const void *)(oybox0 + 7), sizeof base->oy);
        base->ind = 0;
        __builtin_memcpy(&base->ind, (const void *)(cfg + 0x4b), sizeof base->ind);
        base->cols = cols; base->rows = rows;
        base->valid = 1;
        ld_blog("home margin base dir=%d cellW0=%.2f originX0=%.2f cellH0=%.2f originY0=%.2f ind0=%.2f cols=%d dh=%.2f",
                landscape, base->cw, base->ox, base->ch, base->oy, base->ind, cols, dh);
    }
    if ((n % 500u) == 0u) {
        double cw = 0, ch = 0;
        __builtin_memcpy(&cw, (const void *)(cfg + 0x2b), sizeof cw);
        __builtin_memcpy(&ch, (const void *)(cfg + 0x33), sizeof ch);
        ld_blog("home margin currentConfig #%u ptr=%p dir=%d cellW=%.2f cellH=%.2f",
                n + 1u, (void *)cfg, landscape, cw, ch);
    }
    double cw_cur = 0;
    __builtin_memcpy(&cw_cur, (const void *)(cfg + 0x2b), sizeof cw_cur);
    /* 只动和基准同一张网格的 config：文件夹/搜索的 cell 宽度差一个数量级。 */
    if (!isfinite(cw_cur) || __builtin_fabs(cw_cur - base->cw) > 0.3 * base->cw) return (void *)cfg;
    int bi = landscape ? 0 : 1;
    if (isfinite(base->cw) && base->cw > 1.0 && (dh != 0.0 || g_hm_wrote_cw[bi] != 0.0)) {
        double cw = base->cw - 2.0 * dh / (double)cols;
        double ox = base->ox + dh;
        if (isfinite(cw) && isfinite(ox) && cw > 1.0) {
            double cur = 0;
            __builtin_memcpy(&cur, (const void *)(cfg + 0x2b), sizeof cur);
            if (cur != cw) __builtin_memcpy((void *)(cfg + 0x2b), &cw, sizeof cw);
            uintptr_t box = ld_hm_ptr(cfg, 0x3b, x28);
            if (box) {
                double curx = 0;
                __builtin_memcpy(&curx, (const void *)(box + 7), sizeof curx);
                if ((n % 500u) == 0u)
                    ld_blog("home margin h dir=%d cols=%d cw_in=%.2f cw_prev=%.2f ox_in=%.2f ox_prev=%.2f target=cw%.2f/ox%.2f",
                            landscape, cols, cw_cur, g_hm_wrote_cw[bi], curx, g_hm_wrote_ox[bi], cw, ox);
                if (curx != ox) __builtin_memcpy((void *)(box + 7), &ox, sizeof ox);
                g_hm_wrote_ox[bi] = ox;
                g_hm_wrote_cw[bi] = cw;
            }
        }
    }

#if LD_HM_VERTICAL
    double dt = o->top, db = o->bottom;
    if (dt != 0.0 || db != 0.0) {
        if (!base->oy_valid) {
            /* 启动早期 Rx 链还没挂好，originY 会读到 0；拿 0 当基准会把整个网格顶上去。 */
            uintptr_t b = ld_hm_oy_box(x28, x26);
            double v = 0;
            if (b) __builtin_memcpy(&v, (const void *)(b + 7), sizeof v);
            if (isfinite(v) && v > 1.0) { base->oy = v; base->oy_valid = 1; }
        }
        if (rows > 0 && isfinite(base->ch) && base->ch > 1.0) {
            double ch = base->ch - (dt + db) / (double)rows;
            double oy = base->oy + dt;
            if (isfinite(ch) && isfinite(oy) && ch > 1.0) {
                double cur = 0;
                __builtin_memcpy(&cur, (const void *)(cfg + 0x33), sizeof cur);
                if (cur != ch) __builtin_memcpy((void *)(cfg + 0x33), &ch, sizeof ch);
                uintptr_t oybox = ld_hm_oy_box(x28, x26);
                uintptr_t leftbox = ld_hm_ptr(cfg, 0x3b, x28);
                if (oybox && base->oy_valid) {
                    double cury = 0;
                    __builtin_memcpy(&cury, (const void *)(oybox + 7), sizeof cury);
                    if (cury != oy) __builtin_memcpy((void *)(oybox + 7), &oy, sizeof oy);
                }
                if ((n % 500u) == 0u)
                    ld_blog("home margin vertical dir=%d cellH %.2f->%.2f originY %.2f->%.2f t=%.1f b=%.1f cid=%x/%x",
                            landscape, base->ch, ch, base->oy, oy, dt, db, ld_hm_cid(oybox), ld_hm_cid(leftbox));
            }
        }
    }
#endif /* LD_HM_VERTICAL */

    /* 指示器底边距：GridController.workspaceIndicatorMarginBottom = hotSeatsMarginBottom()
     * + cfg+0x4b，再减去 getEditIndicatorMarginBottom。改 cfg+0x4b 即整条链路跟着动。 */
    double di = o->indicator_y;
    if (di != 0.0 && isfinite(base->ind)) {
        double ind = base->ind + di;
        if (isfinite(ind)) {
            double cur = 0;
            __builtin_memcpy(&cur, (const void *)(cfg + 0x4b), sizeof cur);
            if (cur != ind) __builtin_memcpy((void *)(cfg + 0x4b), &ind, sizeof ind);
            if ((n % 500u) == 0u)
                ld_blog("home margin indicator dir=%d ind %.2f->%.2f dy=%.1f",
                        landscape, base->ind, ind, di);
        }
    }
    return (void *)cfg;
}

__attribute__((naked, used)) static void ld_hm_cc_hook(void)
{
    __asm__ volatile(
        "adrp x16, g_hm_cc_save\n"
        "add  x16, x16, :lo12:g_hm_cc_save\n"
        "str  x15, [x16, #8]\n"
        "str  x30, [x16, #16]\n"
        "str  x28, [x16, #40]\n"
        "str  x27, [x16, #48]\n"
        "str  x26, [x16, #56]\n"
        "mov  x17, sp\n"
        "str  x17, [x16, #32]\n"
        "adrp x17, g_hm_cc_orig\n"
        "ldr  x17, [x17, :lo12:g_hm_cc_orig]\n"
        "cbz  x17, 1f\n"
        "blr  x17\n"
        "1:\n"
        "adrp x17, g_hm_cc_save\n"
        "add  x17, x17, :lo12:g_hm_cc_save\n"
        "str  x0, [x17, #24]\n"
        "sub  x17, x15, #1024\n"
        "and  x17, x17, #0xfffffffffffffff0\n"
        "mov  sp, x17\n"
        "adrp x0, g_hm_cc_save\n"
        "add  x0, x0, :lo12:g_hm_cc_save\n"
        "bl   ld_hm_cc_post\n"
        "adrp x17, g_hm_cc_save\n"
        "add  x17, x17, :lo12:g_hm_cc_save\n"
        "ldr  x16, [x17, #32]\n"
        "mov  sp, x16\n"
        "ldr  x15, [x17, #8]\n"
        "ldr  x30, [x17, #16]\n"
        "ret\n");
}

static void ld_home_margins_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    state = 1;
    const struct ld_grid_offsets *lo = &g_home_options.landscape, *po = &g_home_options.portrait;
    /* 全 0 也要装：hook 负责把基准写回去，否则配置归零后界面会停在上一次的偏移上。 */
    ld_blog("home margin: landscape(h=%.1f t=%.1f b=%.1f ind=%.1f) portrait(h=%.1f t=%.1f b=%.1f ind=%.1f)",
            lo->horizontal, lo->top, lo->bottom, lo->indicator_y,
            po->horizontal, po->top, po->bottom, po->indicator_y);
    uintptr_t cc = 0;
    static const uint32_t cc_pat[8] = {
        0xa9bf79fdu, 0xaa0f03fdu, 0xd10021efu, 0xf9403f40u,
        0xf9400000u, 0x6b16001fu, 0x540001a1u, 0xf9403f40u,
    };
    cc = ld_find_aot_pattern(lib, LD_HM_CC_VADDR, cc_pat, 8);
    if (!cc) cc = ld_find_aot_pattern(lib, 0xb72cb8u, cc_pat, 8);
    if (cc) {
        const uint32_t *w = (const uint32_t *)cc;
        uint32_t inst = w[4];
        if ((inst & 0xffc00000u) == 0xf9400000u) {
            g_hm_ctrl_offset = ((inst >> 10) & 0xfffu) * 8;
            g_grid_ctrl_offset = g_hm_ctrl_offset;
            ld_blog("home margin: currentConfig decoded ctrl_offset=0x%lx", (unsigned long)g_hm_ctrl_offset);
        }
    }
    if (!cc) {
        ld_blog("home margin: currentConfig address unavailable or prologue mismatch");
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)cc, (void *)ld_hm_cc_hook, &backup) == 0 && backup) {
        g_hm_cc_orig = backup;
        ld_blog("home margin: currentConfig hooked at %p", (void *)cc);
    } else {
        ld_blog("home margin: currentConfig hook failed");
    }
}

#endif /* LD_HOME_MARGINS_H */
