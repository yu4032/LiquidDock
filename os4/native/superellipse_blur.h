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
#include "hyper_engine_resolver.h"
#include "native_api.h"

#define LD_SE_BLUR_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-superellipse-blur"
#define LD_SE_BLUR_PATCH_HINT 0xc4aedcu
#define LD_SE_BLUR_VTABLE_DELTA 0x30u

static const uint32_t ld_pat_se_blur[7] = {
    0xd0002a28u, /* adrp x8, #page */
    0x912b0108u, /* add  x8, x8, #imm12 (RRectBlurShape vtable) */
    0x910023e3u, /* add  x3, sp, #8 */
    0xaa1303e2u, /* mov  x2, x19 */
    0xf90007e8u, /* str  x8, [sp, #8] */
    0xaa0003f4u, /* mov  x20, x0 */
    0x390043ffu, /* strb wzr, [sp, #0x10] */
};
static const uint32_t ld_msk_se_blur[7] = {
    0x9f00001fu, /* ADRP x8 */
    0xffc003ffu, /* ADD x8, x8, #imm12 */
    0xffffffffu,
    0xffffffffu,
    0xffffffffu,
    0xffffffffu,
    0xffffffffu,
};

/* 返回 0 成功；负值为 -errno 或 -2（内容不符）。 */
static int ld_patch_text_word(uintptr_t address, uint32_t expected, uint32_t value)
{
    size_t page = (size_t)sysconf(_SC_PAGESIZE);
    uintptr_t page_start = address & ~(uintptr_t)(page - 1);
    size_t span = (address + sizeof(uint32_t) > page_start + page) ? page * 2 : page;
    if (mprotect((void *)page_start, span, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        if (mprotect((void *)page_start, span, PROT_READ | PROT_WRITE) != 0)
            return -errno;
    }
    int rc = 0;
    if (*(volatile uint32_t *)address != expected) {
        rc = -2;
    } else {
        *(volatile uint32_t *)address = value;
        __builtin___clear_cache((char *)address, (char *)(address + 4));
        ld_record_hook_site_words(address, 1u);
    }
    if (mprotect((void *)page_start, span, PROT_READ | PROT_EXEC) != 0 && rc == 0)
        rc = -errno;
    return rc;
}

static void ld_superellipse_blur_install(int enabled)
{
    static int state;
    if (state) return;
    if (!enabled && access(LD_SE_BLUR_MARKER, F_OK) != 0) { state = 2; return; }
    struct ld_hyper_engine_info eng;
    if (!ld_hyper_engine_locate(&eng)) return;
    uintptr_t anchor = ld_he_resolve_unique(&eng, LD_SE_BLUR_PATCH_HINT,
                                            ld_pat_se_blur, ld_msk_se_blur, 7);
    if (!anchor) {
        state = 2;
        ld_blog("superellipse blur: pattern not found, skipped");
        return;
    }
    uintptr_t site = anchor + 4u;
    uint32_t old_w = *(volatile uint32_t *)site;
    uint32_t imm12 = (old_w >> 10) & 0xfffu;
    if (imm12 + LD_SE_BLUR_VTABLE_DELTA >= 0x1000u) {
        state = 2;
        ld_blog("superellipse blur: imm12=0x%x overflow, skipped", imm12);
        return;
    }
    uint32_t new_w = (old_w & ~(0xfffu << 10)) | ((imm12 + LD_SE_BLUR_VTABLE_DELTA) << 10);
    int rc = ld_patch_text_word(site, old_w, new_w);
    state = 1;
    ld_blog("superellipse blur: patch %p (off=0x%lx) %08x->%08x rc=%d",
            (void *)site, (unsigned long)(site - eng.elf_base), old_w, new_w, rc);
}

#endif
