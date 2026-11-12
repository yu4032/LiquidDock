/* DividerItemFactory.build 探针：解析分隔符 Widget 工厂真正使用的闭包。
 *
 * DividerItemFactory.build @0x18891e8（非变长参数，安全）：
 *   x27 (PP) + 0xd4a20  → Function 对象（闭包函数）
 *   x27 (PP) + 0x268f0  → 第二个池对象（传给 sub_0x198b68c）
 * 把这两个池项的值与对象前几个字打出来，用于定位闭包代码地址（指向
 * 0x896940.. 代码段的指针），再 lift 该函数找出最终绘制竖线的 Widget。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-divider-factory
 */
#ifndef LD_DIVIDER_FACTORY_PROBE_H
#define LD_DIVIDER_FACTORY_PROBE_H

#include <stdint.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"
#include "dart_observer.h"

#define LD_DFP_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-divider-factory"
#define LD_DFP_VADDR 0x18891e8u

static void *g_dfp_orig __attribute__((used));
static volatile unsigned g_dfp_calls;

static int ld_dfp_read(uintptr_t address, void *out, size_t len)
{
    struct iovec local = { out, len };
    struct iovec remote = { (void *)address, len };
    return syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0) == (long)len;
}

__attribute__((used)) static void ld_dfp_probe(struct ld_dart_context *c)
{
    unsigned n = __atomic_fetch_add(&g_dfp_calls, 1u, __ATOMIC_RELAXED);
    if (n >= 4u) return;
    uintptr_t pp = (uintptr_t)c->x[27];
    uint64_t fn = 0, aux = 0;
    if (!pp) return;
    if (!ld_dfp_read(pp + 0xd4a20, &fn, sizeof fn) ||
        !ld_dfp_read(pp + 0x268f0, &aux, sizeof aux))
        return;
    ld_blog("divider-factory: call #%u pp=%p closure=%p aux=%p", n + 1u,
            (void *)pp, (void *)fn, (void *)aux);
    uint64_t words[8] = {0};
    if (fn && ld_dfp_read((uintptr_t)fn, words, sizeof words)) {
        ld_blog("divider-factory:  closure[0..7]=%016llx %016llx %016llx %016llx %016llx %016llx %016llx %016llx",
                (unsigned long long)words[0], (unsigned long long)words[1],
                (unsigned long long)words[2], (unsigned long long)words[3],
                (unsigned long long)words[4], (unsigned long long)words[5],
                (unsigned long long)words[6], (unsigned long long)words[7]);
    }
    if (aux) {
        uint64_t aw[8] = {0};
        if (ld_dfp_read((uintptr_t)aux, aw, sizeof aw))
            ld_blog("divider-factory:  aux[0..7]=%016llx %016llx %016llx %016llx %016llx %016llx %016llx %016llx",
                    (unsigned long long)aw[0], (unsigned long long)aw[1],
                    (unsigned long long)aw[2], (unsigned long long)aw[3],
                    (unsigned long long)aw[4], (unsigned long long)aw[5],
                    (unsigned long long)aw[6], (unsigned long long)aw[7]);
    }
}

LD_DART_OBSERVER(repl_divider_factory_probe, ld_dfp_probe, g_dfp_orig)

static void ld_divider_factory_probe_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_DFP_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t fn = 0;
    if (!ld_vaddr_to_runtime(lib, LD_DFP_VADDR, &fn)) return;
    static const uint32_t prologue[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd10021efu};
    if (memcmp((const void *)fn, prologue, sizeof prologue) != 0) {
        state = 2;
        ld_blog("divider-factory: prologue mismatch at %p", (void *)fn);
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)fn, (void *)repl_divider_factory_probe, &backup) == 0 && backup) {
        g_dfp_orig = backup;
        state = 1;
        ld_blog("divider-factory: probe installed at %p", (void *)fn);
    } else {
        state = 2;
        ld_blog("divider-factory: hook failed");
    }
}

#endif
