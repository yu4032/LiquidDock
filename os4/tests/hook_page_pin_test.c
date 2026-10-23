#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#ifdef LD_PIN_RED
static int ld_pin_code_pages(uintptr_t address,size_t size) { (void)address; (void)size; return 1; }
#else
#include "../native/hook_page_pin.h"
#endif
int main(void) {
 long page=sysconf(_SC_PAGESIZE); assert(page>0);
 FILE *f=tmpfile(); assert(f); int fd=fileno(f);
 unsigned char *original=malloc((size_t)page*2); assert(original);
 memset(original,0x11,(size_t)page*2);
 assert(fwrite(original,(size_t)page*2,1,f)==1 && !fflush(f));
 unsigned char *map=mmap(NULL,(size_t)page*2,PROT_READ|PROT_WRITE,MAP_PRIVATE,fd,0); assert(map!=MAP_FAILED);
 unsigned char *site=map+page-8; unsigned char patch[16]; memset(patch,0x77,sizeof patch);
 memcpy(site,patch,sizeof patch); assert(!mprotect(map,(size_t)page*2,PROT_READ|PROT_EXEC));
 assert(!madvise(map,(size_t)page*2,MADV_DONTNEED));
 assert(memcmp(site,patch,sizeof patch)); /* Reproduce a discarded inline hook. */
 assert(!mprotect(map,(size_t)page*2,PROT_READ|PROT_WRITE)); memcpy(site,patch,sizeof patch);
 assert(!mprotect(map,(size_t)page*2,PROT_READ|PROT_EXEC));
 assert(ld_pin_code_pages((uintptr_t)site,sizeof patch));
 (void)madvise(map,(size_t)page*2,MADV_DONTNEED);
 assert(!memcmp(site,patch,sizeof patch));
#ifndef LD_PIN_RED
 assert(!ld_pin_code_pages(UINTPTR_MAX-3,16));
 assert(!ld_pin_code_pages((uintptr_t)map,0));
#endif
 assert(!munlock(map,(size_t)page*2)); assert(!munmap(map,(size_t)page*2));
 fclose(f); free(original);
 puts("PASS inline hook survives file-page discard, including a page boundary");
}
