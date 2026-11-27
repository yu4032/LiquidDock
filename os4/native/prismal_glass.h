/* OS4 v2.7.9 Prismal Liquid Glass:
 * Replaces Impeller's MiGlassV13F16 fragment pipeline with the composed v2.7.9
 * Prismal optical shader, bypasses OS4 soft-glass backdrop tinting, provides both
 * sharp (unblurred) and optional Gaussian-blurred untinted backdrops, and injects
 * live GUI parameters (Page.Liquid*) straight into the UBO every draw.
 */
#ifndef LD_PRISMAL_GLASS_H
#define LD_PRISMAL_GLASS_H

#include <dlfcn.h>
#include <errno.h>
#include <math.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#ifdef __ANDROID__
#include <sys/system_properties.h>
#endif
#include "bootlog.h"
#include "grid_conf.h"
#include "hyper_engine_resolver.h"
#include "native_api.h"
#include "prismal_os4_spv.h"

#define LD_PRISMAL_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-prismal-glass"
#define LD_PRISMAL_CONF_PRIMARY \
    "/data/user/0/com.miui.home/files/liquiddock-native.conf"
#define LD_PRISMAL_CONF_EARLY \
    "/data/user_de/0/com.miui.home/files/liquiddock-native-early.conf"

/* 快速路径 hint vaddr；跨版本时自动通过 hyper_engine_resolver 全 .text 掩码扫描解析。 */
#define LD_HINT_MAKE_BASE_BLUR            0xc835b8u
#define LD_HINT_MAKE_REFLECT_BLUR         0xc83b58u
#define LD_HINT_MAKE_TINTED_FROM_BASE     0xc84188u
#define LD_HINT_COMPOSE_TEXTURES          0xc76290u
#define LD_HINT_GLASS_CONTENTS_RENDER     0xd69e00u
#define LD_HINT_BIND_U_MASK_TEX           0xd6c1b8u
#define LD_HINT_BIND_UBO                  0xd6c260u
#define LD_HINT_DEVICE_BUF_GET_CONTENTS   0xdec528u
#define LD_HINT_DEVICE_BUF_FLUSH          0xdec594u
#define LD_HINT_PIPELINE_VK_CREATE        0xdf6028u
#define LD_HINT_RENDER_PASS_SET_SCISSOR   0xe01148u
#define LD_HINT_CANVAS_SAVE_LAYER         0xc34d58u

static const uint32_t ld_pg_pat_make_blur[8] = {
    0x6dba23e9u, 0xf9000bfdu, 0xa90267feu, 0xa9035ff8u,
    0xa90457f6u, 0xa9054ff4u, 0xd10e03ffu, 0x1e204028u,
};
static const uint32_t ld_pg_pat_make_tinted[8] = {
    0xfc1b0fe8u, 0xf90007fdu, 0xa90167feu, 0xa9025ff8u,
    0xa90357f6u, 0xa9044ff4u, 0xd10d43ffu, 0xf9400048u,
};
static const uint32_t ld_pg_pat_compose_textures[8] = {
    0xa9ba7bfdu, 0xa9016ffcu, 0xa90267fau, 0xa9035ff8u,
    0xa90457f6u, 0xa9054ff4u, 0xd10d03ffu, 0xd53bd048u,
};
static const uint32_t ld_pg_pat_canvas_savelayer[8] = {
    0x6db733edu, 0x6d012bebu, 0x6d0223e9u, 0xa9037bfdu,
    0xa9046ffcu, 0xa90567fau, 0xa9065ff8u, 0xa90757f6u,
};
static const uint32_t ld_pg_pat_glass_render[16] = {
    0x6db63befu, 0x6d0133edu, 0x6d022bebu, 0x6d0323e9u,
    0xa9047bfdu, 0xa9056ffcu, 0xa90667fau, 0xa9075ff8u,
    0xa90857f6u, 0xa9094ff4u, 0xd11303ffu, 0xd53bd05bu,
    0xaa0303f3u, 0xaa0103f4u, 0xf9401768u, 0xaa0003f5u,
};
static const uint32_t ld_pg_pat_bind_mask_tex[8] = {
    0xd100c3ffu, 0xf9000bfeu, 0xa9024ff4u, 0x3dc00020u,
    0xa9007c3fu, 0xaa0203e6u, 0xf9400008u, 0xd0002163u,
};
static const uint32_t ld_pg_msk_bind_mask_tex[8] = {
    0xffffffffu, 0xffffffffu, 0xffffffffu, 0xffffffffu,
    0xffffffffu, 0xffffffffu, 0xffffffffu, 0x9f00001fu,
};
static const uint32_t ld_pg_pat_bind_ubo[8] = {
    0xd10143ffu, 0xf9001bfeu, 0xa9044ff4u, 0xad400420u,
    0xd0002163u, 0x910de063u, 0xa9007c3fu, 0xf9401028u,
};
static const uint32_t ld_pg_msk_bind_ubo[8] = {
    0xffffffffu, 0xffffffffu, 0xffffffffu, 0xffffffffu,
    0x9f00001fu, 0xffc003ffu, 0xffffffffu, 0xffffffffu,
};
static const uint32_t ld_pg_pat_devbuf_get[8] = {
    0xd10503ffu, 0xf90093fdu, 0xa9134ffeu, 0xf9402008u,
    0xb5000248u, 0xaa0003f3u, 0xf0ff97c2u, 0x91366c42u,
};
static const uint32_t ld_pg_msk_devbuf_get[8] = {
    0xffffffffu, 0xffffffffu, 0xffffffffu, 0xffffffffu,
    0xffffffffu, 0xffffffffu, 0x9f00001fu, 0xffc003ffu,
};
static const uint32_t ld_pg_pat_devbuf_flush[8] = {
    0x39412008u, 0x37000668u, 0xd10543ffu, 0xf90093fdu,
    0xa91357feu, 0xa9144ff4u, 0x39404028u, 0x7100051fu,
};
static const uint32_t ld_pg_pat_pipeline_vk_create[8] = {
    0xfc190fe8u, 0xa9017bfdu, 0xa9026ffcu, 0xa90367fau,
    0xa9045ff8u, 0xa90557f6u, 0xa9064ff4u, 0xd11d43ffu,
};
static const uint32_t ld_pg_pat_set_scissor[8] = {
    0xd360fc28u, 0xd360fc49u, 0x37f800e2u, 0x36f80281u,
    0x12b0000au, 0x0b0a002bu, 0x6b02017fu, 0x5400010bu,
};

static volatile int g_prismal_glass_active __attribute__((used));
static volatile int g_prismal_no_downsample __attribute__((used));
static volatile int g_prismal_motion_downsample __attribute__((used)) = 1;
static volatile int g_prismal_icon_downsample __attribute__((used)) = 1;
static volatile int g_prismal_icon_fixed_wallpaper __attribute__((used));

typedef struct {
    void *ptr;
    uint8_t *ctrl;
} ld_sp_t;

typedef struct {
    uint32_t sType;
    const void *pNext;
    uint32_t flags;
    size_t codeSize;
    const uint32_t *pCode;
} ld_vk_shader_module_ci_t;

typedef int32_t (*ld_vk_create_sm_fn)(uint64_t device, const ld_vk_shader_module_ci_t *ci,
                                      const void *alloc, uint64_t *out_module);

typedef struct {
    uint64_t offset;
    uint64_t length;
    uint8_t has_value;
    uint8_t pad[7];
} ld_opt_range_t;

static void *(*g_orig_pipeline_vk_create)(void *sret, const void *desc, const void *dev_holder,
                                          const void *lib, int64_t category, const void *sampler);
static void (*g_orig_make_base_blur)(ld_sp_t *sret, void *ctx, const ld_sp_t *src_tex,
                                     ld_sp_t *out_opt, const void *cmd_buf, float blur, float scale);
static void (*g_orig_make_reflect_blur)(ld_sp_t *sret, void *ctx, const ld_sp_t *src_tex,
                                        ld_sp_t *out_opt, const void *cmd_buf, float blur, float scale);
static void (*g_orig_make_tinted_from_base)(ld_sp_t *sret, void *ctx, const ld_sp_t *base_tex,
                                            const ld_sp_t *refract_tex, const void *tint_cfg,
                                            const void *size, const void *cmd_buf, float blur);
static uint8_t (*g_orig_glass_contents_render)(void *self, const void *ctx, const void *entity, void *pass);
static uint8_t (*g_orig_bind_mask_tex)(void *binder, ld_sp_t *tex_sp, void *sampler);
static uint8_t (*g_orig_bind_ubo)(void *binder, void *buffer_view);
static void (*g_orig_set_scissor)(void *pass, uint64_t lt, uint64_t rb);
static void *g_orig_compose_textures __attribute__((used));
static void *g_orig_canvas_savelayer __attribute__((used));

static uint8_t *(*g_fn_devbuf_get_contents)(void *dev_buf);
static void (*g_fn_devbuf_flush)(void *dev_buf, const ld_opt_range_t *opt_range);

static volatile unsigned g_pipeline_vk_calls;
static uint64_t g_prismal_vk_module;
static uint64_t g_prismal_vk_device;

static pthread_mutex_t g_prismal_conf_lock = PTHREAD_MUTEX_INITIALIZER;
static struct ld_prismal_options g_prismal_live_opts;
static struct ld_prismal_options g_prismal_opts_slots[2];
static volatile unsigned g_prismal_opts_slot;
static volatile uint32_t g_prismal_blur_px_bits;
static volatile int g_prismal_in_motion __attribute__((used));
static int g_prismal_opts_initialized;

static void ld_prismal_fade_begin_frame(int64_t now_ns, int64_t dt_ns);

static void ld_prismal_update_motion(void *pass)
{
    static __thread void *last_pass;
    static __thread int64_t last_frame_ns;
    static __thread int consec_fast;
    if (pass && pass == last_pass) return;
    last_pass = pass;

    struct timespec now = {0, 0};
    clock_gettime(CLOCK_MONOTONIC, &now);
    int64_t now_ns = (int64_t)now.tv_sec * 1000000000LL + (int64_t)now.tv_nsec;
    int64_t dt_ns = now_ns - last_frame_ns;
    if (last_frame_ns == 0) {
        last_frame_ns = now_ns;
        ld_prismal_fade_begin_frame(now_ns, 100000000LL);
        return;
    }
    if (dt_ns < 4000000LL) return; /* same frame (multiple RenderPasses within 4ms) */
    last_frame_ns = now_ns;
    ld_prismal_fade_begin_frame(now_ns, dt_ns);
    if (dt_ns < 40000000LL) {
        if (consec_fast < 4) consec_fast++;
        if (consec_fast >= 2) {
            __atomic_store_n(&g_prismal_in_motion, 1, __ATOMIC_RELEASE);
        }
    } else if (dt_ns >= 80000000LL) {
        consec_fast = 0;
        __atomic_store_n(&g_prismal_in_motion, 0, __ATOMIC_RELEASE);
    }
}

static __thread uintptr_t g_tls_glass_contents;
static __thread int g_tls_bound_sharp_tex;
static __thread float g_tls_expand_pad_px;
static __thread float g_tls_entity_tx;
static __thread float g_tls_entity_ty;
static __thread float g_tls_ubo_orig_ox;
static __thread float g_tls_ubo_orig_oy;
static __thread int g_tls_is_small_icon;
static __thread void *g_tls_scissor_pass;
static __thread uint64_t g_tls_scissor_lt;
static __thread uint64_t g_tls_scissor_rb;

/* 2.7.9-style smoothstep fade-in at the tail of an icon return animation (CLOSE_TO_HOME).
 * Runs exclusively on the Impeller raster thread via __thread state (zero locks). */
enum { LD_ICON_TRACK_MAX = 64, LD_ICON_FADE_MAX = 8 };
struct ld_icon_pos { float x, y; };
struct ld_icon_fade { float x, y; int64_t start_ns; int active; };
static __thread struct ld_icon_pos g_tls_prev_icons[LD_ICON_TRACK_MAX];
static __thread int g_tls_prev_icon_n;
static __thread struct ld_icon_pos g_tls_cur_icons[LD_ICON_TRACK_MAX];
static __thread int g_tls_cur_icon_n;
static __thread struct ld_icon_fade g_tls_icon_fades[LD_ICON_FADE_MAX];
static __thread int64_t g_tls_fade_frame_ns;
static __thread int64_t g_tls_fade_armed_until_ns;
static __thread int64_t g_tls_close_fade_start_ns;
static __thread int g_tls_fade_consec_frames;
static __thread int g_tls_fade_new_this_frame;
static __thread int g_tls_fade_scroll_detected;
static __thread float g_tls_icon_scale = 1.0f;

static void ld_prismal_fade_begin_frame(int64_t now_ns, int64_t dt_ns)
{
    if (dt_ns >= 80000000LL) {
        if (dt_ns >= 250000000LL) {
            /* Launcher just resumed from background / app: arm close-to-home fade detector. */
            g_tls_fade_armed_until_ns = now_ns + 1800000000LL;
        }
        g_tls_close_fade_start_ns = 0;
        g_tls_fade_consec_frames = 0;
        g_tls_prev_icon_n = 0;
        g_tls_cur_icon_n = 0;
        g_tls_fade_scroll_detected = 0;
        for (int i = 0; i < LD_ICON_FADE_MAX; i++) g_tls_icon_fades[i].active = 0;
    } else {
        if (g_tls_fade_consec_frames < 1000) g_tls_fade_consec_frames++;
        g_tls_prev_icon_n = g_tls_cur_icon_n;
        if (g_tls_prev_icon_n > 0) {
            memcpy(g_tls_prev_icons, g_tls_cur_icons,
                   (size_t)g_tls_prev_icon_n * sizeof(struct ld_icon_pos));
        }
        g_tls_cur_icon_n = 0;
        g_tls_fade_scroll_detected = 0;
    }
    g_tls_fade_frame_ns = now_ns;
    g_tls_fade_new_this_frame = 0;
}

static float ld_prismal_icon_return_fade(float sx, float sy)
{
    int64_t now_ns = g_tls_fade_frame_ns;
    if (now_ns <= 0 || !isfinite(sx) || !isfinite(sy)) return 1.0f;

    if (g_tls_cur_icon_n < LD_ICON_TRACK_MAX) {
        g_tls_cur_icons[g_tls_cur_icon_n].x = sx;
        g_tls_cur_icons[g_tls_cur_icon_n].y = sy;
        g_tls_cur_icon_n++;
    }

    /* 0. Close-to-home workspace zoom fade-in (starts during the close animation itself,
     * triggered when resuming from background with workspace zoom scale < 0.985). */
    if (g_tls_close_fade_start_ns == 0 &&
        now_ns <= g_tls_fade_armed_until_ns &&
        g_tls_fade_consec_frames <= 12 &&
        g_tls_icon_scale > 0.3f && g_tls_icon_scale < 0.985f) {
        g_tls_close_fade_start_ns = now_ns;
        g_tls_fade_armed_until_ns = 0;
    }
    float close_alpha = 1.0f;
    if (g_tls_close_fade_start_ns > 0) {
        int64_t elapsed = now_ns - g_tls_close_fade_start_ns;
        /* 80ms initial hold while app shrinks, then 220ms smoothstep fade-in during close anim. */
        if (elapsed >= 300000000LL) {
            g_tls_close_fade_start_ns = 0;
        } else if (elapsed <= 80000000LL) {
            close_alpha = 0.0f;
        } else {
            float p = (float)(elapsed - 80000000LL) / 220000000.0f;
            close_alpha = p * p * (3.0f - 2.0f * p);
        }
    }

    /* 1. Continue an active per-icon fade matching this icon's position. */
    for (int i = 0; i < LD_ICON_FADE_MAX; i++) {
        if (!g_tls_icon_fades[i].active) continue;
        float dx = sx - g_tls_icon_fades[i].x;
        float dy = sy - g_tls_icon_fades[i].y;
        if (dx * dx + dy * dy <= 48.0f * 48.0f) {
            g_tls_icon_fades[i].x = sx;
            g_tls_icon_fades[i].y = sy;
            int64_t elapsed = now_ns - g_tls_icon_fades[i].start_ns;
            if (elapsed >= 120000000LL) {
                g_tls_icon_fades[i].active = 0;
                return close_alpha;
            }
            float phase = elapsed > 0 ? (float)elapsed / 120000000.0f : 0.0f;
            float a = phase * phase * (3.0f - 2.0f * phase);
            return a < close_alpha ? a : close_alpha;
        }
    }

    /* 2. Detect a single returning icon reappearing during/after CLOSE_TO_HOME SF flight. */
    if (now_ns <= g_tls_fade_armed_until_ns &&
        !g_tls_fade_scroll_detected &&
        g_tls_fade_consec_frames >= 4 && g_tls_prev_icon_n >= 3) {
        float best_d2 = 1e9f;
        float best_dx = 0.0f;
        for (int i = 0; i < g_tls_prev_icon_n; i++) {
            float dx = sx - g_tls_prev_icons[i].x;
            float dy = sy - g_tls_prev_icons[i].y;
            float d2 = dx * dx + dy * dy;
            if (d2 < best_d2) {
                best_d2 = d2;
                best_dx = fabsf(dx);
            }
        }
        if (best_d2 <= 72.0f * 72.0f) {
            if (best_dx > 8.0f) {
                g_tls_fade_scroll_detected = 1;
                for (int i = 0; i < LD_ICON_FADE_MAX; i++) {
                    if (g_tls_icon_fades[i].active && g_tls_icon_fades[i].start_ns == now_ns) {
                        g_tls_icon_fades[i].active = 0;
                    }
                }
            }
            return close_alpha;
        }
        g_tls_fade_new_this_frame++;
        if (g_tls_fade_new_this_frame >= 3) {
            for (int i = 0; i < LD_ICON_FADE_MAX; i++) {
                if (g_tls_icon_fades[i].active && g_tls_icon_fades[i].start_ns == now_ns) {
                    g_tls_icon_fades[i].active = 0;
                }
            }
            return close_alpha;
        }
        for (int i = 0; i < LD_ICON_FADE_MAX; i++) {
            if (!g_tls_icon_fades[i].active) {
                g_tls_icon_fades[i].x = sx;
                g_tls_icon_fades[i].y = sy;
                g_tls_icon_fades[i].start_ns = now_ns;
                g_tls_icon_fades[i].active = 1;
                g_tls_fade_armed_until_ns = 0;
                return 0.0f;
            }
        }
    }
    return close_alpha;
}

static void repl_set_scissor(void *pass, uint64_t lt, uint64_t rb)
{
    g_tls_scissor_pass = pass;
    g_tls_scissor_lt = lt;
    g_tls_scissor_rb = rb;
    if (g_orig_set_scissor) g_orig_set_scissor(pass, lt, rb);
}

static inline float ld_clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

static float ld_prismal_get_density(void)
{
    static uint32_t cached_bits;
    uint32_t b = __atomic_load_n(&cached_bits, __ATOMIC_RELAXED);
    if (b) {
        float d = 0.0f;
        memcpy(&d, &b, sizeof d);
        if (d > 0.1f) return d;
    }
    float d = 2.75f;
#ifdef __ANDROID__
    char prop[92] = {0};
    int n = __system_property_get("ro.sf.lcd_density", prop);
    if (n <= 0) n = __system_property_get("persist.sys.miui_density", prop);
    if (n > 0) {
        long dpi = strtol(prop, NULL, 10);
        if (dpi >= 120 && dpi <= 960) d = (float)dpi / 160.0f;
    }
#endif
    memcpy(&b, &d, sizeof b);
    __atomic_store_n(&cached_bits, b, __ATOMIC_RELAXED);
    return d;
}

static void ld_prismal_publish_opts_locked(const struct ld_prismal_options *src)
{
    g_prismal_live_opts = *src;
    unsigned next = (__atomic_load_n(&g_prismal_opts_slot, __ATOMIC_RELAXED) ^ 1u) & 1u;
    g_prismal_opts_slots[next] = *src;
    __atomic_store_n(&g_prismal_opts_slot, next, __ATOMIC_RELEASE);
    float blur_px = fmaxf(0.0f, (float)src->blur * ld_prismal_get_density());
    uint32_t bits = 0;
    memcpy(&bits, &blur_px, sizeof bits);
    __atomic_store_n(&g_prismal_blur_px_bits, bits, __ATOMIC_RELEASE);
    __atomic_store_n(&g_prismal_opts_initialized, 1, __ATOMIC_RELEASE);
}

static const struct ld_prismal_options *ld_prismal_read_opts(void)
{
    if (!__atomic_load_n(&g_prismal_opts_initialized, __ATOMIC_ACQUIRE)) {
        pthread_mutex_lock(&g_prismal_conf_lock);
        if (!g_prismal_opts_initialized) {
            struct ld_prismal_options def;
            ld_prismal_options_defaults(&def);
            ld_prismal_publish_opts_locked(&def);
        }
        pthread_mutex_unlock(&g_prismal_conf_lock);
    }
    unsigned slot = __atomic_load_n(&g_prismal_opts_slot, __ATOMIC_ACQUIRE) & 1u;
    return &g_prismal_opts_slots[slot];
}

static void ld_prismal_maybe_reload(void)
{
    static __thread unsigned tick;
    if ((tick++ & 63u) != 0u) return;

    static pthread_mutex_t reload_lock = PTHREAD_MUTEX_INITIALIZER;
    static int64_t last_check_ns;
    static time_t last_mtime;
    static long last_mtimensec;
    static off_t last_size;

    struct timespec now = {0, 0};
    clock_gettime(CLOCK_MONOTONIC, &now);
    int64_t now_ns = (int64_t)now.tv_sec * 1000000000LL + (int64_t)now.tv_nsec;
    int64_t prev_ns = __atomic_load_n(&last_check_ns, __ATOMIC_RELAXED);
    if (prev_ns != 0 && (now_ns - prev_ns) < 250000000LL) return;

    if (pthread_mutex_trylock(&reload_lock) != 0) return;
    __atomic_store_n(&last_check_ns, now_ns, __ATOMIC_RELAXED);

    struct stat st;
    const char *path = LD_PRISMAL_CONF_PRIMARY;
    if (stat(path, &st) != 0) {
        path = LD_PRISMAL_CONF_EARLY;
        if (stat(path, &st) != 0) {
            pthread_mutex_unlock(&reload_lock);
            return;
        }
    }
    if (st.st_mtime != last_mtime || st.st_mtim.tv_nsec != last_mtimensec || st.st_size != last_size) {
        struct ld_grid_conf conf;
        if (ld_grid_conf_parse_file(path, &conf)) {
            pthread_mutex_lock(&g_prismal_conf_lock);
            ld_prismal_publish_opts_locked(&conf.prismal);
            pthread_mutex_unlock(&g_prismal_conf_lock);
            __atomic_store_n(&g_prismal_no_downsample,
                             conf.prismal.disable_stationary_downsample ? 1 : 0,
                             __ATOMIC_RELEASE);
            __atomic_store_n(&g_prismal_motion_downsample,
                             conf.prismal.motion_downsample ? 1 : 0,
                             __ATOMIC_RELEASE);
            __atomic_store_n(&g_prismal_icon_downsample,
                             conf.prismal.icon_downsample ? 1 : 0,
                             __ATOMIC_RELEASE);
            __atomic_store_n(&g_prismal_icon_fixed_wallpaper,
                             conf.prismal.icon_fixed_wallpaper ? 1 : 0,
                             __ATOMIC_RELEASE);
#ifdef LD_GLASS_SUPERELLIPSE_H
            g_ld_gse_large_folder_corner = (float)conf.large_folder_corner;
            g_ld_gse_large_folder_2x1_corner = (float)conf.large_folder_2x1_corner;
            g_ld_gse_widget_corner = (float)conf.prismal.widget_corner;
#endif
#ifdef LD_OS4_GLASS_GATES_H
            g_widget_enforced_radius_override = conf.prismal.widget_corner > 0.0 ? conf.prismal.widget_corner : 0.0;
            __atomic_store_n(&g_os4_recents_blur_percent,
                    (uint32_t)(conf.recents_blur_percent >= 0 && conf.recents_blur_percent <= 100 ? conf.recents_blur_percent : 100),
                    __ATOMIC_RELEASE);
            __atomic_store_n(&g_os4_allapps_blur_percent,
                    (uint32_t)(conf.allapps_blur_percent >= 0 && conf.allapps_blur_percent <= 100 ? conf.allapps_blur_percent : 100),
                    __ATOMIC_RELEASE);
            __atomic_store_n(&g_os4_ws_allapps_blur_percent,
                    (uint32_t)(conf.ws_allapps_blur_percent >= 0 && conf.ws_allapps_blur_percent <= 100 ? conf.ws_allapps_blur_percent : 100),
                    __ATOMIC_RELEASE);
            __atomic_store_n(&g_os4_folder_blur_percent,
                    (uint32_t)(conf.folder_blur_percent >= 0 && conf.folder_blur_percent <= 100 ? conf.folder_blur_percent : 100),
                    __ATOMIC_RELEASE);
            __atomic_store_n(&g_os4_shortcut_blur_percent,
                    (uint32_t)(conf.shortcut_blur_percent >= 0 && conf.shortcut_blur_percent <= 100 ? conf.shortcut_blur_percent : 100),
                    __ATOMIC_RELEASE);
#endif
            last_mtime = st.st_mtime;
            last_mtimensec = st.st_mtim.tv_nsec;
            last_size = st.st_size;
        }
    }
    pthread_mutex_unlock(&reload_lock);
}

static inline float ld_prismal_current_blur_px(void)
{
    uint32_t bits = __atomic_load_n(&g_prismal_blur_px_bits, __ATOMIC_RELAXED);
    float v = 0.0f;
    memcpy(&v, &bits, sizeof v);
    return v;
}

static inline void ld_sp_add_shared(uint8_t *ctrl)
{
    if (ctrl) {
        __atomic_fetch_add((int64_t *)(ctrl + 8), 1, __ATOMIC_RELAXED);
    }
}

static inline void ld_sp_release_shared(uint8_t *ctrl)
{
    if (!ctrl) return;
    if (__atomic_fetch_sub((int64_t *)(ctrl + 8), 1, __ATOMIC_ACQ_REL) == 0) {
        void **vt = *(void ***)ctrl;
        ((void (*)(void *))vt[2])(ctrl);
        if (__atomic_fetch_sub((int64_t *)(ctrl + 16), 1, __ATOMIC_ACQ_REL) == 0) {
            ((void (*)(void *))vt[4])(ctrl);
        }
    }
}

static inline void ld_write_f32(uint8_t *base, size_t off, float v) {
    memcpy(base + off, &v, 4);
}
static inline void ld_write_u32(uint8_t *base, size_t off, uint32_t v) {
    memcpy(base + off, &v, 4);
}
static inline void ld_write_vec2(uint8_t *base, size_t off, float x, float y) {
    ld_write_f32(base, off, x);
    ld_write_f32(base, off + 4u, y);
}
static inline void ld_write_vec3(uint8_t *base, size_t off, float x, float y, float z) {
    ld_write_f32(base, off, x);
    ld_write_f32(base, off + 4u, y);
    ld_write_f32(base, off + 8u, z);
}
static inline void ld_write_vec4(uint8_t *base, size_t off, float x, float y, float z, float w) {
    ld_write_f32(base, off, x);
    ld_write_f32(base, off + 4u, y);
    ld_write_f32(base, off + 8u, z);
    ld_write_f32(base, off + 12u, w);
}

static void ld_prismal_fill_ubo(uint8_t *ubo, const struct ld_prismal_options *opt,
                                float density, int has_sharp_tex)
{
    float gx = 0.0f, gy = 0.0f, gz = 0.0f, orig_edge_px = 18.0f;
    memcpy(&gx, ubo + 0x10u, 4);
    memcpy(&gy, ubo + 0x14u, 4);
    memcpy(&gz, ubo + 0x18u, 4);
    memcpy(&orig_edge_px, ubo + 0x1cu, 4);
    if (!(orig_edge_px > 0.0f && orig_edge_px < 2000.0f)) orig_edge_px = 18.0f;

    float d = density > 0.1f ? density : 2.75f;
    float pad_px = g_tls_expand_pad_px > 0.0f ? g_tls_expand_pad_px : 0.0f;
    float eff_gx = fmaxf(gx - 2.0f * pad_px, 2.0f);
    float eff_gy = fmaxf(gy - 2.0f * pad_px, 2.0f);
    float min_glass_dim = (eff_gx > 1.0f && eff_gy > 1.0f) ? fminf(eff_gx, eff_gy) : 100.0f;
    float max_glass_dim = (eff_gx > 1.0f && eff_gy > 1.0f) ? fmaxf(eff_gx, eff_gy) : 100.0f;
    int is_dock = 0;
    int is_widget_tls = 0;
    int is_large_folder_tls = 0;
#ifdef LD_GLASS_SUPERELLIPSE_H
    is_dock = g_ld_gse_tls_is_dock;
    is_widget_tls = g_ld_gse_tls_is_widget;
    is_large_folder_tls = g_ld_gse_tls_is_large_folder;
#endif
    /* Preserve stock full-circle widgets (e.g. 2x2 circular clock widget) and Dock,
     * while allowing elongated 2x1/1x2 large folders (aspect > 1.35). */
    int is_circular = (max_glass_dim <= min_glass_dim * 1.35f) &&
                      (gz >= min_glass_dim * 0.45f);
    if (!is_circular && !is_dock) {
    if (is_large_folder_tls) {
        double clf = (min_glass_dim < 120.0f * d && opt->large_folder_2x1_corner > 0.0)
            ? opt->large_folder_2x1_corner : opt->large_folder_corner;
        if (clf > 0.0) {
            float cr_px = fminf((float)clf * d, min_glass_dim * 0.5f);
            ld_write_f32(ubo, 0x18u, cr_px);
        }
    } else if (is_widget_tls) {
        if (opt->widget_corner > 0.0) {
            float cr_px = fminf((float)opt->widget_corner * d, min_glass_dim * 0.5f);
            ld_write_f32(ubo, 0x18u, cr_px);
        }
    } else if (min_glass_dim >= 360.0f && max_glass_dim <= 2000.0f && (max_glass_dim / fmaxf(min_glass_dim, 1.0f)) <= 3.2f) {
        int is_2x2_folder_size = ld_gse_matches_recorded_large_folder(eff_gx / d, eff_gy / d) ||
                                 (fabsf(eff_gx - eff_gy) < 12.0f && fabsf(eff_gx - 169.7f * d) < 22.0f * d);
        int is_pre_overridden_widget = (opt->widget_corner > 0.0 && !is_2x2_folder_size &&
                                        fabsf(gz - (float)opt->widget_corner * d) < 1.5f * d);
        int is_large_folder = !is_pre_overridden_widget &&
                              ((gz > 15.2f * d) || is_2x2_folder_size);
        if (is_large_folder) {
            if (opt->large_folder_corner > 0.0) {
                float cr_px = fminf((float)opt->large_folder_corner * d, min_glass_dim * 0.5f);
                ld_write_f32(ubo, 0x18u, cr_px);
            }
        } else if (opt->widget_corner > 0.0) {
            float cr_px = fminf((float)opt->widget_corner * d, min_glass_dim * 0.5f);
            ld_write_f32(ubo, 0x18u, cr_px);
        }
    } else if (min_glass_dim >= 68.0f * d && max_glass_dim >= 115.0f * d && max_glass_dim <= 2000.0f &&
               (max_glass_dim / fmaxf(min_glass_dim, 1.0f)) <= 3.2f &&
               gz <= 15.2f * d) {
        /* 2x1 widgets and scaled-down 2x2 widgets during animations (scale < 1.0). */
        if (opt->widget_corner > 0.0) {
            float cr_px = fminf((float)opt->widget_corner * d, min_glass_dim * 0.5f);
            ld_write_f32(ubo, 0x18u, cr_px);
        }
    } else if (min_glass_dim >= 45.0f * d && min_glass_dim < 120.0f * d &&
               max_glass_dim >= 120.0f * d && max_glass_dim <= 360.0f * d &&
               (max_glass_dim / fmaxf(min_glass_dim, 1.0f)) <= 4.0f &&
               gz > 15.2f * d) {
        /* 2x1 / 1x2 / 3x1 窄条大文件夹（排除 Dock 条） */
        double c21 = opt->large_folder_2x1_corner > 0.0
            ? opt->large_folder_2x1_corner : opt->large_folder_corner;
        if (c21 > 0.0) {
            float cr_px = fminf((float)c21 * d, min_glass_dim * 0.5f);
            ld_write_f32(ubo, 0x18u, cr_px);
        }
    } else if (min_glass_dim >= 80.0f && max_glass_dim <= 340.0f && fabsf(eff_gx - eff_gy) < 12.0f) {
        if (opt->small_folder_corner > 0.0) {
            float cr_px = fminf((float)opt->small_folder_corner * d, min_glass_dim * 0.5f);
            ld_write_f32(ubo, 0x18u, cr_px);
        }
    }
    }
    (void)gz;

    float ior = ld_clampf((float)opt->ior, 1.0f, 3.0f);
    float dome = ld_clampf((float)opt->dome, 0.0f, 2.0f);
    float htw_px = fmaxf(1.0f, (float)opt->htw * d);
    float refraction_height = htw_px * (1.0f + 0.55f * dome);
    float rel_bend = ld_clampf((1.0f - 1.0f / fmaxf(1.0f, ior)) / (1.0f - 1.0f / 1.55f), 0.0f, 1.5f);
    float lens_raw = refraction_height * 2.0f * (float)opt->displacement_scale * (float)opt->lens_refraction * rel_bend;
    float lens_px = ld_clampf(lens_raw, 0.0f, fmaxf(4.0f, min_glass_dim * 0.85f));
    float lens_depth = opt->depth_effect > 0.0 ? ld_clampf((float)opt->depth_effect, 0.0f, 1.0f) : 0.0f;

    /* PrismalOs4InvariantShader.compute */
    float lx = (float)opt->light_dir_x;
    float ly = (float)opt->light_dir_y;
    float l1_len = sqrtf(lx * lx + ly * ly + 1.45f * 1.45f);
    if (l1_len < 1e-6f) l1_len = 1.0f;

    float lx2 = lx + 1e-5f, ly2 = ly + 1e-5f;
    float l2_len = sqrtf(lx2 * lx2 + ly2 * ly2);
    float lxy_x = l2_len > 0.0f ? (lx2 / l2_len) : 0.0f;
    float lxy_y = l2_len > 0.0f ? (ly2 / l2_len) : 0.0f;

    float l3_len = sqrtf(lxy_x * lxy_x + lxy_y * lxy_y + 1.0f);
    if (l3_len < 1e-6f) l3_len = 1.0f;

    float ratio = (1.0f - ior) / (1.0f + ior);
    float r0 = ratio * ratio;

    float shadow_t = ld_clampf((float)opt->shadow_softness / 20.0f, 0.0f, 1.0f);
    float shadow_ext = 0.15f + (0.60f - 0.15f) * shadow_t;

    float blur_px = fmaxf(0.0f, (float)opt->blur * d);
    uint32_t flags = (opt->continuous_corners ? 1u : 0u)
                   | ((blur_px >= 0.5f) ? 2u : 0u)
                   | (opt->show_normals ? 4u : 0u)
                   | (has_sharp_tex ? 8u : 0u)
                   | ((__atomic_load_n(&g_prismal_motion_downsample, __ATOMIC_RELAXED) &&
                       __atomic_load_n(&g_prismal_in_motion, __ATOMIC_RELAXED)) ? 16u : 0u);

    ld_write_vec2(ubo, 0x08u, (float)opt->backdrop_scale_x, (float)opt->backdrop_scale_y);
    ld_write_f32(ubo, 0x1cu, fmaxf(0.0f, (float)opt->refraction_inset));
    ld_write_f32(ubo, 0x20u, fmaxf(0.0f, (float)opt->smin_smoothing));
    ld_write_f32(ubo, 0x24u, fmaxf(0.05f, (float)opt->edge_falloff));
    ld_write_f32(ubo, 0x28u, ior);
    ld_write_vec3(ubo, 0x30u, lx / l1_len, ly / l1_len, 1.45f / l1_len);
    ld_write_f32(ubo, 0x3cu, fmaxf(0.0f, (float)opt->thickness * d));
    ld_write_f32(ubo, 0x40u, (float)opt->normal_strength);
    ld_write_f32(ubo, 0x44u, (float)opt->displacement_scale);
    ld_write_f32(ubo, 0x48u, htw_px);
    ld_write_f32(ubo, 0x4cu, lens_px);
    ld_write_f32(ubo, 0x50u, lens_depth);
    ld_write_f32(ubo, 0x54u, fmaxf(0.0f, (float)opt->chromatic));
    ld_write_f32(ubo, 0x58u, (float)opt->dispersion_r);
    ld_write_f32(ubo, 0x5cu, (float)opt->dispersion_b);
    ld_write_vec4(ubo, 0x60u,
                  (float)opt->tint_r / 255.0f,
                  (float)opt->tint_g / 255.0f,
                  (float)opt->tint_b / 255.0f,
                  (float)opt->tint_a / 255.0f);
    ld_write_f32(ubo, 0x70u, (float)opt->vibrancy);
    ld_write_f32(ubo, 0x74u, (float)opt->brightness);
    ld_write_f32(ubo, 0x78u, dome);
    if (g_tls_is_small_icon && opt->icon_return_fade) {
        float fade_a = ld_prismal_icon_return_fade(
            g_tls_ubo_orig_ox + g_tls_entity_tx,
            g_tls_ubo_orig_oy + g_tls_entity_ty);
        if (fade_a < 0.999f) {
            float cur_alpha = 1.0f;
            memcpy(&cur_alpha, ubo + 0x7cu, sizeof cur_alpha);
            if (!isfinite(cur_alpha)) cur_alpha = 1.0f;
            ld_write_f32(ubo, 0x7cu, cur_alpha * ld_clampf(fade_a, 0.0f, 1.0f));
        }
    }
    ld_write_u32(ubo, 0x94u, flags);
    ld_write_f32(ubo, 0x98u, (float)opt->fresnel_reflect);
    ld_write_f32(ubo, 0x9cu, r0);
    ld_write_vec2(ubo, 0xa0u, lxy_x, lxy_y);
    ld_write_vec2(ubo, 0xa8u, (float)opt->parallax_scale, orig_edge_px);
    ld_write_vec3(ubo, 0xb0u, lxy_x / l3_len, lxy_y / l3_len, 1.0f / l3_len);
    ld_write_f32(ubo, 0xbcu, fmaxf(0.0f, (float)opt->caustics));
    ld_write_vec4(ubo, 0xc0u,
                  (float)opt->os4_edge_width_px,
                  (float)opt->os4_reflect_offset_px,
                  (float)opt->os4_reflection_strength,
                  (float)opt->os4_reflection_lighten);
    ld_write_vec4(ubo, 0xd0u,
                  (float)opt->os4_directional_angle_range,
                  (float)opt->os4_directional_intensity,
                  (float)opt->os4_directional_opposite_intensity,
                  (float)opt->highlight_width);
    ld_write_vec4(ubo, 0xe0u,
                  (float)opt->base_r / 255.0f,
                  (float)opt->base_g / 255.0f,
                  (float)opt->base_b / 255.0f,
                  (float)opt->base_a / 255.0f);
    ld_write_vec4(ubo, 0xf0u,
                  (float)opt->shadow_r / 255.0f,
                  (float)opt->shadow_g / 255.0f,
                  (float)opt->shadow_b / 255.0f,
                  (float)opt->shadow_a / 255.0f);
    ld_write_vec4(ubo, 0x100u, shadow_ext,
                  ld_clampf((float)opt->transmittance, 0.0f, 1.0f),
                  fmaxf(0.0f, (float)opt->lens_refraction), 0.0f);
    float backlit_ext = ld_clampf((float)opt->backlit_extent, 0.0f, 1.0f);
    float backlit_soft = ld_clampf((float)opt->backlit_softness, 0.05f, 2.0f);
    float side_start = (backlit_ext <= 0.0f) ? 2.0f : ld_clampf(0.65f - 0.55f * backlit_ext, 0.10f, 0.65f);
    float side_span = ld_clampf(0.19f + 0.60f * fminf(1.0f, backlit_soft * 0.5f), 0.12f, 0.78f);
    int backlit_on = (g_tls_is_small_icon && opt->icon_backlit_enabled >= 0)
        ? opt->icon_backlit_enabled : opt->backlit_enabled;
    ld_write_vec4(ubo, 0x110u,
                  (float)opt->backlit_width * d,
                  backlit_on ? (float)opt->backlit_strength : 0.0f,
                  side_start, side_span);
    int allow_outer_shadow = opt->outer_shadow_enabled && !g_tls_is_small_icon;
    ld_write_vec4(ubo, 0x120u,
                  (float)opt->outer_shadow_r / 255.0f,
                  (float)opt->outer_shadow_g / 255.0f,
                  (float)opt->outer_shadow_b / 255.0f,
                  allow_outer_shadow ? (float)opt->outer_shadow_a / 255.0f : 0.0f);
    ld_write_vec4(ubo, 0x130u,
                  allow_outer_shadow ? (float)opt->outer_shadow_radius * d : 0.0f,
                  (float)opt->outer_shadow_offset_y * d,
                  ld_clampf((float)opt->outer_shadow_light_follow, 0.0f, 1.0f),
                  pad_px);
}

__attribute__((used)) static int ld_should_elide_icon_savelayer(
    const void *paint, const void *bounds_opt, const void *backdrop, int bounds_prom, int can_elide)
{
    if (backdrop != NULL || !paint || !bounds_opt) return 0;
    if (can_elide && bounds_prom != 2) return 0;
    if (!__atomic_load_n(&g_prismal_glass_active, __ATOMIC_RELAXED)) return 0;
    const uint8_t *p = (const uint8_t *)paint;
    if (p[0x3cu] != 3u || p[0x3du] != 0u || (p[0x8cu] & 1u) != 0u) return 0;
    if (*(void *const *)(p + 0x20u) != NULL || *(void *const *)(p + 0x18u) != NULL) return 0;
    float alpha = 1.0f;
    memcpy(&alpha, p + 0x0cu, sizeof alpha);
    if (!(alpha >= 0.99f)) return 0;

    const uint8_t *bo = (const uint8_t *)bounds_opt;
    if (bo[16] == 0u) return 0;
    float r[4];
    memcpy(r, bo, sizeof r);
    if (!isfinite(r[0]) || !isfinite(r[1]) || !isfinite(r[2]) || !isfinite(r[3])) return 0;
    float w = fabsf(r[2] - r[0]);
    float h = fabsf(r[3] - r[1]);
    /* Match 1x1 icon EntityPass Clip SaveLayer (58.1dp..85.3dp or 167px square; never 2x2 folder/widget). */
    int is_icon_dp = (w >= 40.0f && w <= 110.0f && h >= 40.0f && h <= 110.0f && fabsf(w - h) <= 10.0f);
    int is_icon_px = (w >= 120.0f && w <= 210.0f && h >= 120.0f && h <= 210.0f && fabsf(w - h) <= 12.0f);
    if (!is_icon_dp && !is_icon_px) return 0;

    static volatile unsigned elided_logs;
    if (__atomic_fetch_add(&elided_logs, 1u, __ATOMIC_RELAXED) < 6u) {
        ld_blog("prismal SaveLayer elided 1x1 icon EntityPass Clip: wh=%.1fx%.1f prom=%d can=%d",
                (double)w, (double)h, bounds_prom, can_elide);
    }
    return 1;
}

#if defined(__aarch64__)
__attribute__((naked)) static void repl_canvas_savelayer(void)
{
    __asm__ volatile(
        "cbnz x3, 1f\n"
        "stp x29, x30, [sp, #-0x60]!\n"
        "stp x0, x1, [sp, #0x10]\n"
        "stp x2, x3, [sp, #0x20]\n"
        "stp x4, x5, [sp, #0x30]\n"
        "stp x6, x7, [sp, #0x40]\n"
        "mov x0, x1\n"
        "mov x1, x2\n"
        "mov x2, x3\n"
        "mov w3, w4\n"
        "mov w4, w6\n"
        "bl ld_should_elide_icon_savelayer\n"
        "mov w9, w0\n"
        "ldp x0, x1, [sp, #0x10]\n"
        "ldp x2, x3, [sp, #0x20]\n"
        "ldp x4, x5, [sp, #0x30]\n"
        "ldp x6, x7, [sp, #0x40]\n"
        "ldp x29, x30, [sp], #0x60\n"
        "cbz w9, 1f\n"
        "mov w4, #0\n"
        "mov w6, #1\n"
        "1:\n"
        "adrp x16, g_orig_canvas_savelayer\n"
        "ldr x16, [x16, :lo12:g_orig_canvas_savelayer]\n"
        "br x16\n"
    );
}

static __thread ld_sp_t g_tls_cached_wp_tex;
static __thread int g_tls_in_glass_acquire;

__attribute__((used)) static int ld_compose_should_upscale(void)
{
    return g_tls_in_glass_acquire &&
           __atomic_load_n(&g_prismal_no_downsample, __ATOMIC_RELAXED);
}

__attribute__((used)) static const ld_sp_t *ld_compose_resolve_wp(const ld_sp_t *snap, const ld_sp_t *wp)
{
    if (!g_tls_in_glass_acquire) return wp;
    if (wp && wp->ptr) {
        if (wp->ptr != g_tls_cached_wp_tex.ptr) {
            ld_sp_t old = g_tls_cached_wp_tex;
            g_tls_cached_wp_tex = *wp;
            ld_sp_add_shared(g_tls_cached_wp_tex.ctrl);
            ld_sp_release_shared(old.ctrl);
        }
        return wp;
    }
    if (snap && snap->ptr && g_tls_cached_wp_tex.ptr) {
        return &g_tls_cached_wp_tex;
    }
    return wp;
}

__attribute__((naked)) static void repl_compose_textures(void)
{
    __asm__ volatile(
        "stp x29, x30, [sp, #-0x80]!\n"
        "mov x29, sp\n"
        "stp x0, x1, [sp, #0x10]\n"
        "stp x2, x3, [sp, #0x20]\n"
        "stp x4, x5, [sp, #0x30]\n"
        "stp x6, x7, [sp, #0x40]\n"
        "str x8, [sp, #0x50]\n"
        "stp s0, s1, [sp, #0x58]\n"
        "mov x0, x1\n"
        "mov x1, x2\n"
        "bl ld_compose_resolve_wp\n"
        "mov x9, x0\n"
        "str x9, [sp, #0x60]\n"
        "bl ld_compose_should_upscale\n"
        "mov w10, w0\n"
        "ldr x9, [sp, #0x60]\n"
        "ldp x0, x1, [sp, #0x10]\n"
        "ldp x2, x3, [sp, #0x20]\n"
        "ldp x4, x5, [sp, #0x30]\n"
        "ldp x6, x7, [sp, #0x40]\n"
        "ldr x8, [sp, #0x50]\n"
        "ldp s0, s1, [sp, #0x58]\n"
        "ldp x29, x30, [sp], #0x80\n"
        "mov x2, x9\n"
        "cbz w10, 1f\n"
        "fmov s0, #1.00000000\n"
        "1:\n"
        "adrp x16, g_orig_compose_textures\n"
        "ldr x16, [x16, :lo12:g_orig_compose_textures]\n"
        "br x16\n"
    );
}
#endif

static void repl_make_base_blur(ld_sp_t *sret, void *ctx, const ld_sp_t *src_tex,
                                ld_sp_t *out_opt, const void *cmd_buf, float blur, float scale)
{
    if (__atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE) && blur < 0.5f) {
        if (sret) { sret->ptr = NULL; sret->ctrl = NULL; }
        if (out_opt) { out_opt->ptr = NULL; out_opt->ctrl = NULL; }
        return;
    }
    if (__atomic_load_n(&g_prismal_no_downsample, __ATOMIC_ACQUIRE) &&
        scale > 0.0f && scale < 1.0f) {
        scale = 1.0f;
    }
    g_orig_make_base_blur(sret, ctx, src_tex, out_opt, cmd_buf, blur, scale);
}

static void repl_make_reflect_blur(ld_sp_t *sret, void *ctx, const ld_sp_t *src_tex,
                                   ld_sp_t *out_opt, const void *cmd_buf, float blur, float scale)
{
    if (__atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE) && blur < 0.5f) {
        if (sret) { sret->ptr = NULL; sret->ctrl = NULL; }
        if (out_opt) { out_opt->ptr = NULL; out_opt->ctrl = NULL; }
        return;
    }
    if (__atomic_load_n(&g_prismal_no_downsample, __ATOMIC_ACQUIRE) &&
        scale > 0.0f && scale < 1.0f) {
        scale = 1.0f;
    }
    g_orig_make_reflect_blur(sret, ctx, src_tex, out_opt, cmd_buf, blur, scale);
}

static void repl_make_tinted_from_base(ld_sp_t *sret, void *ctx, const ld_sp_t *base_tex,
                                       const ld_sp_t *refract_tex, const void *tint_cfg,
                                       const void *size, const void *cmd_buf, float blur)
{
    if (__atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE)) {
        const ld_sp_t *pick = (base_tex && base_tex->ptr) ? base_tex
                            : ((refract_tex && refract_tex->ptr) ? refract_tex : NULL);
        if (sret) {
            if (pick) {
                *sret = *pick;
                ld_sp_add_shared(sret->ctrl);
            } else {
                sret->ptr = NULL;
                sret->ctrl = NULL;
            }
        }
        return;
    }
    g_orig_make_tinted_from_base(sret, ctx, base_tex, refract_tex, tint_cfg, size, cmd_buf, blur);
}

static uint8_t repl_glass_contents_render(void *self, const void *ctx, const void *entity, void *pass)
{
    uintptr_t prev = g_tls_glass_contents;
    int prev_sharp = g_tls_bound_sharp_tex;
    float prev_pad_px = g_tls_expand_pad_px;
    float prev_tx = g_tls_entity_tx, prev_ty = g_tls_entity_ty;
    int prev_small = g_tls_is_small_icon;
    g_tls_glass_contents = (uintptr_t)self;
    g_tls_bound_sharp_tex = 0;
    g_tls_expand_pad_px = 0.0f;
    g_tls_entity_tx = 0.0f;
    g_tls_entity_ty = 0.0f;
    g_tls_is_small_icon = 0;

    int expanded_rect = 0, expanded_alt = 0, expanded_geo = 0;
    float orig_rect[4] = {0, 0, 0, 0};
    float orig_alt[4] = {0, 0, 0, 0};
    float orig_geo[2] = {0, 0};
    if (self && __atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE)) {
        ld_prismal_update_motion(pass);
        ld_prismal_maybe_reload();
        const struct ld_prismal_options *opts = ld_prismal_read_opts();
        float d = ld_prismal_get_density();
        memcpy(orig_geo, (const uint8_t *)self + 0x1c8u, sizeof orig_geo);
        g_tls_is_small_icon = (isfinite(orig_geo[0]) && isfinite(orig_geo[1]) &&
                               orig_geo[0] > 8.0f && orig_geo[1] > 8.0f &&
                               fmaxf(orig_geo[0], orig_geo[1]) < 115.0f * d &&
                               fabsf(orig_geo[0] - orig_geo[1]) < 16.0f * d);
        if (entity) {
            float m[16];
            memcpy(m, entity, sizeof m);
            if (isfinite(m[12]) && isfinite(m[13]) && fabsf(m[15] - 1.0f) < 0.05f) {
                g_tls_entity_tx = m[12];
                g_tls_entity_ty = m[13];
                if (g_tls_is_small_icon) {
                    float sx = sqrtf(m[0] * m[0] + m[1] * m[1]);
                    float sy = sqrtf(m[4] * m[4] + m[5] * m[5]);
                    float sc = fminf(sx, sy);
                    g_tls_icon_scale = (isfinite(sc) && sc > 0.1f && sc < 4.0f) ? sc : 1.0f;
                } else {
                    g_tls_icon_scale = 1.0f;
                }
            } else {
                g_tls_icon_scale = 1.0f;
            }
            if (g_tls_is_small_icon) {
                float alt_w = 0.0f;
                memcpy(&alt_w, (const uint8_t *)self + 0x1e0u, sizeof alt_w);
                if (isfinite(alt_w) && alt_w > 10.0f && alt_w < 200.0f) {
                    float geo_sc = orig_geo[0] / (alt_w * d);
                    if (isfinite(geo_sc) && geo_sc > 0.1f && geo_sc < g_tls_icon_scale) {
                        g_tls_icon_scale = geo_sc;
                    }
                }
            }
        } else {
            g_tls_icon_scale = 1.0f;
        }
        float pad_dp = 0.0f;
        if (opts->outer_shadow_enabled && opts->outer_shadow_radius > 0.0 && opts->outer_shadow_a > 0.0) {
            float sh = (float)opts->outer_shadow_radius * 2.0f + fabsf((float)opts->outer_shadow_offset_y) + 2.0f;
            if (sh > pad_dp) pad_dp = fminf(sh, 36.0f);
        }
        if (opts->backlit_enabled && opts->backlit_strength > 0.0 && opts->backlit_width > 0.0) {
            float st = (float)opts->backlit_width + 2.0f;
            if (st > pad_dp) pad_dp = fminf(st, 10.0f);
        }
        float taskbar_lr_ov_dp = 0.0f;
        float taskbar_b_ov_dp = 0.0f;
#ifdef LD_WORKSTATION_H
        if (__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED) &&
            g_home_options.ws.dock_custom && g_home_options.ws.taskbar_mode &&
            isfinite(orig_geo[0]) && isfinite(orig_geo[1]) &&
            orig_geo[1] >= 24.0f * d && orig_geo[1] <= 240.0f * d) {
            float r_chk[4];
            memcpy(r_chk, (const uint8_t *)self + 0x28u, sizeof r_chk);
            float rect_max = fmaxf(fabsf(r_chk[2] - r_chk[0]), fabsf(r_chk[3] - r_chk[1]));
            double dw = ld_ws_device_width();
            float vp_w = (dw > 100.0) ? (float)(dw * d) : 0.0f;
            if (isfinite(rect_max) && rect_max > vp_w && rect_max < 8192.0f) vp_w = rect_max;
            /* In Workstation taskbar mode, always extend the bottom off-screen
             * (hiding the bottom glass bevel/corners) on both Desktop and AllApps.
             * In AllApps, where horizontal bounds are clipped to [0, vp_w], also
             * extend left and right off-screen. */
            if (vp_w > 300.0f && orig_geo[0] >= vp_w - 40.0f * d) {
                taskbar_b_ov_dp = 24.0f;
                if (orig_geo[0] <= vp_w + 16.0f * d) {
                    taskbar_lr_ov_dp = 24.0f;
                }
            }
        }
#endif
        if (pad_dp > 0.0f || taskbar_lr_ov_dp > 0.0f || taskbar_b_ov_dp > 0.0f) {
            memcpy(orig_rect, (const uint8_t *)self + 0x28u, sizeof orig_rect);
            memcpy(orig_alt, (const uint8_t *)self + 0x1d8u, sizeof orig_alt);
            memcpy(orig_geo, (const uint8_t *)self + 0x1c8u, sizeof orig_geo);
            int use_alt = (isfinite(orig_alt[2]) && isfinite(orig_alt[3]) &&
                           orig_alt[2] > 8.0f && orig_alt[3] > 8.0f &&
                           orig_alt[2] < 4000.0f && orig_alt[3] < 4000.0f);
            float w = use_alt ? orig_alt[2] : (orig_rect[2] - orig_rect[0]);
            float h = use_alt ? orig_alt[3] : (orig_rect[3] - orig_rect[1]);
            if (isfinite(w) && isfinite(h) && w > 8.0f && h > 8.0f && w < 4000.0f && h < 4000.0f &&
                isfinite(orig_geo[0]) && isfinite(orig_geo[1]) && orig_geo[0] > 8.0f && orig_geo[1] > 8.0f) {
                float eff_pad_dp = pad_dp;
                if (g_tls_is_small_icon) {
                    /* 1x1 icons render inside a tight 167x167 EntityPass Clip RT; outer shadow
                     * cannot extend past the 4 straight RT edges and would clip to 4 corner triangles. */
                    eff_pad_dp = 0.0f;
                }
                if (eff_pad_dp > 0.0f || taskbar_lr_ov_dp > 0.0f || taskbar_b_ov_dp > 0.0f) {
                    float pad_px = eff_pad_dp * d;
                    float lr_px = taskbar_lr_ov_dp * d;
                    float b_px = taskbar_b_ov_dp * d;
                    float qscale = orig_geo[0] / w;
                    if (!(qscale > 0.25f && qscale < 8.0f)) qscale = d;
                    float pad_q = pad_px / qscale;
                    float lr_q = lr_px / qscale;
                    float b_q = b_px / qscale;
                    if (use_alt) {
                        float exp_alt[4] = {
                            orig_alt[0] - pad_q - lr_q,
                            orig_alt[1] - pad_q,
                            orig_alt[2] + 2.0f * (pad_q + lr_q),
                            orig_alt[3] + 2.0f * pad_q + b_q
                        };
                        memcpy((uint8_t *)self + 0x1d8u, exp_alt, sizeof exp_alt);
                        expanded_alt = 1;
                    } else {
                        float exp_rect[4] = {
                            orig_rect[0] - pad_q - lr_q,
                            orig_rect[1] - pad_q,
                            orig_rect[2] + pad_q + lr_q,
                            orig_rect[3] + pad_q + b_q
                        };
                        memcpy((uint8_t *)self + 0x28u, exp_rect, sizeof exp_rect);
                        expanded_rect = 1;
                    }
                    float exp_geo[2] = {
                        orig_geo[0] + 2.0f * (pad_px + lr_px),
                        orig_geo[1] + 2.0f * pad_px + b_px
                    };
                    memcpy((uint8_t *)self + 0x1c8u, exp_geo, sizeof exp_geo);
                    expanded_geo = 1;
                    g_tls_expand_pad_px = pad_px;
                }
            }
        }
    }

    int scissor_widened = 0;
    uint64_t saved_sc_lt = 0, saved_sc_rb = 0;
    if (expanded_geo && pass && g_orig_set_scissor) {
        if (g_tls_scissor_pass == pass) {
            saved_sc_lt = g_tls_scissor_lt;
            saved_sc_rb = g_tls_scissor_rb;
            scissor_widened = 1;
        }
        g_orig_set_scissor(pass, 0ull, ((uint64_t)4096u << 32) | 4096u);
    }

    uint8_t ret = g_orig_glass_contents_render(self, ctx, entity, pass);

    if (scissor_widened && g_orig_set_scissor) {
        g_orig_set_scissor(pass, saved_sc_lt, saved_sc_rb);
    }

    if (expanded_alt)  memcpy((uint8_t *)self + 0x1d8u, orig_alt, sizeof orig_alt);
    if (expanded_rect) memcpy((uint8_t *)self + 0x28u, orig_rect, sizeof orig_rect);
    if (expanded_geo)  memcpy((uint8_t *)self + 0x1c8u, orig_geo, sizeof orig_geo);
    g_tls_is_small_icon = prev_small;
    g_tls_entity_tx = prev_tx;
    g_tls_entity_ty = prev_ty;
    g_tls_expand_pad_px = prev_pad_px;
    g_tls_bound_sharp_tex = prev_sharp;
    g_tls_glass_contents = prev;
    return ret;
}

static uint8_t repl_bind_mask_tex(void *binder, ld_sp_t *tex_sp, void *sampler)
{
    if (__atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE) && g_tls_glass_contents) {
        void *refract_ptr = *(void **)(g_tls_glass_contents + 0x138u);
        ld_sp_t bg_sp = *(const ld_sp_t *)(g_tls_glass_contents + 0x148u);
        if (tex_sp && tex_sp->ptr == refract_ptr && bg_sp.ptr != NULL) {
            ld_sp_t sharp_sp = bg_sp;
            ld_sp_add_shared(sharp_sp.ctrl);
            g_tls_bound_sharp_tex = 1;
            return g_orig_bind_mask_tex(binder, &sharp_sp, sampler);
        }
    }
    g_tls_bound_sharp_tex = 0;
    return g_orig_bind_mask_tex(binder, tex_sp, sampler);
}

static uint8_t repl_bind_ubo(void *binder, void *buffer_view)
{
    if (__atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE) &&
        buffer_view && g_fn_devbuf_get_contents) {
        const uint8_t *bv = (const uint8_t *)buffer_view;
        void *dev_buf = *(void *const *)(bv + 0x00u);
        if (!dev_buf) dev_buf = *(void *const *)(bv + 0x10u);
        uint64_t offset = *(const uint64_t *)(bv + 0x18u);
        uint64_t length = *(const uint64_t *)(bv + 0x20u);
        if (dev_buf && length == 0x180u) {
            uint8_t *mapped = g_fn_devbuf_get_contents(dev_buf);
            if (mapped) {
                uint8_t *ubo = mapped + offset;
                const struct ld_prismal_options *opts = ld_prismal_read_opts();
                float density = ld_prismal_get_density();
                int sharp = g_tls_bound_sharp_tex;

                float orig_ox = 0.0f, orig_oy = 0.0f;
                memcpy(&orig_ox, ubo + 0x00u, sizeof orig_ox);
                memcpy(&orig_oy, ubo + 0x04u, sizeof orig_oy);
                g_tls_ubo_orig_ox = isfinite(orig_ox) ? orig_ox : 0.0f;
                g_tls_ubo_orig_oy = isfinite(orig_oy) ? orig_oy : 0.0f;

                static volatile unsigned logged;
                if (__atomic_fetch_add(&logged, 1u, __ATOMIC_RELAXED) == 0) {
                    float gx = 0.0f, gy = 0.0f, gz = 0.0f, ix = 0.0f, iy = 0.0f, sx = 0.0f, sy = 0.0f;
                    uint32_t um = 0;
                    int32_t cs = 0;
                    memcpy(&gx, ubo + 0x10u, 4); memcpy(&gy, ubo + 0x14u, 4); memcpy(&gz, ubo + 0x18u, 4);
                    memcpy(&ix, ubo + 0x80u, 4); memcpy(&iy, ubo + 0x84u, 4);
                    memcpy(&sx, ubo + 0x88u, 4); memcpy(&sy, ubo + 0x8cu, 4);
                    memcpy(&um, ubo + 0x90u, 4); memcpy(&cs, ubo + 0x160u, 4);
                    ld_blog("prismal BindUBO first: geo=(%.1f,%.1f,%.1f) invRes=(%.6f,%.6f) scale=(%.2f,%.2f) mask=%u cs=%d density=%.2f sharp=%d blur=%.1f",
                            gx, gy, gz, ix, iy, sx, sy, um, cs, density, sharp, opts->blur);
                }

                ld_prismal_fill_ubo(ubo, opts, density, sharp);
                if (g_fn_devbuf_flush) {
                    ld_opt_range_t range = { offset, length, 1u, {0} };
                    g_fn_devbuf_flush(dev_buf, &range);
                }
            }
        }
    }
    return g_orig_bind_ubo(binder, buffer_view);
}

static const char *ld_read_libcxx_str(const uint8_t *s, size_t *out_len) {
    if (!s) return NULL;
    uint8_t tag = s[0];
    if ((tag & 1u) == 0u) {
        if (out_len) *out_len = tag >> 1;
        return (const char *)(s + 1);
    } else {
        size_t len = *(const size_t *)(s + 8);
        const char *ptr = *(const char *const *)(s + 16);
        if (out_len) *out_len = len;
        return ptr;
    }
}

static uintptr_t ld_find_frag_shader_in_desc(uintptr_t desc, const char **out_name, size_t *out_nlen) {
    if (!desc) return 0;
    uintptr_t end_node = desc + 0x38u;
    uintptr_t node = 0;
    memcpy(&node, (const void *)(desc + 0x30u), sizeof node);
    for (unsigned steps = 0; steps < 64 && node && node != end_node; steps++) {
        uint32_t stage = 0;
        memcpy(&stage, (const void *)(node + 0x20u), sizeof stage);
        uintptr_t fn = 0;
        memcpy(&fn, (const void *)(node + 0x28u), sizeof fn);
        if (stage == 2u && fn) {
            if (out_name) *out_name = ld_read_libcxx_str((const uint8_t *)(fn + 0x10u), out_nlen);
            return fn;
        }
        uintptr_t right = 0;
        memcpy(&right, (const void *)(node + 0x08u), sizeof right);
        if (right) {
            node = right;
            for (unsigned s = 0; s < 32; s++) {
                uintptr_t left = 0;
                memcpy(&left, (const void *)(node + 0x00u), sizeof left);
                if (!left) break;
                node = left;
            }
        } else {
            for (unsigned s = 0; s < 32; s++) {
                uintptr_t parent = 0;
                memcpy(&parent, (const void *)(node + 0x10u), sizeof parent);
                if (!parent || parent == end_node) { node = parent; break; }
                uintptr_t pleft = 0;
                memcpy(&pleft, (const void *)(parent + 0x00u), sizeof pleft);
                node = parent;
                if (pleft == node) break;
            }
        }
    }
    return 0;
}

static void *repl_pipeline_vk_create(void *sret, const void *desc, const void *dev_holder,
                                     const void *lib, int64_t category, const void *sampler) {
    unsigned call_idx = __atomic_add_fetch(&g_pipeline_vk_calls, 1u, __ATOMIC_RELAXED);
    uintptr_t d = (uintptr_t)desc;
    int swapped = 0;
    int clip_test_cleared = 0;
    uint8_t saved_depth = 0, saved_front_stencil = 0, saved_back_stencil = 0;
    uintptr_t frag_fn = 0;
    uint64_t saved_mod = 0;

    if (d) {
        size_t nlen = 0;
        const char *fname = NULL;
        frag_fn = ld_find_frag_shader_in_desc(d, &fname, &nlen);
        if (call_idx <= 15) {
            size_t llen = 0;
            const char *lbl = ld_read_libcxx_str((const uint8_t *)(d + 0x18u), &llen);
            ld_blog("prismal: PipelineVK::Create #%u lbl=%.*s frag=%.*s",
                    call_idx, (int)(llen < 48 ? llen : 48), lbl ? lbl : "(null)",
                    (int)(nlen < 64 ? nlen : 64), fname ? fname : "(null)");
        }
        if (frag_fn) {
            if (fname && nlen >= 12 && strncmp(fname, "mi_glass_v13", 12) == 0) {
                uint64_t vk_dev = 0;
                memcpy(&vk_dev, (const void *)(frag_fn + 0x30u), sizeof vk_dev);
                if (vk_dev) {
                    if (!g_prismal_vk_module || g_prismal_vk_device != vk_dev) {
                        void *vk_lib = dlopen("libvulkan.so", RTLD_NOLOAD | RTLD_NOW);
                        if (!vk_lib) vk_lib = dlopen("libvulkan.so", RTLD_NOW);
                        ld_vk_create_sm_fn create_sm = vk_lib
                            ? (ld_vk_create_sm_fn)dlsym(vk_lib, "vkCreateShaderModule")
                            : NULL;
                        if (create_sm) {
                            ld_vk_shader_module_ci_t ci;
                            ci.sType = 16u;
                            ci.pNext = NULL;
                            ci.flags = 0u;
                            ci.codeSize = sizeof g_prismal_os4_spv;
                            ci.pCode = g_prismal_os4_spv;
                            uint64_t mod = 0;
                            int32_t vrc = create_sm(vk_dev, &ci, NULL, &mod);
                            ld_blog("prismal: vkCreateShaderModule dev=%llx rc=%d mod=%llx (%zu bytes)",
                                    (unsigned long long)vk_dev, (int)vrc, (unsigned long long)mod,
                                    sizeof g_prismal_os4_spv);
                            if (vrc == 0 && mod) {
                                g_prismal_vk_module = mod;
                                g_prismal_vk_device = vk_dev;
                            }
                        } else {
                            ld_blog("prismal: dlsym vkCreateShaderModule failed");
                        }
                    }
                    if (g_prismal_vk_module && g_prismal_vk_device == vk_dev) {
                        memcpy(&saved_mod, (const void *)(frag_fn + 0x48u), sizeof saved_mod);
                        memcpy((void *)(frag_fn + 0x48u), &g_prismal_vk_module, sizeof g_prismal_vk_module);
                        swapped = 1;
                        /* Disable hardware depth/stencil clip test on mi_glass_v13 (Impeller uses
                         * depth-buffer clipping for ClipRRect/ClipRSuperellipse) so expanded
                         * exterior cast shadow & outer rim stroke render unclipped outside the
                         * container; prismal_os4.frag computes the exact analytical superellipse SDF. */
                        saved_depth = *(const uint8_t *)(d + 0x74u);
                        saved_front_stencil = *(const uint8_t *)(d + 0x84u);
                        saved_back_stencil = *(const uint8_t *)(d + 0x94u);
                        if (saved_depth || saved_front_stencil || saved_back_stencil) {
                            *(uint8_t *)(d + 0x74u) = 0;
                            *(uint8_t *)(d + 0x84u) = 0;
                            *(uint8_t *)(d + 0x94u) = 0;
                            clip_test_cleared = 1;
                            ld_blog("prismal: cleared depth/stencil clip on mi_glass_v13 (depth=%u front=%u back=%u)",
                                    (unsigned)saved_depth, (unsigned)saved_front_stencil, (unsigned)saved_back_stencil);
                        }
                    }
                }
            }
        }
    }

    void *ret = g_orig_pipeline_vk_create(sret, desc, dev_holder, lib, category, sampler);
    if (clip_test_cleared && d) {
        *(uint8_t *)(d + 0x74u) = saved_depth;
        *(uint8_t *)(d + 0x84u) = saved_front_stencil;
        *(uint8_t *)(d + 0x94u) = saved_back_stencil;
    }
    if (swapped && frag_fn) {
        memcpy((void *)(frag_fn + 0x48u), &saved_mod, sizeof saved_mod);
    }
    return ret;
}

static void ld_prismal_glass_install_ex(const struct native_api *api, int gui_enabled,
                                        const struct ld_prismal_options *initial_opts)
{
    static int state;
    if (state) return;
    if (!gui_enabled && access(LD_PRISMAL_MARKER, F_OK) != 0) {
        return;
    }
    if (initial_opts && initial_opts->ior >= 1.0) {
        pthread_mutex_lock(&g_prismal_conf_lock);
        ld_prismal_publish_opts_locked(initial_opts);
        pthread_mutex_unlock(&g_prismal_conf_lock);
        __atomic_store_n(&g_prismal_no_downsample,
                         initial_opts->disable_stationary_downsample ? 1 : 0,
                         __ATOMIC_RELEASE);
        __atomic_store_n(&g_prismal_motion_downsample,
                         initial_opts->motion_downsample ? 1 : 0,
                         __ATOMIC_RELEASE);
        __atomic_store_n(&g_prismal_icon_downsample,
                         initial_opts->icon_downsample ? 1 : 0,
                         __ATOMIC_RELEASE);
        __atomic_store_n(&g_prismal_icon_fixed_wallpaper,
                         initial_opts->icon_fixed_wallpaper ? 1 : 0,
                         __ATOMIC_RELEASE);
    }

    struct ld_hyper_engine_info eng;
    if (!ld_hyper_engine_locate(&eng)) return;
    uintptr_t base = eng.elf_base;

    uintptr_t pipe_addr = ld_he_resolve_unique(&eng, LD_HINT_PIPELINE_VK_CREATE,
                                               ld_pg_pat_pipeline_vk_create, NULL, 8);
    uintptr_t tint_addr = ld_he_resolve_unique(&eng, LD_HINT_MAKE_TINTED_FROM_BASE,
                                               ld_pg_pat_make_tinted, NULL, 8);
    uintptr_t render_addr = ld_he_resolve_unique(&eng, LD_HINT_GLASS_CONTENTS_RENDER,
                                                 ld_pg_pat_glass_render, NULL, 16);
    uintptr_t ubo_addr = ld_he_resolve_unique(&eng, LD_HINT_BIND_UBO,
                                              ld_pg_pat_bind_ubo, ld_pg_msk_bind_ubo, 8);
    uintptr_t mask_addr = ld_he_resolve_near(&eng, LD_HINT_BIND_U_MASK_TEX,
                                             ubo_addr, 0x400u, 0x200u,
                                             ld_pg_pat_bind_mask_tex, ld_pg_msk_bind_mask_tex, 8);
    uintptr_t flush_addr = ld_he_resolve_unique(&eng, LD_HINT_DEVICE_BUF_FLUSH,
                                                ld_pg_pat_devbuf_flush, NULL, 8);
    uintptr_t getc_addr = ld_he_resolve_near(&eng, LD_HINT_DEVICE_BUF_GET_CONTENTS,
                                             flush_addr, 0x400u, 0x200u,
                                             ld_pg_pat_devbuf_get, ld_pg_msk_devbuf_get, 8);
    uintptr_t sc_addr = ld_he_resolve_unique(&eng, LD_HINT_RENDER_PASS_SET_SCISSOR,
                                             ld_pg_pat_set_scissor, NULL, 8);
#if defined(__aarch64__)
    uintptr_t ct_addr = ld_he_resolve_unique(&eng, LD_HINT_COMPOSE_TEXTURES,
                                             ld_pg_pat_compose_textures, NULL, 8);
    uintptr_t sl_addr = ld_he_resolve_unique(&eng, LD_HINT_CANVAS_SAVE_LAYER,
                                             ld_pg_pat_canvas_savelayer, NULL, 8);
#endif

    /* MakeBaseBlur & MakeReflectBlur share the same 8-word prologue and identical hook logic. */
    uintptr_t blur_addrs[2] = {0, 0};
    uintptr_t b_hint0 = base + LD_HINT_MAKE_BASE_BLUR;
    uintptr_t b_hint1 = base + LD_HINT_MAKE_REFLECT_BLUR;
    if (b_hint0 >= eng.text_start && b_hint0 + 32u <= eng.text_end &&
        b_hint1 >= eng.text_start && b_hint1 + 32u <= eng.text_end &&
        ld_he_match_masked(b_hint0, ld_pg_pat_make_blur, NULL, 8) &&
        ld_he_match_masked(b_hint1, ld_pg_pat_make_blur, NULL, 8)) {
        blur_addrs[0] = b_hint0;
        blur_addrs[1] = b_hint1;
    } else {
        ld_he_scan_masked_range(eng.text_start, eng.text_end,
                                ld_pg_pat_make_blur, NULL, 8, blur_addrs, 2);
    }

    if (getc_addr) g_fn_devbuf_get_contents = (uint8_t *(*)(void *))getc_addr;
    if (flush_addr) g_fn_devbuf_flush = (void (*)(void *, const ld_opt_range_t *))flush_addr;

    int prc = -1, b1 = -1, b2 = -1, t1 = -1, r1 = -1, m1 = -1, u1 = -1, c1 = -1, s1 = -1, sl1 = -1;
    if (api && api->hook) {
        if (pipe_addr) {
            prc = g_orig_pipeline_vk_create ? 0
                : api->hook((void *)pipe_addr, (void *)repl_pipeline_vk_create,
                            (void **)&g_orig_pipeline_vk_create);
        }
        if (blur_addrs[0]) {
            b1 = g_orig_make_base_blur ? 0
                : api->hook((void *)blur_addrs[0], (void *)repl_make_base_blur,
                            (void **)&g_orig_make_base_blur);
        }
        if (blur_addrs[1]) {
            b2 = g_orig_make_reflect_blur ? 0
                : api->hook((void *)blur_addrs[1], (void *)repl_make_reflect_blur,
                            (void **)&g_orig_make_reflect_blur);
        }
        if (tint_addr) {
            t1 = g_orig_make_tinted_from_base ? 0
                : api->hook((void *)tint_addr, (void *)repl_make_tinted_from_base,
                            (void **)&g_orig_make_tinted_from_base);
        }
        if (render_addr) {
            r1 = g_orig_glass_contents_render ? 0
                : api->hook((void *)render_addr, (void *)repl_glass_contents_render,
                            (void **)&g_orig_glass_contents_render);
        }
        if (mask_addr) {
            m1 = g_orig_bind_mask_tex ? 0
                : api->hook((void *)mask_addr, (void *)repl_bind_mask_tex,
                            (void **)&g_orig_bind_mask_tex);
        }
        if (ubo_addr) {
            u1 = g_orig_bind_ubo ? 0
                : api->hook((void *)ubo_addr, (void *)repl_bind_ubo,
                            (void **)&g_orig_bind_ubo);
        }
        if (sc_addr) {
            s1 = g_orig_set_scissor ? 0
                : api->hook((void *)sc_addr, (void *)repl_set_scissor,
                            (void **)&g_orig_set_scissor);
        }
#if defined(__aarch64__)
        if (ct_addr) {
            c1 = g_orig_compose_textures ? 0
                : api->hook((void *)ct_addr, (void *)repl_compose_textures,
                            &g_orig_compose_textures);
        }
        if (sl_addr) {
            sl1 = g_orig_canvas_savelayer ? 0
                : api->hook((void *)sl_addr, (void *)repl_canvas_savelayer,
                            &g_orig_canvas_savelayer);
        }
#endif
    }

    state = 1;
    if (prc == 0 && b1 == 0 && b2 == 0 && t1 == 0 && r1 == 0 && m1 == 0 && u1 == 0 &&
        g_fn_devbuf_get_contents && g_fn_devbuf_flush) {
        __atomic_store_n(&g_prismal_glass_active, 1, __ATOMIC_RELEASE);
    }
    ld_blog("prismal v2.7.9 hooks: pipe=%d baseBlur=%d reflBlur=%d tint=%d render=%d mask=%d ubo=%d sc=%d compose=%d savelayer=%d spv=%zu",
            prc, b1, b2, t1, r1, m1, u1, s1, c1, sl1, sizeof g_prismal_os4_spv);
}

static inline void ld_prismal_glass_install(const struct native_api *api)
{
    ld_prismal_glass_install_ex(api, 0, NULL);
}

#endif /* LD_PRISMAL_GLASS_H */
