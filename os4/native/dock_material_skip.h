/* 实验 B2：跳过 Dock 玻璃材质绘制（DrawHyperMaterial），观察竖线是否随之消失。
 *
 * impeller::Canvas::DrawHyperMaterial(const DlImageFilter*, const TRect<float>&, ...)
 * @0xc3c20c：x1=filter, x2=TRect(l,t,r,b)。
 * 只对 Dock 尺寸的矩形（宽 100..400、高 60..100）跳过原函数，其它材质照常绘制。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-dockmat-skip
 */
#ifndef LD_DOCK_MATERIAL_SKIP_H
#define LD_DOCK_MATERIAL_SKIP_H

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"

#define LD_DMS_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-dockmat-skip"
#define LD_DMS_VADDR 0xc3c20cu

static void *g_dms_orig __attribute__((used));
static volatile unsigned g_dms_skips;

__attribute__((used)) static void ld_dms_hook(void *self, void *filter, const float *rect,
        void *a3, void *a4, void *a5, void *a6, void *a7, float a8)
{
    if (rect) {
        float w = rect[2] - rect[0], h = rect[3] - rect[1];
        /* buffer 旋转 90°：Dock 材质实测 224.9(宽) x 616.4(高) */
        if (w > 150.0f && w < 400.0f && h > 400.0f && h < 900.0f) {
            unsigned n = __atomic_fetch_add(&g_dms_skips, 1u, __ATOMIC_RELAXED);
            if (n < 4u)
                ld_blog("dockmat-skip: skip %.1f,%.1f %.1fx%.1f", rect[0], rect[1], w, h);
            return;
        }
    }
    if (g_dms_orig)
        ((void (*)(void *, void *, const float *, void *, void *, void *, void *, void *, float))
         g_dms_orig)(self, filter, rect, a3, a4, a5, a6, a7, a8);
}

static void ld_dock_material_skip_install(const struct native_api *api)
{
    static int state;
    if (state || !api || !api->hook) return;
    if (access(LD_DMS_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t base = 0;
    FILE *f = fopen("/proc/self/maps", "r");
    if (!f) return;
    char line[512];
    while (fgets(line, sizeof line, f)) {
        if (!strstr(line, "libhyper_os_flutter.so")) continue;
        unsigned long start = 0;
        if (sscanf(line, "%lx-", &start) == 1) { base = (uintptr_t)start; break; }
    }
    fclose(f);
    if (!base) return;
    void *fn = (void *)(base + LD_DMS_VADDR);
    void *b = NULL;
    if (api->hook(fn, (void *)ld_dms_hook, &b) == 0 && b) {
        g_dms_orig = b;
        state = 1;
        ld_blog("dockmat-skip: hooked at %p", fn);
    } else {
        state = 2;
        ld_blog("dockmat-skip: hook failed");
    }
}

#endif
