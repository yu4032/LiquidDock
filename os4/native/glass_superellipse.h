/* OS4 超椭圆：DrawHyperMaterial 引擎注入（路径 2，方案 A）。
 *
 * 目标：让 Dock 玻璃（HyperMaterial）走引擎内置超椭圆（连续曲线）clip 渲染，
 * 不改 Dart 场景。Canvas::DrawHyperMaterial (@0xc3c20c) 在 +0x5434 区段
 * (0xc414dc–0xc41648) 从 canvas+0x120/0x138 的 arena 栈顶 clip 记录（步长
 * 0x260）取参数，当记录 +0xcc (n) > 2.0 时 memcpy 记录 +0xd0 的 396B
 * SuperellipseParams 并调 TextureContents::SetClipSuperellipseParams
 * (@0xc43cd4)，着色器 `clip_superellipse_n > 2.0` 选超椭圆角部 SDF。
 *
 * 本 hook 挂 DrawHyperMaterial 入口（x19 = x0 = canvas，见 0xc3c24c），
 * 在原函数执行前自栈顶记录合成参数，使原有代码自动走超椭圆分支：
 *   - arena 完全按 0xc414dc 的原指令推导：
 *       idx = [canvas+0x140] + [canvas+0x138] - 1
 *       page = *( [canvas+0x120] + (idx>>4)*8 )
 *       rec  = page + (idx & 0xf) * 0x260
 *   - 记录布局（Canvas::ClipGeometry @0xc394ec 写入点反推）：
 *       +0x63 kind 字节(=1)  +0x64 (rx,ry) 半径对  +0x6c 四角半径 quad
 *       +0x7c rect quad      +0x8c 变换矩阵 64B    +0xcc n 浮点
 *       +0xd0 SuperellipseParams 396B              +0x25c 标志字节
 *   - 仅当记录 n<=2、rect 命中 Dock 尺寸（1880x3008 全屏层或"宽且矮"
 *     dock 条）时才写内存：调 ComputeSuperellipseParams(@0xd83644,
 *     x0=dest, x1=TRect*, x2=RoundingRadii*) 合成 396B 写 +0xd0，
 *     n 取输出 params[0]（引擎写入约定，见 0xc3aa04）；若其 <=2 则退回
 *     5.0（引擎规范指数，见 0x806550 fmov s1,#5.0）写 +0xcc 以确保
 *     走 >2 分支。不命中 Dock 的记录只打日志不改。
 *
 * 所有地址在安装时做 prologue/字面量校验，不匹配 fail-closed 记日志退出。
 * 关键值（arena、记录地址、kind、rect、radii、n）全部打进
 * liquiddock4-boot.log 供真机核对。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-glass-superellipse
 * 只观察不写：/data/user_de/0/com.miui.home/files/liquiddock-glass-superellipse-logonly
 */
#ifndef LD_GLASS_SUPERELLIPSE_H
#define LD_GLASS_SUPERELLIPSE_H

#include <errno.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "hyper_engine_resolver.h"
#include "native_api.h"

#define LD_GSE_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-glass-superellipse"
#define LD_GSE_LOGONLY_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-glass-superellipse-logonly"

/* 快速路径 hint vaddr；跨版本时自动全 .text 掩码扫描解析。 */
#define LD_GSE_DHM_HINT      0xc3c20cu
#define LD_GSE_COMPUTE_HINT  0xd83644u

static const uint32_t ld_gse_pat_dhm[16] = {
    0x6db63befu, 0x6d0133edu, 0x6d022bebu, 0x6d0323e9u,
    0xa9047bfdu, 0xa9056ffcu, 0xa90667fau, 0xa9075ff8u,
    0xa90857f6u, 0xa9094ff4u, 0xd12e03ffu, 0xd53bd059u,
    0x1e20400cu, 0xaa0703f5u, 0xf9401728u, 0xaa0603f6u,
};
static const uint32_t ld_gse_pat_compute[8] = {
    0x6db93befu, 0x6d0133edu, 0x6d022bebu, 0x6d0323e9u,
    0xa9047bfdu, 0xa90557f6u, 0xa9064ff4u, 0xd10783ffu,
};
static const uint32_t ld_gse_pat_clipwalk[4] = {
    0xa953a269u, /* ldp x9,x8,[x19,#0x138] */
    0x52804c0bu, /* mov w11,#0x260 */
    0x8b090108u, /* add x8,x8,x9 */
    0xf9409269u, /* ldr x9,[x19,#0x120] */
};

/* 记录字段偏移 / 尺寸。 */
#define LD_GSE_REC_SIZE   0x260u
#define LD_GSE_OFF_KIND   0x63u
#define LD_GSE_OFF_RADII  0x64u  /* (rx, ry) 2 floats */
#define LD_GSE_OFF_QUAD   0x6cu  /* 四角半径 quad 4 floats */
#define LD_GSE_OFF_RECT   0x7cu  /* rect quad 4 floats */
#define LD_GSE_OFF_N      0xccu  /* float n */
#define LD_GSE_OFF_PARAMS 0xd0u  /* SuperellipseParams 396B */
#define LD_GSE_PARAMS_SIZE 0x18cu

typedef void *(*ld_gse_dhm_fn)(void *self, void *a1, void *a2, void *a3,
                               void *a4, void *a5, void *a6, void *a7,
                               float a8);
typedef void (*ld_gse_compute_fn)(void *dest, const void *rect,
                                  const void *radii);

static ld_gse_dhm_fn g_ld_gse_orig_dhm;
static ld_gse_compute_fn g_ld_gse_compute;
static int g_ld_gse_logonly;
static volatile unsigned g_ld_gse_calls;
static volatile float g_ld_gse_large_folder_corner;
static volatile float g_ld_gse_large_folder_2x1_corner;
static volatile float g_ld_gse_widget_corner;

enum ld_glass_kind {
    LD_GLASS_KIND_NONE = 0,
    LD_GLASS_KIND_DOCK,
    LD_GLASS_KIND_CIRCULAR,
    LD_GLASS_KIND_SMALL_ICON,
    LD_GLASS_KIND_LARGE_FOLDER_2X1,
    LD_GLASS_KIND_LARGE_FOLDER,
    LD_GLASS_KIND_WIDGET,
};
static __thread enum ld_glass_kind g_ld_gse_tls_kind;

/* Observed large-folder (span_x, span_y) -> (w, h) in dp, populated by folder_size.h.
 * Used to distinguish large folders (2x1, 1x2, 2x2, 4x2, ...) from widgets when
 * widget_corner happens to equal a large folder's native corner radius (e.g. 24dp). */
#define LD_GSE_LF_DIM_SLOTS 8
struct ld_gse_lf_dim {
    int span_x;
    int span_y;
    float w;
    float h;
};
static struct ld_gse_lf_dim g_ld_gse_lf_dims[LD_GSE_LF_DIM_SLOTS];
static volatile int g_ld_gse_lf_dim_count;

static inline void ld_gse_record_large_folder_dim(int span_x, int span_y, int vertical, float extent)
{
    if (span_x < 1 || span_y < 1 || (span_x == 1 && span_y == 1) ||
        !(extent > 10.0f && extent < 1000.0f)) {
        return;
    }
    int n = __atomic_load_n(&g_ld_gse_lf_dim_count, __ATOMIC_ACQUIRE);
    if (n > LD_GSE_LF_DIM_SLOTS) n = LD_GSE_LF_DIM_SLOTS;
    for (int i = 0; i < n; i++) {
        if (g_ld_gse_lf_dims[i].span_x == span_x && g_ld_gse_lf_dims[i].span_y == span_y) {
            if (vertical) g_ld_gse_lf_dims[i].h = extent;
            else g_ld_gse_lf_dims[i].w = extent;
            return;
        }
    }
    if (n < LD_GSE_LF_DIM_SLOTS) {
        g_ld_gse_lf_dims[n].span_x = span_x;
        g_ld_gse_lf_dims[n].span_y = span_y;
        g_ld_gse_lf_dims[n].w = vertical ? 0.0f : extent;
        g_ld_gse_lf_dims[n].h = vertical ? extent : 0.0f;
        __atomic_store_n(&g_ld_gse_lf_dim_count, n + 1, __ATOMIC_RELEASE);
    }
}

static inline int ld_gse_matches_recorded_large_folder(float w, float h)
{
    int n = __atomic_load_n(&g_ld_gse_lf_dim_count, __ATOMIC_ACQUIRE);
    if (n > LD_GSE_LF_DIM_SLOTS) n = LD_GSE_LF_DIM_SLOTS;
    for (int i = 0; i < n; i++) {
        float fw = g_ld_gse_lf_dims[i].w;
        float fh = g_ld_gse_lf_dims[i].h;
        if (fw > 10.0f && fh > 10.0f &&
            fabsf(w - fw) < 4.5f && fabsf(h - fh) < 4.5f) {
            return 1;
        }
    }
    return 0;
}

/* Single source of truth for classifying glass rects across glass_superellipse.h,
 * prismal_glass.h, and glass_filter_hook.h (all dimensions in dp). */
static inline enum ld_glass_kind ld_gse_classify_rect(float w, float h, float rx, float ry,
                                                      int dock, float wg_c)
{
    if (dock) return LD_GLASS_KIND_DOCK;
    float min_dim = fminf(w, h);
    float max_dim = fmaxf(w, h);
    if (max_dim <= 1.0f) return LD_GLASS_KIND_NONE;
    if (max_dim < 115.0f) return LD_GLASS_KIND_SMALL_ICON;
    /* Preserve native full-circle widgets (e.g. 2x2 circular clock) while allowing
     * elongated 2x1/1x2 large folders (aspect > 1.35). */
    if (max_dim <= min_dim * 1.35f &&
        (rx >= min_dim * 0.45f || ry >= min_dim * 0.45f)) {
        return LD_GLASS_KIND_CIRCULAR;
    }
    float aspect = max_dim / fmaxf(min_dim, 1.0f);
    int is_known_folder_size = ld_gse_matches_recorded_large_folder(w, h) ||
                               (fabsf(w - h) < 4.0f && fabsf(w - 169.7f) < 12.0f);
    if (min_dim >= 68.0f && max_dim <= 800.0f && aspect <= 3.2f &&
        (rx <= 15.2f || (wg_c > 0.0f && fabsf(rx - wg_c) < 0.5f && !is_known_folder_size))) {
        return LD_GLASS_KIND_WIDGET;
    }
    if (min_dim >= 45.0f && max_dim >= 120.0f && max_dim <= 800.0f && aspect <= 4.0f &&
        rx > 15.2f) {
        return (min_dim < 120.0f) ? LD_GLASS_KIND_LARGE_FOLDER_2X1 : LD_GLASS_KIND_LARGE_FOLDER;
    }
    return LD_GLASS_KIND_NONE;
}

struct ld_gse_cache_entry {
    float w, h, rx, ry, n;
    uint8_t params[LD_GSE_PARAMS_SIZE];
};
static __thread struct ld_gse_cache_entry g_ld_gse_cache[8];
static __thread unsigned g_ld_gse_cache_next;

static int ld_gse_cache_lookup(float w, float h, float rx, float ry,
                               uint8_t *params_out, float *n_out)
{
    for (unsigned i = 0; i < 8u; i++) {
        const struct ld_gse_cache_entry *e = &g_ld_gse_cache[i];
        if (e->n > 2.0f &&
            fabsf(e->w - w) < 0.05f && fabsf(e->h - h) < 0.05f &&
            fabsf(e->rx - rx) < 0.05f && fabsf(e->ry - ry) < 0.05f) {
            memcpy(params_out, e->params, LD_GSE_PARAMS_SIZE);
            *n_out = e->n;
            return 1;
        }
    }
    return 0;
}

static void ld_gse_cache_store(float w, float h, float rx, float ry,
                               const uint8_t *params, float n)
{
    unsigned slot = (g_ld_gse_cache_next++) & 7u;
    struct ld_gse_cache_entry *e = &g_ld_gse_cache[slot];
    e->w = w; e->h = h; e->rx = rx; e->ry = ry; e->n = n;
    memcpy(e->params, params, LD_GSE_PARAMS_SIZE);
}

/* Dock 命中：矩形宽高匹配 1880x3008（全屏玻璃层）或 dock 条阈值
 * （宽 > 600 且高 < 320，与 superellipse.h 的 dock 探测一致）。
 * rect 在引擎记录中为 (l,t,r,b)；排除桌面小组件/大文件夹各自的对称内边距 SaveLayer
 * （例如 (33.5, 33.5) 起点的 2x1/3x1 条形大文件夹 183.1x58.1）。 */
static int ld_gse_rect_is_dock(const float *rect, float *w_out, float *h_out)
{
    float l = rect[0], t = rect[1], r = rect[2], b = rect[3];
    if (!isfinite(l) || !isfinite(t) || !isfinite(r) || !isfinite(b))
        return 0;
    float w1 = fabsf(r - l), h1 = fabsf(b - t); /* (l,t,r,b) */
    *w_out = w1;
    *h_out = h1;
    int full1 = fabsf(w1 - 1880.0f) < 2.0f && fabsf(h1 - 3008.0f) < 2.0f;
    if (full1) return 1;
    /* 桌面单卡（1x1/2x1/1x2/2x2/4x2）通过 SaveLayer 外扩渲染，局部原点 l≈t 且 > 4dp。 */
    int is_local_item_layer = (l >= 4.0f && l < 64.0f && fabsf(l - t) < 1.5f);
    if (is_local_item_layer) return 0;
    int bar1 = w1 > 600.0f && h1 < 320.0f;
    /* 平板浮动 Dock：实测 226x78 / 276.6x75.9（逻辑像素），短边 >= 65 且宽 >= 210；
     * 排除靠近局部原点的 2x1/3x1 大文件夹（含外扩时宽约 195~240）。 */
    int bar3 = h1 >= 65.0f && h1 <= 140.0f && w1 >= 210.0f && w1 <= 1600.0f &&
               !(l < 64.0f && t < 64.0f && w1 < 260.0f);
    return bar1 || bar3;
}

/* DrawHyperMaterial 替身：先按 0xc414dc 的推导找到 arena 栈顶 clip 记录，
 * 对 Dock 记录合成超椭圆参数，再进原函数。 */
__attribute__((used)) static void *ld_gse_draw_hyper_material(
    void *self, void *a1, void *a2, void *a3,
    void *a4, void *a5, void *a6, void *a7, float a8)
{
    unsigned call = __atomic_fetch_add(&g_ld_gse_calls, 1u, __ATOMIC_RELAXED);
    uintptr_t canvas = (uintptr_t)self;
    uint8_t *rec = NULL;
    unsigned kind = 0;
    uint64_t base_idx = 0, count = 0;
    float rect[4] = {0, 0, 0, 0};
    float rad[2] = {0, 0};
    float quad[4] = {0, 0, 0, 0};
    float n_old = -1.0f;
    int dock = 0, patched = 0;
    enum ld_glass_kind glass_kind = LD_GLASS_KIND_NONE;
    float w = 0, h = 0;

    if (canvas) {
        base_idx = *(const uint64_t *)(canvas + 0x138);
        count = *(const uint64_t *)(canvas + 0x140);
        const uint64_t *pages = *(const uint64_t *const *)(canvas + 0x120);
        if (count > 0 && count < 0x100000u && pages) {
            uint64_t max_walk = count < 4u ? count : 4u;
            for (uint64_t step = 0; step < max_walk; step++) {
                uint64_t idx = base_idx + count - 1u - step;
                uint64_t page = pages[idx >> 4];
                if (!page) continue;
                uint8_t *cand = (uint8_t *)page + (unsigned)(idx & 0xfu) * LD_GSE_REC_SIZE;
                unsigned ckind = cand[LD_GSE_OFF_KIND];
                float crad[2] = {0, 0};
                memcpy(crad, cand + LD_GSE_OFF_RADII, sizeof crad);
                if (step == 0 || (ckind == 1 && (crad[0] > 0.5f || crad[1] > 0.5f))) {
                    rec = cand;
                    kind = ckind;
                    memcpy(rect, rec + LD_GSE_OFF_RECT, sizeof rect);
                    memcpy(rad, crad, sizeof rad);
                    memcpy(quad, rec + LD_GSE_OFF_QUAD, sizeof quad);
                    memcpy(&n_old, rec + LD_GSE_OFF_N, sizeof n_old);
                    dock = ld_gse_rect_is_dock(rect, &w, &h);
                    if (ckind == 1 && (crad[0] > 0.5f || crad[1] > 0.5f)) break;
                }
            }
        }
    }

    int interesting = (rec != NULL) && (dock || kind == 1);
    if (interesting && call < 24u) {
        ld_blog("glass superellipse: arena=%p base=%llu count=%llu rec=%p "
                "kind=%u rect=%.1f,%.1f,%.1f,%.1f wh=%.1fx%.1f "
                "radii=%.1f,%.1f quad=%.1f,%.1f,%.1f,%.1f n=%.3f dock=%d",
                (void *)canvas, (unsigned long long)base_idx,
                (unsigned long long)count, (void *)rec, kind,
                (double)rect[0], (double)rect[1], (double)rect[2],
                (double)rect[3], (double)w, (double)h,
                (double)rad[0], (double)rad[1],
                (double)quad[0], (double)quad[1],
                (double)quad[2], (double)quad[3],
                (double)n_old, dock);
    }

    /* 对所有圆角矩形或超椭圆记录（kind==1，半径>0）按组件圆角更新并合成超椭圆参数。 */
    if (rec && kind == 1 && (rad[0] > 0.5f || rad[1] > 0.5f) && g_ld_gse_compute) {
        float wg_c = g_ld_gse_widget_corner;
        glass_kind = ld_gse_classify_rect(w, h, rad[0], rad[1], dock, wg_c);
        if (!g_ld_gse_logonly) {
            float min_dim = fminf(w, h);
            float max_dim = fmaxf(w, h);
            int radius_overridden = 0;
            float lf_c = g_ld_gse_large_folder_corner;
            float lf_2x1_c = g_ld_gse_large_folder_2x1_corner;
            float target_r = 0.0f;
            if (glass_kind == LD_GLASS_KIND_LARGE_FOLDER_2X1) {
                target_r = lf_2x1_c > 0.0f ? lf_2x1_c : lf_c;
            } else if (glass_kind == LD_GLASS_KIND_LARGE_FOLDER) {
                target_r = lf_c;
            } else if (glass_kind == LD_GLASS_KIND_WIDGET) {
                target_r = wg_c;
            }
            if (target_r > 0.0f) {
                float r_ov = fminf(target_r, min_dim * 0.5f);
                rad[0] = rad[1] = r_ov;
                quad[0] = quad[1] = quad[2] = quad[3] = r_ov;
                memcpy(rec + LD_GSE_OFF_RADII, rad, sizeof rad);
                memcpy(rec + LD_GSE_OFF_QUAD, quad, sizeof quad);
                radius_overridden = 1;
            }
            if (glass_kind != LD_GLASS_KIND_CIRCULAR && (dock || max_dim >= 115.0f) &&
                (!(n_old > 2.0f) || radius_overridden)) {
                __attribute__((aligned(16))) uint8_t params[LD_GSE_PARAMS_SIZE];
                float n_new = 0.0f;
                if (!ld_gse_cache_lookup(w, h, rad[0], rad[1], params, &n_new)) {
                    __attribute__((aligned(16))) float rect_buf[4];
                    __attribute__((aligned(16))) float radii_buf[8];
                    memcpy(rect_buf, rec + LD_GSE_OFF_RECT, sizeof rect_buf);
                    for (int i = 0; i < 4; i++) {
                        radii_buf[i * 2 + 0] = rad[0];
                        radii_buf[i * 2 + 1] = rad[1];
                    }
                    memset(params, 0, sizeof params);
                    g_ld_gse_compute(params, rect_buf, radii_buf);
                    memcpy(&n_new, params, sizeof n_new);
                    if (!isfinite(n_new) || !(n_new > 2.0f)) n_new = 5.0f;
                    ld_gse_cache_store(w, h, rad[0], rad[1], params, n_new);
                }
                memcpy(rec + LD_GSE_OFF_PARAMS, params, LD_GSE_PARAMS_SIZE);
                memcpy(rec + LD_GSE_OFF_N, &n_new, sizeof n_new);
                patched = 1;
                if (call < 24u) {
                    ld_blog("glass superellipse: patched rec=%p n=%.3f->%.3f "
                            "rect=%.1f,%.1f,%.1f,%.1f radii=%.1f,%.1f",
                            (void *)rec, (double)n_old, (double)n_new,
                            (double)rect[0], (double)rect[1], (double)rect[2],
                            (double)rect[3], (double)rad[0], (double)rad[1]);
                }
            }
        } else if (call < 24u) {
            ld_blog("glass superellipse: logonly, rec=%p not patched",
                    (void *)rec);
        }
    }

    enum ld_glass_kind prev_kind = g_ld_gse_tls_kind;
    if (glass_kind == LD_GLASS_KIND_NONE && rec != NULL) {
        if (dock) glass_kind = LD_GLASS_KIND_DOCK;
        else if (fmaxf(w, h) > 1.0f && fmaxf(w, h) < 115.0f) glass_kind = LD_GLASS_KIND_SMALL_ICON;
    }
    g_ld_gse_tls_kind = glass_kind;

    void *ret = NULL;
    if (g_ld_gse_orig_dhm)
        ret = ((ld_gse_dhm_fn)g_ld_gse_orig_dhm)(self, a1, a2, a3,
                                                 a4, a5, a6, a7, a8);
    g_ld_gse_tls_kind = prev_kind;
    (void)patched;
    return ret;
}

static void ld_glass_superellipse_install(const struct native_api *api, int enabled,
                                          float large_folder_corner, float large_folder_2x1_corner,
                                          float widget_corner)
{
    g_ld_gse_large_folder_corner = large_folder_corner;
    g_ld_gse_large_folder_2x1_corner = large_folder_2x1_corner;
    g_ld_gse_widget_corner = widget_corner;
    static int state;
    if (state || !api || !api->hook) return;
    if (!enabled && access(LD_GSE_MARKER, F_OK) != 0) { state = 2; return; }
    g_ld_gse_logonly = access(LD_GSE_LOGONLY_MARKER, F_OK) == 0;

    struct ld_hyper_engine_info eng;
    if (!ld_hyper_engine_locate(&eng)) return; /* 引擎未映射，下次再试。 */

    uintptr_t dhm = ld_he_resolve_unique(&eng, LD_GSE_DHM_HINT, ld_gse_pat_dhm, NULL, 16);
    uintptr_t compute = ld_he_resolve_unique(&eng, LD_GSE_COMPUTE_HINT, ld_gse_pat_compute, NULL, 8);
    if (!dhm || !compute) {
        state = 2;
        ld_blog("glass superellipse: dynamic resolve failed dhm=%p compute=%p",
                (void *)dhm, (void *)compute);
        return;
    }
    /* 动态校验 Canvas::DrawHyperMaterial 内部确有 0x260 stride + 0x120/0x138 clip-walk，保证结构一致。 */
    uintptr_t walk_hit = 0;
    uintptr_t walk_hi = (dhm + 0x7000u < eng.text_end) ? (dhm + 0x7000u) : eng.text_end;
    if (ld_he_scan_masked_range(dhm, walk_hi, ld_gse_pat_clipwalk, NULL, 4, &walk_hit, 1) == 0) {
        state = 2;
        ld_blog("glass superellipse: clipwalk layout mismatch inside dhm=%p, fail-closed", (void *)dhm);
        return;
    }

    g_ld_gse_compute = (ld_gse_compute_fn)compute;
    void *backup = NULL;
    if (api->hook((void *)dhm, (void *)ld_gse_draw_hyper_material, &backup) == 0
        && backup) {
        g_ld_gse_orig_dhm = (ld_gse_dhm_fn)backup;
        state = 1;
        ld_blog("glass superellipse: installed dhm=%p (off=0x%lx) compute=%p (off=0x%lx) walk=%p logonly=%d",
                (void *)dhm, (unsigned long)(dhm - eng.elf_base),
                (void *)compute, (unsigned long)(compute - eng.elf_base),
                (void *)walk_hit, g_ld_gse_logonly);
    } else {
        state = 2;
        ld_blog("glass superellipse: hook failed rc-errno=%d", errno);
    }
}

#endif
