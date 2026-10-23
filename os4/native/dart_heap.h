#ifndef LD_DART_HEAP_H
#define LD_DART_HEAP_H
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
static uintptr_t ld_heap_pointer(struct ld_dart_context *c,uintptr_t object,size_t offset)
{
    uint32_t value=ld_heap_u32(object,offset);
    if(!(value&1) || value==(uint32_t)c->x[22]) return 0;
    return value+(c->x[28]<<32);
}
#endif
