/* Dock 竖线绘制探针：hook Impeller 显示列表的 DrawRect / DrawLine。
 *
 * 目的：直接抓到「细长矩形」或「竖直直线」的绘制调用，定位竖线的真正绘制点。
 *   DisplayListBuilder::DrawRect(const TRect<float>&, const DlPaint&) @0x7d90f4
 *   DisplayListBuilder::DrawLine(TPoint, TPoint, const DlPaint&)         @0x7d905c
 * TRect<float> = {origin.x, origin.y, size.width, size.height}（4×float）
 *
 * 只记录「细」的几何（宽或高 < 8），最多 40 条，避免刷屏。
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-draw-probe
 */
#ifndef LD_DIVIDER_DRAW_PROBE_H
#define LD_DIVIDER_DRAW_PROBE_H

#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"

#define LD_DDP_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-draw-probe"
#define LD_DDP_RECT_VADDR 0x7d90f4u
#define LD_DDP_LINE_VADDR 0x7d905cu
#define LD_DDP_IMG_VADDR 0x7d94fcu
#define LD_DDP_RRECT_VADDR 0x7d91a8u

static void *g_ddp_orig_rect __attribute__((used));
static void *g_ddp_orig_line __attribute__((used));
static void *g_ddp_orig_img __attribute__((used));
static void *g_ddp_orig_rrect __attribute__((used));
static volatile unsigned g_ddp_rect_logs, g_ddp_line_logs;

typedef void (*ld_ddp_draw_rect_fn)(void *self, const float *rect, const void *paint);
typedef void (*ld_ddp_draw_line_fn)(void *self, const float *p0, const float *p1, const void *paint);

__attribute__((used)) static void ld_ddp_draw_rect(void *self, const float *rect, const void *paint)
{
    if (rect) {
        float w = rect[2] - rect[0], h = rect[3] - rect[1];
        if ((w > 0.0f && w < 8.0f) || (h > 0.0f && h < 8.0f)) {
            unsigned n = __atomic_fetch_add(&g_ddp_rect_logs, 1u, __ATOMIC_RELAXED);
            if (n < 40u) {
                const uint32_t *p = (const uint32_t *)paint;
                ld_blog("draw-probe: rect %.1f,%.1f %.1fx%.1f paint=%08x %08x %08x %08x",
                        rect[0], rect[1], w, h,
                        p ? p[0] : 0, p ? p[1] : 0, p ? p[2] : 0, p ? p[3] : 0);
            }
        }
    }
    if (g_ddp_orig_rect)
        ((ld_ddp_draw_rect_fn)g_ddp_orig_rect)(self, rect, paint);
}

__attribute__((used)) static void ld_ddp_draw_line(void *self, const float *p0, const float *p1, const void *paint)
{
    if (p0 && p1 && fabsf(p0[0] - p1[0]) < 2.0f) {
        unsigned n = __atomic_fetch_add(&g_ddp_line_logs, 1u, __ATOMIC_RELAXED);
        if (n < 40u) {
            const uint32_t *p = (const uint32_t *)paint;
            ld_blog("draw-probe: vline (%.1f,%.1f)-(%.1f,%.1f) paint=%08x %08x %08x %08x",
                    p0[0], p0[1], p1[0], p1[1],
                    p ? p[0] : 0, p ? p[1] : 0, p ? p[2] : 0, p ? p[3] : 0);
        }
    }
    if (g_ddp_orig_line)
        ((ld_ddp_draw_line_fn)g_ddp_orig_line)(self, p0, p1, paint);
}

static volatile unsigned g_ddp_img_logs;

typedef void (*ld_ddp_img_fn)(void *self, const void *image, const float *src,
                              const float *dst, int sampling, const void *paint, int constraint);

__attribute__((used)) static void ld_ddp_draw_image(void *self, const void *image,
        const float *src, const float *dst, int sampling, const void *paint, int constraint)
{
    if (dst) {
        float w = dst[2] - dst[0], h = dst[3] - dst[1];
        if ((w > 0.0f && w < 8.0f) || (h > 0.0f && h < 8.0f)) {
            unsigned n = __atomic_fetch_add(&g_ddp_img_logs, 1u, __ATOMIC_RELAXED);
            if (n < 40u) {
                const uint32_t *p = (const uint32_t *)paint;
                ld_blog("draw-probe: img dst=%.1f,%.1f %.1fx%.1f src=%.1f,%.1f %.1fx%.1f paint=%08x %08x %08x %08x",
                        dst[0], dst[1], w, h,
                        src ? src[0] : 0.0f, src ? src[1] : 0.0f,
                        src ? src[2] : 0.0f, src ? src[3] : 0.0f,
                        p ? p[0] : 0, p ? p[1] : 0, p ? p[2] : 0, p ? p[3] : 0);
            }
        }
    }
    if (g_ddp_orig_img)
        ((ld_ddp_img_fn)g_ddp_orig_img)(self, image, src, dst, sampling, paint, constraint);
}

static volatile unsigned g_ddp_rrect_logs;

/* DrawRoundRect(const RoundRect&, const DlPaint&)：RoundRect = { TRect(l,t,r,b) + radii } */
__attribute__((used)) static void ld_ddp_draw_rrect(void *self, const float *rr, const void *paint)
{
    if (rr) {
        float w = rr[2] - rr[0], h = rr[3] - rr[1];
        if ((w > 0.0f && w < 8.0f) || (h > 0.0f && h < 8.0f)) {
            unsigned n = __atomic_fetch_add(&g_ddp_rrect_logs, 1u, __ATOMIC_RELAXED);
            if (n < 40u) {
                const uint32_t *p = (const uint32_t *)paint;
                ld_blog("draw-probe: rrect %.1f,%.1f %.1fx%.1f r=%.1f,%.1f paint=%08x %08x %08x %08x",
                        rr[0], rr[1], w, h, rr[4], rr[5],
                        p ? p[0] : 0, p ? p[1] : 0, p ? p[2] : 0, p ? p[3] : 0);
            }
        }
    }
    if (g_ddp_orig_rrect)
        ((void (*)(void *, const float *, const void *))g_ddp_orig_rrect)(self, rr, paint);
}

static void ld_divider_draw_probe_install(const struct native_api *api)
{
    static int state;
    if (state || !api || !api->hook) return;
    if (access(LD_DDP_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t base = 0;
    {
        FILE *f = fopen("/proc/self/maps", "r");
        if (!f) return;
        char line[512];
        while (fgets(line, sizeof line, f)) {
            if (!strstr(line, "libhyper_os_flutter.so")) continue;
            unsigned long start = 0;
            if (sscanf(line, "%lx-", &start) == 1) { base = (uintptr_t)start; break; }
        }
        fclose(f);
    }
    if (!base) return;
    void *rect = (void *)(base + LD_DDP_RECT_VADDR);
    void *line = (void *)(base + LD_DDP_LINE_VADDR);
    void *b = NULL;
    if (api->hook(rect, (void *)ld_ddp_draw_rect, &b) == 0 && b) {
        g_ddp_orig_rect = b;
        ld_blog("draw-probe: DrawRect hooked at %p", rect);
    }
    b = NULL;
    if (api->hook(line, (void *)ld_ddp_draw_line, &b) == 0 && b) {
        g_ddp_orig_line = b;
        ld_blog("draw-probe: DrawLine hooked at %p", line);
    }
    void *rr = (void *)(base + LD_DDP_RRECT_VADDR);
    b = NULL;
    if (api->hook(rr, (void *)ld_ddp_draw_rrect, &b) == 0 && b) {
        g_ddp_orig_rrect = b;
        ld_blog("draw-probe: DrawRoundRect hooked at %p", rr);
    }
    void *img = (void *)(base + LD_DDP_IMG_VADDR);
    b = NULL;
    if (api->hook(img, (void *)ld_ddp_draw_image, &b) == 0 && b) {
        g_ddp_orig_img = b;
        ld_blog("draw-probe: DrawImageRect hooked at %p", img);
    }
    state = 1;
}

#endif
