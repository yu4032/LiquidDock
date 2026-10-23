#include "../native/glass_elf.h"
#include <stdio.h>
#include <string.h>

int main(void)
{
    unsigned char file[1024]={0};
    Elf64_Ehdr *h=(Elf64_Ehdr *)file;
    memcpy(h->e_ident,ELFMAG,SELFMAG);
    h->e_ident[EI_CLASS]=ELFCLASS64;
    h->e_ident[EI_DATA]=ELFDATA2LSB;
    h->e_shoff=128; h->e_shnum=3; h->e_shentsize=sizeof(Elf64_Shdr);
    Elf64_Shdr *sh=(Elf64_Shdr *)(file+128);
    sh[1].sh_type=SHT_SYMTAB; sh[1].sh_offset=400;
    sh[1].sh_size=2*sizeof(Elf64_Sym);
    sh[1].sh_entsize=sizeof(Elf64_Sym); sh[1].sh_link=2;
    sh[2].sh_type=SHT_STRTAB; sh[2].sh_offset=512; sh[2].sh_size=64;
    const char *name="BlurUtilities.isGlassMaterialSupported";
    memcpy(file+513,name,strlen(name)+1);
    Elf64_Sym *syms=(Elf64_Sym *)(file+400);
    syms[1].st_name=1; syms[1].st_value=0xa800; syms[1].st_size=88;
    syms[1].st_shndx=1; syms[1].st_info=ELF64_ST_INFO(STB_GLOBAL,STT_FUNC);
    uint64_t addr=0,size=0;
    if(!ld_os4_glass_symbol(file,sizeof file,name,&addr,&size)
            || addr!=0xa800 || size!=88) return 1;
    if(ld_os4_glass_symbol(file,sizeof file,"unknown",&addr,&size)) return 2;
    sh[1].sh_size=3*sizeof(Elf64_Sym);
    syms[2]=syms[1];
    if(ld_os4_glass_symbol(file,sizeof file,name,&addr,&size)) return 3;
    sh[1].sh_size=2*sizeof(Elf64_Sym);
    sh[1].sh_offset=sizeof file-8;
    if(ld_os4_glass_symbol(file,sizeof file,name,&addr,&size)) return 4;
    sh[1].sh_offset=400;
    h->e_shoff=sizeof file-16;
    if(ld_os4_glass_symbol(file,sizeof file,name,&addr,&size)) return 5;
    puts("PASS: OS4 glass ELF symbol binding unique and fail-closed");
    return 0;
}
