#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "../native/elf_named_symbol.h"

/* Tiny ELF64 little-endian mini-ELF with a real SHT_SYMTAB and SHT_STRTAB. */
static uint8_t elf[1024];
static void wr16(size_t p, uint16_t n) { memcpy(elf + p, &n, 2); }
static void wr32(size_t p, uint32_t n) { memcpy(elf + p, &n, 4); }
static void wr64(size_t p, uint64_t n) { memcpy(elf + p, &n, 8); }
static void fixture(void)
{
    memset(elf, 0, sizeof elf);
    elf[0]=0x7f; elf[1]='E'; elf[2]='L'; elf[3]='F';
    elf[4]=2; elf[5]=1; elf[6]=1;
    wr64(0x28, 0x200); /* section headers */
    wr16(0x3a, 64);
    wr16(0x3c, 3);
    /* section[1] = symbol table, 2 entries */
    wr32(0x200+64+4, 2);
    wr64(0x200+64+24, 0x40);
    wr64(0x200+64+32, 48);
    wr32(0x200+64+40, 2);
    wr64(0x200+64+56, 24);
    /* section[2] = string table */
    wr32(0x200+128+4, 3);
    wr64(0x200+128+24, 0x180);
    static const char name[] = "\0RotationHelper.updateRotation\0";
    memcpy(elf + 0x180, name, sizeof name);
    wr64(0x200+128+32, sizeof name);
    /* symbol[1] = defined function */
    size_t sym=0x40+24;
    wr32(sym, 1);
    elf[sym+4]=2;
    wr16(sym+6, 1);
    wr64(sym+8, 0xa1ef0c);
    wr64(sym+16, 512);
}
int main(void)
{
    uintptr_t addr=0; size_t len=0;
    fixture();
    assert(ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.updateRotation",&addr,&len));
    assert(addr==0xa1ef0c && len==512);
    assert(!ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.refreshDisplayRotation",&addr,&len));
    /* A second identically named symbol is not an unambiguous hook target. */
    fixture();
    memcpy(elf+0x40+48,elf+0x40+24,24);
    wr64(0x200+64+32,72);
    assert(!ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.updateRotation",&addr,&len));
    fixture(); wr64(0x28,UINT64_MAX);
    assert(!ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.updateRotation",&addr,&len));
    fixture(); wr64(0x200+64+24,UINT64_MAX);
    assert(!ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.updateRotation",&addr,&len));
    fixture(); wr64(0x200+128+32,UINT64_MAX);
    assert(!ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.updateRotation",&addr,&len));
    fixture(); wr64(0x200+64+56,16);
    assert(!ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.updateRotation",&addr,&len));
    fixture(); elf[0x40+24+4]=1;
    assert(!ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.updateRotation",&addr,&len));
    fixture(); wr16(0x40+24+6,0);
    assert(!ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.updateRotation",&addr,&len));
    fixture(); elf[4]=1;
    assert(!ld_elf64_find_named_sym_in_mem(elf,sizeof elf,
            "RotationHelper.updateRotation",&addr,&len));
    puts("PASS: ELF named rotation symbol uniqueness, bounds and type");
}
