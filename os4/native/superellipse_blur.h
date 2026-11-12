/* OS4 超椭圆：blur 路径 4 字节补丁。
 *
 * impeller::Canvas::DrawRoundRect (@0xc4aee0) 里：
 *     c4aee0: add x8, x8, #0xac0      ; RRectBlurShape   vtable (0x1190ac0)
 * 改成
 *     c4aee0: add x8, x8, #0xaf0      ; RSuperellipseBlurShape vtable (0x1190af0)
 * 之后 DrawRoundRect 的模糊快路径直接走 rsuperellipse_blur_fragment_main。
 *
 * 只影响纯模糊（backdrop blur），不改玻璃材质；四角均匀在此路径已由入口检查
 * （四角半径差 <1e-3 且 rx==ry）保证。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-superellipse-blur
 */
#ifndef LD_SUPERELLIPSE_BLUR_H
#define LD_SUPERELLIPSE_BLUR_H

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"

#define LD_SE_BLUR_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-superellipse-blur"
#define LD_SE_BLUR_PATCH_VADDR 0xc4aee0u
#define LD_SE_BLUR_OLD 0x912b0108u   /* add x8, x8, #0xac0 */
#define LD_SE_BLUR_NEW 0x912bc108u   /* add x8, x8, #0xaf0 */

static uintptr_t ld_se_blur_engine_base(void)
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

/* 返回 0 成功；负值为 -errno 或 -2（内容不符）。 */
static int ld_patch_text_word(uintptr_t address, uint32_t expected, uint32_t value)
{
    size_t page = (size_t)sysconf(_SC_PAGESIZE);
    uintptr_t page_start = address & ~(uintptr_t)(page - 1);
    size_t span = (address + sizeof(uint32_t) > page_start + page) ? page * 2 : page;
    if (mprotect((void *)page_start, span, PROT_READ | PROT_WRITE | PROT_EXEC) != 0)
        return -errno;
    int rc = 0;
    if (*(volatile uint32_t *)address != expected) {
        rc = -2;
    } else {
        *(volatile uint32_t *)address = value;
        __builtin___clear_cache((char *)address, (char *)(address + 4));
    }
    if (mprotect((void *)page_start, span, PROT_READ | PROT_EXEC) != 0 && rc == 0)
        rc = -errno;
    return rc;
}

static void ld_superellipse_blur_install(void)
{
    static int state;
    if (state) return;
    if (access(LD_SE_BLUR_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t base = ld_se_blur_engine_base();
    if (!base) return;
    uintptr_t site = base + LD_SE_BLUR_PATCH_VADDR;
    int rc = ld_patch_text_word(site, LD_SE_BLUR_OLD, LD_SE_BLUR_NEW);
    state = 1;
    ld_blog("superellipse blur: patch %p rc=%d now=%08x", (void *)site, rc,
            *(volatile uint32_t *)site);
}

#endif
