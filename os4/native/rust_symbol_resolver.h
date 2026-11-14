#ifndef LIQUIDDOCK_RUST_SYMBOL_RESOLVER_H
#define LIQUIDDOCK_RUST_SYMBOL_RESOLVER_H

/*
 * Runtime Rust symbol resolver for libapp_launcher.so:
 *   1. Locates .symtab or decompresses .gnu_debugdata via xz_mini.h
 *   2. Matches Rust v0 mangled symbols by module+function path, ignoring crate hash
 *   3. Enforces uniqueness (match_count == 1) and AArch64 boundary/semantic checks
 */

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "lib_base.h"
#include "bootlog.h"
#include "executable_range.h"
#include "xz_mini.h"

typedef struct {
    uintptr_t vaddr;
    size_t    size;
    uintptr_t runtime;
    bool      from_debugdata;
} ld_rust_sym_result_t;

static bool ld_rust_v0_match_path(const char *sym, const char *path_suffix) {
    if (!sym || !path_suffix) return false;
    if (strncmp(sym, "_RNv", 4) != 0) return false;
    const char *p = sym + 4;
    while (p[0] == 'N' && p[1] == 't') p += 2;
    if (p[0] != 'C' || p[1] != 's') return false;
    p += 2;
    while (*p && *p != '_') {
        char c = *p;
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
            return false;
        }
        p++;
    }
    if (*p != '_') return false;
    p++;
    return strcmp(p, path_suffix) == 0;
}

static bool ld_elf64_find_sym_in_mem(const uint8_t *elf, size_t elf_len,
                                     const char *path_suffix,
                                     uintptr_t *out_vaddr, size_t *out_size,
                                     size_t *out_matches) {
    if (!elf || elf_len < 64) return false;
    uint32_t magic = 0;
    memcpy(&magic, elf, 4);
    if (magic != LD_ELF_MAGIC_WORD || elf[4] != 2u || elf[5] != 1u) return false;

    uint64_t e_shoff = 0;
    uint16_t e_shentsize = 0, e_shnum = 0;
    memcpy(&e_shoff, elf + 0x28, 8);
    memcpy(&e_shentsize, elf + 0x3a, 2);
    memcpy(&e_shnum, elf + 0x3c, 2);
    if (e_shoff == 0 || e_shentsize < 64 || e_shnum == 0) return false;
    if (e_shoff + (uint64_t)e_shentsize * e_shnum > elf_len) return false;

    for (uint16_t i = 0; i < e_shnum; i++) {
        const uint8_t *sh = elf + e_shoff + (size_t)i * e_shentsize;
        uint32_t sh_type = 0, sh_link = 0;
        uint64_t sh_offset = 0, sh_size = 0;
        memcpy(&sh_type, sh + 4, 4);
        memcpy(&sh_offset, sh + 24, 8);
        memcpy(&sh_size, sh + 32, 8);
        memcpy(&sh_link, sh + 40, 4);
        if (sh_type != 2u) continue; /* SHT_SYMTAB */
        if (sh_link >= e_shnum) return false;
        if (sh_offset + sh_size > elf_len) return false;

        const uint8_t *str_sh = elf + e_shoff + (size_t)sh_link * e_shentsize;
        uint64_t str_off = 0, str_size = 0;
        memcpy(&str_off, str_sh + 24, 8);
        memcpy(&str_size, str_sh + 32, 8);
        if (str_off + str_size > elf_len || str_size == 0) return false;

        const char *strtab = (const char *)(elf + str_off);
        size_t count = (size_t)(sh_size / 24u);
        size_t matches = 0;
        uintptr_t matched_vaddr = 0;
        size_t matched_size = 0;

        for (size_t idx = 0; idx < count; idx++) {
            const uint8_t *ent = elf + sh_offset + idx * 24u;
            uint32_t st_name = 0;
            uint8_t st_info = ent[4];
            uint64_t st_value = 0, st_size = 0;
            memcpy(&st_name, ent + 0, 4);
            memcpy(&st_value, ent + 8, 8);
            memcpy(&st_size, ent + 16, 8);
            if ((st_info & 0x0fu) != 2u || st_value == 0) continue; /* STT_FUNC */
            if ((uint64_t)st_name >= str_size) continue;

            const char *name = strtab + st_name;
            size_t max_len = (size_t)(str_size - st_name);
            if (!memchr(name, '\0', max_len)) continue;

            if (ld_rust_v0_match_path(name, path_suffix)) {
                matches++;
                if (matches == 1u) {
                    matched_vaddr = (uintptr_t)st_value;
                    matched_size = (size_t)st_size;
                }
            }
        }
        *out_matches = matches;
        *out_vaddr = matched_vaddr;
        *out_size = matched_size;
        return matches == 1u;
    }
    return false;
}

static bool ld_elf64_read_exact(FILE *f, uint64_t off, void *dst, size_t len) {
    if (fseeko(f, (off_t)off, SEEK_SET) != 0) return false;
    return fread(dst, 1, len, f) == len;
}

static bool ld_resolve_rust_symbol(const ld_lib_info *lib,
                                   const char *apk_substr,
                                   const char *path_suffix,
                                   ld_rust_sym_result_t *out) {
    if (!lib || !apk_substr || !path_suffix || !out) return false;
    memset(out, 0, sizeof(*out));

    char apk_path[512];
    if (!ld_apk_real_path(apk_substr, apk_path, sizeof apk_path)) return false;

    FILE *f = fopen(apk_path, "rb");
    if (!f) return false;

    uint64_t base_off = (uint64_t)lib->entry_off;
    uint8_t eh[64];
    if (!ld_elf64_read_exact(f, base_off, eh, sizeof eh)) { fclose(f); return false; }

    uint32_t magic = 0;
    memcpy(&magic, eh, 4);
    if (magic != LD_ELF_MAGIC_WORD || eh[4] != 2u || eh[5] != 1u) { fclose(f); return false; }

    uint64_t e_shoff = 0;
    uint16_t e_shentsize = 0, e_shnum = 0, e_shstrndx = 0;
    memcpy(&e_shoff, eh + 0x28, 8);
    memcpy(&e_shentsize, eh + 0x3a, 2);
    memcpy(&e_shnum, eh + 0x3c, 2);
    memcpy(&e_shstrndx, eh + 0x3e, 2);
    if (e_shoff == 0 || e_shentsize < 64 || e_shnum == 0 || e_shnum > 256 || e_shstrndx >= e_shnum) {
        fclose(f);
        return false;
    }

    size_t sht_bytes = (size_t)e_shentsize * e_shnum;
    uint8_t *sht = (uint8_t *)malloc(sht_bytes);
    if (!sht) { fclose(f); return false; }
    if (!ld_elf64_read_exact(f, base_off + e_shoff, sht, sht_bytes)) {
        free(sht); fclose(f); return false;
    }

    const uint8_t *shstr_hdr = sht + (size_t)e_shstrndx * e_shentsize;
    uint64_t shstr_off = 0, shstr_size = 0;
    memcpy(&shstr_off, shstr_hdr + 24, 8);
    memcpy(&shstr_size, shstr_hdr + 32, 8);
    if (shstr_size == 0 || shstr_size > 1024u * 1024u) {
        free(sht); fclose(f); return false;
    }
    char *shstr = (char *)malloc((size_t)shstr_size);
    if (!shstr || !ld_elf64_read_exact(f, base_off + shstr_off, shstr, (size_t)shstr_size)) {
        free(shstr); free(sht); fclose(f); return false;
    }

    uint64_t dbg_off = 0, dbg_size = 0;
    for (uint16_t i = 0; i < e_shnum; i++) {
        const uint8_t *sh = sht + (size_t)i * e_shentsize;
        uint32_t sh_name = 0;
        memcpy(&sh_name, sh + 0, 4);
        if ((uint64_t)sh_name >= shstr_size) continue;
        const char *sname = shstr + sh_name;
        size_t max_len = (size_t)(shstr_size - sh_name);
        if (!memchr(sname, '\0', max_len)) continue;
        if (strcmp(sname, ".gnu_debugdata") == 0) {
            memcpy(&dbg_off, sh + 24, 8);
            memcpy(&dbg_size, sh + 32, 8);
            break;
        }
    }
    free(shstr);
    free(sht);

    if (dbg_off == 0 || dbg_size == 0 || dbg_size > 8u * 1024u * 1024u) {
        fclose(f);
        return false;
    }

    uint8_t *xz_buf = (uint8_t *)malloc((size_t)dbg_size);
    if (!xz_buf || !ld_elf64_read_exact(f, base_off + dbg_off, xz_buf, (size_t)dbg_size)) {
        free(xz_buf); fclose(f); return false;
    }
    fclose(f);

    uint8_t *mini_elf = NULL;
    size_t mini_len = 0;
    bool decomp_ok = xz_mini_decompress(xz_buf, (size_t)dbg_size, &mini_elf, &mini_len);
    free(xz_buf);
    if (!decomp_ok) return false;

    uintptr_t vaddr = 0;
    size_t sym_size = 0, matches = 0;
    bool found = ld_elf64_find_sym_in_mem(mini_elf, mini_len, path_suffix,
                                          &vaddr, &sym_size, &matches);
    free(mini_elf);
    if (!found) {
        ld_blog("Rust symbol resolver: path=%s matches=%zu", path_suffix, matches);
        return false;
    }

    uintptr_t runtime = 0;
    if (!ld_vaddr_to_runtime(lib, vaddr, &runtime)) return false;

    out->vaddr = vaddr;
    out->size = sym_size;
    out->runtime = runtime;
    out->from_debugdata = true;
    return true;
}

static bool ld_verify_rust_orientation_fn(const ld_lib_info *lib,
                                          const ld_rust_sym_result_t *sym) {
    if (!lib || !sym || !sym->runtime || !sym->vaddr) return false;
    if (sym->size < 256u || sym->size > 32768u) return false;
    if (!executable_range(sym->runtime, 16u)) return false;
    if (!executable_range(sym->runtime + sym->size - 4u, 4u)) return false;

    /* Verify both start and end vaddr map cleanly inside libapp_launcher.so PT_LOAD */
    if (!ld_cross_check_strict(lib, sym->vaddr, sym->vaddr + sym->size - 4u)) return false;

    const uint32_t *insns = (const uint32_t *)sym->runtime;
    uint32_t w0 = insns[0];
    bool has_prologue = ((w0 & 0xffc07fffu) == 0xa9807bfdu) || /* stp x29, x30, [sp, #-imm]! */
                        ((w0 & 0xffc07fffu) == 0xa9007bfdu) || /* stp x29, x30, [sp, #imm] */
                        ((w0 & 0xffc003ffu) == 0xd10003ffu);   /* sub sp, sp, #imm */
    if (!has_prologue) return false;

    size_t scan_words = sym->size / 4u;
    if (scan_words > 256u) scan_words = 256u;
    size_t bl_count = 0;
    bool has_ldp_or_ldpsw = false;
    bool has_cset = false;
    for (size_t i = 0; i < scan_words; i++) {
        uint32_t w = insns[i];
        if ((w & 0xfc000000u) == 0x94000000u) bl_count++;
        if ((w & 0xffc00000u) == 0x69400000u || (w & 0xffc00000u) == 0xa9400000u) {
            has_ldp_or_ldpsw = true;
        }
        if ((w & 0x7fff0fe0u) == 0x1a9f07e0u) has_cset = true;
    }
    return bl_count >= 2u && has_ldp_or_ldpsw && has_cset;
}

#endif /* LIQUIDDOCK_RUST_SYMBOL_RESOLVER_H */
