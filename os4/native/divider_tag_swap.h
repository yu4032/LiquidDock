/* 分隔符 tag 交换实验（二进制补丁，不改 Dart 帧）。
 *
 * _createDividerItem @0x9c68a8 里：
 *   0x9c6958  add x2, x27, #9, lsl#12      ; x27 + 0x9000
 *   0x9c695c  ldr x2, [x2, #0xb00]         ; pool "Divider"        -> item+0x3b
 *   0x9c6960  stur w2, [x1, #0x3b]
 *   0x9c6964  add x2, x27, #9, lsl#12
 *   0x9c6968  ldr x2, [x2, #0xb08]         ; pool "hotseat_divider" -> item+0x3f
 *   0x9c696c  stur w2, [x1, #0x3f]
 *
 * 实验：把 0x9c6968 的 imm12 从 0x161（0xb08）改成 0x141（0xa08 = " | isDivider: "），
 * 即把分隔符 item 的 tag 换成一个无害字符串，观察 Dock 竖线是否消失/变化，
 * 用来判定 tag 是否驱动背景绘制。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-divider-tag
 */
#ifndef LD_DIVIDER_TAG_SWAP_H
#define LD_DIVIDER_TAG_SWAP_H

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"

#define LD_DTS_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-divider-tag"
#define LD_DTS_VADDR 0x9c6968u
#define LD_DTS_OLD 0xf9458442u   /* ldr x2,[x2,#0xb08]  -> "hotseat_divider" */
#define LD_DTS_NEW 0xf9450442u   /* ldr x2,[x2,#0xa08]  -> " | isDivider: " */

static int ld_dts_patch(uintptr_t address, uint32_t expected, uint32_t value)
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

static void ld_divider_tag_swap_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !lib) return;
    (void)api;
    if (access(LD_DTS_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t site = 0;
    if (!ld_vaddr_to_runtime(lib, LD_DTS_VADDR, &site)) return;
    int rc = ld_dts_patch(site, LD_DTS_OLD, LD_DTS_NEW);
    state = 1;
    ld_blog("divider-tag: patch %p rc=%d now=%08x", (void *)site, rc,
            *(volatile uint32_t *)site);
}

#endif
