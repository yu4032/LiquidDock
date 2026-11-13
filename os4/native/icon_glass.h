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

/* SceneBuilder::pushPassBlurLight (libhyper_os_flutter.so). The stock icon material is
 * PassBlurBackgroundLight; its blur radius (w3) and ARGB tint list (x5, Int32List of
 * (color, aux) pairs) are rewritten here from the native_glass customization. */
typedef uintptr_t (*ld_pbl_fn)(void*,void*,void*,int32_t,void*,void*,int32_t,int32_t,
    double,double,double,double,double,double,double,double,void*);
static ld_pbl_fn g_orig_pbl;
static bool (*g_pbl_is_null)(void *h);
static void (*g_pbl_acquire)(void *h, int32_t *type, void **data, int64_t *len);

static inline bool ld_ng_has(unsigned i){return (g_home_options.native_glass.mask>>i)&1;}

static uintptr_t ld_pbl_hook(void*x0,void*x1,void*x2,int32_t w3,void*x4,void*x5,int32_t w6,int32_t w7,
    double d0,double d1,double d2,double d3,double d4,double d5,double d6,double d7,void*stk)
{
    static unsigned calls;
    unsigned n=__atomic_fetch_add(&calls,1u,__ATOMIC_RELAXED);
    if(g_home_options.glass_enabled && g_home_options.glass_icons && g_home_options.native_glass.mask){
        int32_t orig_r=w3;
        if(ld_ng_has(23)){ double v=g_home_options.native_glass.values[23]; if(v>=1.0) w3=(int32_t)v; }
        else if(ld_ng_has(22)){ double v=g_home_options.native_glass.values[22]; if(v>=1.0) w3=(int32_t)v; }
        bool tint=ld_ng_has(15)||ld_ng_has(16)||ld_ng_has(17)||ld_ng_has(18);
        uint32_t *pairs=NULL; int64_t len=0;
        if(x5 && g_pbl_is_null && g_pbl_acquire && !g_pbl_is_null(x5)){
            int32_t type=0; void *data=NULL;
            g_pbl_acquire(x5,&type,&data,&len);
            if(type==6 && data && len>0 && len<=64 && (len&1)==0) pairs=(uint32_t*)data;
        }
        if(pairs){
            if(n<6){
                ld_blog("PBL[%u] r=%d->%d len=%lld p0=(%08x,%08x) p1=(%08x,%08x) tint=%d",n,orig_r,w3,(long long)len,
                    pairs[0],pairs[1],len>2?pairs[2]:0,len>2?pairs[3]:0,tint);
            }
            if(tint) for(int64_t i=0;i+1<len;i+=2){
                uint32_t c=pairs[i];
                uint32_t a=c>>24,r=(c>>16)&255,g=(c>>8)&255,b=c&255;
                if(ld_ng_has(15)) r=(uint32_t)(g_home_options.native_glass.values[15]*255.0+0.5);
                if(ld_ng_has(16)) g=(uint32_t)(g_home_options.native_glass.values[16]*255.0+0.5);
                if(ld_ng_has(17)) b=(uint32_t)(g_home_options.native_glass.values[17]*255.0+0.5);
                if(ld_ng_has(18)) a=(uint32_t)(g_home_options.native_glass.values[18]*255.0+0.5);
                if(r>255)r=255; if(g>255)g=255; if(b>255)b=255; if(a>255)a=255;
                pairs[i]=(a<<24)|(r<<16)|(g<<8)|b;
            }
        } else if(n<3) ld_blog("PBL[%u] r=%d->%d (no tint list)",n,orig_r,w3);
    }
    return g_orig_pbl(x0,x1,x2,w3,x4,x5,w6,w7,d0,d1,d2,d3,d4,d5,d6,d7,stk);
}

static uintptr_t ld_decode_bl(uintptr_t at)
{
    uint32_t w=*(uint32_t*)at;
    if((w&0xfc000000u)!=0x94000000u) return 0;
    int32_t imm=(int32_t)((w&0x03ffffffu)<<6)>>6;
    return at+((intptr_t)imm<<2);
}

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

static void ld_passblur_light_install(const struct native_api *api)
{
    static bool installed;
    if (installed || !api || !api->hook || !g_home_options.glass_enabled || !g_home_options.glass_icons) return;
    ld_lib_info lib = {0};
    if (!ld_resolve_so("libhyper_os_flutter.so", &lib) || !lib.elf_base) return;
    static const uint32_t pat[8] = {
        0xd10683ffu, 0x6d122bebu, 0x6d1323e9u, 0xa9147bfdu,
        0xa9156ffcu, 0xa91667fau, 0xa9175ff8u, 0xa91857f6u,
    };
    uintptr_t target = ld_find_aot_pattern(&lib, 0xea7624u, pat, 8);
    if (!target) { ld_blog("pushPassBlurLight pattern not found"); return; }
    uintptr_t isnull = ld_decode_bl(target + 0x68);
    uintptr_t acquire = ld_decode_bl(target + 0x84);
    if (!isnull || !acquire) { ld_blog("pushPassBlurLight helper decode failed"); return; }
    g_pbl_is_null = (bool (*)(void *))isnull;
    g_pbl_acquire = (void (*)(void *, int32_t *, void **, int64_t *))acquire;
    void *backup = NULL;
    if (api->hook((void *)target, (void *)ld_pbl_hook, &backup) == 0 && backup) {
        g_orig_pbl = (ld_pbl_fn)backup;
        installed = true;
        ld_blog("pushPassBlurLight hook installed at %p isnull=%p acquire=%p", (void *)target, (void*)isnull, (void*)acquire);
    }
}

LD_DART_OBSERVER(repl_icon_glass_enabled,ld_icon_force_folder_flag,g_orig_icon_glass_enabled)
LD_DART_OBSERVER(repl_icon_glass_size,ld_icon_adjust_native_size,g_orig_icon_glass_size)
LD_DART_OBSERVER(repl_icon_glass_radius,ld_icon_adjust_radius,g_orig_icon_glass_radius)
LD_DART_OBSERVER(repl_icon_glass_radius_b,ld_icon_adjust_radius_b,g_orig_icon_glass_radius_b)

#endif
