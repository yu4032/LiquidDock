#ifndef LIQUIDDOCK_GLASS_FILTER_HOOK_H
#define LIQUIDDOCK_GLASS_FILTER_HOOK_H
/* Applies native_glass customization to every DlGlassImageFilter unwrapped by
 * MiGlassFilterHelper in Canvas::DrawHyperMaterial (icons, folders, dock, widgets). */
static void *(*g_orig_unwrap_glass)(void *filter, int depth);
static bool (*g_orig_acquire_blur)(void *canvas, void *filter, void *a2, void *a3, void *a4, void *a5, int w6, float s0);
static uintptr_t g_glass_vt_expect;
static uintptr_t g_glass_eng_base;

static int ld_glass_fast_read(uintptr_t addr, void *buf, size_t n)
{
    if (addr < 4096u) return 0;
    memcpy(buf, (const void *)addr, n);
    return 1;
}

static int ld_glass_fast_write(const uintptr_t *addrs, const float *vals, size_t n)
{
    for (size_t i = 0; i < n; i++) {
        if (addrs[i] < 4096u) return 0;
        memcpy((void *)addrs[i], &vals[i], sizeof(float));
    }
    return 1;
}

static void ld_glass_apply_obj(void *obj, int from_unwrap)
{
    if ((uintptr_t)obj < 4096u) return;
    uintptr_t vt = 0;
    uintptr_t expect = __atomic_load_n(&g_glass_vt_expect, __ATOMIC_ACQUIRE);
    if (!expect) {
        if (!from_unwrap) return;
        if (!ld_read_heap((uintptr_t)obj, &vt, 8) || !vt) return;
        /* Learn DlGlassImageFilter vtable dynamically from Unwrap's first returned object. */
        if (g_glass_eng_base && vt > g_glass_eng_base && vt < g_glass_eng_base + 0x4000000u) {
            __atomic_store_n(&g_glass_vt_expect, vt, __ATOMIC_RELEASE);
            expect = vt;
            ld_blog("glass filter vtable learned dynamically: %p (off=0x%lx)",
                    (void *)vt, (unsigned long)(vt - g_glass_eng_base));
        } else {
            return;
        }
    } else {
        memcpy(&vt, obj, sizeof(vt));
    }
    if (vt != expect) return;
    if (g_home_options.glass_enabled && g_home_options.native_glass.mask) {
        ld_native_glass_apply((uintptr_t)obj, &g_home_options.native_glass, ld_glass_fast_read, ld_glass_fast_write);
    }
    if (!from_unwrap &&
        (g_home_options.prismal.disable_stationary_downsample ||
         __atomic_load_n(&g_prismal_no_downsample, __ATOMIC_RELAXED))) {
        /* Use live 1.0x capture (rate = -1) for Dock, Widgets, Large Folders, and
         * stationary 1x1 icons (when icon_downsample == 0), so they never hit a
         * pre-hook 0.25x cached wallpaper entry and Workstation Dock refracts live
         * content. Fall back to shared cache (rate = 0) for 1x1 icons during motion
         * or when icon_downsample == 1 to keep 120fps locked with zero icon RT churn. */
        int is_small_icon = 0;
#ifdef LD_GLASS_SUPERELLIPSE_H
        is_small_icon = g_ld_gse_tls_is_small_icon;
#endif
        int downsample_icon = is_small_icon &&
            (__atomic_load_n(&g_prismal_icon_downsample, __ATOMIC_RELAXED) ||
             __atomic_load_n(&g_prismal_in_motion, __ATOMIC_RELAXED));
        int32_t rate_i = 0;
        uint8_t *rate_ptr = (uint8_t *)obj + 0x34u;
        memcpy(&rate_i, rate_ptr, sizeof(rate_i));
        if (!downsample_icon && rate_i >= 0) {
            int32_t live_rate = -1;
            memcpy(rate_ptr, &live_rate, sizeof(live_rate));
        } else if (downsample_icon && rate_i < 0) {
            int32_t shared_rate = 0;
            memcpy(rate_ptr, &shared_rate, sizeof(shared_rate));
        }
    }
    if (__atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE)) {
        uint8_t *base = (uint8_t *)obj;
        float blur_px = ld_prismal_current_blur_px();
        float br = blur_px >= 0.5f ? blur_px : 0.01f;
        float bg = 0.01f; /* always triggers MakeBaseBlurImage sharp bypass for +0x148 */
        static const unsigned zero_idx[] = { 24, 28, 30, 31, 32, 33 }; /* darker, bg_brightness, brightness, inner_white, inner_mix, luminance_amount */
        static const unsigned one_idx[] = { 27, 29, 38 }; /* bg_saturation, saturation, burn */
        const float z = 0.0f, o = 1.0f;
        memcpy(base + ld_native_glass_fields[22].offset, &br, sizeof(float));
        memcpy(base + ld_native_glass_fields[23].offset, &bg, sizeof(float));
        for (size_t i = 0; i < sizeof(zero_idx) / sizeof(zero_idx[0]); i++) {
            memcpy(base + ld_native_glass_fields[zero_idx[i]].offset, &z, sizeof(float));
        }
        for (size_t i = 0; i < sizeof(one_idx) / sizeof(one_idx[0]); i++) {
            memcpy(base + ld_native_glass_fields[one_idx[i]].offset, &o, sizeof(float));
        }
    }
}

/* Canvas::AcquireGlassBlurTextures reads blur_refract/blur_background straight from the
 * filter; apply the customization right before it so icon/folder blur follows it. */
static bool ld_acquire_blur_hook(void *canvas, void *filter, void *a2, void *a3, void *a4, void *a5, int w6, float s0)
{
    if (filter && ((g_home_options.glass_enabled && g_home_options.native_glass.mask) ||
                   __atomic_load_n(&g_prismal_glass_active, __ATOMIC_ACQUIRE))) {
        ld_glass_apply_obj(filter, 0);
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
        ld_glass_apply_obj(res, 1);
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
    g_glass_eng_base = lib.elf_base;
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
