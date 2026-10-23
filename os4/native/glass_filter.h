#ifndef LD_GLASS_FILTER_H
#define LD_GLASS_FILTER_H
#include <math.h>
#include <string.h>
#include <dlfcn.h>
#include "native_glass_apply.h"
#include "native_glass_fingerprints.h"
static void *g_orig_glass_filter __attribute__((used));
static int g_native_glass_engine_verified;
static void ld_prepare_native_glass(void)
{
    static const unsigned char expected[32]={0x7f,0x88,0xd7,0xf4,0xd9,0xa4,0x64,0xfd,0xd5,0x6f,0xb2,0x55,0x64,0x2c,0x9d,0x30,0x0f,0x28,0xf5,0x02,0x72,0xcc,0xf5,0xfd,0x31,0xf0,0x82,0xc1,0xa5,0x2e,0x17,0xf4};
    const char *path="/system_ext/lib64/libhyper_os_flutter.so";
    unsigned char digest[32];struct stat st;
    g_native_glass_engine_verified=stat(path,&st)==0 && st.st_size==18815256
        && ld_sha256_region(path,0,18815256,digest) && !memcmp(digest,expected,32);
    ld_blog("native glass engine verified=%d; sparse overrides=%llx; stock shaders/capture",g_native_glass_engine_verified,(unsigned long long)g_home_options.native_glass.mask);
}
static int ld_native_glass_write(const uintptr_t *addresses,const float *values,size_t count)
{
    struct iovec local[LD_NATIVE_GLASS_COUNT],remote[LD_NATIVE_GLASS_COUNT];
    for(size_t i=0;i<count;i++){local[i]=(struct iovec){(void *)(values+i),4};remote[i]=(struct iovec){(void *)addresses[i],4};}
    return syscall(SYS_process_vm_writev,getpid(),local,count,remote,count,0)==(ssize_t)(count*4);
}
__attribute__((used)) static void ld_observe_native_glass(struct ld_dart_context *c)
{
    if(!g_hook_pin_ok || !g_home_options.glass_enabled || !g_native_glass_engine_verified || !g_home_options.native_glass.mask) return;
    uintptr_t holder=0,filter=0,vt=0;
    if(!ld_read_heap(c->x[29]-0x70,&holder,8) || !ld_read_heap(holder+0x30,&filter,8) || !ld_read_heap(filter,&vt,8)) return;
    Dl_info info={0};
    if(!dladdr((void *)vt,&info) || vt-(uintptr_t)info.dli_fbase!=0x1180c08) return;
    static uintptr_t verified_base;
    uintptr_t base=(uintptr_t)info.dli_fbase;
    if(__atomic_load_n(&verified_base,__ATOMIC_ACQUIRE)!=base) {
        unsigned char live[sizeof ld_native_glass_engine_words];
        if(!ld_read_heap(base+0xe878c4,live,sizeof live) || memcmp(live,ld_native_glass_engine_words,sizeof live)) return;
        __atomic_store_n(&verified_base,base,__ATOMIC_RELEASE);
    }
    int result=ld_native_glass_apply(filter,&g_home_options.native_glass,ld_read_heap,ld_native_glass_write);
    static unsigned reported;
    if(__atomic_fetch_add(&reported,1u,__ATOMIC_RELAXED)<3)ld_blog("native glass configured fields=%d",result);
}
#ifndef LD_GLASS_FILTER_NO_BRIDGE
LD_DART_OBSERVER(repl_glass_filter,ld_observe_native_glass,g_orig_glass_filter)
#endif
#endif
