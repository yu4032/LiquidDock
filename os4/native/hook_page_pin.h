#ifndef LD_HOOK_PAGE_PIN_H
#define LD_HOOK_PAGE_PIN_H
#include <stdint.h>
#include <stddef.h>
#include <sys/mman.h>
#include <unistd.h>
/* Inline hooks modify MAP_PRIVATE file-backed executable pages. Discarding
 * those pages restores the APK bytes and silently removes the hooks. Keep only
 * the pages containing each 16-byte entry patch resident for this process.
 * Linux rejects MADV_DONTNEED on VM_LOCKED pages; normal unmapping at process
 * exit releases the lock. The fuxi process limit is 64 MiB, well above this set. */
static inline int ld_pin_code_pages(uintptr_t address,size_t size) {
    long raw=sysconf(_SC_PAGESIZE);
    if(raw<=0 || !size || address>UINTPTR_MAX-size) return 0;
    uintptr_t page=(uintptr_t)raw;
    if(page&(page-1)) return 0;
    uintptr_t start=address&~(page-1),last=(address+size-1)|(page-1);
    if(last==UINTPTR_MAX) return 0;
    return mlock((void *)start,(size_t)(last+1-start))==0;
}
#endif
