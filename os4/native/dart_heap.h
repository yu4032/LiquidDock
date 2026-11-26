#ifndef LD_DART_HEAP_H
#define LD_DART_HEAP_H
#include <dlfcn.h>
#include <sched.h>
#include <stdio.h>
#include <string.h>
#include <sys/uio.h>
#include <sys/syscall.h>
#include <unistd.h>
#include "dart_observer.h"
static int ld_read_heap(uintptr_t address, void *out, size_t size)
{
    if (address < 4096) return 0;
    struct iovec local = {out, size}, remote = {(void *)address, size};
    return syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0)==(ssize_t)size;
}
static uint32_t ld_heap_u32(uintptr_t object, size_t offset)
{
    uint32_t value=0; ld_read_heap(object+offset,&value,sizeof value); return value;
}
static int64_t ld_heap_i64(uintptr_t object,size_t offset)
{
    int64_t value=-999; ld_read_heap(object+offset,&value,sizeof value); return value;
}
static uintptr_t ld_heap_pointer(const struct ld_dart_context *c,uintptr_t object,size_t offset)
{
    uint32_t value=ld_heap_u32(object,offset);
    if(!(value&1) || value==(uint32_t)c->x[22]) return 0;
    return value+(c->x[28]<<32);
}

static uintptr_t g_ld_dart_ro_start[16];
static uintptr_t g_ld_dart_ro_end[16];
static int g_ld_dart_ro_count;
static int g_ld_dart_ro_state; /* 0=uninit, 1=init, 2=ready */

static inline void ld_dart_init_ro_ranges(void)
{
    int st = __atomic_load_n(&g_ld_dart_ro_state, __ATOMIC_ACQUIRE);
    if (st == 2) return;
    int expected = 0;
    if (!__atomic_compare_exchange_n(&g_ld_dart_ro_state, &expected, 1,
                                      0, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) {
        for (int spin = 0; spin < 1000 && __atomic_load_n(&g_ld_dart_ro_state, __ATOMIC_ACQUIRE) != 2; spin++) {
            sched_yield();
        }
        return;
    }
    FILE *fp = fopen("/proc/self/maps", "r");
    int count = 0;
    if (fp) {
        char line[512];
        while (fgets(line, sizeof line, fp) && count < 16) {
            if (!strstr(line, "[anon:dart-heap]")) continue;
            unsigned long start = 0, end = 0;
            char perms[8] = {0};
            if (sscanf(line, "%lx-%lx %7s", &start, &end, perms) == 3 &&
                start < end && perms[1] != 'w') {
                g_ld_dart_ro_start[count] = (uintptr_t)start;
                g_ld_dart_ro_end[count] = (uintptr_t)end;
                count++;
            }
        }
        fclose(fp);
    }
    __atomic_store_n(&g_ld_dart_ro_count, count, __ATOMIC_RELEASE);
    __atomic_store_n(&g_ld_dart_ro_state, 2, __ATOMIC_RELEASE);
}

static inline int ld_dart_is_ro_addr(uintptr_t addr)
{
    if (addr < 4096u) return 1;
    Dl_info di;
    if (dladdr((const void *)addr, &di) != 0) return 1;
    ld_dart_init_ro_ranges();
    int n = __atomic_load_n(&g_ld_dart_ro_count, __ATOMIC_ACQUIRE);
    for (int i = 0; i < n; i++) {
        if (addr >= g_ld_dart_ro_start[i] && addr < g_ld_dart_ro_end[i]) return 1;
    }
    return 0;
}
#endif
