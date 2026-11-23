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
#include "native_api.h"
#include "prismal_os4_spv.h"

#define LD_PRISMAL_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-prismal-glass"
#define LD_PRISMAL_CONF_PRIMARY \
    "/data/user/0/com.miui.home/files/liquiddock-native.conf"
#define LD_PRISMAL_CONF_EARLY \
    "/data/user_de/0/com.miui.home/files/liquiddock-native-early.conf"

#define LD_PRISMAL_SPV_SIZE_VADDR         0x513a84u
#define LD_PRISMAL_SPV_DATA_VADDR         0x513a88u
#define LD_PRISMAL_SPV_MAX_BYTES          22816u
#define LD_PRISMAL_SPV_MAGIC              0x07230203u

#define LD_VADDR_MAKE_BASE_BLUR           0xc835b8u
#define LD_VADDR_MAKE_REFLECT_BLUR        0xc83b58u
#define LD_VADDR_MAKE_TINTED_FROM_BASE    0xc84188u
#define LD_VADDR_COMPOSE_TEXTURES         0xc76290u
#define LD_VADDR_GLASS_CONTENTS_RENDER    0xd69e00u
#define LD_VADDR_BIND_U_MASK_TEX          0xd6c1b8u
#define LD_VADDR_BIND_UBO                 0xd6c260u
#define LD_VADDR_DEVICE_BUF_GET_CONTENTS  0xdec528u
#define LD_VADDR_DEVICE_BUF_FLUSH         0xdec594u
#define LD_VADDR_PIPELINE_VK_CREATE       0xdf6028u
#define LD_VADDR_RENDER_PASS_SET_SCISSOR  0xe01148u

static volatile int g_prismal_glass_active __attribute__((used));
static volatile int g_prismal_no_downsample __attribute__((used));

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

static uint8_t *(*g_fn_devbuf_get_contents)(void *dev_buf);
static void (*g_fn_devbuf_flush)(void *dev_buf, const ld_opt_range_t *opt_range);

static volatile unsigned g_pipeline_vk_calls;
static uint64_t g_prismal_vk_module;
static uint64_t g_prismal_vk_device;

static pthread_mutex_t g_prismal_conf_lock = PTHREAD_MUTEX_INITIALIZER;
static struct ld_prismal_options g_prismal_live_opts;
static int g_prismal_opts_initialized;

static __thread uintptr_t g_tls_glass_contents;
static __thread int g_tls_bound_sharp_tex;
static __thread float g_tls_expand_pad_px;
static __thread void *g_tls_scissor_pass;
static __thread uint64_t g_tls_scissor_lt;
static __thread uint64_t g_tls_scissor_rb;

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

static void ld_prismal_copy_opts(struct ld_prismal_options *out)
{
    pthread_mutex_lock(&g_prismal_conf_lock);
    if (!g_prismal_opts_initialized) {
        ld_prismal_options_defaults(&g_prismal_live_opts);
        g_prismal_opts_initialized = 1;
    }
    *out = g_prismal_live_opts;
    pthread_mutex_unlock(&g_prismal_conf_lock);
}

static void ld_prismal_maybe_reload(void)
{
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
            g_prismal_live_opts = conf.prismal;
            g_prismal_opts_initialized = 1;
            pthread_mutex_unlock(&g_prismal_conf_lock);
            __atomic_store_n(&g_prismal_no_downsample,
                             conf.prismal.disable_stationary_downsample ? 1 : 0,
                             __ATOMIC_RELEASE);
#ifdef LD_GLASS_SUPERELLIPSE_H
            g_ld_gse_large_folder_corner = (float)conf.large_folder_corner;
            g_ld_gse_widget_corner = (float)conf.prismal.widget_corner;
#endif
            last_mtime = st.st_mtime;
            last_mtimensec = st.st_mtim.tv_nsec;
            last_size = st.st_size;
        }
    }
    pthread_mutex_unlock(&reload_lock);
}

static float ld_prismal_current_blur_px(void)
{
    struct ld_prismal_options opts;
    ld_prismal_maybe_reload();
    ld_prismal_copy_opts(&opts);
    return fmaxf(0.0f, (float)opts.blur * ld_prismal_get_density());
}

static inline void ld_sp_add_shared(uint8_t *ctrl)
{
    if (ctrl) {
        __atomic_fetch_add((int64_t *)(ctrl + 8), 1, __ATOMIC_RELAXED);
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
    if (min_glass_dim >= 360.0f && max_glass_dim <= 2000.0f && (max_glass_dim / fmaxf(min_glass_dim, 1.0f)) <= 3.2f) {
        int is_large_folder = (gz > 15.2f * d) ||
                              (fabsf(eff_gx - eff_gy) < 12.0f && fabsf(eff_gx - 169.7f * d) < 22.0f * d);
        if (is_large_folder) {
            if (opt->large_folder_corner > 0.0) {
                float cr_px = fminf((float)opt->large_folder_corner * d, min_glass_dim * 0.5f);
                ld_write_f32(ubo, 0x18u, cr_px);
            }
        } else if (opt->widget_corner > 0.0) {
            float cr_px = fminf((float)opt->widget_corner * d, min_glass_dim * 0.5f);
            ld_write_f32(ubo, 0x18u, cr_px);
        }
    } else if (min_glass_dim >= 45.0f * d && min_glass_dim < 120.0f * d &&
               fabsf(max_glass_dim - 169.7f * d) < 20.0f * d) {
        /* 1x2 / 2x1 大文件夹 */
        if (opt->large_folder_corner > 0.0) {
            float cr_px = fminf((float)opt->large_folder_corner * d, min_glass_dim * 0.5f);
            ld_write_f32(ubo, 0x18u, cr_px);
        }
    } else if (min_glass_dim >= 80.0f && max_glass_dim <= 340.0f && fabsf(eff_gx - eff_gy) < 12.0f) {
        if (opt->small_folder_corner > 0.0) {
            float cr_px = fminf((float)opt->small_folder_corner * d, min_glass_dim * 0.5f);
            ld_write_f32(ubo, 0x18u, cr_px);
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
                   | (has_sharp_tex ? 8u : 0u);

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
    ld_write_vec4(ubo, 0x110u,
                  (float)opt->backlit_width * d,
                  opt->backlit_enabled ? (float)opt->backlit_strength : 0.0f,
                  side_start, side_span);
    ld_write_vec4(ubo, 0x120u,
                  (float)opt->outer_shadow_r / 255.0f,
                  (float)opt->outer_shadow_g / 255.0f,
                  (float)opt->outer_shadow_b / 255.0f,
                  opt->outer_shadow_enabled ? (float)opt->outer_shadow_a / 255.0f : 0.0f);
    ld_write_vec4(ubo, 0x130u,
                  opt->outer_shadow_enabled ? (float)opt->outer_shadow_radius * d : 0.0f,
                  (float)opt->outer_shadow_offset_y * d,
                  ld_clampf((float)opt->outer_shadow_light_follow, 0.0f, 1.0f),
                  pad_px);
}

#if defined(__aarch64__)
__attribute__((naked)) static void repl_compose_textures(void)
{
    __asm__ volatile(
        "adrp x16, g_prismal_no_downsample\n"
        "ldr w17, [x16, :lo12:g_prismal_no_downsample]\n"
        "cbz w17, 1f\n"
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
    if (__atomic_load_n(&g_prismal_no_downsample, __ATOMIC_ACQUIRE) && scale > 0.0f && scale < 1.0f) {
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
    if (__atomic_load_n(&g_prismal_no_downsample, __ATOMIC_ACQUIRE) && scale > 0.0f && scale < 1.0f) {
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
    g_tls_glass_contents = (uintptr_t)self;
    g_tls_bound_sharp_tex = 0;
    g_tls_expand_pad_px = 0.0f;

    int expanded_rect = 0, expanded_alt = 0, expanded_geo = 0;
    float orig_rect[4] = {0, 0, 0, 0};
    float orig_alt[4] = {0, 0, 0, 0};
    float orig_geo[2] = {0, 0};
    if (self && __atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE)) {
        ld_prismal_maybe_reload();
        struct ld_prismal_options opts;
        ld_prismal_copy_opts(&opts);
        float d = ld_prismal_get_density();
        float pad_dp = 0.0f;
        if (opts.outer_shadow_enabled && opts.outer_shadow_radius > 0.0 && opts.outer_shadow_a > 0.0) {
            float sh = (float)opts.outer_shadow_radius * 2.0f + fabsf((float)opts.outer_shadow_offset_y) + 2.0f;
            if (sh > pad_dp) pad_dp = fminf(sh, 36.0f);
        }
        if (opts.backlit_enabled && opts.backlit_strength > 0.0 && opts.backlit_width > 0.0) {
            float st = (float)opts.backlit_width + 2.0f;
            if (st > pad_dp) pad_dp = fminf(st, 10.0f);
        }
        if (pad_dp > 0.0f) {
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
                float pad_px = pad_dp * d;
                float qscale = orig_geo[0] / w;
                if (!(qscale > 0.25f && qscale < 8.0f)) qscale = d;
                float pad_q = pad_px / qscale;
                if (use_alt) {
                    float exp_alt[4] = {
                        orig_alt[0] - pad_q, orig_alt[1] - pad_q,
                        orig_alt[2] + 2.0f * pad_q, orig_alt[3] + 2.0f * pad_q
                    };
                    memcpy((uint8_t *)self + 0x1d8u, exp_alt, sizeof exp_alt);
                    expanded_alt = 1;
                } else {
                    float exp_rect[4] = {
                        orig_rect[0] - pad_q, orig_rect[1] - pad_q,
                        orig_rect[2] + pad_q, orig_rect[3] + pad_q
                    };
                    memcpy((uint8_t *)self + 0x28u, exp_rect, sizeof exp_rect);
                    expanded_rect = 1;
                }
                float exp_geo[2] = { orig_geo[0] + 2.0f * pad_px, orig_geo[1] + 2.0f * pad_px };
                memcpy((uint8_t *)self + 0x1c8u, exp_geo, sizeof exp_geo);
                expanded_geo = 1;
                g_tls_expand_pad_px = pad_px;
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
                ld_prismal_maybe_reload();
                struct ld_prismal_options opts;
                ld_prismal_copy_opts(&opts);
                float density = ld_prismal_get_density();
                int sharp = g_tls_bound_sharp_tex;

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
                            gx, gy, gz, ix, iy, sx, sy, um, cs, density, sharp, opts.blur);
                }

                ld_prismal_fill_ubo(ubo, &opts, density, sharp);
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

static uintptr_t ld_prismal_engine_base(void)
{
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return 0;
    char line[512];
    uintptr_t base = 0;
    while (fgets(line, sizeof line, maps)) {
        if (!strstr(line, "libhyper_os_flutter.so")) continue;
        unsigned long start = 0;
        if (sscanf(line, "%lx-", &start) == 1) {
            base = (uintptr_t)start;
            break;
        }
    }
    fclose(maps);
    return base;
}

static int ld_prismal_patch_rodata(uintptr_t size_addr, uintptr_t data_addr,
                                   const uint32_t *spv, size_t spv_bytes)
{
    if (!size_addr || !data_addr || !spv || spv_bytes == 0 ||
        spv_bytes > LD_PRISMAL_SPV_MAX_BYTES || (spv_bytes & 3u) != 0) {
        return -EINVAL;
    }
    if (*(const volatile uint32_t *)data_addr != LD_PRISMAL_SPV_MAGIC) {
        return -2;
    }
    if (*(const volatile uint32_t *)size_addr != LD_PRISMAL_SPV_MAX_BYTES) {
        return -3;
    }

    size_t page = (size_t)sysconf(_SC_PAGESIZE);
    uintptr_t page_start = size_addr & ~(uintptr_t)(page - 1u);
    uintptr_t end_addr = data_addr + spv_bytes;
    uintptr_t page_end = (end_addr + page - 1u) & ~(uintptr_t)(page - 1u);
    size_t span = (size_t)(page_end - page_start);

    if (mprotect((void *)page_start, span, PROT_READ | PROT_WRITE) != 0) {
        return -errno;
    }

    *(volatile uint32_t *)size_addr = (uint32_t)spv_bytes;
    memcpy((void *)data_addr, spv, spv_bytes);

    if (mprotect((void *)page_start, span, PROT_READ) != 0) {
        return -errno;
    }
    return 0;
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
        g_prismal_live_opts = *initial_opts;
        g_prismal_opts_initialized = 1;
        pthread_mutex_unlock(&g_prismal_conf_lock);
        __atomic_store_n(&g_prismal_no_downsample,
                         initial_opts->disable_stationary_downsample ? 1 : 0,
                         __ATOMIC_RELEASE);
    }

    uintptr_t base = ld_prismal_engine_base();
    if (!base) return;

    int ro_rc = 0;
    if (sizeof g_prismal_os4_spv <= LD_PRISMAL_SPV_MAX_BYTES) {
        ro_rc = ld_prismal_patch_rodata(base + LD_PRISMAL_SPV_SIZE_VADDR,
                                        base + LD_PRISMAL_SPV_DATA_VADDR,
                                        g_prismal_os4_spv, sizeof g_prismal_os4_spv);
    }

    g_fn_devbuf_get_contents = (uint8_t *(*)(void *))(base + LD_VADDR_DEVICE_BUF_GET_CONTENTS);
    g_fn_devbuf_flush = (void (*)(void *, const ld_opt_range_t *))(base + LD_VADDR_DEVICE_BUF_FLUSH);

    int prc = -1, b1 = -1, b2 = -1, t1 = -1, r1 = -1, m1 = -1, u1 = -1, c1 = -1;
    if (api && api->hook) {
        prc = g_orig_pipeline_vk_create ? 0
            : api->hook((void *)(base + LD_VADDR_PIPELINE_VK_CREATE),
                        (void *)repl_pipeline_vk_create, (void **)&g_orig_pipeline_vk_create);
        b1 = g_orig_make_base_blur ? 0
            : api->hook((void *)(base + LD_VADDR_MAKE_BASE_BLUR),
                        (void *)repl_make_base_blur, (void **)&g_orig_make_base_blur);
        b2 = g_orig_make_reflect_blur ? 0
            : api->hook((void *)(base + LD_VADDR_MAKE_REFLECT_BLUR),
                        (void *)repl_make_reflect_blur, (void **)&g_orig_make_reflect_blur);
        t1 = g_orig_make_tinted_from_base ? 0
            : api->hook((void *)(base + LD_VADDR_MAKE_TINTED_FROM_BASE),
                        (void *)repl_make_tinted_from_base, (void **)&g_orig_make_tinted_from_base);
        r1 = g_orig_glass_contents_render ? 0
            : api->hook((void *)(base + LD_VADDR_GLASS_CONTENTS_RENDER),
                        (void *)repl_glass_contents_render, (void **)&g_orig_glass_contents_render);
        m1 = g_orig_bind_mask_tex ? 0
            : api->hook((void *)(base + LD_VADDR_BIND_U_MASK_TEX),
                        (void *)repl_bind_mask_tex, (void **)&g_orig_bind_mask_tex);
        u1 = g_orig_bind_ubo ? 0
            : api->hook((void *)(base + LD_VADDR_BIND_UBO),
                        (void *)repl_bind_ubo, (void **)&g_orig_bind_ubo);
        uintptr_t sc_addr = base + LD_VADDR_RENDER_PASS_SET_SCISSOR;
        if (!g_orig_set_scissor && *(const uint32_t *)sc_addr == 0xd360fc28u) {
            api->hook((void *)sc_addr, (void *)repl_set_scissor, (void **)&g_orig_set_scissor);
        }
#if defined(__aarch64__)
        uintptr_t ct_addr = base + LD_VADDR_COMPOSE_TEXTURES;
        if (*(const uint32_t *)ct_addr == 0xa9ba7bfdu) {
            c1 = g_orig_compose_textures ? 0
                : api->hook((void *)ct_addr, (void *)repl_compose_textures, &g_orig_compose_textures);
        }
#endif
    }

    state = 1;
    if (prc == 0 && b1 == 0 && b2 == 0 && t1 == 0 && r1 == 0 && m1 == 0 && u1 == 0) {
        __atomic_store_n(&g_prismal_glass_active, 1, __ATOMIC_RELEASE);
    }
    ld_blog("prismal v2.7.9 hooks: pipe=%d baseBlur=%d reflBlur=%d tint=%d render=%d mask=%d ubo=%d compose=%d ro=%d spv=%zu",
            prc, b1, b2, t1, r1, m1, u1, c1, ro_rc, sizeof g_prismal_os4_spv);
}

static inline void ld_prismal_glass_install(const struct native_api *api)
{
    ld_prismal_glass_install_ex(api, 0, NULL);
}

#endif /* LD_PRISMAL_GLASS_H */
