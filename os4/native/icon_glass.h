#ifndef LD_ICON_GLASS_H
#define LD_ICON_GLASS_H

#include "dart_heap.h"
#include "icon_glass_fingerprints.h"
#include "sha256_region.h"

#include <math.h>
#include <sys/uio.h>
#include <sys/syscall.h>
#include <unistd.h>

static void *g_orig_icon_glass_enabled;
static void *g_orig_icon_glass_size;
static void *g_orig_icon_glass_radius;
static int g_icon_glass_installed;
static int g_icon_glass_radius_installed;
static void *g_orig_icon_glass_flight;
static volatile unsigned g_icon_glass_flag_calls;
static volatile unsigned g_icon_glass_radius_calls;
static int g_icon_glass_state_logged;

/* Dart's local Hero flight carries the native glass background with the icon.
 * The SF bitmap flight only contains the resource image. Select local mode at
 * capability initialization, so native source hiding and completion remain
 * paired; changing canUseSfMode after driver setup produces duplicate icons.
 * Installed only for enabled icon glass. The getter caches this result for
 * the Launcher process, matching the restart required by material settings. */
__attribute__((used,naked)) static void repl_icon_glass_local_flight(void)
{
    __asm__ volatile("add x0,x22,#0x30\nret\n");
}

static int ld_icon_body_matches(uintptr_t base,uintptr_t start,size_t size,const unsigned char expected[32])
{
    unsigned char body[1024],digest[32];
    if(size>sizeof body || !ld_read_heap(base+start,body,size)) return 0;
    struct ld_sha256 sha; ld_sha256_init(&sha); ld_sha256_update(&sha,body,size); ld_sha256_final(&sha,digest);
    return memcmp(digest,expected,sizeof digest)==0;
}

static uintptr_t ld_icon_decode_compressed(struct ld_dart_context *c,uint32_t value)
{
    if(!(value&1) || value==(uint32_t)c->x[22]) return 0;
    return (uintptr_t)value+(c->x[28]<<32);
}

static uint32_t ld_icon_owner_class_id(uintptr_t object)
{
    uint64_t header=0;
    if(!object || object<1 || !ld_read_heap(object-1,&header,sizeof header)) return UINT32_MAX;
    return (uint32_t)((header>>12)&0xfffff);
}

__attribute__((used)) static void ld_icon_force_folder_flag(struct ld_dart_context *c)
{
    if(!g_hook_pin_ok || !g_icon_glass_installed || !g_home_options.glass_enabled || !g_home_options.glass_icons) return;
    uint32_t ref=0;
    if(!ld_read_heap((uintptr_t)c->x[29]-8,&ref,sizeof ref)) return;
    uintptr_t context=ld_icon_decode_compressed(c,ref);
    uintptr_t owner=ld_heap_pointer(c,context,0x0f);
    uint32_t cid=ld_icon_owner_class_id(owner);
    unsigned call=__atomic_add_fetch(&g_icon_glass_flag_calls,1,__ATOMIC_RELAXED);
    if(call<=4) ld_blog("icon glass flag callback call=%u context=%llx owner=%llx cid=%u expected=8306",
            call,(unsigned long long)context,(unsigned long long)owner,cid);
    if(cid!=8306) return;
    /* x0 is the boolean returned at this call boundary; do not mutate heap. */
    c->x[0]=c->x[22]+0x20u;
    if(call<=4) ld_blog("icon glass flag applied to Launcher class id 8306");
}

__attribute__((used)) static void ld_icon_adjust_native_size(struct ld_dart_context *c)
{
    if(!g_hook_pin_ok || !g_icon_glass_installed || !g_home_options.glass_enabled || !g_home_options.glass_icons
            || !isfinite(g_home_options.glass_icon_size) || g_home_options.glass_icon_size==0.0) return;
    uint32_t ref=0;
    if(!ld_read_heap((uintptr_t)c->x[29]-8,&ref,sizeof ref)) return;
    uintptr_t context=ld_icon_decode_compressed(c,ref);
    uintptr_t owner=ld_heap_pointer(c,context,0x0f);
    if(ld_icon_owner_class_id(owner)!=8306) return;
    /* At this exact instruction boundary the next original instructions reload
     * d1/d0 from these two fp slots. Patch the source doubles so the trampoline
     * cannot overwrite the adjustment with its replayed loads. */
    uintptr_t frame=(uintptr_t)c->x[29];
    double width=0,height=0,delta=2.0*g_home_options.glass_icon_size;
    if(!ld_read_heap(frame-0x40,&width,sizeof width)
            || !ld_read_heap(frame-0x48,&height,sizeof height)) return;
    if(!isfinite(width)||!isfinite(height)||!isfinite(delta)) return;
    width+=delta; height+=delta;
    if(width<=0.0 || height<=0.0 || !isfinite(width) || !isfinite(height)) return;
    struct iovec local[2]={{&width,sizeof width},{&height,sizeof height}};
    struct iovec remote[2]={{(void *)(frame-0x40),sizeof width},{(void *)(frame-0x48),sizeof height}};
    (void)syscall(SYS_process_vm_writev,getpid(),local,2,remote,2,0);
}

__attribute__((used)) static void ld_icon_adjust_radius(struct ld_dart_context *c)
{
    if(!g_hook_pin_ok || !g_icon_glass_radius_installed || !g_home_options.glass_enabled || !g_home_options.glass_icons
            || !isfinite(g_home_options.glass_icon_corner) || g_home_options.glass_icon_corner<=0.0) return;
    /* This site is inside the existing background closure. The saved Dart
     * context is retained by the observer; only its scalar result is changed. */
    uint32_t ref=0;
    if(!ld_read_heap((uintptr_t)c->x[29]-0x10,&ref,sizeof ref)) return;
    uintptr_t new_context=ld_icon_decode_compressed(c,ref);
    if(!new_context) return;
    uintptr_t original_context=ld_heap_pointer(c,new_context,0x0b);
    uintptr_t owner=ld_heap_pointer(c,original_context,0x0f);
    uint32_t cid=ld_icon_owner_class_id(owner);
    unsigned call=__atomic_add_fetch(&g_icon_glass_radius_calls,1,__ATOMIC_RELAXED);
    if(call<=4) ld_blog("icon glass radius callback call=%u context=%llx original=%llx owner=%llx cid=%u expected=8306",
            call,(unsigned long long)new_context,(unsigned long long)original_context,
            (unsigned long long)owner,cid);
    if(cid!=8306) return;
    double radius=g_home_options.glass_icon_corner;
    memcpy(c->q[0],&radius,sizeof radius);
    if(call<=4) ld_blog("icon glass radius applied %.1f to Launcher class id 8306",radius);
}

LD_DART_OBSERVER(repl_icon_glass_enabled,ld_icon_force_folder_flag,g_orig_icon_glass_enabled)
LD_DART_OBSERVER(repl_icon_glass_size,ld_icon_adjust_native_size,g_orig_icon_glass_size)
LD_DART_OBSERVER(repl_icon_glass_radius,ld_icon_adjust_radius,g_orig_icon_glass_radius)

#endif
