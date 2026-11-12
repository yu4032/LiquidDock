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
#include "native_api.h"

#define LD_GSE_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-glass-superellipse"
#define LD_GSE_LOGONLY_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-glass-superellipse-logonly"

/* 引擎 vaddr（运行时 = libhyper_os_flutter.so 映射基址 + vaddr）。 */
#define LD_GSE_DHM_VADDR        0xc3c20cu /* Canvas::DrawHyperMaterial 入口 */
#define LD_GSE_DHM_MOVX19_VADDR 0xc3c24cu /* mov x19, x0（canvas） */
#define LD_GSE_CLIPWALK_VADDR   0xc414dcu /* ldp x9,x8,[x19,#0x138] */
#define LD_GSE_STRIDE_VADDR     0xc414e0u /* mov w11,#0x260 */
#define LD_GSE_PAGES_VADDR      0xc414e8u /* ldr x9,[x19,#0x120] */
#define LD_GSE_FCMP_VADDR       0xc415e0u /* fcmp s0,s1（n 对 2.0） */
#define LD_GSE_BLE_VADDR        0xc415e4u /* b.le 跳过 SE 分支 */
#define LD_GSE_COMPUTE_VADDR    0xd83644u /* ComputeSuperellipseParams */

/* 校验字面量（均取自 turner 8.01.02.8604 libhyper_os_flutter.so）。 */
#define LD_GSE_DHM_W0   0x6db63befu /* stp d15,d14,[sp,#-0xa0]! */
#define LD_GSE_DHM_W1   0x6d0133edu
#define LD_GSE_MOVX19   0xaa0003f3u /* mov x19, x0 */
#define LD_GSE_CLIPWALK 0xa953a269u /* ldp x9,x8,[x19,#0x138] */
#define LD_GSE_STRIDE_W 0x52804c0bu /* mov w11,#0x260 */
#define LD_GSE_PAGES_W  0xf9409269u /* ldr x9,[x19,#0x120] */
#define LD_GSE_FCMP_W   0x1e212000u /* fcmp s0,s1 */
#define LD_GSE_BLE_W    0x5400032du /* b.le .+0x64 */
#define LD_GSE_COMPUTE_W0 0x6db93befu /* stp d15,d14,[sp,#-0x70]! */
#define LD_GSE_COMPUTE_W1 0x6d0133edu

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

/* 引擎 .so 首个映射 = ELF 基址（首个 PT_LOAD，offset 0）。 */
static uintptr_t ld_gse_engine_base(void)
{
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return 0;
    char line[512];
    uintptr_t base = 0;
    while (fgets(line, sizeof line, maps)) {
        if (!strstr(line, "libhyper_os_flutter.so")) continue;
        unsigned long start = 0;
        if (sscanf(line, "%lx-", &start) == 1) { base = (uintptr_t)start; break; }
    }
    fclose(maps);
    return base;
}

static int ld_gse_word_ok(uintptr_t site, uint32_t expected)
{
    return *(const volatile uint32_t *)site == expected;
}

/* Dock 命中：矩形宽高匹配 1880x3008（全屏玻璃层）或 dock 条阈值
 * （宽 > 600 且高 < 320，与 superellipse.h 的 dock 探测一致）。
 * 同时兼容 rect 的 (l,t,r,b) 与 (x,y,w,h) 两种解释，日志两者都打。 */
static int ld_gse_rect_is_dock(const float *rect, float *w_out, float *h_out)
{
    float l = rect[0], t = rect[1], r = rect[2], b = rect[3];
    if (!isfinite(l) || !isfinite(t) || !isfinite(r) || !isfinite(b))
        return 0;
    float w1 = fabsf(r - l), h1 = fabsf(b - t); /* (l,t,r,b) */
    float w2 = fabsf(r), h2 = fabsf(b);         /* (x,y,w,h) */
    *w_out = w1;
    *h_out = h1;
    int full1 = fabsf(w1 - 1880.0f) < 2.0f && fabsf(h1 - 3008.0f) < 2.0f;
    int full2 = fabsf(w2 - 1880.0f) < 2.0f && fabsf(h2 - 3008.0f) < 2.0f;
    int bar1 = w1 > 600.0f && h1 < 320.0f;
    int bar2 = w2 > 600.0f && h2 < 320.0f;
    /* 平板浮动 Dock：实测约 226 x 78（逻辑像素）。 */
    int bar3 = h1 >= 50.0f && h1 <= 140.0f && w1 >= 150.0f && w1 <= 1600.0f;
    int bar4 = h2 >= 50.0f && h2 <= 140.0f && w2 >= 150.0f && w2 <= 1600.0f;
    return full1 || full2 || bar1 || bar2 || bar3 || bar4;
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
    float w = 0, h = 0;

    if (canvas) {
        base_idx = *(const uint64_t *)(canvas + 0x138);
        count = *(const uint64_t *)(canvas + 0x140);
        const uint64_t *pages = *(const uint64_t *const *)(canvas + 0x120);
        if (count > 0 && count < 0x100000u && pages) {
            uint64_t idx = base_idx + count - 1u;
            uint64_t page = pages[idx >> 4];
            if (page) {
                rec = (uint8_t *)page + (unsigned)(idx & 0xfu) * LD_GSE_REC_SIZE;
                kind = rec[LD_GSE_OFF_KIND];
                memcpy(rect, rec + LD_GSE_OFF_RECT, sizeof rect);
                memcpy(rad, rec + LD_GSE_OFF_RADII, sizeof rad);
                memcpy(quad, rec + LD_GSE_OFF_QUAD, sizeof quad);
                memcpy(&n_old, rec + LD_GSE_OFF_N, sizeof n_old);
                dock = ld_gse_rect_is_dock(rect, &w, &h);
            }
        }
    }

    int interesting = (rec != NULL) && (dock || kind == 1);
    if (interesting && (call < 8u || (call % 60u) == 0u)) {
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

    /* 只改 Dock 的圆角矩形记录（n<=2）；真 SE 记录（n>2）不动。 */
    if (rec && dock && kind == 1 && !(n_old > 2.0f) && g_ld_gse_compute) {
        if (!g_ld_gse_logonly) {
            __attribute__((aligned(16))) float rect_buf[4];
            __attribute__((aligned(16))) float radii_buf[8];
            __attribute__((aligned(16))) uint8_t params[LD_GSE_PARAMS_SIZE];
            memcpy(rect_buf, rec + LD_GSE_OFF_RECT, sizeof rect_buf);
            for (int i = 0; i < 4; i++) {
                radii_buf[i * 2 + 0] = rad[0];
                radii_buf[i * 2 + 1] = rad[1];
            }
            memset(params, 0, sizeof params);
            g_ld_gse_compute(params, rect_buf, radii_buf);
            float n_new;
            memcpy(&n_new, params, sizeof n_new);
            if (!isfinite(n_new) || !(n_new > 2.0f)) n_new = 5.0f;
            memcpy(rec + LD_GSE_OFF_PARAMS, params, LD_GSE_PARAMS_SIZE);
            memcpy(rec + LD_GSE_OFF_N, &n_new, sizeof n_new);
            patched = 1;
            ld_blog("glass superellipse: patched rec=%p n=%.3f->%.3f "
                    "rect=%.1f,%.1f,%.1f,%.1f radii=%.1f,%.1f",
                    (void *)rec, (double)n_old, (double)n_new,
                    (double)rect[0], (double)rect[1], (double)rect[2],
                    (double)rect[3], (double)rad[0], (double)rad[1]);
        } else if (call < 8u || (call % 60u) == 0u) {
            ld_blog("glass superellipse: logonly, rec=%p not patched",
                    (void *)rec);
        }
    }

    if (g_ld_gse_orig_dhm)
        return ((ld_gse_dhm_fn)g_ld_gse_orig_dhm)(self, a1, a2, a3,
                                                  a4, a5, a6, a7, a8);
    (void)patched;
    return NULL;
}

static void ld_glass_superellipse_install(const struct native_api *api)
{
    static int state;
    if (state || !api || !api->hook) return;
    if (access(LD_GSE_MARKER, F_OK) != 0) { state = 2; return; }
    g_ld_gse_logonly = access(LD_GSE_LOGONLY_MARKER, F_OK) == 0;

    uintptr_t base = ld_gse_engine_base();
    if (!base) return; /* 引擎未映射，下次再试。 */

    uintptr_t dhm = base + LD_GSE_DHM_VADDR;
    uintptr_t compute = base + LD_GSE_COMPUTE_VADDR;

    /* Fail-closed：prologue / 关键指令字面量逐一校验。 */
    if (!ld_gse_word_ok(dhm, LD_GSE_DHM_W0) ||
        !ld_gse_word_ok(dhm + 4, LD_GSE_DHM_W1) ||
        !ld_gse_word_ok(base + LD_GSE_DHM_MOVX19_VADDR, LD_GSE_MOVX19) ||
        !ld_gse_word_ok(base + LD_GSE_CLIPWALK_VADDR, LD_GSE_CLIPWALK) ||
        !ld_gse_word_ok(base + LD_GSE_STRIDE_VADDR, LD_GSE_STRIDE_W) ||
        !ld_gse_word_ok(base + LD_GSE_PAGES_VADDR, LD_GSE_PAGES_W) ||
        !ld_gse_word_ok(base + LD_GSE_FCMP_VADDR, LD_GSE_FCMP_W) ||
        !ld_gse_word_ok(base + LD_GSE_BLE_VADDR, LD_GSE_BLE_W) ||
        !ld_gse_word_ok(compute, LD_GSE_COMPUTE_W0) ||
        !ld_gse_word_ok(compute + 4, LD_GSE_COMPUTE_W1) ||
        !ld_gse_word_ok(compute + 0x20, LD_GSE_MOVX19)) {
        state = 2;
        ld_blog("glass superellipse: literal mismatch, fail-closed "
                "dhm=%08x movx19=%08x walk=%08x stride=%08x pages=%08x "
                "fcmp=%08x ble=%08x compute=%08x",
                *(const volatile uint32_t *)dhm,
                *(const volatile uint32_t *)(base + LD_GSE_DHM_MOVX19_VADDR),
                *(const volatile uint32_t *)(base + LD_GSE_CLIPWALK_VADDR),
                *(const volatile uint32_t *)(base + LD_GSE_STRIDE_VADDR),
                *(const volatile uint32_t *)(base + LD_GSE_PAGES_VADDR),
                *(const volatile uint32_t *)(base + LD_GSE_FCMP_VADDR),
                *(const volatile uint32_t *)(base + LD_GSE_BLE_VADDR),
                *(const volatile uint32_t *)compute);
        return;
    }

    g_ld_gse_compute = (ld_gse_compute_fn)compute;
    void *backup = NULL;
    if (api->hook((void *)dhm, (void *)ld_gse_draw_hyper_material, &backup) == 0
        && backup) {
        g_ld_gse_orig_dhm = (ld_gse_dhm_fn)backup;
        state = 1;
        ld_blog("glass superellipse: installed dhm=%p compute=%p logonly=%d",
                (void *)dhm, (void *)compute, g_ld_gse_logonly);
    } else {
        state = 2;
        ld_blog("glass superellipse: hook failed rc-errno=%d", errno);
    }
}

#endif
