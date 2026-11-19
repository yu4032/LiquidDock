#ifndef LD_MAML_ELF_SYMBOL_H
#define LD_MAML_ELF_SYMBOL_H

#include <elf.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static int ld_maml_elf_range_fits(uint64_t offset, uint64_t size, uint64_t file_size)
{
    return offset <= file_size && size <= file_size - offset;
}

static int ld_maml_strict_target_allowed(int exported_function_verified,
                                         int body_readable, int body_matches_pin)
{
    return exported_function_verified || (body_readable && body_matches_pin);
}

/* Resolve one unique exported AArch64 function from a 64-bit little-endian ET_DYN ELF.
 * The owning section must be executable and contain the complete symbol range. */
static int ld_maml_elf_resolve_function(FILE *file, const char *name,
                                        uintptr_t *value_out, size_t *size_out)
{
    if (value_out) *value_out = 0;
    if (size_out) *size_out = 0;
    if (!file || !name || !*name || !value_out || !size_out) return 0;

    struct stat st;
    if (fstat(fileno(file), &st) != 0 || st.st_size < (off_t)sizeof(Elf64_Ehdr)) return 0;
    const uint64_t file_size = (uint64_t)st.st_size;

    Elf64_Ehdr eh;
    if (fseeko(file, 0, SEEK_SET) != 0 || fread(&eh, 1, sizeof(eh), file) != sizeof(eh)) return 0;
    if (memcmp(eh.e_ident, ELFMAG, SELFMAG) != 0 ||
        eh.e_ident[EI_CLASS] != ELFCLASS64 || eh.e_ident[EI_DATA] != ELFDATA2LSB ||
        eh.e_ident[EI_VERSION] != EV_CURRENT || eh.e_type != ET_DYN ||
        eh.e_machine != EM_AARCH64 || eh.e_version != EV_CURRENT ||
        eh.e_shnum == 0 || eh.e_shnum > 512 || eh.e_shentsize < sizeof(Elf64_Shdr)) return 0;

    const uint64_t section_bytes = (uint64_t)eh.e_shentsize * eh.e_shnum;
    if (!ld_maml_elf_range_fits(eh.e_shoff, section_bytes, file_size)) return 0;
    unsigned char *sections = (unsigned char *)malloc((size_t)section_bytes);
    if (!sections) return 0;
    if (fseeko(file, (off_t)eh.e_shoff, SEEK_SET) != 0 ||
        fread(sections, 1, (size_t)section_bytes, file) != (size_t)section_bytes) {
        free(sections);
        return 0;
    }

    uintptr_t resolved_value = 0;
    size_t resolved_size = 0;
    unsigned matches = 0;
    for (uint16_t i = 0; i < eh.e_shnum; ++i) {
        Elf64_Shdr symtab;
        memcpy(&symtab, sections + (size_t)i * eh.e_shentsize, sizeof(symtab));
        if (symtab.sh_type != SHT_DYNSYM || symtab.sh_link >= eh.e_shnum ||
            symtab.sh_entsize < sizeof(Elf64_Sym) || symtab.sh_size == 0 ||
            symtab.sh_size % symtab.sh_entsize != 0 || symtab.sh_size / symtab.sh_entsize > 200000 ||
            !ld_maml_elf_range_fits(symtab.sh_offset, symtab.sh_size, file_size)) continue;

        Elf64_Shdr strtab;
        memcpy(&strtab, sections + (size_t)symtab.sh_link * eh.e_shentsize, sizeof(strtab));
        if (strtab.sh_type != SHT_STRTAB || strtab.sh_size == 0 ||
            strtab.sh_size > 16u * 1024u * 1024u ||
            !ld_maml_elf_range_fits(strtab.sh_offset, strtab.sh_size, file_size)) continue;

        unsigned char *symbols = (unsigned char *)malloc((size_t)symtab.sh_size);
        char *strings = (char *)malloc((size_t)strtab.sh_size);
        if (!symbols || !strings) {
            free(symbols);
            free(strings);
            continue;
        }
        int read_ok = fseeko(file, (off_t)symtab.sh_offset, SEEK_SET) == 0 &&
            fread(symbols, 1, (size_t)symtab.sh_size, file) == (size_t)symtab.sh_size &&
            fseeko(file, (off_t)strtab.sh_offset, SEEK_SET) == 0 &&
            fread(strings, 1, (size_t)strtab.sh_size, file) == (size_t)strtab.sh_size;
        if (read_ok) {
            const uint64_t count = symtab.sh_size / symtab.sh_entsize;
            for (uint64_t k = 0; k < count; ++k) {
                Elf64_Sym sym;
                memcpy(&sym, symbols + (size_t)k * (size_t)symtab.sh_entsize, sizeof(sym));
                if (sym.st_name == 0 || sym.st_name >= strtab.sh_size ||
                    !memchr(strings + sym.st_name, '\0', (size_t)(strtab.sh_size - sym.st_name)) ||
                    strcmp(strings + sym.st_name, name) != 0 ||
                    ELF64_ST_TYPE(sym.st_info) != STT_FUNC ||
                    (ELF64_ST_BIND(sym.st_info) != STB_GLOBAL && ELF64_ST_BIND(sym.st_info) != STB_WEAK) ||
                    sym.st_shndx == SHN_UNDEF || sym.st_shndx >= eh.e_shnum ||
                    sym.st_size == 0 || sym.st_size > SIZE_MAX || sym.st_value > UINTPTR_MAX) continue;

                Elf64_Shdr code;
                memcpy(&code, sections + (size_t)sym.st_shndx * eh.e_shentsize, sizeof(code));
                if ((code.sh_flags & SHF_EXECINSTR) == 0 || sym.st_value < code.sh_addr) continue;
                uint64_t delta = sym.st_value - code.sh_addr;
                if (delta > code.sh_size || sym.st_size > code.sh_size - delta) continue;

                matches++;
                resolved_value = (uintptr_t)sym.st_value;
                resolved_size = (size_t)sym.st_size;
            }
        }
        free(symbols);
        free(strings);
    }
    free(sections);

    if (matches != 1) return 0;
    *value_out = resolved_value;
    *size_out = resolved_size;
    return 1;
}

#endif
