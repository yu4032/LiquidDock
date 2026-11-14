#ifndef LIQUIDDOCK_FOLDER_GRID_H
#define LIQUIDDOCK_FOLDER_GRID_H

/*
 * Opened-folder grid customization for OS4 Dart AOT (libapp.so):
 *   1. GridController.folderGridViewCol -> custom column count (Smi)
 *   2. FolderGridViewGetxController.calGridHeight (Pad Q18 + Phone) -> custom max rows (Smi)
 *   3. FolderGridViewGetxController.calGridViewportHeight -> bypass 2-row availableHeight clamp
 */

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include "native_api.h"
#include "lib_base.h"
#include "aot_scanner.h"
#include "executable_range.h"
#include "hook_page_pin.h"
#include "bootlog.h"

static volatile int g_folder_grid_cols __attribute__((used));
static volatile int g_folder_grid_rows __attribute__((used));

static void *g_orig_folder_grid_cols __attribute__((used));
static void *g_orig_folder_q18_max_rows __attribute__((used));
static void *g_orig_folder_phone_max_rows __attribute__((used));
static void *g_orig_folder_viewport_height __attribute__((used));

static const uint32_t ld_pat_folder_grid_cols[] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10021efu, 0xf9403f40u, 0xf953e000u, 0x6b16001fu,
    0x540001c1u, 0xf9403f40u, 0xf94e9800u, 0xf9402370u, 0x6b10001fu, 0x54000061u,
    0xf97c8b62u, 0x944c4f15u, 0xf97c8f70u, 0xf90001f0u, 0xf9402b64u, 0x940930a7u,
    0xaa0003e1u, 0x14000002u, 0xaa0003e1u, 0xb84e7022u, 0x8b1c8042u, 0xf27c005fu,
    0x9a9f07e0u, 0xd1000400u, 0x927f0000u, 0x91001800u, 0xaa1d03efu, 0xa8c179fdu,
    0xd65f03c0u
};

static const uint32_t ld_pat_folder_q18_clamp[] = {
    0xf85c03a3u, 0x937f7860u, 0xeb80047fu, 0x54000060u, 0x944c40b8u, 0xf8007003u,
    0xaa0203e1u, 0xaa0003e3u, 0xd2800042u, 0x9440cc51u, 0xf85f83a1u, 0xf8463022u,
    0x93417c03u, 0x36000040u, 0xf8407003u, 0xf81e83a3u
};

static const uint32_t ld_pat_folder_phone_clamp[] = {
    0x935efc30u, 0xeb81fe1fu, 0x54003181u, 0xd37ff821u, 0xd2800042u, 0xd2800103u,
    0x9440ccfdu, 0x93417c01u, 0x36000040u, 0xf8407001u, 0xaa0103e0u, 0x14000002u,
    0xd2800080u, 0xf85f83a1u, 0xfc5b83a0u, 0x14000082u
};

static const uint32_t ld_pat_folder_viewport_height[] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10061efu, 0xaa0103e0u, 0xf81f83a1u, 0x97d2d0ccu,
    0xf85f83a1u, 0x97d2d0abu, 0xf85f83a1u, 0x97d2ca48u, 0xfc1f03a0u, 0xf9403f40u,
    0xf9541400u, 0x6b16001fu, 0x540001e1u, 0xf9403f40u, 0xf94e9800u, 0xf9402370u,
    0x6b10001fu, 0x54000061u, 0xf97c8b62u, 0x941f04ceu, 0x91402770u, 0xf9408210u,
    0xf90001f0u, 0xf9402b64u, 0x97dbe65fu, 0xaa0003e1u, 0x14000002u, 0xaa0003e1u,
    0x97d28837u, 0x362000a0u, 0xfc5f03a0u, 0xaa1d03efu, 0xa8c179fdu, 0xd65f03c0u,
    0xf85f83a0u, 0xfc5f03a1u, 0xfc4cf002u, 0x4ee2f420u, 0xaa1d03efu, 0xa8c179fdu,
    0xd65f03c0u
};

__attribute__((naked, used)) static void repl_folder_grid_cols(void) {
    __asm__ volatile(
        "adrp x16, g_folder_grid_cols\n"
        "ldr w16, [x16, :lo12:g_folder_grid_cols]\n"
        "cmp w16, #2\n"
        "b.lt 1f\n"
        "lsl x0, x16, #1\n"
        "ret\n"
        "1: adrp x16, g_orig_folder_grid_cols\n"
        "ldr x16, [x16, :lo12:g_orig_folder_grid_cols]\n"
        "br x16\n"
    );
}

__attribute__((naked, used)) static void repl_folder_q18_max_rows(void) {
    __asm__ volatile(
        "adrp x16, g_folder_grid_rows\n"
        "ldr w16, [x16, :lo12:g_folder_grid_rows]\n"
        "cmp w16, #2\n"
        "b.lt 1f\n"
        "lsl x0, x16, #1\n"
        "1: adrp x16, g_orig_folder_q18_max_rows\n"
        "ldr x16, [x16, :lo12:g_orig_folder_q18_max_rows]\n"
        "br x16\n"
    );
}

__attribute__((naked, used)) static void repl_folder_phone_max_rows(void) {
    __asm__ volatile(
        "adrp x16, g_folder_grid_rows\n"
        "ldr w16, [x16, :lo12:g_folder_grid_rows]\n"
        "cmp w16, #2\n"
        "b.lt 1f\n"
        "lsl x3, x16, #1\n"
        "1: adrp x16, g_orig_folder_phone_max_rows\n"
        "ldr x16, [x16, :lo12:g_orig_folder_phone_max_rows]\n"
        "br x16\n"
    );
}

__attribute__((naked, used)) static void repl_folder_viewport_height(void) {
    __asm__ volatile(
        "adrp x16, g_folder_grid_rows\n"
        "ldr w16, [x16, :lo12:g_folder_grid_rows]\n"
        "cmp w16, #2\n"
        "b.lt 1f\n"
        "ldur d0, [x29, #-0x10]\n"
        "mov x15, x29\n"
        "ldp x29, x30, [x15], #0x10\n"
        "ret\n"
        "1: adrp x16, g_orig_folder_viewport_height\n"
        "ldr x16, [x16, :lo12:g_orig_folder_viewport_height]\n"
        "br x16\n"
    );
}

static bool ld_install_folder_site(const struct native_api *api, uintptr_t site,
                                   void *repl, void **orig, const char *label) {
    if (!site || !executable_range(site, 16u)) return false;
    int rc = api->hook((void *)site, repl, orig);
    if (rc == 0 && *orig) {
        ld_pin_code_pages_soft(site, 16u);
        ld_blog("OS4 folder grid hook %s installed at %p", label, (void *)site);
        return true;
    }
    ld_blog("OS4 folder grid hook %s failed at %p rc=%d", label, (void *)site, rc);
    return false;
}

static void ld_folder_grid_install(const struct native_api *api, const ld_lib_info *lib,
                                   int cols, int rows) {
    __atomic_store_n(&g_folder_grid_cols, cols, __ATOMIC_RELEASE);
    __atomic_store_n(&g_folder_grid_rows, rows, __ATOMIC_RELEASE);
    if (!api || !api->hook || !lib || (cols <= 0 && rows <= 0)) return;

    static bool installed = false;
    if (installed) return;
    installed = true;

    int ok = 0;
    if (cols > 0 && !g_orig_folder_grid_cols) {
        uintptr_t site = ld_find_aot_pattern(lib, 0x92c104u, ld_pat_folder_grid_cols,
                                             sizeof ld_pat_folder_grid_cols / sizeof(uint32_t));
        if (ld_install_folder_site(api, site, (void *)repl_folder_grid_cols,
                                   &g_orig_folder_grid_cols, "folderGridViewCol")) ok++;
    }
    if (rows > 0) {
        if (!g_orig_folder_q18_max_rows) {
            uintptr_t base = ld_find_aot_pattern(lib, 0x931bdcu, ld_pat_folder_q18_clamp,
                                                 sizeof ld_pat_folder_q18_clamp / sizeof(uint32_t));
            if (base && ld_install_folder_site(api, base + 0x18u, (void *)repl_folder_q18_max_rows,
                                               &g_orig_folder_q18_max_rows, "calGridHeight.q18")) ok++;
        }
        if (!g_orig_folder_phone_max_rows) {
            uintptr_t base = ld_find_aot_pattern(lib, 0x931938u, ld_pat_folder_phone_clamp,
                                                 sizeof ld_pat_folder_phone_clamp / sizeof(uint32_t));
            if (base && ld_install_folder_site(api, base + 0x18u, (void *)repl_folder_phone_max_rows,
                                               &g_orig_folder_phone_max_rows, "calGridHeight.phone")) ok++;
        }
        if (!g_orig_folder_viewport_height) {
            uintptr_t base = ld_find_aot_pattern(lib, 0x147ea00u, ld_pat_folder_viewport_height,
                                                 sizeof ld_pat_folder_viewport_height / sizeof(uint32_t));
            if (base && ld_install_folder_site(api, base + 0x90u, (void *)repl_folder_viewport_height,
                                               &g_orig_folder_viewport_height, "calGridViewportHeight")) ok++;
        }
    }
    ld_blog("OS4 folder grid setup cols=%d rows=%d installed=%d", cols, rows, ok);
}

#endif /* LIQUIDDOCK_FOLDER_GRID_H */
