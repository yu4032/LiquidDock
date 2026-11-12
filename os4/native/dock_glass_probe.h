/* Dock 玻璃参数探针：在共用构建器 sub_0x1175df0 入口，识别 Dock 调用者并
 * 把 Dart 栈上的一段原始内存 dump 出来，用于反推变长参数槽位布局。
 *
 * 只用 process_vm_readv 读取（读到非法地址返回 EFAULT，不会 SIGSEGV），
 * 入口观察器不调用任何 Dart 函数、不修改寄存器。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-dockglass-probe
 */
#ifndef LD_DOCK_GLASS_PROBE_H
#define LD_DOCK_GLASS_PROBE_H

#include <stdint.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"
#include "dart_observer.h"

#define LD_DGP_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-dockglass-probe"
#define LD_DGP_VADDR 0x1175df0u
#define LD_DGP_DARK 0x1175b6cu
#define LD_DGP_LIGHT 0x1176eecu

static void *g_dgp_orig __attribute__((used));
static volatile unsigned g_dgp_dumps;

static int ld_dgp_peek(uintptr_t address, void *out, size_t len)
{
    struct iovec local = { out, len };
    struct iovec remote = { (void *)address, len };
    long rc = syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0);
    return rc == (long)len;
}

__attribute__((used)) static void ld_dgp_probe(struct ld_dart_context *c)
{
    uintptr_t lr = (uintptr_t)c->x[30];
    unsigned n = __atomic_fetch_add(&g_dgp_dumps, 1u, __ATOMIC_RELAXED);
    if (n >= 3u) return;
    ld_blog("dockglass: call #%u lr=%p fp=%p sp=%p x4=%p x5=%p x6=%p",
            n + 1u, (void *)lr, (void *)c->x[29], (void *)c->x[15],
            (void *)c->x[4], (void *)c->x[5], (void *)c->x[6]);
    uintptr_t fp = (uintptr_t)c->x[29];
    if (!fp) return;
    /* 以 FP 为锚，dump -0x40 .. +0x1c0，每 8 字节一次 */
    for (int off = -0x40; off <= 0x1c0; off += 8) {
        uint64_t w = 0;
        if (!ld_dgp_peek(fp + off, &w, sizeof w)) {
            ld_blog("dockglass:  fp%+04d <unreadable>", off);
            continue;
        }
        double d;
        __builtin_memcpy(&d, &w, sizeof d);
        if (w == 0) continue;
        ld_blog("dockglass:  fp%+04d = %016llx  d=%.6g",
                off, (unsigned long long)w, d);
    }
}

LD_DART_OBSERVER(repl_dockglass_probe, ld_dgp_probe, g_dgp_orig)

static void ld_dock_glass_probe_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_DGP_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t fn = 0;
    if (!ld_vaddr_to_runtime(lib, LD_DGP_VADDR, &fn)) return;
    static const uint32_t prologue[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd10b61efu};
    if (memcmp((const void *)fn, prologue, sizeof prologue) != 0) {
        const uint32_t *p = (const uint32_t *)fn;
        ld_blog("dockglass: prologue mismatch at %p: %08x %08x %08x",
                (void *)fn, p[0], p[1], p[2]);
        state = 2;
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)fn, (void *)repl_dockglass_probe, &backup) == 0 && backup) {
        g_dgp_orig = backup;
        state = 1;
        ld_blog("dockglass: probe installed at %p", (void *)fn);
    } else {
        state = 2;
        ld_blog("dockglass: hook failed");
    }
}

#endif
