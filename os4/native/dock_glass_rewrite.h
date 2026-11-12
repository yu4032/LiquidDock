/* Dock 玻璃参数改写（入口处写 Dart 栈上的变长参数槽）。
 *
 * 探针实测（builder sub_0x1175df0 入口，Dock 调用）：
 *   fp+336 = 36.0    ← 模糊半径
 *   fp+352 = 100.0   ← 厚度（与模块 SoftGlass THICKNESS 默认一致）
 *   fp+360 = 200.0   ← 反射偏移（与 OS4_REFLECT_OFFSET_PX 默认一致）
 * 其它相邻槽为 2.2 / 1.0 / 0.2 / 1.4 等材质参数。
 *
 * 做法：入口只读校验「36.0 @ +336 且 100.0 @ +352」这一特征，命中才把这三个
 * double 槽改成配置值；不调用 Dart 函数、不动寄存器、不改堆。
 *
 * 配置：liquiddock-dockglass.conf（blur=<x> thickness=<y> reflect=<z>）
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-dockglass-blur
 */
#ifndef LD_DOCK_GLASS_REWRITE_H
#define LD_DOCK_GLASS_REWRITE_H

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"
#include "dart_observer.h"

#define LD_DGR_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-dockglass-blur"
#define LD_DGR_CONF "/data/user_de/0/com.miui.home/files/liquiddock-dockglass.conf"
#define LD_DGR_VADDR 0x1175df0u

static double g_dgr_blur __attribute__((used)) = 36.0;
static double g_dgr_thickness __attribute__((used)) = 100.0;
static double g_dgr_reflect __attribute__((used)) = 200.0;
static int g_dgr_enabled __attribute__((used));
static void *g_dgr_orig __attribute__((used));
static volatile unsigned g_dgr_hits;

static int ld_dgr_read(uintptr_t address, void *out, size_t len)
{
    struct iovec local = { out, len };
    struct iovec remote = { (void *)address, len };
    return syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0) == (long)len;
}

static int ld_dgr_write(uintptr_t address, const void *in, size_t len)
{
    struct iovec local = { (void *)in, len };
    struct iovec remote = { (void *)address, len };
    return syscall(SYS_process_vm_writev, getpid(), &local, 1, &remote, 1, 0) == (long)len;
}

__attribute__((used)) static void ld_dgr_probe(struct ld_dart_context *c)
{
    if (!g_dgr_enabled) return;
    uintptr_t fp = (uintptr_t)c->x[29];
    if (!fp) return;
    double blur = 0, thickness = 0;
    if (!ld_dgr_read(fp + 336, &blur, sizeof blur) ||
        !ld_dgr_read(fp + 352, &thickness, sizeof thickness))
        return;
    /* 只改 Dock 玻璃这一组参数（36 / 100 是它的签名） */
    if (blur != 36.0 || thickness != 100.0) return;
    double rblur = g_dgr_blur, rthick = g_dgr_thickness, rrefl = g_dgr_reflect;
    ld_dgr_write(fp + 336, &rblur, sizeof rblur);
    ld_dgr_write(fp + 352, &rthick, sizeof rthick);
    ld_dgr_write(fp + 360, &rrefl, sizeof rrefl);
    unsigned n = __atomic_fetch_add(&g_dgr_hits, 1u, __ATOMIC_RELAXED);
    if (n < 4u)
        ld_blog("dockglass-rewrite: hit #%u blur %.1f->%.1f thick %.1f->%.1f refl=%.1f",
                n + 1u, blur, rblur, thickness, rthick, rrefl);
}

LD_DART_OBSERVER(repl_dockglass_rewrite, ld_dgr_probe, g_dgr_orig)

static void ld_dock_glass_rewrite_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_DGR_MARKER, F_OK) != 0) { state = 2; return; }

    FILE *f = fopen(LD_DGR_CONF, "r");
    if (f) {
        char line[128];
        while (fgets(line, sizeof line, f)) {
            double v = 0;
            if (sscanf(line, "blur=%lf", &v) == 1) g_dgr_blur = v;
            else if (sscanf(line, "thickness=%lf", &v) == 1) g_dgr_thickness = v;
            else if (sscanf(line, "reflect=%lf", &v) == 1) g_dgr_reflect = v;
        }
        fclose(f);
    }
    g_dgr_enabled = 1;
    ld_blog("dockglass-rewrite: conf blur=%.1f thick=%.1f refl=%.1f",
            g_dgr_blur, g_dgr_thickness, g_dgr_reflect);

    uintptr_t fn = 0;
    if (!ld_vaddr_to_runtime(lib, LD_DGR_VADDR, &fn)) return;
    static const uint32_t prologue[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd10b61efu};
    if (memcmp((const void *)fn, prologue, sizeof prologue) != 0) {
        state = 2;
        ld_blog("dockglass-rewrite: prologue mismatch at %p", (void *)fn);
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)fn, (void *)repl_dockglass_rewrite, &backup) == 0 && backup) {
        g_dgr_orig = backup;
        state = 1;
        ld_blog("dockglass-rewrite: installed at %p", (void *)fn);
    } else {
        state = 2;
        ld_blog("dockglass-rewrite: hook failed");
    }
}

#endif
