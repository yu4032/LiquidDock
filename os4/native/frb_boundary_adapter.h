#ifndef LIQUIDDOCK_FRB_BOUNDARY_ADAPTER_H
#define LIQUIDDOCK_FRB_BOUNDARY_ADAPTER_H

/*
 * Rust/FRB Boundary Adapter (Stage 1: Zero-Hardcoding Discovery + Read-Only Boundary Observation)
 *
 * Locates frb_pde_ffi_dispatcher_primary via libapp_launcher.so .dynsym,
 * decodes its AArch64 uint16 jump table, and matches target addresses against
 * .gnu_debugdata SHT_SYMTAB to bind launcher_model_* func_ids dynamically
 * without hardcoding func_ids or Dart heap offsets.
 */

#include <elf.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "bootlog.h"
#include "executable_range.h"
#include "lib_base.h"
#include "rust_symbol_resolver.h"

typedef struct {
    int32_t start_load;
    int32_t bind_items_sub;
    int32_t bind_screens;
    int32_t update_item_batch;
    int32_t update_item;
    int32_t move_item;
} ld_frb_launcher_ids_t;

static ld_frb_launcher_ids_t g_frb_ids = {-1, -1, -1, -1, -1, -1};
static void (*g_orig_frb_primary_adapter)(int32_t, int64_t, uint8_t *, int32_t, int32_t) = NULL;
static bool g_frb_adapter_installed = false;

static int ld_frb_adapter_ready(void) {
    return (g_frb_adapter_installed && g_orig_frb_primary_adapter &&
            g_frb_ids.start_load >= 0 && g_frb_ids.update_item_batch >= 0) ? 1 : 0;
}

static uintptr_t ld_frb_find_dynsym(uintptr_t base, const char *target_name) {
    const Elf64_Ehdr *eh = (const Elf64_Ehdr *)base;
    if (!base || memcmp(eh->e_ident, ELFMAG, SELFMAG) != 0) return 0;
    const Elf64_Phdr *ph = (const Elf64_Phdr *)(base + eh->e_phoff);
    const Elf64_Dyn *dyn = NULL;
    for (uint16_t i = 0; i < eh->e_phnum; i++) {
        if (ph[i].p_type == PT_DYNAMIC) {
            dyn = (const Elf64_Dyn *)(base + ph[i].p_vaddr);
            break;
        }
    }
    if (!dyn) return 0;
    const char *strtab = NULL;
    const Elf64_Sym *symtab = NULL;
    uint32_t nchain = 0;
    for (; dyn->d_tag != DT_NULL; dyn++) {
        if (dyn->d_tag == DT_STRTAB) strtab = (const char *)(base + dyn->d_un.d_ptr);
        else if (dyn->d_tag == DT_SYMTAB) symtab = (const Elf64_Sym *)(base + dyn->d_un.d_ptr);
        else if (dyn->d_tag == DT_HASH) {
            const uint32_t *h = (const uint32_t *)(base + dyn->d_un.d_ptr);
            nchain = h[1];
        }
    }
    if (!strtab || !symtab) return 0;
    uint32_t limit = nchain ? nchain : 8192u;
    for (uint32_t i = 0; i < limit; i++) {
        if ((uintptr_t)&symtab[i] >= (uintptr_t)strtab) break;
        if (symtab[i].st_value == 0 || ELF64_ST_TYPE(symtab[i].st_info) != STT_FUNC) continue;
        if (strcmp(strtab + symtab[i].st_name, target_name) == 0) {
            return base + symtab[i].st_value;
        }
    }
    return 0;
}

static uintptr_t ld_frb_follow_b(uintptr_t addr) {
    uint32_t insn = 0;
    memcpy(&insn, (const void *)addr, 4);
    if ((insn & 0xfc000000u) == 0x14000000u) {
        int32_t imm26 = (int32_t)(insn & 0x03ffffffu);
        if (imm26 & 0x02000000) imm26 -= 0x04000000;
        return addr + ((int64_t)imm26 << 2);
    }
    return addr;
}

typedef struct {
    const char *needle;
    int32_t    *out_id;
    uintptr_t   vaddr;
} ld_frb_target_desc_t;

static bool ld_frb_match_debugdata_vaddrs(const uint8_t *elf, size_t elf_len,
                                          ld_frb_target_desc_t *targets, size_t ntargets) {
    if (!elf || elf_len < 64) return false;
    uint64_t e_shoff = 0;
    uint16_t e_shentsize = 0, e_shnum = 0;
    memcpy(&e_shoff, elf + 0x28, 8);
    memcpy(&e_shentsize, elf + 0x3a, 2);
    memcpy(&e_shnum, elf + 0x3c, 2);
    if (!e_shoff || e_shentsize < 64 || !e_shnum || e_shoff + (uint64_t)e_shentsize * e_shnum > elf_len) {
        return false;
    }

    for (uint16_t i = 0; i < e_shnum; i++) {
        const uint8_t *sh = elf + e_shoff + (size_t)i * e_shentsize;
        uint32_t sh_type = 0, sh_link = 0;
        uint64_t sh_offset = 0, sh_size = 0;
        memcpy(&sh_type, sh + 4, 4);
        memcpy(&sh_offset, sh + 24, 8);
        memcpy(&sh_size, sh + 32, 8);
        memcpy(&sh_link, sh + 40, 4);
        if (sh_type != 2u || sh_link >= e_shnum || sh_offset + sh_size > elf_len) continue;

        const uint8_t *str_sh = elf + e_shoff + (size_t)sh_link * e_shentsize;
        uint64_t str_off = 0, str_size = 0;
        memcpy(&str_off, str_sh + 24, 8);
        memcpy(&str_size, str_sh + 32, 8);
        if (!str_size || str_off + str_size > elf_len) continue;

        const char *strtab = (const char *)(elf + str_off);
        size_t count = (size_t)(sh_size / 24u);
        for (size_t idx = 0; idx < count; idx++) {
            const uint8_t *ent = elf + sh_offset + idx * 24u;
            uint32_t st_name = 0;
            uint8_t st_info = ent[4];
            uint64_t st_value = 0;
            memcpy(&st_name, ent, 4);
            memcpy(&st_value, ent + 8, 8);
            if ((st_info & 0x0fu) != 2u || !st_value || st_name >= str_size) continue;
            const char *name = strtab + st_name;
            if (!memchr(name, '\0', (size_t)(str_size - st_name))) continue;
            /* Skip closure/vtable suffixes (e.g. ..._impl0s_0E...) so we bind the primary wire entrypoint */
            if (strstr(name, "frb_generated") == NULL) continue;
            for (size_t t = 0; t < ntargets; t++) {
                const char *hit = strstr(name, targets[t].needle);
                if (hit && strcmp(hit, targets[t].needle) == 0) {
                    targets[t].vaddr = (uintptr_t)st_value;
                }
            }
        }
        return true;
    }
    return false;
}

static bool ld_frb_decode_primary_table(uintptr_t disp_rt, uintptr_t rust_base,
                                        ld_frb_target_desc_t *targets, size_t ntargets) {
    const uint32_t *insns = (const uint32_t *)disp_rt;
    int32_t min_id = -1;
    uint32_t max_delta = 0;
    const uint16_t *table = NULL;
    uintptr_t stub_base = 0;
    uintptr_t adrp_base = 0;
    uint32_t adrp_rd = 32;

    for (size_t i = 0; i < 16; i++) {
        uint32_t w = insns[i];
        uintptr_t pc = disp_rt + i * 4u;
        /* sub w8, w0, #imm12 */
        if ((w & 0xffc003ffu) == 0x51000008u && min_id < 0) {
            min_id = (int32_t)((w >> 10) & 0xfffu);
        }
        /* cmp w8, #imm12 */
        else if ((w & 0xffc003ffu) == 0x7100011fu && max_delta == 0) {
            max_delta = (w >> 10) & 0xfffu;
        }
        /* adrp x9, #imm */
        else if ((w & 0x9f00001fu) == 0x90000009u) {
            int64_t imm = (int64_t)(((uint64_t)(w >> 5) & 0x7ffffu) << 2) | ((w >> 29) & 0x3u);
            if (imm & (1LL << 20)) imm -= (1LL << 21);
            adrp_base = (pc & ~0xfffULL) + (imm << 12);
            adrp_rd = w & 0x1fu;
        }
        /* add x9, x9, #imm12 */
        else if ((w & 0xffc00000u) == 0x91000000u && adrp_rd == ((w >> 5) & 0x1fu) && (w & 0x1fu) == 9u) {
            uint32_t imm12 = (w >> 10) & 0xfffu;
            table = (const uint16_t *)(adrp_base + imm12);
        }
        /* adr x10, #imm */
        else if ((w & 0x9f00001fu) == 0x1000000au) {
            int64_t imm = (int64_t)(((uint64_t)(w >> 5) & 0x7ffffu) << 2) | ((w >> 29) & 0x3u);
            if (imm & (1LL << 20)) imm -= (1LL << 21);
            stub_base = pc + imm;
        }
    }

    if (min_id < 0 || max_delta == 0 || max_delta > 4096u || !table || !stub_base) {
        return false;
    }

    for (uint32_t i = 0; i <= max_delta; i++) {
        uintptr_t stub = stub_base + ((uintptr_t)table[i] << 2);
        if (!executable_range(stub, 24u)) continue;
        const uint32_t *sw = (const uint32_t *)stub;
        for (size_t k = 0; k < 6; k++) {
            if ((sw[k] & 0xfc000000u) == 0x14000000u) {
                int32_t imm26 = (int32_t)(sw[k] & 0x03ffffffu);
                if (imm26 & 0x02000000) imm26 -= 0x04000000;
                uintptr_t target_rt = (stub + k * 4u) + ((int64_t)imm26 << 2);
                uintptr_t target_va = target_rt - rust_base;
                for (size_t t = 0; t < ntargets; t++) {
                    if (targets[t].vaddr && targets[t].vaddr == target_va) {
                        *targets[t].out_id = min_id + (int32_t)i;
                    }
                }
                break;
            }
        }
    }
    return true;
}

static void repl_frb_primary_adapter(int32_t func_id, int64_t port, uint8_t *ptr,
                                     int32_t rust_vec_len, int32_t data_len) {
    if (func_id == g_frb_ids.start_load && ptr && data_len >= 16) {
        int32_t args[4] = {0};
        memcpy(args, ptr, 16);
        ld_blog("FRB adapter start_load(id=%d): args=[%d,%d,%d,%d]",
                func_id, args[0], args[1], args[2], args[3]);
    } else if (func_id == g_frb_ids.update_item_batch) {
        uint32_t count = 0;
        if (ptr && data_len >= 4) memcpy(&count, ptr, 4);
        ld_blog("FRB adapter update_item_batch(id=%d): bytes=%d header_count=%u",
                func_id, data_len, count);
    } else if (func_id == g_frb_ids.update_item) {
        ld_blog("FRB adapter update_item(id=%d): bytes=%d", func_id, data_len);
    } else if (func_id == g_frb_ids.move_item) {
        ld_blog("FRB adapter move_item(id=%d): bytes=%d", func_id, data_len);
    }
    g_orig_frb_primary_adapter(func_id, port, ptr, rust_vec_len, data_len);
}

static void ld_frb_adapter_init(int (*hook_fn)(void *, void *, void **),
                                const ld_lib_info *rust,
                                const char *apk_substr) {
    if (g_frb_adapter_installed || !hook_fn || !rust || !apk_substr) return;

    uintptr_t rust_base = 0;
    if (!ld_vaddr_to_runtime(rust, 0, &rust_base)) return;

    uintptr_t p_sym = ld_frb_find_dynsym(rust_base, "frb_pde_ffi_dispatcher_primary");
    if (!p_sym || !executable_range(p_sym, 4u)) return;

    uintptr_t p_disp = ld_frb_follow_b(p_sym);
    if (!executable_range(p_disp, 64u)) return;

    uint8_t *mini_elf = NULL;
    size_t mini_len = 0;
    if (!ld_rust_decompress_debugdata(rust, apk_substr, &mini_elf, &mini_len)) return;

    ld_frb_target_desc_t targets[] = {
        {"launcher_model_start_load_impl", &g_frb_ids.start_load, 0},
        {"launcher_model_set_bind_items_sub_callback_impl", &g_frb_ids.bind_items_sub, 0},
        {"launcher_model_set_bind_screens_callback_impl", &g_frb_ids.bind_screens, 0},
        {"launcher_model_update_item_database_batch_impl", &g_frb_ids.update_item_batch, 0},
        {"launcher_model_update_item_database_impl", &g_frb_ids.update_item, 0},
        {"launcher_model_move_item_in_database_impl", &g_frb_ids.move_item, 0},
    };
    size_t ntargets = sizeof(targets) / sizeof(targets[0]);

    bool sym_ok = ld_frb_match_debugdata_vaddrs(mini_elf, mini_len, targets, ntargets);
    free(mini_elf);
    if (!sym_ok || !ld_frb_decode_primary_table(p_disp, rust_base, targets, ntargets)) {
        ld_blog("FRB adapter: table decode failed");
        return;
    }

    if (g_frb_ids.start_load < 0 || g_frb_ids.update_item_batch < 0) {
        ld_blog("FRB adapter: missing core func_ids start_load=%d batch=%d",
                g_frb_ids.start_load, g_frb_ids.update_item_batch);
        return;
    }

    g_frb_adapter_installed = true;
    int r = hook_fn((void *)p_disp, (void *)repl_frb_primary_adapter,
                    (void **)&g_orig_frb_primary_adapter);
    ld_blog("FRB adapter ready=%d hook_res=%d ids={start=%d,bind_items=%d,bind_scr=%d,batch=%d,upd=%d,mov=%d}",
            r == 0 ? 1 : 0, r, g_frb_ids.start_load, g_frb_ids.bind_items_sub,
            g_frb_ids.bind_screens, g_frb_ids.update_item_batch,
            g_frb_ids.update_item, g_frb_ids.move_item);
}

#endif /* LIQUIDDOCK_FRB_BOUNDARY_ADAPTER_H */
