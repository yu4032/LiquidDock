#ifndef LD_ICON_GLASS_H
#define LD_ICON_GLASS_H

#include "dart_heap.h"
#include "icon_glass_fingerprints.h"
#include "sha256_region.h"

#include <math.h>
#include <sys/uio.h>
#include <sys/syscall.h>
#include <unistd.h>

__attribute__((used)) static void *g_orig_icon_glass_enabled;
__attribute__((used)) static void *g_orig_icon_glass_size;
__attribute__((used)) static void *g_orig_icon_glass_radius;
__attribute__((used)) static void *g_orig_icon_glass_radius_b;
__attribute__((used)) static int g_icon_glass_installed;
__attribute__((used)) static int g_icon_glass_radius_installed;
__attribute__((used)) static int g_icon_glass_container_patched;
__attribute__((used)) static void *g_orig_icon_glass_flight;
__attribute__((used)) static volatile unsigned g_icon_glass_flag_calls;
__attribute__((used)) static volatile unsigned g_icon_glass_radius_calls;
__attribute__((used)) static int g_icon_glass_state_logged;

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

static int __attribute__((unused)) ld_icon_body_matches(uintptr_t base,uintptr_t start,size_t size,const unsigned char expected[32])
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

/* Owner class of the workspace icon host. 8604 uses the same id as 8584; other owners
 * of the shared closures (e.g. cid 8308) are left untouched. Every distinct
 * (site, cid) pair is logged once. */
#define LD_ICON_GLASS_OWNER_CID 8306u
#define LD_ICON_GLASS_OWNER_CID2 8308u
#define LD_ICON_GLASS_OWNER_9239_A 8383u
#define LD_ICON_GLASS_OWNER_9239_B 8385u
#define LD_ICON_GLASS_OWNER_OK(cid) ((cid)==LD_ICON_GLASS_OWNER_CID || (cid)==LD_ICON_GLASS_OWNER_CID2 || \
                                     (cid)==LD_ICON_GLASS_OWNER_9239_A || (cid)==LD_ICON_GLASS_OWNER_9239_B)
static void ld_icon_note_cid(unsigned site,uint32_t cid)
{
    static uint32_t seen[4][8]; static unsigned count[4];
    if(site>=4) return;
    for(unsigned i=0;i<count[site] && i<8;i++) if(seen[site][i]==cid) return;
    unsigned slot=__atomic_fetch_add(&count[site],1u,__ATOMIC_RELAXED);
    if(slot<8) { seen[site][slot]=cid; ld_blog("icon glass owner site=%u cid=%u",site,cid); }
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
    ld_icon_note_cid(0,cid);
    uintptr_t orig_x0 = (uintptr_t)c->x[0];
    uintptr_t x22 = (uintptr_t)c->x[22];
    if(call<=10) {
        ld_blog("icon glass flag call=%u cid=%u orig_off=%#lx (x0=%#lx x22=%#lx)",
                call, cid, (unsigned long)(orig_x0 - x22), (unsigned long)orig_x0, (unsigned long)x22);
    }
    if(!LD_ICON_GLASS_OWNER_OK(cid)) return;
    /* x0 is the boolean returned at this call boundary; do not mutate heap. */
    c->x[0]=c->x[22]+0x20u;
}

__attribute__((used)) static void ld_icon_adjust_native_size(struct ld_dart_context *c)
{
    if(!g_hook_pin_ok || !g_icon_glass_installed || !g_home_options.glass_enabled || !g_home_options.glass_icons) return;
    uint32_t ref=0;
    if(!ld_read_heap((uintptr_t)c->x[29]-8,&ref,sizeof ref)) return;
    uintptr_t context=ld_icon_decode_compressed(c,ref);
    uintptr_t owner=ld_heap_pointer(c,context,0x0f);
    uint32_t size_cid=ld_icon_owner_class_id(owner);
    ld_icon_note_cid(1,size_cid);
    if(!LD_ICON_GLASS_OWNER_OK(size_cid)) return;

    uintptr_t frame=(uintptr_t)c->x[29];
    uint32_t flag_ref=0, ref113=0, ref10f=0;
    ld_read_heap(frame-0x10,&flag_ref,sizeof flag_ref);
    ld_read_heap(frame-0x18,&ref113,sizeof ref113);
    ld_read_heap(frame-0x28,&ref10f,sizeof ref10f);

    uintptr_t obj113=ld_icon_decode_compressed(c,ref113);
    uintptr_t obj10f=ld_icon_decode_compressed(c,ref10f);
    double d113=-999.0, d10f=-999.0;
    if(obj113) ld_read_heap(obj113+7,&d113,sizeof d113);
    if(obj10f) ld_read_heap(obj10f+7,&d10f,sizeof d10f);

    double width=0,height=0;
    ld_read_heap(frame-0x40,&width,sizeof width);
    ld_read_heap(frame-0x48,&height,sizeof height);

    static unsigned size_calls = 0;
    unsigned scall = __atomic_add_fetch(&size_calls, 1, __ATOMIC_RELAXED);
    if(scall <= 10 || ((d113 < 0.99 || d10f < 0.99) && scall % 20 == 0)) {
        ld_blog("icon glass size call=%u cid=%u flag_off=%#lx d113=%.3f d10f=%.3f w=%.1f h=%.1f",
                scall, size_cid, (unsigned long)((uintptr_t)flag_ref - (uintptr_t)c->x[22]),
                d113, d10f, width, height);
    }

    if(isfinite(g_home_options.glass_icon_size) && g_home_options.glass_icon_size!=0.0) {
        double delta=2.0*g_home_options.glass_icon_size;
        width+=delta; height+=delta;
        if(width>0.0 && height>0.0 && isfinite(width) && isfinite(height)) {
            struct iovec local[2]={{&width,sizeof width},{&height,sizeof height}};
            struct iovec remote[2]={{(void *)(frame-0x40),sizeof width},{(void *)(frame-0x48),sizeof height}};
            (void)syscall(SYS_process_vm_writev,getpid(),local,2,remote,2,0);
        }
    }
}

static void ld_icon_adjust_radius_site(struct ld_dart_context *c,unsigned site)
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
    ld_icon_note_cid(2+site,cid);
    if(!LD_ICON_GLASS_OWNER_OK(cid)) return;
    double radius=g_home_options.glass_icon_corner;
    memcpy(c->q[0],&radius,sizeof radius);
    if(call<=4) ld_blog("icon glass radius applied %.1f site=%u cid=%u",radius,site,cid);
}
__attribute__((used)) static void ld_icon_adjust_radius(struct ld_dart_context *c) { ld_icon_adjust_radius_site(c,0); }
__attribute__((used)) static void ld_icon_adjust_radius_b(struct ld_dart_context *c) { ld_icon_adjust_radius_site(c,1); }

static bool ld_resolve_so(const char *lib_substr, ld_lib_info *out)
{
    memset(out, 0, sizeof *out);
    ld_mapseg *segs = (ld_mapseg *)malloc(sizeof(ld_mapseg) * LD_MAX_SEGS);
    if (!segs) return false;
    int nsegs = ld_collect_segs(lib_substr, segs, LD_MAX_SEGS);
    if (nsegs <= 0) { free(segs); return false; }

    uintptr_t map_start = 0;
    size_t map_off = 0;
    if (!ld_seg_for_offset(segs, nsegs, 0, &map_start, &map_off)) {
        free(segs);
        return false;
    }
    uintptr_t elf_base = map_start;
    uint32_t magic = 0;
    memcpy(&magic, (const void *)elf_base, sizeof magic);
    if (magic != LD_ELF_MAGIC_WORD) { free(segs); return false; }

    const unsigned char *eh = (const unsigned char *)elf_base;
    uint64_t e_phoff = 0;
    uint16_t e_phentsize = 0, e_phnum = 0;
    memcpy(&e_phoff, eh + 0x20, 8);
    memcpy(&e_phentsize, eh + 0x36, 2);
    memcpy(&e_phnum, eh + 0x38, 2);
    if (e_phentsize < 56 || e_phnum == 0 || e_phnum > 64) { free(segs); return false; }

    out->elf_base = elf_base;
    out->entry_off = 0;
    out->phdr = (const void *)(elf_base + (uintptr_t)e_phoff);
    out->phnum = e_phnum;
    out->phentsize = e_phentsize;
    out->page = ld_page_size();
    bool ok = ld_verify_load_layout(out, segs, nsegs);
    free(segs);
    return ok;
}

LD_DART_OBSERVER(repl_icon_glass_enabled,ld_icon_force_folder_flag,g_orig_icon_glass_enabled)
LD_DART_OBSERVER(repl_icon_glass_size,ld_icon_adjust_native_size,g_orig_icon_glass_size)
LD_DART_OBSERVER(repl_icon_glass_radius,ld_icon_adjust_radius,g_orig_icon_glass_radius)
LD_DART_OBSERVER(repl_icon_glass_radius_b,ld_icon_adjust_radius_b,g_orig_icon_glass_radius_b)

#endif
