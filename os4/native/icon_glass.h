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
__attribute__((used)) static void *g_orig_icon_glass_bdi;
__attribute__((used)) static void *g_orig_icon_glass_wrap;
__attribute__((used)) static int g_icon_glass_installed;
__attribute__((used)) static int g_icon_glass_radius_installed;
__attribute__((used)) static int g_icon_glass_container_patched;
__attribute__((used)) static void *g_orig_icon_glass_flight;
__attribute__((used)) static void *g_orig_icon_glass_dock_scale;
__attribute__((used)) static uintptr_t g_getstream_add_rt;
__attribute__((used)) static volatile unsigned g_icon_glass_flag_calls;
__attribute__((used)) static volatile unsigned g_icon_glass_radius_calls;
static volatile uint64_t g_dock_scaled_icon_bits;

static inline void ld_icon_store_dock_size(double d)
{
    if (d > 1.0 && d < 256.0 && isfinite(d)) {
        uint64_t bits = 0;
        memcpy(&bits, &d, sizeof bits);
        __atomic_store_n(&g_dock_scaled_icon_bits, bits, __ATOMIC_RELAXED);
    }
}

static inline double ld_icon_get_dock_size(void)
{
    uint64_t bits = __atomic_load_n(&g_dock_scaled_icon_bits, __ATOMIC_RELAXED);
    if (!bits) return 0.0;
    double d = 0.0;
    memcpy(&d, &bits, sizeof d);
    return (d > 1.0 && d < 256.0 && isfinite(d)) ? d : 0.0;
}

enum { LD_ICON_SIZE_CACHE_N = 64 };
struct ld_icon_size_entry {
    uintptr_t ctx;
    double min_dim;
    double scale;
};
static struct ld_icon_size_entry g_icon_size_cache[LD_ICON_SIZE_CACHE_N];
static volatile unsigned g_icon_size_cache_idx;

static inline void ld_icon_cache_size(uintptr_t ctx, double w, double h, double s)
{
    if (!ctx || !(w > 0.0) || !(h > 0.0)) return;
    double min_dim = (w < h) ? w : h;
    double scale = (s > 0.0 && isfinite(s)) ? s : 1.0;
    unsigned idx = __atomic_fetch_add(&g_icon_size_cache_idx, 1, __ATOMIC_RELAXED) % LD_ICON_SIZE_CACHE_N;
    g_icon_size_cache[idx].ctx = ctx;
    g_icon_size_cache[idx].min_dim = min_dim;
    g_icon_size_cache[idx].scale = scale;
}

static inline bool ld_icon_lookup_size(uintptr_t ctx, double *out_min_dim, double *out_scale)
{
    if (!ctx) return false;
    unsigned cur = __atomic_load_n(&g_icon_size_cache_idx, __ATOMIC_RELAXED);
    for (unsigned i = 0; i < LD_ICON_SIZE_CACHE_N; i++) {
        unsigned slot = (cur - 1u - i) % LD_ICON_SIZE_CACHE_N;
        if (g_icon_size_cache[slot].ctx == ctx) {
            *out_min_dim = g_icon_size_cache[slot].min_dim;
            *out_scale = g_icon_size_cache[slot].scale;
            return true;
        }
    }
    return false;
}
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

static uint32_t g_off_owner_ctrl = 0x2bu;
static uint32_t g_off_ctrl_info = 0x14bu;
static uint32_t g_off_info_container = 0x47u;
static uint32_t g_off_ctrl_rx = 0x10bu;
static uint32_t g_off_rx_subject = 0x0bu;
static uint32_t g_off_rx_val = 0x13u;

static void ld_resolve_icon_field_offsets(uintptr_t bdi_rt, uintptr_t body_rt)
{
    if (bdi_rt && executable_range(bdi_rt, 64 * sizeof(uint32_t))) {
        const uint32_t *w = (const uint32_t *)bdi_rt;
        for (size_t i = 0; i + 5 < 64; i++) {
            if ((w[i] & 0xffe00fffu) == 0xb8400001u &&
                w[i + 1] == 0x8b1c8021u &&
                (w[i + 2] & 0xffe0001fu) == 0xd2800011u &&
                w[i + 3] == 0xb8716822u &&
                w[i + 4] == 0x8b1c8042u &&
                (w[i + 5] & 0xffe00fffu) == 0xf8400041u) {
                g_off_owner_ctrl = (w[i] >> 12) & 0x1ffu;
                g_off_ctrl_info = (w[i + 2] >> 5) & 0xffffu;
                g_off_info_container = (w[i + 5] >> 12) & 0x1ffu;
                break;
            }
        }
    }
    if (body_rt && executable_range(body_rt, 32 * sizeof(uint32_t))) {
        const uint32_t *w = (const uint32_t *)body_rt;
        for (size_t i = 0; i + 4 < 32; i++) {
            if ((w[i] & 0xffe0001fu) == 0xd2800011u &&
                w[i + 1] == 0xb8716820u &&
                w[i + 2] == 0x8b1c8000u &&
                w[i + 3] == 0xaa0003e1u &&
                (w[i + 4] & 0xfc000000u) == 0x94000000u) {
                g_off_ctrl_rx = (w[i] >> 5) & 0xffffu;
                int32_t imm26 = (int32_t)(w[i + 4] << 6) >> 6;
                uintptr_t getter_rt = (uintptr_t)&w[i + 4] + (intptr_t)imm26 * 4;
                if (executable_range(getter_rt, 24 * sizeof(uint32_t))) {
                    const uint32_t *gw = (const uint32_t *)getter_rt;
                    for (size_t j = 0; j + 5 < 24; j++) {
                        if ((gw[j] & 0xffe00fffu) == 0xb8400002u &&
                            gw[j + 1] == 0x8b1c8042u &&
                            (gw[j + 4] & 0xffe00fffu) == 0xb8400020u &&
                            gw[j + 5] == 0x8b1c8000u) {
                            g_off_rx_subject = (gw[j] >> 12) & 0x1ffu;
                            g_off_rx_val = (gw[j + 4] >> 12) & 0x1ffu;
                            break;
                        }
                    }
                }
                break;
            }
        }
    }
    ld_blog("icon glass dynamic offsets: ctrl=0x%x info=0x%x container=0x%x rx=0x%x subj=0x%x val=0x%x",
            g_off_owner_ctrl, g_off_ctrl_info, g_off_info_container,
            g_off_ctrl_rx, g_off_rx_subject, g_off_rx_val);
}

static bool ld_icon_is_hotseat_owner(struct ld_dart_context *c, uintptr_t owner)
{
    if (!c || !owner || owner < 1) return false;
    uint32_t ctrl_comp = 0, info_comp = 0;
    if (!ld_read_heap(owner + g_off_owner_ctrl, &ctrl_comp, sizeof ctrl_comp)) return false;
    uintptr_t ctrl = ld_icon_decode_compressed(c, ctrl_comp);
    if (!ctrl || ctrl < 1) return false;
    if (!ld_read_heap(ctrl + g_off_ctrl_info, &info_comp, sizeof info_comp)) return false;
    uintptr_t info = ld_icon_decode_compressed(c, info_comp);
    if (!info || info < 1) return false;
    int64_t container = 0;
    if (!ld_read_heap(info + g_off_info_container, &container, sizeof container)) return false;
    return container == -101 || container == -102 || container == -103 ||
           container == -105 || container == -106;
}

static bool ld_icon_owner_allowed(uint32_t cid);

static void ld_call_dart_2(struct ld_dart_context *c, uintptr_t fn, uintptr_t arg1, uintptr_t arg2)
{
    if (!c || !fn || !arg1 || !arg2) return;
    volatile uint64_t call_args[4] = { (uint64_t)(uintptr_t)c->x, fn, arg1, arg2 };
    register volatile uint64_t *r_args __asm__("x9") = call_args;
    __asm__ volatile(
        "stp x19, x20, [sp, #-16]!\n"
        "stp x21, x22, [sp, #-16]!\n"
        "stp x23, x24, [sp, #-16]!\n"
        "stp x25, x26, [sp, #-16]!\n"
        "stp x27, x28, [sp, #-16]!\n"
        "stp x29, x30, [sp, #-16]!\n"
        "ldr x10, [%[a], #0]\n"
        "sub x15, sp, #64\n"
        "ldp x19, x20, [x10, #152]\n"
        "ldp x21, x22, [x10, #168]\n"
        "ldp x23, x24, [x10, #184]\n"
        "ldp x25, x26, [x10, #200]\n"
        "ldp x27, x28, [x10, #216]\n"
        "ldr x29, [x10, #232]\n"
        "ldr x16, [%[a], #8]\n"
        "ldr x1,  [%[a], #16]\n"
        "ldr x2,  [%[a], #24]\n"
        "blr x16\n"
        "ldp x29, x30, [sp], #16\n"
        "ldp x27, x28, [sp], #16\n"
        "ldp x25, x26, [sp], #16\n"
        "ldp x23, x24, [sp], #16\n"
        "ldp x21, x22, [sp], #16\n"
        "ldp x19, x20, [sp], #16\n"
        : [a] "+r"(r_args)
        :
        : "x0", "x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8",
          "x10", "x11", "x12", "x13", "x14", "x15", "x16", "x17",
          "memory", "cc"
    );
}

enum { LD_HOTSEAT_RX_MAX = 128 };
struct ld_hotseat_rx_entry {
    uintptr_t rx;
    uint32_t rx_cid;
    uint32_t subj_cid;
};
static struct ld_hotseat_rx_entry g_hotseat_rx_cache[LD_HOTSEAT_RX_MAX];

static void ld_hotseat_cache_rx(struct ld_dart_context *c, uintptr_t owner)
{
    uintptr_t ctrl = ld_heap_pointer(c, owner, g_off_owner_ctrl);
    uintptr_t rx = ctrl ? ld_heap_pointer(c, ctrl, g_off_ctrl_rx) : 0;
    uintptr_t subject = rx ? ld_heap_pointer(c, rx, g_off_rx_subject) : 0;
    if (!rx || !subject) return;
    uint32_t rx_cid = ld_icon_owner_class_id(rx);
    uint32_t subj_cid = ld_icon_owner_class_id(subject);
    if (rx_cid == UINT32_MAX || subj_cid == UINT32_MAX) return;
    int free_slot = -1;
    for (int i = 0; i < LD_HOTSEAT_RX_MAX; i++) {
        uintptr_t cur = g_hotseat_rx_cache[i].rx;
        if (cur == rx) return;
        if (free_slot < 0 && (cur == 0 || ld_icon_owner_class_id(cur) != g_hotseat_rx_cache[i].rx_cid)) {
            free_slot = i;
        }
    }
    if (free_slot >= 0) {
        g_hotseat_rx_cache[free_slot].rx = rx;
        g_hotseat_rx_cache[free_slot].rx_cid = rx_cid;
        g_hotseat_rx_cache[free_slot].subj_cid = subj_cid;
    }
}

static int ld_icon_refresh_cached_rx(struct ld_dart_context *c)
{
    if (!g_getstream_add_rt) return 0;
    int refreshed = 0;
    for (int i = 0; i < LD_HOTSEAT_RX_MAX; i++) {
        uintptr_t rx = g_hotseat_rx_cache[i].rx;
        if (!rx) continue;
        if (ld_icon_owner_class_id(rx) != g_hotseat_rx_cache[i].rx_cid) {
            g_hotseat_rx_cache[i].rx = 0;
            continue;
        }
        uintptr_t subject = ld_heap_pointer(c, rx, g_off_rx_subject);
        uintptr_t val = ld_heap_pointer(c, rx, g_off_rx_val);
        if (!subject || !val) continue;
        if (ld_icon_owner_class_id(subject) != g_hotseat_rx_cache[i].subj_cid) continue;
        ld_call_dart_2(c, g_getstream_add_rt, subject, val);
        refreshed++;
    }
    return refreshed;
}

__attribute__((used)) static void ld_icon_record_dock_size(struct ld_dart_context *c)
{
    static volatile int s_in_refresh = 0;
    static double s_bounce_prev = 0.0;
    static uint64_t s_bounce_until_ns = 0;

    double d = 0.0;
    memcpy(&d, c->q[0], sizeof d);
    if (!(d > 1.0 && d < 256.0 && isfinite(d))) return;

    struct timespec ts;
    uint64_t now_ns = 0;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0) {
        now_ns = (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
    }
    if (__atomic_load_n(&s_in_refresh, __ATOMIC_RELAXED)) return;
    if (now_ns < s_bounce_until_ns && fabs(d - s_bounce_prev) < 1.0) return;

    double prev_d = ld_icon_get_dock_size();
    ld_icon_store_dock_size(d);
    if (!g_getstream_add_rt || !(prev_d > 1.0) || fabs(d - prev_d) <= 0.5) return;

    s_bounce_prev = prev_d;
    s_bounce_until_ns = now_ns + 250000000ULL;
    __atomic_store_n(&s_in_refresh, 1, __ATOMIC_RELAXED);
    int refreshed = ld_icon_refresh_cached_rx(c);
    __atomic_store_n(&s_in_refresh, 0, __ATOMIC_RELAXED);
    if (refreshed > 0) {
        ld_blog("icon glass dock refresh %.1f -> %.1f count=%d", prev_d, d, refreshed);
    }
}

/* Owner class of the workspace icon host. Known IDs serve as fallback while
 * dynamic observation on ShortcutIconWidget.buildDesktopIcon / wrapWithFolderCreateAnim
 * learns the exact owner class IDs for any Launcher build at runtime. */
#define LD_ICON_GLASS_OWNER_CID 8306u
#define LD_ICON_GLASS_OWNER_CID2 8308u
#define LD_ICON_GLASS_OWNER_8583_A 8307u
#define LD_ICON_GLASS_OWNER_8583_B 8309u
#define LD_ICON_GLASS_OWNER_9239_A 8383u
#define LD_ICON_GLASS_OWNER_9239_B 8385u
#define LD_ICON_GLASS_OWNER_OK(cid) ((cid)==LD_ICON_GLASS_OWNER_CID || (cid)==LD_ICON_GLASS_OWNER_CID2 || \
                                     (cid)==LD_ICON_GLASS_OWNER_8583_A || (cid)==LD_ICON_GLASS_OWNER_8583_B || \
                                     (cid)==LD_ICON_GLASS_OWNER_9239_A || (cid)==LD_ICON_GLASS_OWNER_9239_B)
#define LD_ICON_GLASS_DYN_MAX 8
static volatile uint32_t g_icon_glass_dyn_cids[LD_ICON_GLASS_DYN_MAX];

static void ld_icon_register_dyn_cid(uint32_t cid)
{
    if (cid == 0 || cid == UINT32_MAX) return;
    for (size_t i = 0; i < LD_ICON_GLASS_DYN_MAX; i++) {
        uint32_t cur = __atomic_load_n(&g_icon_glass_dyn_cids[i], __ATOMIC_ACQUIRE);
        if (cur == cid) return;
        if (cur == 0) {
            uint32_t expected = 0;
            if (__atomic_compare_exchange_n(&g_icon_glass_dyn_cids[i], &expected, cid,
                                            false, __ATOMIC_RELEASE, __ATOMIC_RELAXED)) {
                ld_blog("icon glass learned owner cid=%u (slot=%zu)", cid, i);
                return;
            }
            if (__atomic_load_n(&g_icon_glass_dyn_cids[i], __ATOMIC_ACQUIRE) == cid) return;
        }
    }
}

static bool ld_icon_owner_allowed(uint32_t cid)
{
    if (cid == 0 || cid == UINT32_MAX) return false;
    for (size_t i = 0; i < LD_ICON_GLASS_DYN_MAX; i++) {
        uint32_t v = __atomic_load_n(&g_icon_glass_dyn_cids[i], __ATOMIC_ACQUIRE);
        if (v == 0) break;
        if (v == cid) return true;
    }
    return LD_ICON_GLASS_OWNER_OK(cid);
}

__attribute__((used)) static void ld_icon_observe_owner(struct ld_dart_context *c)
{
    uint32_t cid = ld_icon_owner_class_id((uintptr_t)c->x[1]);
    ld_icon_register_dyn_cid(cid);
}
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
    if(!ld_icon_owner_allowed(cid)) return;
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
    if(!ld_icon_owner_allowed(size_cid)) return;

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
    if(scall <= 4) {
        ld_blog("icon glass size call=%u cid=%u d113=%.3f d10f=%.3f w=%.1f h=%.1f",
                scall, size_cid, d113, d10f, width, height);
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
    double min_s = (d113 > 0.0 && d10f > 0.0) ? ((d113 < d10f) ? d113 : d10f) : 1.0;
    ld_icon_cache_size(owner, width, height, min_s);
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
    if(!ld_icon_owner_allowed(cid)) return;
    double orig_r=0.0;
    memcpy(&orig_r,c->q[0],sizeof orig_r);
    static double s_ref_orig_r = 0.0;
    if(isfinite(orig_r) && orig_r > s_ref_orig_r && orig_r < 64.0) s_ref_orig_r = orig_r;
    double radius=g_home_options.glass_icon_corner;
    double min_dim=0.0, scale=1.0;
    bool hit = ld_icon_lookup_size(owner, &min_dim, &scale);
    bool is_hotseat = ld_icon_is_hotseat_owner(c, owner);
    ld_hotseat_cache_rx(c, owner);
    static double s_ref_dim = 0.0;
    if (!is_hotseat && hit && isfinite(min_dim) && min_dim > s_ref_dim && min_dim < 256.0) {
        s_ref_dim = min_dim;
    }
    if (is_hotseat) {
        double dock_dim = ld_icon_get_dock_size();
        if (dock_dim > 1.0) {
            min_dim = dock_dim;
            hit = true;
        }
    }
    double ref_dim = (s_ref_dim > 10.0) ? s_ref_dim : 58.1;
    double dim_scale = (hit && min_dim > 0.0 && min_dim < ref_dim) ? (min_dim / ref_dim) : 1.0;
    double anim_scale = (hit && scale > 0.0 && scale < 1.0) ? scale : 1.0;
    double ratio = dim_scale * anim_scale;
    if(ratio > 0.99 && isfinite(orig_r) && orig_r > 0.0 && s_ref_orig_r > 0.0 && orig_r < s_ref_orig_r * 0.98) {
        ratio = orig_r / s_ref_orig_r;
    }
    if(ratio > 0.0 && ratio < 1.0) radius *= ratio;
    if(hit && min_dim > 0.0 && radius > min_dim * 0.5) radius = min_dim * 0.5;
    memcpy(c->q[0],&radius,sizeof radius);
    if(call<=4 || is_hotseat) {
        static unsigned dock_rad_logs = 0;
        if(call<=4 || __atomic_add_fetch(&dock_rad_logs,1,__ATOMIC_RELAXED)<=16)
            ld_blog("icon glass radius applied %.1f (orig=%.2f dim=%.1f ref=%.1f hotseat=%d s=%.3f) site=%u cid=%u",
                    radius, orig_r, min_dim, ref_dim, (int)is_hotseat, scale, site, cid);
    }
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
LD_DART_OBSERVER(repl_icon_glass_bdi,ld_icon_observe_owner,g_orig_icon_glass_bdi)
LD_DART_OBSERVER(repl_icon_glass_wrap,ld_icon_observe_owner,g_orig_icon_glass_wrap)
LD_DART_OBSERVER(repl_icon_glass_dock_scale,ld_icon_record_dock_size,g_orig_icon_glass_dock_scale)

#endif
