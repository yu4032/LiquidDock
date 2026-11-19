#include "maml_elf_symbol.h"

#include <stdio.h>

static FILE *make_fixture(uint16_t machine, int executable)
{
    enum { SECTION_COUNT = 4, DYNSTR_OFFSET = 344, TEXT_OFFSET = 384, FILE_SIZE = 400 };
    unsigned char bytes[FILE_SIZE] = {0};
    Elf64_Ehdr eh = {0};
    memcpy(eh.e_ident, ELFMAG, SELFMAG);
    eh.e_ident[EI_CLASS] = ELFCLASS64;
    eh.e_ident[EI_DATA] = ELFDATA2LSB;
    eh.e_ident[EI_VERSION] = EV_CURRENT;
    eh.e_type = ET_DYN;
    eh.e_machine = machine;
    eh.e_version = EV_CURRENT;
    eh.e_ehsize = sizeof(eh);
    eh.e_shoff = sizeof(eh);
    eh.e_shentsize = sizeof(Elf64_Shdr);
    eh.e_shnum = SECTION_COUNT;
    memcpy(bytes, &eh, sizeof(eh));

    Elf64_Shdr sections[SECTION_COUNT] = {0};
    sections[1].sh_type = SHT_DYNSYM;
    sections[1].sh_offset = sizeof(eh) + sizeof(sections);
    sections[1].sh_size = sizeof(Elf64_Sym);
    sections[1].sh_link = 2;
    sections[1].sh_entsize = sizeof(Elf64_Sym);
    sections[2].sh_type = SHT_STRTAB;
    sections[2].sh_offset = DYNSTR_OFFSET;
    sections[2].sh_size = sizeof("\0init_maml");
    sections[3].sh_type = SHT_PROGBITS;
    sections[3].sh_flags = SHF_ALLOC | (executable ? SHF_EXECINSTR : 0);
    sections[3].sh_addr = 0x1000;
    sections[3].sh_offset = TEXT_OFFSET;
    sections[3].sh_size = 16;
    memcpy(bytes + eh.e_shoff, sections, sizeof(sections));

    Elf64_Sym symbol = {0};
    symbol.st_name = 1;
    symbol.st_info = ELF64_ST_INFO(STB_GLOBAL, STT_FUNC);
    symbol.st_shndx = 3;
    symbol.st_value = 0x1000;
    symbol.st_size = 16;
    memcpy(bytes + sections[1].sh_offset, &symbol, sizeof(symbol));
    memcpy(bytes + DYNSTR_OFFSET, "\0init_maml", sizeof("\0init_maml"));

    FILE *file = tmpfile();
    if (!file || fwrite(bytes, 1, sizeof(bytes), file) != sizeof(bytes) || fflush(file) != 0) {
        if (file) fclose(file);
        return NULL;
    }
    rewind(file);
    return file;
}

int main(int argc, char **argv)
{
    if (!ld_maml_strict_target_allowed(1, 1, 0) ||
        !ld_maml_strict_target_allowed(1, 0, 0) ||
        !ld_maml_strict_target_allowed(0, 1, 1) ||
        ld_maml_strict_target_allowed(0, 1, 0) ||
        ld_maml_strict_target_allowed(0, 0, 0)) {
        puts("FAIL: strict target acceptance policy");
        return 1;
    }

    uintptr_t value = 0;
    size_t size = 0;
    FILE *valid = make_fixture(EM_AARCH64, 1);
    if (!valid || !ld_maml_elf_resolve_function(valid, "init_maml", &value, &size) ||
        value != 0x1000 || size != 16) {
        puts("FAIL: valid exported executable function was not resolved");
        if (valid) fclose(valid);
        return 1;
    }
    fclose(valid);

    FILE *non_executable = make_fixture(EM_AARCH64, 0);
    if (!non_executable || ld_maml_elf_resolve_function(non_executable, "init_maml", &value, &size)) {
        puts("FAIL: non-executable function symbol accepted");
        if (non_executable) fclose(non_executable);
        return 1;
    }
    fclose(non_executable);

    FILE *wrong_arch = make_fixture(EM_X86_64, 1);
    if (!wrong_arch || ld_maml_elf_resolve_function(wrong_arch, "init_maml", &value, &size)) {
        puts("FAIL: non-AArch64 ELF accepted");
        if (wrong_arch) fclose(wrong_arch);
        return 1;
    }
    fclose(wrong_arch);

    if (argc == 2) {
        FILE *sdk = fopen(argv[1], "rb");
        if (!sdk || !ld_maml_elf_resolve_function(sdk, "init_maml", &value, &size)) {
            puts("FAIL: actual MAML SDK init_maml symbol was not resolved");
            if (sdk) fclose(sdk);
            return 1;
        }
        fclose(sdk);
        printf("PASS: actual MAML SDK init_maml vaddr=0x%lx size=%zu\n",
               (unsigned long)value, size);
    } else if (argc != 1) {
        puts("usage: maml_elf_symbol_test [MAML-SDK-ELF]");
        return 2;
    }

    puts("PASS: MAML ELF symbol resolution validates architecture and executable range");
    return 0;
}
