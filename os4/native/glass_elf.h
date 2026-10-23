#ifndef LD_OS4_GLASS_ELF_H
#define LD_OS4_GLASS_ELF_H
#include <elf.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>

/* Locate uniquely named Dart AOT functions in the pinned 8583 ELF.
 * Do not reuse prior-build function addresses or accept ambiguous clones. */
static int ld_os4_glass_symbol(const void *image,size_t file_size,const char *name,
                               uint64_t *value,uint64_t *bytes)
{
    if(!image || !name || !*name || !value || !bytes
            || file_size<sizeof(Elf64_Ehdr)) return 0;
    const uint8_t *b=image;
    const Elf64_Ehdr *h=(const Elf64_Ehdr *)b;
    if(memcmp(h->e_ident,ELFMAG,SELFMAG)!=0
            || h->e_ident[EI_CLASS]!=ELFCLASS64
            || h->e_ident[EI_DATA]!=ELFDATA2LSB
            || h->e_shentsize!=sizeof(Elf64_Shdr) || !h->e_shnum
            || h->e_shoff>file_size
            || (uint64_t)h->e_shnum>(file_size-h->e_shoff)/sizeof(Elf64_Shdr))
        return 0;
    const Elf64_Shdr *sections=(const Elf64_Shdr *)(b+h->e_shoff);
    uint64_t found_value=0,found_size=0;
    unsigned matches=0;
    const size_t name_len=strlen(name);
    for(size_t k=0;k<h->e_shnum;k++) {
        const Elf64_Shdr *sym=&sections[k];
        if(sym->sh_type!=SHT_SYMTAB || sym->sh_link>=h->e_shnum
                || sym->sh_entsize!=sizeof(Elf64_Sym) || sym->sh_size==0
                || sym->sh_offset>file_size
                || sym->sh_size>file_size-sym->sh_offset) continue;
        const Elf64_Shdr *str=&sections[sym->sh_link];
        if(str->sh_type!=SHT_STRTAB || str->sh_offset>file_size
                || str->sh_size>file_size-str->sh_offset) continue;
        const char *names=(const char *)(b+str->sh_offset);
        const Elf64_Sym *symbols=(const Elf64_Sym *)(b+sym->sh_offset);
        for(size_t i=0;i<sym->sh_size/sizeof *symbols;i++) {
            const Elf64_Sym *v=&symbols[i];
            if(ELF64_ST_TYPE(v->st_info)!=STT_FUNC || v->st_shndx==SHN_UNDEF
                    || v->st_name>=str->sh_size || v->st_size<16
                    || v->st_size>2048 || (v->st_value&3)) continue;
            const char *label=names+v->st_name;
            const char *end=(const char *)memchr(label,0,str->sh_size-v->st_name);
            if(end && name_len==(size_t)(end-label) && !memcmp(label,name,name_len)) {
                found_value=v->st_value;
                found_size=v->st_size;
                if(++matches>1) return 0;
            }
        }
    }
    if(matches!=1) return 0;
    *value=found_value; *bytes=found_size;
    return 1;
}
#endif
