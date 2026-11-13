#ifndef LIQUIDDOCK_GLASS_FILTER_HOOK_H
#define LIQUIDDOCK_GLASS_FILTER_HOOK_H
/* Applies native_glass customization to every DlGlassImageFilter unwrapped by
 * MiGlassFilterHelper in Canvas::DrawHyperMaterial (icons, folders, dock, widgets). */
static void *(*g_orig_unwrap_glass)(void *filter, int depth);
static uintptr_t g_glass_vt_expect;

static void ld_glass_apply_obj(void *obj)
{
    uintptr_t vt = 0;
    if (!obj || !g_glass_vt_expect || !ld_read_heap((uintptr_t)obj, &vt, 8) || vt != g_glass_vt_expect) return;
    ld_native_glass_apply((uintptr_t)obj, &g_home_options.native_glass, ld_read_heap, ld_native_glass_write);
}

static void *ld_unwrap_glass_hook(void *filter, int depth)
{
    void *res = g_orig_unwrap_glass(filter, depth);
    if (res && g_home_options.glass_enabled && g_home_options.native_glass.mask) {
        ld_glass_apply_obj(res);
        static unsigned calls;
        unsigned n = __atomic_fetch_add(&calls, 1u, __ATOMIC_RELAXED);
        if (n == 0) ld_blog("glass Unwrap hook active res=%p", res);
    }
    return res;
}

static void ld_glass_filter_hook_install(const struct native_api *api)
{
    static bool installed;
    if (installed || !api || !api->hook || !g_home_options.glass_enabled) return;
    ld_lib_info lib = {0};
    if (!ld_resolve_so("libhyper_os_flutter.so", &lib) || !lib.elf_base) return;
    static const uint32_t pat[8] = {
        0xa9bd5ffeu, 0xa90157f6u, 0xa9024ff4u, 0x2a0103f3u,
        0xaa0003f4u, 0xaa1f03e0u, 0xb4000ad4u, 0x7100427fu,
    };
    uintptr_t target = ld_find_aot_pattern(&lib, 0xc42840u, pat, 8);
    if (!target) { ld_blog("glass Unwrap pattern not found"); return; }
    g_glass_vt_expect = lib.elf_base + 0x1190e98u;
    void *backup = NULL;
    if (api->hook((void *)target, (void *)ld_unwrap_glass_hook, &backup) == 0 && backup) {
        g_orig_unwrap_glass = (void *(*)(void *, int))backup;
        installed = true;
        ld_blog("glass Unwrap hook installed at %p", (void *)target);
    }
}
#endif
