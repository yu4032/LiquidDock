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
 * 实现：后置 hook currentConfig。基准优先按 GridController、方向、网格尺寸和原生几何识别，
 * 不把页面 scope 当成基准；Normal、工作台桌面、AllApps 的输出分别留档。Launcher 为页面
 * 新建控制器时，只要输入完整命中本模块留存的旧输出，也会复用其原始基准。
 * 全零配置仍保留 hook，以便切回零值。
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
#include "hm_transition.h"
#include "screen_rotation.h"

#define LD_HM_CC_VADDR 0xb47388u   /* GridController.currentConfig */
#define LD_HM_VERTICAL 1           /* 竖直：cellH 收缩 + GridController+0xeb 的 originY */

static void *g_hm_cc_orig __attribute__((used));
static volatile unsigned g_hm_cc_calls __attribute__((used));
static volatile int g_hm_status __attribute__((used));
static volatile unsigned g_hm_context_hits __attribute__((used));
static volatile unsigned g_hm_scope_mask __attribute__((used));
static volatile unsigned g_hm_dir_mask __attribute__((used));

enum {
    LD_HM_STATUS_PENDING = 0,
    LD_HM_STATUS_SYMBOLS_MISSING = 1,
    LD_HM_STATUS_HOOK_FAILED = 2,
    LD_HM_STATUS_INSTALLED = 3,
    LD_HM_STATUS_CONTEXT_MATCHED = 4,
    LD_HM_STATUS_FIELD_DECODE_FAILED = 5,
    LD_HM_STATUS_RUNTIME_OBJECT_MISMATCH = 6,
    LD_HM_STATUS_RUNTIME_DIMENSIONS_MISSING = 7,
};

static const char *ld_hm_status_name(int status)
{
    switch (status) {
    case LD_HM_STATUS_SYMBOLS_MISSING: return "symbols_missing";
    case LD_HM_STATUS_HOOK_FAILED: return "hook_failed";
    case LD_HM_STATUS_INSTALLED: return "hooks_installed_waiting_for_grid";
    case LD_HM_STATUS_CONTEXT_MATCHED: return "grid_context_matched";
    case LD_HM_STATUS_FIELD_DECODE_FAILED: return "field_decode_failed";
    case LD_HM_STATUS_RUNTIME_OBJECT_MISMATCH: return "runtime_object_mismatch";
    case LD_HM_STATUS_RUNTIME_DIMENSIONS_MISSING: return "runtime_dimensions_missing";
    default: return "pending";
    }
}

static void ld_hm_set_status(int status, const char *detail)
{
    int previous = __atomic_exchange_n(&g_hm_status, status, __ATOMIC_RELAXED);
    if (previous != status)
        ld_blog("home margin monitor state=%s detail=%s", ld_hm_status_name(status),
                detail ? detail : "");
}

struct ld_hm_output {
    double cw, ox, ch, oy, ind;
    int valid, oy_valid;
};
struct ld_hm_base {
    uintptr_t identity;
    double cw, ox, ch, oy, ind;
    struct ld_hm_output output[3]; /* Normal, Workstation desktop, Workstation AllApps */
    int valid, identity_kind;
    int bi, cols, rows, oy_valid;
};
#define LD_HM_MAX_BASES 16
static struct ld_hm_base g_hm_bases[LD_HM_MAX_BASES] __attribute__((used));
/* New-direction geometry can precede the vendor's GridConfig commit by ~53ms.
 * Keep its transient baseline OUT of the durable orientation cache, yet
 * retain the last output on this thread to make repeated callbacks idempotent.
 * One slot per controller: a A->B->A sequence across a page switch must not
 * evict A's in-flight baseline and re-derive it from already-shifted cells. */
#define LD_HM_MAX_TRANS 8
/* Frames after which an idle provisional slot may be recycled. */
#define LD_HM_TRANS_MAX_AGE 1024u
static __thread struct ld_hm_base g_hm_trans[LD_HM_MAX_TRANS];
static __thread unsigned g_hm_trans_seen[LD_HM_MAX_TRANS];
static __thread unsigned g_hm_trans_clock __attribute__((used));

static struct ld_hm_base *ld_hm_trans_slot(uintptr_t identity, int identity_kind,
                                           int dir, int cols, int rows)
{
    struct ld_hm_trans_key keys[LD_HM_MAX_TRANS];
    unsigned char valid[LD_HM_MAX_TRANS];
    for (unsigned i = 0; i < LD_HM_MAX_TRANS; i++) {
        keys[i] = (struct ld_hm_trans_key){g_hm_trans[i].identity,
                                           g_hm_trans[i].identity_kind,
                                           g_hm_trans[i].bi,
                                           g_hm_trans[i].cols,
                                           g_hm_trans[i].rows};
        valid[i] = g_hm_trans[i].valid != 0;
    }
    unsigned now = ++g_hm_trans_clock;
    int idx = ld_hm_trans_pick(keys, valid, g_hm_trans_seen, LD_HM_MAX_TRANS,
                               (struct ld_hm_trans_key){identity, identity_kind,
                                                         dir, cols, rows},
                               now, LD_HM_TRANS_MAX_AGE);
    if (idx < 0) return NULL;
    g_hm_trans_seen[idx] = now;
    return &g_hm_trans[idx];
}
static unsigned g_hm_base_next __attribute__((used));
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
static volatile int g_hm_field_decode_failed __attribute__((used));

static int ld_hm_has_vertical_offsets(void)
{
    if (g_home_options.portrait.top != 0.0 || g_home_options.landscape.top != 0.0)
        return 1;
    if (g_home_options.ws.dock_custom) {
        if (g_home_options.ws.apps_land_top != 0.0 || g_home_options.ws.apps_port_top != 0.0)
            return 1;
        if (!g_home_options.ws.desktop_sync_normal &&
            (g_home_options.ws.desktop_landscape.top != 0.0 ||
             g_home_options.ws.desktop_portrait.top != 0.0))
            return 1;
    }
    return 0;
}

static int ld_hm_offsets_nonzero(const struct ld_grid_offsets *o)
{
    return o && (o->horizontal != 0.0 || o->top != 0.0 || o->bottom != 0.0 ||
                 o->indicator_y != 0.0);
}

static int ld_hm_has_any_offsets(void)
{
    if (ld_hm_offsets_nonzero(&g_home_options.portrait) ||
        ld_hm_offsets_nonzero(&g_home_options.landscape))
        return 1;
    if (!g_home_options.ws.dock_custom) return 0;
    if (g_home_options.ws.apps_land_h_offset != 0.0 ||
        g_home_options.ws.apps_land_top != 0.0 ||
        g_home_options.ws.apps_land_bottom != 0.0 ||
        g_home_options.ws.apps_land_ind_y != 0.0 ||
        g_home_options.ws.apps_port_h_offset != 0.0 ||
        g_home_options.ws.apps_port_top != 0.0 ||
        g_home_options.ws.apps_port_bottom != 0.0 ||
        g_home_options.ws.apps_port_ind_y != 0.0)
        return 1;
    return !g_home_options.ws.desktop_sync_normal &&
        (ld_hm_offsets_nonzero(&g_home_options.ws.desktop_landscape) ||
         ld_hm_offsets_nonzero(&g_home_options.ws.desktop_portrait));
}

__attribute__((used)) static void ld_home_margins_note(uintptr_t cfg, int dir, int cols, int rows)
{
    if (!cfg || (dir != 0 && dir != 1) || cols <= 0 || rows <= 0) return;
    g_hm_cur_dir = dir; g_hm_cur_cols = cols; g_hm_cur_rows = rows;
    static int last_note_dir = -2, last_note_cols = -1, last_note_rows = -1;
    if (dir != last_note_dir || cols != last_note_cols || rows != last_note_rows) {
        last_note_dir = dir;
        last_note_cols = cols; last_note_rows = rows;
        ld_blog("home margin pipeline grid dir=%d cols=%d rows=%d configured=%dx%d",
                dir, cols, rows, g_grid_cols, g_grid_rows);
    }
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
static uintptr_t g_hm_oy_offset = 0;

static uintptr_t ld_hm_controller(uintptr_t x26)
{
    uintptr_t fields = 0, ctrl = 0;
    if (!x26 || !g_hm_ctrl_offset) return 0;
    __builtin_memcpy(&fields, (const void *)(x26 + 0x78), sizeof fields);
    if (!fields) return 0;
    __builtin_memcpy(&ctrl, (const void *)(fields + g_hm_ctrl_offset), sizeof ctrl);
    return ctrl;
}

/* currentConfig 编译后会移动字段访问指令。按语义找 controller 字段与 Rx
 * 压缩指针字段，避免把某一版的固定指令序号当作跨版本 ABI。 */
static int ld_hm_decode_current_config_fields(uintptr_t cc, size_t bytes)
{
    if (!cc || !executable_range(cc, 16u)) return 0;
    if (bytes < 16u || bytes > 0x1000u) bytes = 0x100u;
    if (!executable_range(cc, bytes)) return 0;
    const uint32_t *w = (const uint32_t *)cc;
    size_t n = bytes / sizeof(uint32_t);
    int found_ctrl = 0, found_rx = 0, ambiguous_ctrl = 0, ambiguous_rx = 0;
    uintptr_t ctrl = 0, rx = 0;
    for (size_t i = 0; i + 2 < n; i++) {
        /* ldr x0,[x26,#fields] ; ldr x0,[x0,#controller] ; cmp w0,w22 */
        if ((w[i] & 0xffc003ffu) == 0xf9400340u &&
            (w[i + 1] & 0xffc003ffu) == 0xf9400000u &&
            w[i + 2] == 0x6b16001fu) {
            uintptr_t candidate = (uintptr_t)((w[i + 1] >> 10) & 0xfffu) * 8u;
            if (!found_ctrl) { ctrl = candidate; found_ctrl = 1; }
            else if (ctrl != candidate) ambiguous_ctrl = 1;
        }
        /* ldur w1,[x0,#rx] ; add x1,x1,x28,lsl #32 ; bl Rx.value */
        if ((w[i] & 0xffe00fffu) == 0xb8400001u &&
            w[i + 1] == 0x8b1c8021u &&
            (w[i + 2] & 0xfc000000u) == 0x94000000u) {
            int imm = (int)((w[i] >> 12) & 0x1ffu);
            if (imm & 0x100) imm -= 0x200;
            if (imm >= 0) {
                uintptr_t candidate = (uintptr_t)imm;
                if (!found_rx) { rx = candidate; found_rx = 1; }
                else if (rx != candidate) ambiguous_rx = 1;
            }
        }
    }
    found_ctrl = found_ctrl && !ambiguous_ctrl;
    found_rx = found_rx && !ambiguous_rx;
    g_hm_ctrl_offset = found_ctrl ? ctrl : 0;
    g_grid_ctrl_offset = g_hm_ctrl_offset;
    g_grid_rx_offset = found_rx ? rx : 0;
    ld_blog("home margin currentConfig semantic decode ctrl=%s0x%lx rx=%s0x%lx",
            found_ctrl ? "" : "unavailable/", (unsigned long)g_hm_ctrl_offset,
            found_rx ? "" : "unavailable/", (unsigned long)g_grid_rx_offset);
    return found_ctrl && found_rx;
}

static int ld_hm_decode_gridheight_oy(uintptr_t fn, size_t bytes, uintptr_t *out)
{
    if (out) *out = 0;
    if (!fn || !out) return 0;
    if (bytes < 16u || bytes > 0x2000u) bytes = 0x200u;
    /* .gnu_debugdata may omit/understate Dart function sizes; include the known
     * compiler-generated tail while staying inside executable pages. */
    size_t scan = bytes + 0x200u;
    if (scan > 0x4000u) scan = 0x4000u;
    int found = 0, ambiguous = 0;
    uintptr_t value = 0;
    for (size_t off = 0; off + 16u <= scan; off += 4u) {
        uintptr_t at = fn + off;
        if (!executable_range(at, 16u)) break;
        const uint32_t *w = (const uint32_t *)at;
        if ((w[0] & 0xffe00fffu) != 0xb8400001u ||
            w[1] != 0x8b1c8021u ||
            (w[2] & 0xfc000000u) != 0x94000000u ||
            (w[3] & 0xffe00fffu) != 0xfc400001u)
            continue;
        int imm = (int)((w[0] >> 12) & 0x1ffu);
        if (imm & 0x100) imm -= 0x200;
        if (imm < 0) continue;
        if (!found) { value = (uintptr_t)imm; found = 1; }
        else if (value != (uintptr_t)imm) ambiguous = 1;
    }
    if (found && !ambiguous) *out = value;
    return found && !ambiguous;
}

/* originY 的真值不在 cfg 上：cellToCenterOffset 用的是 GridController 单例的
 * controller+decodedOffset -> Rx+0x13 -> boxed double+7。controllerOffset 来自 currentConfig，
 * Rx 字段来自 GridController.gridHeight 的指令语义扫描。 */
static uintptr_t ld_hm_oy_box(uintptr_t x28, uintptr_t x26)
{
    uintptr_t fields = 0, ctrl = 0;
    if (!x26 || !g_hm_ctrl_offset || !g_hm_oy_offset) return 0;
    __builtin_memcpy(&fields, (const void *)(x26 + 0x78), sizeof fields);
    if (!fields) return 0;
    __builtin_memcpy(&ctrl, (const void *)(fields + g_hm_ctrl_offset), sizeof ctrl);
    if (!ctrl) return 0;
    uintptr_t rx = ld_hm_ptr(ctrl, g_hm_oy_offset, x28);
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

    /* Follow the launcher orientation enum. GridConfig dimensions may be
     * transposed on tablets and must not override the physical orientation. */
    uintptr_t variables = ld_hm_ptr(cfg, 0x8f, x28);
    uintptr_t vo = ld_hm_ptr(variables, 0x23, x28);
    int64_t cfg_cols = 0, cfg_rows = 0, dir_raw = -1;
    __builtin_memcpy(&cfg_cols, (const void *)(cfg + 0x1b), sizeof cfg_cols);
    __builtin_memcpy(&cfg_rows, (const void *)(cfg + 0x23), sizeof cfg_rows);
    if (vo) __builtin_memcpy(&dir_raw, (const void *)(vo + 7), sizeof dir_raw);

    int landscape = -1, cols = 0, rows = 0;
    int is_laptop = __atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED);
    int is_allapps = is_laptop && g_home_options.ws.dock_custom &&
        ld_ws_allapps_geometry();
    int scope = is_laptop ? (is_allapps ? 2 : 1) : 0;
    uintptr_t controller = ld_hm_controller(x26);
    uintptr_t identity = controller ? controller : variables ? variables : cfg;
    int identity_kind = controller ? 1 : variables ? 2 : 3;
    const struct ld_hm_note *note = ld_hm_lookup(cfg, x28);
    if (cfg_cols >= 2 && cfg_cols <= 16 && cfg_rows >= 2 && cfg_rows <= 16) {
        cols = (int)cfg_cols;
        rows = (int)cfg_rows;
        if (dir_raw == 0 || dir_raw == 1)
            landscape = (dir_raw == 1);
    }
    int display_landscape = ld_screen_landscape();
    int transitional_geometry = 0;
    if (display_landscape >= 0) {
        /* The display event is physically classified from width/height,
         * never raw rotation parity. Resolve the row/column divisors in
         * the same operation so new landscape offsets cannot be divided
         * by obsolete portrait counts. */
        /* AllApps has its own full, non-sparse grid. Do not replace its
         * counts with the workspace's configured rows/columns. */
        int nominal_cols = is_allapps ? 0 : (int)g_grid_cols;
        int nominal_rows = is_allapps ? 0 : (int)g_grid_rows;
        /* Validate wide Dart fields BEFORE narrowing to int; otherwise
         * values such as 0x100000008 would alias a legitimate count of 8. */
        int valid_dir = (dir_raw == 0 || dir_raw == 1) ? (int)dir_raw : -1;
        int valid_cols = (cfg_cols >= 2 && cfg_cols <= 16) ? (int)cfg_cols : 0;
        int valid_rows = (cfg_rows >= 2 && cfg_rows <= 16) ? (int)cfg_rows : 0;
        struct ld_hm_layout_shape shape = ld_hm_layout_shape_for_display(
                valid_dir, valid_cols, valid_rows,
                display_landscape, nominal_cols, nominal_rows);
        if (shape.landscape >= 0 && shape.cols > 0 && shape.rows > 0) {
            landscape = shape.landscape;
            cols = shape.cols;
            rows = shape.rows;
            transitional_geometry = shape.provisional;
        }
    }
    if (landscape < 0 && note) {
        landscape = (note->dir == 1);   /* ld_grid_direction: 1=横屏 0=竖屏 */
        if (cols <= 0) { cols = note->cols; rows = note->rows; }
    } else if (landscape < 0 && (g_hm_cur_dir == 0 || g_hm_cur_dir == 1)) {
        landscape = (g_hm_cur_dir == 1);
        if (cols <= 0) { cols = g_hm_cur_cols; rows = g_hm_cur_rows; }
    }
    if (landscape < 0 && (dir_raw == 0 || dir_raw == 1)) {
        landscape = (dir_raw == 1);
        if (cols <= 0) {
            cols = landscape ? g_grid_cols : g_grid_rows;
            rows = landscape ? g_grid_rows : g_grid_cols;
        }
    }
    if (landscape < 0 || cols <= 0 || rows <= 0) {
        if (ld_hm_has_any_offsets() && n >= 8u)
            ld_hm_set_status(LD_HM_STATUS_RUNTIME_DIMENSIONS_MISSING,
                             is_allapps
                                 ? "AllApps is active but orientation or grid dimensions are unavailable"
                                 : "orientation or grid dimensions are unavailable for configured margins");
        return (void *)cfg;
    }
    double cell_w_now = 0;
    __builtin_memcpy(&cell_w_now, (const void *)(cfg + 0x2b), sizeof cell_w_now);
    static int last_land = -1, last_cols = -1, last_rows = -1;
    if (landscape != last_land || cols != last_cols || rows != last_rows) {
        last_land = landscape; last_cols = cols; last_rows = rows;
        ld_blog("home margin orient land=%d cols=%d rows=%d raw_dir=%lld surface_rotation=%d display_land=%d transitional=%d cfg=%lldx%lld note=%d cellW=%.2f",
                landscape, cols, rows, (long long)dir_raw, ld_screen_rotation(),
                display_landscape, transitional_geometry,
                (long long)cfg_cols, (long long)cfg_rows, note ? 1 : 0, cell_w_now);
    }

    struct ld_grid_offsets ws_off = {0};
    const struct ld_grid_offsets *o;
    if (is_laptop && g_home_options.ws.dock_custom) {
        if (is_allapps) {
            ws_off.horizontal  = landscape ? g_home_options.ws.apps_land_h_offset : g_home_options.ws.apps_port_h_offset;
            ws_off.top         = landscape ? g_home_options.ws.apps_land_top      : g_home_options.ws.apps_port_top;
            ws_off.bottom      = landscape ? g_home_options.ws.apps_land_bottom   : g_home_options.ws.apps_port_bottom;
            ws_off.indicator_y = landscape ? -g_home_options.ws.apps_land_ind_y   : -g_home_options.ws.apps_port_ind_y;
            o = &ws_off;
        } else if (!g_home_options.ws.desktop_sync_normal) {
            o = landscape ? &g_home_options.ws.desktop_landscape : &g_home_options.ws.desktop_portrait;
        } else {
            o = landscape ? &g_home_options.landscape : &g_home_options.portrait;
        }
    } else {
        o = landscape ? &g_home_options.landscape : &g_home_options.portrait;
    }
    double dh = o->horizontal;
    if (cols <= 0) return (void *)cfg;

    int mode_idx = is_laptop ? 1 : 0;
    int bi = landscape ? 0 : 1;
    uintptr_t box0 = ld_hm_ptr(cfg, 0x3b, x28);
    double cw_now = 0, ch_now = 0, ox_now = 0, ind_now = 0;
    __builtin_memcpy(&cw_now, (const void *)(cfg + 0x2b), sizeof cw_now);
    __builtin_memcpy(&ch_now, (const void *)(cfg + 0x33), sizeof ch_now);
    __builtin_memcpy(&ind_now, (const void *)(cfg + 0x4b), sizeof ind_now);
    if (box0) __builtin_memcpy(&ox_now, (const void *)(box0 + 7), sizeof ox_now);
    double oy_now = 0;
    int oy_ok = 0;
    if (box0) {
        __builtin_memcpy(&oy_now, (const void *)(box0 + 0xf), sizeof oy_now);
        oy_ok = isfinite(oy_now);
    }

    struct ld_hm_base *base = 0;
    int matched_scope = -1;
    /* Prefer this controller's baseline. Some Launcher pages create another
     * controller while carrying forward geometry already written by a prior
     * scope; allow cross-controller reuse only when every managed field exactly
     * matches one of our recorded outputs. Never infer a baseline from another
     * controller's unmodified geometry. */
    for (int pass = 0; pass < 2 && !base; pass++) {
        /* A just-committed GridConfig can still carry the exact output
         * we wrote during the preceding transition. Check those provisional
         * slots LAST, after durable baselines, so we don't capture our own
         * offset as a new durable native baseline and apply it twice. They
         * are scanned in both modes: the commit may arrive after the window. */
        unsigned slots = LD_HM_MAX_BASES + LD_HM_MAX_TRANS;
        for (unsigned i = 0; i < slots; i++) {
            int from_provisional = i >= LD_HM_MAX_BASES;
            struct ld_hm_base *b = from_provisional
                    ? &g_hm_trans[i - LD_HM_MAX_BASES] : &g_hm_bases[i];
            int same_identity = b->identity == identity && b->identity_kind == identity_kind;
            int provisional = from_provisional || transitional_geometry;
            /* The provisional baseline may only follow its own controller
             * through a transition, never migrate to a different page. */
            if (provisional && !same_identity) continue;
            if (!b->valid || b->bi != bi || b->cols != cols || b->rows != rows ||
                (pass == 0 ? !same_identity : same_identity)) continue;
            int native_geometry = __builtin_fabs(cw_now - b->cw) < 0.5 &&
                __builtin_fabs(ch_now - b->ch) < 0.5 &&
                __builtin_fabs(ind_now - b->ind) < 0.5 &&
                (!box0 || __builtin_fabs(ox_now - b->ox) < 1.0) &&
                (!oy_ok || !b->oy_valid || __builtin_fabs(oy_now - b->oy) < 1.0);
            int written_scope = -1;
            for (int s = 0; s < 3; s++) {
                const struct ld_hm_output *out = &b->output[s];
                if (out->valid && __builtin_fabs(cw_now - out->cw) < 0.5 &&
                    __builtin_fabs(ch_now - out->ch) < 0.5 &&
                    __builtin_fabs(ind_now - out->ind) < 0.5 &&
                    (!box0 || __builtin_fabs(ox_now - out->ox) < 1.0) &&
                    (!oy_ok || (out->oy_valid && __builtin_fabs(oy_now - out->oy) < 1.0))) {
                    written_scope = s;
                    break;
                }
            }
            if ((pass == 0 && native_geometry && !provisional) ||
                written_scope >= 0) {
                base = b;
                matched_scope = native_geometry ? -1 : written_scope;
                break;
            }
        }
    }
    if (!base) {
        /* 启动早期会拿到还没填值的空 config（0.00）；文件夹/搜索等容器的格子只有
         * 40 上下，也不是主页网格。两者都不配当基准，否则偏移会被写进别的容器。 */
        if (!isfinite(cw_now) || !isfinite(ch_now) || !(cw_now > 80.0) || !(ch_now > 80.0)) {
            if ((note || is_allapps) && n >= 8u)
                ld_hm_set_status(LD_HM_STATUS_RUNTIME_OBJECT_MISMATCH,
                                 "active home grid resolved to an empty or non-grid config");
            return (void *)cfg;
        }
        if (transitional_geometry) {
            base = ld_hm_trans_slot(identity, identity_kind, bi, cols, rows);
            if (!base) {
                /* Every provisional slot still belongs to a recent controller.
                 * Registering this config now would record geometry we already
                 * shifted as native and apply the offset twice; skip the frame
                 * and let the vendor commit settle first. */
                static int logged_full;
                if (!logged_full) {
                    logged_full = 1;
                    ld_blog("home margin transition slots full identity=%p; frame left untouched",
                            (void *)identity);
                }
                return (void *)cfg;
            }
        } else {
            base = &g_hm_bases[g_hm_base_next++ % LD_HM_MAX_BASES];
        }
        base->cw = cw_now;
        base->ch = ch_now;
        base->ox = 0;
        if (box0) __builtin_memcpy(&base->ox, (const void *)(box0 + 7), sizeof base->ox);
        /* cfg+0x3b is the per-config workspace EdgeInsets. Its top stays native
         * while the controller's shared Rx can still hold the previous
         * orientation during currentConfig. Capture both origins from cfg. */
        base->oy = oy_ok ? oy_now : 0.0;
        base->oy_valid = oy_ok;
        base->ind = 0;
        __builtin_memcpy(&base->ind, (const void *)(cfg + 0x4b), sizeof base->ind);
        base->bi = bi;
        base->cols = cols;
        base->rows = rows;
        base->identity = identity;
        base->identity_kind = identity_kind;
        __builtin_memset(base->output, 0, sizeof base->output);
        base->valid = 1;
        ld_blog("home margin base identity=%p key=%d scope=%d dir=%d transient=%d cellW0=%.2f originX0=%.2f cellH0=%.2f originY0=%.2f ind0=%.2f cols=%d dh=%.2f",
                (void *)identity, identity_kind, scope, landscape, transitional_geometry,
                base->cw, base->ox,
                base->ch, base->oy, base->ind, cols, dh);
    } else if (matched_scope >= 0 && matched_scope != scope) {
        static int last_source_scope = -1, last_target_scope = -1, last_reuse_dir = -1;
        if (matched_scope != last_source_scope || scope != last_target_scope || landscape != last_reuse_dir) {
            last_source_scope = matched_scope;
            last_target_scope = scope;
            last_reuse_dir = landscape;
            ld_blog("home margin baseline reused source=%p current=%p dir=%d source_scope=%d target_scope=%d",
                    (void *)base->identity, (void *)identity, landscape, matched_scope, scope);
        }
    }
    if (n == 0u || n == 1000u || n == 100000u) {
        double cw = 0, ch = 0;
        __builtin_memcpy(&cw, (const void *)(cfg + 0x2b), sizeof cw);
        __builtin_memcpy(&ch, (const void *)(cfg + 0x33), sizeof ch);
        ld_blog("home margin currentConfig #%u ptr=%p mode=%d dir=%d cellW=%.2f cellH=%.2f",
                n + 1u, (void *)cfg, mode_idx, landscape, cw, ch);
    }
    double cw_cur = 0;
    __builtin_memcpy(&cw_cur, (const void *)(cfg + 0x2b), sizeof cw_cur);
    /* The lookup above accepts either the native geometry or this hook's last
     * output. This keeps mode/page transitions idempotent while rejecting
     * unrelated configs before they can become a baseline. */
    __atomic_add_fetch(&g_hm_context_hits, 1u, __ATOMIC_RELAXED);
    __atomic_fetch_or(&g_hm_scope_mask, 1u << scope, __ATOMIC_RELAXED);
    __atomic_fetch_or(&g_hm_dir_mask, 1u << landscape, __ATOMIC_RELAXED);
    if (!g_hm_field_decode_failed)
        ld_hm_set_status(LD_HM_STATUS_CONTEXT_MATCHED,
                         "currentConfig matched active grid dimensions and scope");
    static int last_logged_scope = -1, last_logged_dir = -1;
    static int last_logged_cols = -1, last_logged_rows = -1;
    if (scope != last_logged_scope || landscape != last_logged_dir ||
        cols != last_logged_cols || rows != last_logged_rows) {
        last_logged_scope = scope;
        last_logged_dir = landscape;
        last_logged_cols = cols;
        last_logged_rows = rows;
        ld_blog("home margin monitor runtime=matched scope=%d dir=%d cols=%d rows=%d",
                scope, landscape, cols, rows);
    }
    if (isfinite(base->cw) && base->cw > 1.0) {
        double cw = base->cw - 2.0 * dh / (double)cols;
        double ox = base->ox + dh;
        if (isfinite(cw) && isfinite(ox) && cw > 1.0) {
            if (cw_cur != cw) __builtin_memcpy((void *)(cfg + 0x2b), &cw, sizeof cw);
            uintptr_t box = ld_hm_ptr(cfg, 0x3b, x28);
            if (box) {
                double curx = 0;
                __builtin_memcpy(&curx, (const void *)(box + 7), sizeof curx);
                if (curx != ox) __builtin_memcpy((void *)(box + 7), &ox, sizeof ox);
                if ((dh != 0.0 || cw_cur != cw || curx != ox) && (n % 500u) == 0u)
                    ld_blog("home margin h mode=%d scope=%d dir=%d cols=%d dh=%.2f cw_in=%.2f ox_in=%.2f target=cw%.2f/ox%.2f",
                            mode_idx, scope, landscape, cols, dh, cw_cur, curx, cw, ox);
            } else if (dh != 0.0) {
                ld_hm_set_status(LD_HM_STATUS_RUNTIME_OBJECT_MISMATCH,
                                 "GridConfig originX box is unavailable for horizontal offset");
            }
        }
    }

#if LD_HM_VERTICAL
    double dt = o->top, db = o->bottom;
    if (base->oy_valid && rows > 0 && isfinite(base->ch) && base->ch > 1.0) {
        double ch = base->ch - (dt + db) / (double)rows;
        double oy = base->oy + dt;
        if (isfinite(ch) && isfinite(oy) && ch > 1.0) {
            double ch_cur = 0;
            __builtin_memcpy(&ch_cur, (const void *)(cfg + 0x33), sizeof ch_cur);
            if (ch_cur != ch) __builtin_memcpy((void *)(cfg + 0x33), &ch, sizeof ch);
            uintptr_t oybox = ld_hm_oy_box(x28, x26);
            /* The active-config listener publishes this inset to originY.
             * Keep it in sync so the next rotation publish sees the same value. */
            int publish_through_config = scope == 0 && oy_ok &&
                (oy_now == oy || !ld_ws_is_const_ro(box0));
            if (oy_ok && oy_now != oy && !ld_ws_is_const_ro(box0)) {
                __builtin_memcpy((void *)(box0 + 0xf), &oy, sizeof oy);
                static unsigned reports;
                if (reports++ < 32u)
                    ld_blog("home margin top synced scope=%d dir=%d grid=%dx%d inset=%.2f->%.2f",
                            scope, landscape, cols, rows, oy_now, oy);
                oy_now = oy;
            }
            double cury = oy;
            if (oybox) {
                __builtin_memcpy(&cury, (const void *)(oybox + 7), sizeof cury);
                /* The Launcher setter compares the old and new values before
                 * notifying layout listeners. Leave the old value intact so
                 * a normal desktop rotation can publish the new one. */
                if (!publish_through_config && cury != oy)
                    __builtin_memcpy((void *)(oybox + 7), &oy, sizeof oy);
                static int last_publish_dir = -1;
                if (publish_through_config && landscape != last_publish_dir) {
                    ld_blog("home margin top publish dir=%d grid=%dx%d inset=%.2f previous_rx=%.2f",
                            landscape, cols, rows, oy, cury);
                    last_publish_dir = landscape;
                }
            } else if (dt != 0.0) {
                ld_hm_set_status(LD_HM_STATUS_RUNTIME_OBJECT_MISMATCH,
                                 "GridController originY box is unavailable for top offset");
            }
            if ((dt != 0.0 || db != 0.0 || ch_cur != ch || cury != oy) && (n % 500u) == 0u)
                ld_blog("home margin vertical mode=%d dir=%d cellH %.2f->%.2f originY %.2f->%.2f t=%.1f b=%.1f cid=%x/%x",
                        mode_idx, landscape, base->ch, ch, base->oy, oy, dt, db, ld_hm_cid(oybox), ld_hm_cid(box0));
        }
    } else if (o->top != 0.0 && (note || is_allapps)) {
        ld_hm_set_status(LD_HM_STATUS_RUNTIME_DIMENSIONS_MISSING,
                         "top offset is configured but the native originY baseline is unavailable");
    }
#endif /* LD_HM_VERTICAL */

    /* 指示器底边距：GridController.workspaceIndicatorMarginBottom = hotSeatsMarginBottom()
     * + cfg+0x4b，再减去 getEditIndicatorMarginBottom。改 cfg+0x4b 即整条链路跟着动。 */
    double di = o->indicator_y;
    if (isfinite(base->ind)) {
        double ind = base->ind + di;
        if (isfinite(ind)) {
            double ind_cur = 0;
            __builtin_memcpy(&ind_cur, (const void *)(cfg + 0x4b), sizeof ind_cur);
            if (ind_cur != ind) __builtin_memcpy((void *)(cfg + 0x4b), &ind, sizeof ind);
            if ((di != 0.0 || ind_cur != ind) && (n % 500u) == 0u)
                ld_blog("home margin indicator mode=%d dir=%d ind %.2f->%.2f dy=%.1f",
                        mode_idx, landscape, base->ind, ind, di);
        }
    }
    double out_cw = 0, out_ox = 0, out_ch = 0, out_ind = 0;
    __builtin_memcpy(&out_cw, (const void *)(cfg + 0x2b), sizeof out_cw);
    __builtin_memcpy(&out_ch, (const void *)(cfg + 0x33), sizeof out_ch);
    __builtin_memcpy(&out_ind, (const void *)(cfg + 0x4b), sizeof out_ind);
    int out_geometry_valid = isfinite(out_cw) && isfinite(out_ch) && isfinite(out_ind);
    if (box0) {
        uintptr_t out_box = ld_hm_ptr(cfg, 0x3b, x28);
        if (out_box) __builtin_memcpy(&out_ox, (const void *)(out_box + 7), sizeof out_ox);
        else out_geometry_valid = 0;
    } else {
        out_ox = base->ox;
    }
    if (scope >= 0 && scope < 3) {
        struct ld_hm_output *out = &base->output[scope];
        out->cw = out_cw;
        out->ox = out_ox;
        out->ch = out_ch;
        out->oy = oy_now;
        out->oy_valid = oy_ok;
        out->ind = out_ind;
        out->valid = out_geometry_valid && isfinite(out_ox);

        /* One concise applied-value record per scope/orientation, refreshed when
         * settings or native baselines change. This makes a matched-but-no-op
         * margin hook distinguishable from an actually applied setting. */
        struct ld_hm_applied_log {
            int valid;
            double h, t, b, indicator, base_cw, base_ox, base_ch, base_oy;
        };
        static struct ld_hm_applied_log last_applied[3][2];
        struct ld_hm_applied_log *last = &last_applied[scope][landscape];
        double base_oy = base->oy_valid ? base->oy : 0.0;
        int changed = !last->valid || last->h != o->horizontal || last->t != o->top ||
            last->b != o->bottom || last->indicator != o->indicator_y ||
            last->base_cw != base->cw || last->base_ox != base->ox ||
            last->base_ch != base->ch || last->base_oy != base_oy;
        if (changed) {
            ld_blog("home margin applied scope=%d dir=%d grid=%dx%d offsets=h%.2f/t%.2f/b%.2f/i%.2f base=cw%.2f/ox%.2f/ch%.2f/oy%.2f out=cw%.2f/ox%.2f/ch%.2f/oy%.2f/ind%.2f oy_valid=%d geometry_valid=%d",
                    scope, landscape, cols, rows, o->horizontal, o->top, o->bottom,
                    o->indicator_y, base->cw, base->ox, base->ch, base_oy,
                    out_cw, out_ox, out_ch,
                    oy_ok ? oy_now : 0.0, out_ind,
                    base->oy_valid, out->valid);
            last->valid = 1;
            last->h = o->horizontal;
            last->t = o->top;
            last->b = o->bottom;
            last->indicator = o->indicator_y;
            last->base_cw = base->cw;
            last->base_ox = base->ox;
            last->base_ch = base->ch;
            last->base_oy = base_oy;
        }
    }
    return (void *)cfg;
}

__attribute__((naked, used)) static void ld_hm_cc_hook(void)
{
    __asm__ volatile(
        "sub  x15, x15, #64\n"
        "str  x19, [x15, #0]\n"
        "add  x16, x15, #64\n"
        "str  x16, [x15, #8]\n"
        "str  x30, [x15, #16]\n"
        "mov  x17, sp\n"
        "str  x17, [x15, #32]\n"
        "str  x28, [x15, #40]\n"
        "str  x27, [x15, #48]\n"
        "str  x26, [x15, #56]\n"
        "adrp x17, g_hm_cc_orig\n"
        "ldr  x17, [x17, :lo12:g_hm_cc_orig]\n"
        "cbz  x17, 1f\n"
        "blr  x17\n"
        "1:\n"
        "str  x0, [x15, #24]\n"
        "mov  x19, x15\n"
        "sub  x17, x15, #1024\n"
        "and  x17, x17, #0xfffffffffffffff0\n"
        "mov  sp, x17\n"
        "mov  x0, x15\n"
        "bl   ld_hm_cc_post\n"
        "ldr  x16, [x19, #32]\n"
        "mov  sp, x16\n"
        "ldr  x30, [x19, #16]\n"
        "add  x15, x19, #64\n"
        "ldr  x19, [x19, #0]\n"
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
    static const uint32_t cc_pat[25] = {
        0xa9bf79fdu, 0xaa0f03fdu, 0xd10021efu, 0xf9403f40u,
        0xf953e400u, 0x6b16001fu, 0x540001a1u, 0xf9403f40u,
        0xf94e8000u, 0xf9402370u, 0x6b10001fu, 0x54000061u,
        0xf97bf362u, 0x94425156u, 0x91402370u, 0xf947d210u,
        0xf90001f0u, 0xf9402b64u, 0x940015bcu, 0xb842b001u,
        0x8b1c8021u, 0x94107745u, 0xaa1d03efu, 0xa8c179fdu,
        0xd65f03c0u,
    };
    cc = ld_resolve_sym_prologue_or_aot(
        lib, g_home_options.home_margin_target_present,
        g_home_options.home_margin_vaddr, g_home_options.home_margin_size,
        LD_HM_CC_VADDR, cc_pat, 25, "home margin currentConfig");
    if (!cc) cc = ld_find_aot_pattern(lib, 0xb72cb8u, cc_pat, 25);
    size_t cc_size = sizeof cc_pat;
    uintptr_t symbol_cc = 0;
    if (cc && g_home_options.home_margin_target_present &&
        ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.home_margin_vaddr, &symbol_cc) &&
        symbol_cc == cc)
        cc_size = g_home_options.home_margin_size;
    if (cc && !ld_hm_decode_current_config_fields(cc, cc_size))
        ld_blog("home margin: currentConfig semantic field decode incomplete");
    static const uint32_t oy_pat[11] = {
        0x1e612802u, 0xfc1f03a2u, 0xb84eb001u, 0x8b1c8021u,
        0x9419a98fu, 0xfc407001u, 0xfc5f03a2u, 0x1e613840u,
        0xaa1d03efu, 0xa8c179fdu, 0xd65f03c0u,
    };
    uintptr_t oy_fn = 0;
    size_t oy_size = 0;
    uintptr_t oy_symbol = 0;
    if (g_home_options.home_opt_targets_present &&
        ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.home_opt_vaddr[10], &oy_symbol) &&
        executable_range(oy_symbol, 16u)) {
        oy_fn = oy_symbol;
        oy_size = g_home_options.home_opt_size[10];
    }
    if (oy_fn && !ld_hm_decode_gridheight_oy(oy_fn, oy_size, &g_hm_oy_offset)) {
        ld_blog("home margin: gridHeight symbol semantic decode failed; retrying AOT signature");
        oy_fn = 0;
    }
    if (!oy_fn) {
        uintptr_t oy_match = ld_resolve_sym_scan_or_aot(
            lib, g_home_options.home_opt_targets_present,
            g_home_options.home_opt_vaddr[10], g_home_options.home_opt_size[10], 0x200u,
            0x8fa880u, oy_pat, 11, "home margin gridHeight");
        if (oy_match && ld_hm_decode_gridheight_oy(oy_match, sizeof oy_pat, &g_hm_oy_offset))
            oy_fn = oy_match;
    }
    if (oy_fn && g_hm_oy_offset)
        ld_blog("home margin: gridHeight semantic decode oy_offset=0x%lx", (unsigned long)g_hm_oy_offset);
    if (ld_hm_has_vertical_offsets() && (!g_hm_ctrl_offset || !g_hm_oy_offset)) {
        g_hm_field_decode_failed = 1;
        ld_blog("home margin: required vertical field decode failed ctrl=0x%lx oy=0x%lx",
                (unsigned long)g_hm_ctrl_offset, (unsigned long)g_hm_oy_offset);
    }
    if (!cc) {
        ld_blog("home margin: currentConfig address unavailable or prologue mismatch");
        ld_hm_set_status(LD_HM_STATUS_SYMBOLS_MISSING,
                         "currentConfig target unavailable or signature mismatch");
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)cc, (void *)ld_hm_cc_hook, &backup) == 0 && backup) {
        g_hm_cc_orig = backup;
        ld_blog("home margin: currentConfig hooked at %p", (void *)cc);
        if (g_hm_field_decode_failed)
            ld_hm_set_status(LD_HM_STATUS_FIELD_DECODE_FAILED,
                             "vertical offset field decode failed; horizontal and bottom paths may still work");
        else
            ld_hm_set_status(LD_HM_STATUS_INSTALLED,
                             "currentConfig hook installed; waiting for a matching grid");
    } else {
        ld_blog("home margin: currentConfig hook failed");
        ld_hm_set_status(LD_HM_STATUS_HOOK_FAILED, "currentConfig hook installation failed");
    }
}

#endif /* LD_HOME_MARGINS_H */
