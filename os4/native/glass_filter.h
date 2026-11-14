#ifndef LD_GLASS_FILTER_H
#define LD_GLASS_FILTER_H
#include <math.h>
#include <string.h>
#include <dlfcn.h>
#include "native_glass_apply.h"
#include "native_glass_fingerprints.h"
static void *g_orig_glass_filter __attribute__((used));
static int g_native_glass_engine_ready;
static __thread int g_ld_active_glass_scope;
static void ld_prepare_native_glass(void)
{
    static const unsigned char expected[32]={0x77,0xa3,0x90,0x1d,0x9d,0xf4,0x1d,0x0d,0x7d,0xb4,0x1a,0x09,0x25,0x0e,0xa1,0x62,0x70,0x52,0x22,0x5b,0xf8,0x66,0x1d,0x0c,0x99,0x2d,0x4d,0x66,0x8c,0xc1,0x5e,0x2b};
    const char *path="/system_ext/lib64/libhyper_os_flutter.so";
    unsigned char digest[32];struct stat st;
    g_native_glass_engine_ready=!g_home_options.hook_safety_enabled ||
        (stat(path,&st)==0 && st.st_size==18882704
        && ld_sha256_region(path,0,18882704,digest) && !memcmp(digest,expected,32));
    ld_blog("native glass engine ready=%d safety=%d; sparse overrides=%llx; stock shaders/capture",
        g_native_glass_engine_ready,g_home_options.hook_safety_enabled,(unsigned long long)g_home_options.native_glass.mask);
}
static int ld_native_glass_write(const uintptr_t *addresses,const float *values,size_t count)
{
    struct iovec local[LD_NATIVE_GLASS_COUNT],remote[LD_NATIVE_GLASS_COUNT];
    for(size_t i=0;i<count;i++){local[i]=(struct iovec){(void *)(values+i),4};remote[i]=(struct iovec){(void *)addresses[i],4};}
    return syscall(SYS_process_vm_writev,getpid(),local,count,remote,count,0)==(ssize_t)(count*4);
}
__attribute__((used)) static void ld_observe_native_glass(struct ld_dart_context *c)
{
    int scope = g_ld_active_glass_scope;
    g_ld_active_glass_scope = LD_GLASS_SCOPE_GLOBAL;
    if(!g_hook_pin_ok || !g_home_options.glass_enabled || !g_native_glass_engine_ready) return;
    const struct ld_native_glass_options *opts = &g_home_options.native_glass;
    if(scope > LD_GLASS_SCOPE_GLOBAL && scope < LD_GLASS_SCOPE_COUNT
            && g_home_options.scoped_glass[scope].mask) {
        opts = &g_home_options.scoped_glass[scope];
    }
    if(!opts->mask) return;
    int strict=g_home_options.hook_safety_enabled;
    uintptr_t holder=0,filter=0,vt=0;
    if(!ld_read_heap(c->x[29]-0x70,&holder,8) || !ld_read_heap(holder+0x30,&filter,8) || !ld_read_heap(filter,&vt,8)) return;
    Dl_info info={0};
    if(!dladdr((void *)vt,&info) || (strict && (vt<(uintptr_t)info.dli_fbase
            || vt-(uintptr_t)info.dli_fbase!=0x1190e98))) return;
    static uintptr_t verified_base;
    uintptr_t base=(uintptr_t)info.dli_fbase;
    if(strict && __atomic_load_n(&verified_base,__ATOMIC_ACQUIRE)!=base) {
        unsigned char live[sizeof ld_native_glass_engine_words];
        if(!ld_read_heap(base+LD_NATIVE_GLASS_ENGINE_VADDR,live,sizeof live) || memcmp(live,ld_native_glass_engine_words,sizeof live)) return;
        __atomic_store_n(&verified_base,base,__ATOMIC_RELEASE);
    }
    int result=ld_native_glass_apply(filter,opts,ld_read_heap,ld_native_glass_write);
    static unsigned reported;
    if(__atomic_fetch_add(&reported,1u,__ATOMIC_RELAXED)<3)ld_blog("native glass configured fields=%d",result);
}
#ifndef LD_GLASS_FILTER_NO_BRIDGE
LD_DART_OBSERVER(repl_glass_filter,ld_observe_native_glass,g_orig_glass_filter)
#endif
#endif
