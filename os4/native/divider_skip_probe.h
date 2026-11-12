/* 实验 C：跳过 type=1003（固定区↔推荐区）分隔符的插入。
 *
 * HotseatsList.getAllDisplayCellItems @0x9c5b50 中，divider#1 的两条入口分支：
 *   0x9c612c  b   0x9c6164        ; laptop 路径直达“创建”
 *   0x9c6160  b.le 0x9c6290       ; 推荐数量 <=0 才跳过
 * 把这两条都改成无条件跳到 0x9c6290（跳过创建），观察 Dock 竖线是否随之消失：
 *   消失 → 竖线由该 item 驱动；不消失 → 竖线与该 item 无关。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-divider-skip
 */
#ifndef LD_DIVIDER_SKIP_PROBE_H
#define LD_DIVIDER_SKIP_PROBE_H

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"

#define LD_DSP_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-divider-skip"

static int ld_dsp_patch(uintptr_t address, uint32_t expected, uint32_t value)
{
    size_t page = (size_t)sysconf(_SC_PAGESIZE);
    uintptr_t base = address & ~(uintptr_t)(page - 1);
    size_t span = (address + 4 > base + page) ? page * 2 : page;
    if (mprotect((void *)base, span, PROT_READ | PROT_WRITE | PROT_EXEC) != 0)
        return -errno;
    int rc = 0;
    if (*(volatile uint32_t *)address != expected) {
        rc = -2;
    } else {
        *(volatile uint32_t *)address = value;
        __builtin___clear_cache((char *)address, (char *)(address + 4));
    }
    if (mprotect((void *)base, span, PROT_READ | PROT_EXEC) != 0 && rc == 0)
        rc = -errno;
    return rc;
}

static void ld_divider_skip_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !lib) return;
    (void)api;
    if (access(LD_DSP_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t a = 0, b = 0;
    if (!ld_vaddr_to_runtime(lib, 0x9c612cu, &a) ||
        !ld_vaddr_to_runtime(lib, 0x9c6160u, &b)) return;
    int r1 = ld_dsp_patch(a, 0x1400000eu, 0x14000059u); /* b 0x9c6164 -> b 0x9c6290 */
    int r2 = ld_dsp_patch(b, 0x5400098du, 0x1400004cu); /* b.le      -> b 0x9c6290 */
    state = 1;
    ld_blog("divider-skip: patch %p rc=%d now=%08x; %p rc=%d now=%08x",
            (void *)a, r1, *(volatile uint32_t *)a,
            (void *)b, r2, *(volatile uint32_t *)b);
}

#endif
