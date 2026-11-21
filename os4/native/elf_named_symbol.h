#ifndef LD_ELF_NAMED_SYMBOL_H
#define LD_ELF_NAMED_SYMBOL_H

/* Bounded ELF64 little-endian symbol lookup for XZ .gnu_debugdata.
 * This is deliberately independent of Android runtime and can be fuzzed on
 * the host. A returned name is still NOT a sufficient condition to hook: the
 * caller must separately validate the mapped executable function and ABI.
 */
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

static inline bool ld_elf_named_range(size_t total, uint64_t off, uint64_t size)
{
    return off <= (uint64_t)total && size <= (uint64_t)total - off;
}

static bool ld_elf64_find_named_sym_in_mem(const uint8_t *elf, size_t elf_len,
        const char *want, uintptr_t *out_vaddr, size_t *out_size)
{
    if (!elf || elf_len < 64 || !want || !*want || !out_vaddr || !out_size)
        return false;
    *out_vaddr = 0;
    *out_size = 0;
    static const uint8_t magic[4] = {0x7f, 'E', 'L', 'F'};
    if (memcmp(elf, magic, 4) || elf[4] != 2u || elf[5] != 1u || elf[6] != 1u)
        return false;

    uint64_t shoff = 0;
    uint16_t shentsize = 0, shnum = 0;
    memcpy(&shoff, elf + 0x28, 8);
    memcpy(&shentsize, elf + 0x3a, 2);
    memcpy(&shnum, elf + 0x3c, 2);
    if (!shoff || shentsize < 64u || shnum == 0u || shnum > 4096u ||
        !ld_elf_named_range(elf_len, shoff, (uint64_t)shentsize * shnum))
        return false;

    unsigned matches = 0;
    uintptr_t vaddr = 0;
    size_t fsize = 0;
    for (uint16_t i = 0; i < shnum; ++i) {
        const uint8_t *sh = elf + (size_t)shoff + (size_t)i * shentsize;
        uint32_t sh_type = 0, sh_link = 0;
        uint64_t sh_offset = 0, sh_size = 0, sh_entsize = 0;
        memcpy(&sh_type, sh + 4, 4);
        if (sh_type != 2u) continue; /* SHT_SYMTAB */
        memcpy(&sh_offset, sh + 24, 8);
        memcpy(&sh_size, sh + 32, 8);
        memcpy(&sh_link, sh + 40, 4);
        memcpy(&sh_entsize, sh + 56, 8);
        if (sh_link >= shnum || sh_entsize != 24u ||
            sh_size % sh_entsize != 0 ||
            !ld_elf_named_range(elf_len, sh_offset, sh_size))
            return false;

        const uint8_t *str = elf + (size_t)shoff + (size_t)sh_link * shentsize;
        uint32_t str_type = 0;
        uint64_t str_off = 0, str_size = 0;
        memcpy(&str_type, str + 4, 4);
        memcpy(&str_off, str + 24, 8);
        memcpy(&str_size, str + 32, 8);
        if (str_type != 3u || !str_size ||
            !ld_elf_named_range(elf_len, str_off, str_size))
            return false;

        const char *strtab = (const char *)(elf + (size_t)str_off);
        for (uint64_t j = 0; j < sh_size / sh_entsize; ++j) {
            const uint8_t *sym = elf + (size_t)sh_offset + (size_t)j * 24u;
            uint32_t name_off = 0;
            uint16_t sec_idx = 0;
            uint64_t sym_value = 0, sym_size = 0;
            memcpy(&name_off, sym, 4);
            memcpy(&sec_idx, sym + 6, 2);
            if ((sym[4] & 15u) != 2u || sec_idx == 0) continue;
            memcpy(&sym_value, sym + 8, 8);
            memcpy(&sym_size, sym + 16, 8);
            if (!sym_value || name_off >= str_size) continue;
            const char *name = strtab + name_off;
            size_t available = (size_t)(str_size - name_off);
            if (!memchr(name, 0, available) || strcmp(name, want)) continue;
            if (++matches > 1) return false;
            if (sym_value > UINTPTR_MAX || sym_size > SIZE_MAX) return false;
            vaddr = (uintptr_t)sym_value;
            fsize = (size_t)sym_size;
        }
    }
    if (matches != 1u) return false;
    *out_vaddr = vaddr;
    *out_size = fsize;
    return true;
}
#endif
