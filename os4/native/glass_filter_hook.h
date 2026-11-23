#ifndef LIQUIDDOCK_GLASS_FILTER_HOOK_H
#define LIQUIDDOCK_GLASS_FILTER_HOOK_H
/* Applies native_glass customization to every DlGlassImageFilter unwrapped by
 * MiGlassFilterHelper in Canvas::DrawHyperMaterial (icons, folders, dock, widgets). */
static void *(*g_orig_unwrap_glass)(void *filter, int depth);
static bool (*g_orig_acquire_blur)(void *canvas, void *filter, void *a2, void *a3, void *a4, void *a5, int w6, float s0);
static uintptr_t g_glass_vt_expect;

static void ld_glass_apply_obj(void *obj)
{
    uintptr_t vt = 0;
    if (!obj || !g_glass_vt_expect || !ld_read_heap((uintptr_t)obj, &vt, 8) || vt != g_glass_vt_expect) return;
    if (g_home_options.glass_enabled && g_home_options.native_glass.mask) {
        ld_native_glass_apply((uintptr_t)obj, &g_home_options.native_glass, ld_read_heap, ld_native_glass_write);
    }
    if (g_home_options.prismal.disable_stationary_downsample ||
        __atomic_load_n(&g_prismal_no_downsample, __ATOMIC_RELAXED)) {
        uintptr_t rate_addr = (uintptr_t)obj + 0x34u;
        int32_t rate_i = 0;
        if (ld_read_heap(rate_addr, &rate_i, sizeof(rate_i)) && rate_i >= 0) {
            /* pass_blur_rate < 0 (-1) bypasses ProcessHyperGlassMaterial's cached/downsampled
             * pass_blur lookup (0xd629c8) and uses the live inline RenderTarget every frame,
             * avoiding both stationary downsampling (rate==0) and frozen folder-close frames (rate>0). */
            int32_t live_rate = -1;
            memcpy((void *)rate_addr, &live_rate, sizeof(live_rate));
        }
    }
    if (__atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE)) {
        uintptr_t base = (uintptr_t)obj;
        float blur_px = ld_prismal_current_blur_px();
        float br = blur_px >= 0.5f ? blur_px : 0.01f;
        float bg = 0.01f; /* always triggers MakeBaseBlurImage sharp bypass for +0x148 */
        static const unsigned zero_idx[] = { 24, 28, 30, 31, 32, 33 }; /* darker, bg_brightness, brightness, inner_white, inner_mix, luminance_amount */
        static const unsigned one_idx[] = { 27, 29, 38 }; /* bg_saturation, saturation, burn */
        float z = 0.0f, o = 1.0f;
        uintptr_t br_addr = base + ld_native_glass_fields[22].offset;
        uintptr_t bg_addr = base + ld_native_glass_fields[23].offset;
        ld_native_glass_write(&br_addr, &br, 1);
        ld_native_glass_write(&bg_addr, &bg, 1);
        for (size_t i = 0; i < sizeof(zero_idx) / sizeof(zero_idx[0]); i++) {
            uintptr_t addr = base + ld_native_glass_fields[zero_idx[i]].offset;
            ld_native_glass_write(&addr, &z, 1);
        }
        for (size_t i = 0; i < sizeof(one_idx) / sizeof(one_idx[0]); i++) {
            uintptr_t addr = base + ld_native_glass_fields[one_idx[i]].offset;
            ld_native_glass_write(&addr, &o, 1);
        }
    }
}

/* Canvas::AcquireGlassBlurTextures reads blur_refract/blur_background straight from the
 * filter; apply the customization right before it so icon/folder blur follows it. */
static bool ld_acquire_blur_hook(void *canvas, void *filter, void *a2, void *a3, void *a4, void *a5, int w6, float s0)
{
    if (filter && ((g_home_options.glass_enabled && g_home_options.native_glass.mask) ||
                   __atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE))) {
        ld_glass_apply_obj(filter);
        static unsigned calls;
        unsigned n = __atomic_fetch_add(&calls, 1u, __ATOMIC_RELAXED);
        float orig_s0 = s0;
        if ((g_home_options.prismal.disable_stationary_downsample ||
             __atomic_load_n(&g_prismal_no_downsample, __ATOMIC_ACQUIRE)) &&
            s0 > 0.0f && s0 < 1.0f) {
            s0 = 1.0f;
        }
        if (n < 8u || (orig_s0 > 0.0f && orig_s0 < 0.99f && n < 32u)) {
            ld_blog("glass AcquireBlur hook active f=%p s0=%.3f->%.3f", filter, (double)orig_s0, (double)s0);
        }
    }
    return g_orig_acquire_blur(canvas, filter, a2, a3, a4, a5, w6, s0);
}

static void *ld_unwrap_glass_hook(void *filter, int depth)
{
    void *res = g_orig_unwrap_glass(filter, depth);
    if (res && ((g_home_options.glass_enabled && g_home_options.native_glass.mask) ||
                __atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE))) {
        ld_glass_apply_obj(res);
        static unsigned calls;
        unsigned n = __atomic_fetch_add(&calls, 1u, __ATOMIC_RELAXED);
        if (n == 0) ld_blog("glass Unwrap hook active res=%p", res);
    }
    return res;
}

static void ld_glass_filter_hook_install(const struct native_api *api)
{
    static bool unwrap_installed;
    static bool acq_installed;
    if ((unwrap_installed && acq_installed) || !api || !api->hook ||
        (!g_home_options.glass_enabled && !__atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE))) return;
    ld_lib_info lib = {0};
    if (!ld_resolve_so("libhyper_os_flutter.so", &lib) || !lib.elf_base) return;
    g_glass_vt_expect = lib.elf_base + 0x1190e98u;
    if (!unwrap_installed) {
        static const uint32_t pat[8] = {
            0xa9bd5ffeu, 0xa90157f6u, 0xa9024ff4u, 0x2a0103f3u,
            0xaa0003f4u, 0xaa1f03e0u, 0xb4000ad4u, 0x7100427fu,
        };
        uintptr_t target = ld_find_aot_pattern(&lib, 0xc42840u, pat, 8);
        if (!target) {
            ld_blog("glass Unwrap pattern not found");
        } else {
            void *backup = NULL;
            if (api->hook((void *)target, (void *)ld_unwrap_glass_hook, &backup) == 0 && backup) {
                g_orig_unwrap_glass = (void *(*)(void *, int))backup;
                unwrap_installed = true;
                ld_blog("glass Unwrap hook installed at %p", (void *)target);
            } else {
                ld_blog("glass Unwrap hook failed");
            }
        }
    }
    if (!acq_installed) {
        static const uint32_t pat_acq[8] = {
            0x6db63befu, 0x6d0133edu, 0x6d022bebu, 0x6d0323e9u,
            0xa9047bfdu, 0xa9056ffcu, 0xa90667fau, 0xa9075ff8u,
        };
        uintptr_t acq = ld_find_aot_pattern(&lib, 0xc45964u, pat_acq, 8);
        if (!acq) {
            ld_blog("glass AcquireBlur pattern not found");
        } else {
            void *abak = NULL;
            if (api->hook((void *)acq, (void *)ld_acquire_blur_hook, &abak) == 0 && abak) {
                g_orig_acquire_blur = (bool (*)(void *, void *, void *, void *, void *, void *, int, float))abak;
                acq_installed = true;
                ld_blog("glass AcquireBlur hook installed at %p", (void *)acq);
            } else {
                ld_blog("glass AcquireBlur hook failed");
            }
        }
    }
}
#endif
