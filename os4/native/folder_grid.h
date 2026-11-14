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
static volatile uint32_t g_folder_col_field_off __attribute__((used));

static void *g_orig_folder_grid_cols __attribute__((used));
static void *g_orig_folder_cal_grid_width __attribute__((used));
static void *g_orig_folder_cal_grid_height __attribute__((used));
static void *g_orig_folder_build_scrollable_grid __attribute__((used));
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

/* FolderGridViewGetxController.calGridWidth: index 5 is ldr x0, [x0, #field_off] */
static const uint32_t ld_pat_folder_cal_grid_width[] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10041efu, 0xf81f83a1u, 0xf9403f40u, 0xf9542000u,
    0xf9402370u, 0x6b10001fu, 0x54000081u, 0x91403762u, 0xf9420c42u, 0x944c3440u,
    0xf85f83a1u, 0xf81f03a0u, 0x97fffdacu, 0xf85f03a1u, 0x93417c30u, 0x1e620201u,
    0x1e610802u, 0xf85f83a2u, 0xfc443040u, 0x93417c23u, 0xd1000461u, 0x9e620021u,
    0x1e610803u, 0x1e632840u, 0xfc053040u, 0xaa1603e0u, 0xaa1d03efu, 0xa8c179fdu,
    0xd65f03c0u
};

/* FolderGridViewGetxController.calGridHeight entry */
static const uint32_t ld_pat_folder_cal_grid_height[] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10181efu, 0xf81f83a1u, 0xd2800041u, 0x944c3e17u,
    0xaa0003e2u, 0xf85f83a0u, 0xf81f03a2u, 0xb800f040u, 0xb841f001u, 0x8b1c8021u,
    0xf9402370u, 0x6b10003fu, 0x54005f40u, 0x97ffed01u, 0xf81e83a0u, 0xf9403f41u,
    0xf953e021u, 0x6b16003fu, 0x540001a1u, 0xf9403f40u, 0xf94e9800u, 0xf9402370u
};

/* FolderGridView._buildScrollableGrid entry */
static const uint32_t ld_pat_folder_build_scrollable_grid[] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10241efu, 0xaa0103e0u, 0xaa0203e1u, 0xf81f83a2u,
    0xd2800021u, 0x941f0d6cu, 0xf85f83a1u, 0xf81e83a0u, 0xb800f001u, 0xb84bb023u,
    0x8b1c8063u, 0xf81f03a3u, 0x6b16007fu, 0x540018c0u, 0x940000fdu, 0xf81e03a0u,
    0xf9403f40u, 0xf9542000u, 0xf9402370u, 0x6b10001fu, 0x54000081u, 0x91403762u
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

/* Epilogue tail inside FolderGridViewGetxController.calGridViewportHeight (+0x90) */
static const uint32_t ld_pat_folder_viewport_tail[] = {
    0xf85f83a0u, 0xfc5f03a1u, 0xfc4cf002u, 0x4ee2f420u, 0xaa1d03efu, 0xa8c179fdu,
    0xd65f03c0u
};

#define LD_DEFINE_SYNC_FOLDER_COL_HOOK(fn_name, orig_var)                       \
    __attribute__((naked, used)) static void fn_name(void) {                   \
        __asm__ volatile(                                                      \
            "adrp x16, g_folder_grid_cols\n"                                   \
            "ldr w17, [x16, :lo12:g_folder_grid_cols]\n"                      \
            "cmp w17, #2\n"                                                    \
            "b.lt 1f\n"                                                        \
            "adrp x16, g_folder_col_field_off\n"                               \
            "ldr w16, [x16, :lo12:g_folder_col_field_off]\n"                  \
            "cbz w16, 1f\n"                                                    \
            "ldr x17, [x26, #0x78]\n"                                          \
            "cbz x17, 1f\n"                                                    \
            "add x16, x17, x16\n"                                              \
            "adrp x17, g_folder_grid_cols\n"                                   \
            "ldr w17, [x17, :lo12:g_folder_grid_cols]\n"                      \
            "lsl x17, x17, #1\n"                                               \
            "str x17, [x16]\n"                                                 \
            "1: adrp x16, " #orig_var "\n"                                     \
            "ldr x16, [x16, :lo12:" #orig_var "]\n"                            \
            "br x16\n"                                                         \
        );                                                                     \
    }

LD_DEFINE_SYNC_FOLDER_COL_HOOK(repl_folder_sync_cal_grid_width, g_orig_folder_cal_grid_width)
LD_DEFINE_SYNC_FOLDER_COL_HOOK(repl_folder_sync_cal_grid_height, g_orig_folder_cal_grid_height)
LD_DEFINE_SYNC_FOLDER_COL_HOOK(repl_folder_sync_build_scrollable_grid, g_orig_folder_build_scrollable_grid)

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

typedef struct {
    uintptr_t runtime;
    size_t size;
} ld_folder_sym_t;

static bool ld_resolve_folder_sym(const ld_lib_info *lib, size_t idx, ld_folder_sym_t *out) {
    if (!lib || !out || !g_home_options.folder_grid_targets_present || idx >= 5) return false;
    uint64_t vaddr = g_home_options.folder_grid_vaddr[idx];
    size_t size = g_home_options.folder_grid_size[idx];
    if (!vaddr || size < 16u) return false;
    uintptr_t rt = 0;
    if (!ld_vaddr_to_runtime(lib, (uintptr_t)vaddr, &rt) || !executable_range(rt, size)) return false;
    const uint32_t *w = (const uint32_t *)rt;
    if (w[0] != 0xa9bf79fdu || w[1] != 0xaa0f03fdu) return false;
    out->runtime = rt;
    out->size = size;
    return true;
}

static uintptr_t ld_scan_within_sym(const ld_folder_sym_t *sym, const uint32_t *pat, size_t pat_words) {
    if (!sym || !pat || pat_words == 0 || sym->size < pat_words * sizeof(uint32_t)) return 0;
    const uint32_t *code = (const uint32_t *)sym->runtime;
    size_t n_words = sym->size / sizeof(uint32_t);
    size_t limit = n_words - pat_words;
    uintptr_t hit = 0;
    int hits = 0;
    for (size_t i = 0; i <= limit; i++) {
        bool ok = true;
        for (size_t j = 0; j < pat_words; j++) {
            uint32_t mask = ld_aot_inst_mask(pat[j]);
            if ((code[i + j] & mask) != (pat[j] & mask)) {
                ok = false;
                break;
            }
        }
        if (ok) {
            hit = sym->runtime + i * sizeof(uint32_t);
            hits++;
            if (hits > 1) return 0;
        }
    }
    return hits == 1 ? hit : 0;
}

static uintptr_t ld_resolve_folder_entry(const ld_lib_info *lib, size_t sym_idx,
                                         uintptr_t hint_vaddr, const uint32_t *pat, size_t pat_words,
                                         const char **via_out) {
    ld_folder_sym_t sym;
    if (ld_resolve_folder_sym(lib, sym_idx, &sym)) {
        if (via_out) *via_out = "sym";
        return sym.runtime;
    }
    if (g_home_options.folder_grid_targets_present) {
        if (via_out) *via_out = "sym-fail";
        return 0;
    }
    if (via_out) *via_out = "aot";
    return ld_find_aot_pattern(lib, hint_vaddr, pat, pat_words);
}

static uintptr_t ld_resolve_folder_subsite(const ld_lib_info *lib, size_t sym_idx,
                                           uintptr_t hint_vaddr, const uint32_t *pat, size_t pat_words,
                                           size_t add_off, uintptr_t *base_out, const char **via_out) {
    ld_folder_sym_t sym;
    uintptr_t base = 0;
    if (ld_resolve_folder_sym(lib, sym_idx, &sym)) {
        base = ld_scan_within_sym(&sym, pat, pat_words);
        if (via_out) *via_out = "sym-sub";
    } else if (!g_home_options.folder_grid_targets_present) {
        base = ld_find_aot_pattern(lib, hint_vaddr, pat, pat_words);
        if (via_out) *via_out = "aot";
    } else {
        if (via_out) *via_out = "sym-fail";
    }
    if (base_out) *base_out = base;
    return base ? (base + add_off) : 0;
}

static bool ld_install_folder_site(const struct native_api *api, uintptr_t site,
                                   void *repl, void **orig, const char *label, const char *via) {
    if (!site || !executable_range(site, 16u)) {
        ld_blog("OS4 folder grid hook %s skipped (site=%p via=%s)", label, (void *)site, via ? via : "?");
        return false;
    }
    int rc = api->hook((void *)site, repl, orig);
    if (rc == 0 && *orig) {
        ld_pin_code_pages_soft(site, 16u);
        ld_blog("OS4 folder grid hook %s installed at %p via=%s", label, (void *)site, via ? via : "?");
        return true;
    }
    ld_blog("OS4 folder grid hook %s failed at %p via=%s rc=%d", label, (void *)site, via ? via : "?", rc);
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
    /* Resolve sym[2] (calGridHeight) before calGridHeight.sync overwrites its prologue. */
    ld_folder_sym_t cal_height_sym;
    bool has_cal_height_sym = ld_resolve_folder_sym(lib, 2, &cal_height_sym);

    if (cols > 0) {
        if (!g_orig_folder_grid_cols) {
            const char *via = NULL;
            uintptr_t site = ld_resolve_folder_entry(
                lib, 0, 0x92c104u, ld_pat_folder_grid_cols,
                sizeof ld_pat_folder_grid_cols / sizeof(uint32_t), &via);
            if (ld_install_folder_site(api, site, (void *)repl_folder_grid_cols,
                                       &g_orig_folder_grid_cols, "folderGridViewCol", via)) ok++;
        }
        if (!g_orig_folder_cal_grid_width) {
            const char *via = NULL;
            uintptr_t site = ld_resolve_folder_entry(
                lib, 1, 0x932cc8u, ld_pat_folder_cal_grid_width,
                sizeof ld_pat_folder_cal_grid_width / sizeof(uint32_t), &via);
            if (site) {
                uint32_t insn4 = ((const uint32_t *)site)[4];
                uint32_t insn5 = ((const uint32_t *)site)[5];
                if (insn4 == 0xf9403f40u && (insn5 & 0xffc003ffu) == 0xf9400000u) {
                    uint32_t field_off = ((insn5 >> 10) & 0xfffu) << 3;
                    __atomic_store_n(&g_folder_col_field_off, field_off, __ATOMIC_RELEASE);
                    ld_blog("OS4 folder grid resolved col_field_off=0x%x", field_off);
                }
                if (ld_install_folder_site(api, site, (void *)repl_folder_sync_cal_grid_width,
                                           &g_orig_folder_cal_grid_width, "calGridWidth.sync", via)) ok++;
            }
        }
        if (!g_orig_folder_cal_grid_height && g_folder_col_field_off) {
            const char *via = has_cal_height_sym ? "sym" :
                (g_home_options.folder_grid_targets_present ? "sym-fail" : "aot");
            uintptr_t site = has_cal_height_sym ? cal_height_sym.runtime :
                (g_home_options.folder_grid_targets_present ? 0 :
                 ld_find_aot_pattern(lib, 0x931344u, ld_pat_folder_cal_grid_height,
                                     sizeof ld_pat_folder_cal_grid_height / sizeof(uint32_t)));
            if (ld_install_folder_site(api, site, (void *)repl_folder_sync_cal_grid_height,
                                       &g_orig_folder_cal_grid_height, "calGridHeight.sync", via)) ok++;
        }
        if (!g_orig_folder_build_scrollable_grid && g_folder_col_field_off) {
            const char *via = NULL;
            uintptr_t site = ld_resolve_folder_entry(
                lib, 3, 0x147d5e8u, ld_pat_folder_build_scrollable_grid,
                sizeof ld_pat_folder_build_scrollable_grid / sizeof(uint32_t), &via);
            if (ld_install_folder_site(api, site, (void *)repl_folder_sync_build_scrollable_grid,
                                       &g_orig_folder_build_scrollable_grid, "_buildScrollableGrid.sync", via)) ok++;
        }
    }
    if (rows > 0) {
        if (!g_orig_folder_q18_max_rows) {
            const char *via = has_cal_height_sym ? "sym-sub" :
                (g_home_options.folder_grid_targets_present ? "sym-fail" : "aot");
            uintptr_t base = has_cal_height_sym
                ? ld_scan_within_sym(&cal_height_sym, ld_pat_folder_q18_clamp,
                                     sizeof ld_pat_folder_q18_clamp / sizeof(uint32_t))
                : (g_home_options.folder_grid_targets_present ? 0 :
                   ld_find_aot_pattern(lib, 0x931bdcu, ld_pat_folder_q18_clamp,
                                       sizeof ld_pat_folder_q18_clamp / sizeof(uint32_t)));
            uintptr_t site = base ? (base + 0x18u) : 0;
            if (ld_install_folder_site(api, site, (void *)repl_folder_q18_max_rows,
                                       &g_orig_folder_q18_max_rows, "calGridHeight.q18", via)) ok++;
        }
        if (!g_orig_folder_phone_max_rows) {
            const char *via = has_cal_height_sym ? "sym-sub" :
                (g_home_options.folder_grid_targets_present ? "sym-fail" : "aot");
            uintptr_t base = has_cal_height_sym
                ? ld_scan_within_sym(&cal_height_sym, ld_pat_folder_phone_clamp,
                                     sizeof ld_pat_folder_phone_clamp / sizeof(uint32_t))
                : (g_home_options.folder_grid_targets_present ? 0 :
                   ld_find_aot_pattern(lib, 0x931938u, ld_pat_folder_phone_clamp,
                                       sizeof ld_pat_folder_phone_clamp / sizeof(uint32_t)));
            uintptr_t site = base ? (base + 0x18u) : 0;
            if (ld_install_folder_site(api, site, (void *)repl_folder_phone_max_rows,
                                       &g_orig_folder_phone_max_rows, "calGridHeight.phone", via)) ok++;
        }
        if (!g_orig_folder_viewport_height) {
            const char *via = NULL;
            uintptr_t site = ld_resolve_folder_subsite(
                lib, 4, 0x147ea00u, ld_pat_folder_viewport_tail,
                sizeof ld_pat_folder_viewport_tail / sizeof(uint32_t), 0u, NULL, &via);
            if (!site && !g_home_options.folder_grid_targets_present) {
                uintptr_t base = ld_find_aot_pattern(lib, 0x147ea00u, ld_pat_folder_viewport_height,
                                                     sizeof ld_pat_folder_viewport_height / sizeof(uint32_t));
                site = base ? (base + 0x90u) : 0;
                via = "aot";
            }
            if (ld_install_folder_site(api, site, (void *)repl_folder_viewport_height,
                                       &g_orig_folder_viewport_height, "calGridViewportHeight", via)) ok++;
        }
    }
    ld_blog("OS4 folder grid setup cols=%d rows=%d installed=%d", cols, rows, ok);
}

#endif /* LIQUIDDOCK_FOLDER_GRID_H */
