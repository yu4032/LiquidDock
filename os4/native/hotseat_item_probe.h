/* Hotseat item 序列探针：_HotseatWidgetPadState._buildSingleItem 入口。
 *
 * 目的：看 Dock 当前到底构建了哪些 item 类型（是否真的存在 1003/1007 分隔符），
 * 从而判断「竖线是否可能来自 divider item」。
 *
 * 入口寄存器（Dart AOT：this=x1，参数 x2..）：
 *   x1 = state, x2 = ?, x3 = wrapper(hRb), x4 = ?
 *   wrapper+0x8 = item(CellItemInfo 压缩指针)
 *   item+0x8   = itemType（Smi，1003=0x7d6<<1? 见下）
 *   item+0x38  = label（"Divider"）压缩指针
 *   item+0x40  = tag（"hotseat_divider"）压缩指针
 * 只读，用 process_vm_readv，不修改任何状态。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-hotseat-items
 */
#ifndef LD_HOTSEAT_ITEM_PROBE_H
#define LD_HOTSEAT_ITEM_PROBE_H

#include <stdint.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"
#include "dart_observer.h"

#define LD_HIP_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-hotseat-items"
#define LD_HIP_VADDR 0x119c8ecu

static void *g_hip_orig __attribute__((used));
static volatile unsigned g_hip_calls;

static int ld_hip_read(uintptr_t address, void *out, size_t len)
{
    struct iovec local = { out, len };
    struct iovec remote = { (void *)address, len };
    return syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0) == (long)len;
}

__attribute__((used)) static void ld_hip_probe(struct ld_dart_context *c)
{
    unsigned n = __atomic_fetch_add(&g_hip_calls, 1u, __ATOMIC_RELAXED);
    if (n >= 40u) return;
    uintptr_t wrapper = (uintptr_t)c->x[3];
    uintptr_t heap = (uintptr_t)c->x[28];
    uint32_t item_c = 0;
    if (!wrapper || !ld_hip_read(wrapper + 7, &item_c, 4)) return;
    uintptr_t item = heap + (uintptr_t)item_c;
    uint32_t type = 0, label_c = 0, tag_c = 0;
    if (!ld_hip_read(item + 7, &type, 4)) return;
    ld_hip_read(item + 0x3b, &label_c, 4);
    ld_hip_read(item + 0x3f, &tag_c, 4);
    ld_blog("hotseat-item #%u wrapper=%p item=%p type=%d label=%p tag=%p",
            n + 1u, (void *)wrapper, (void *)item, (int)type >> 1,
            (void *)(heap + label_c), (void *)(heap + tag_c));
}

LD_DART_OBSERVER(repl_hotseat_item_probe, ld_hip_probe, g_hip_orig)

static void ld_hotseat_item_probe_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_HIP_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t fn = 0;
    if (!ld_vaddr_to_runtime(lib, LD_HIP_VADDR, &fn)) return;
    static const uint32_t prologue[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd10181efu};
    if (memcmp((const void *)fn, prologue, sizeof prologue) != 0) {
        state = 2;
        ld_blog("hotseat-item: prologue mismatch at %p", (void *)fn);
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)fn, (void *)repl_hotseat_item_probe, &backup) == 0 && backup) {
        g_hip_orig = backup;
        state = 1;
        ld_blog("hotseat-item: probe installed at %p", (void *)fn);
    } else {
        state = 2;
        ld_blog("hotseat-item: hook failed");
    }
}

#endif
