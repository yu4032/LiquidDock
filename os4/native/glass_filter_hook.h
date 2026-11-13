#ifndef LIQUIDDOCK_GLASS_FILTER_HOOK_H
#define LIQUIDDOCK_GLASS_FILTER_HOOK_H
/* Applies native_glass customization to every DlGlassImageFilter that reaches the engine
 * (icons, dock, widgets) instead of only the Dart sites that observe the clock widget. */
static bool (*g_orig_glass_equals)(void *self, void *other);
static uintptr_t g_glass_vt_expect;

static void ld_glass_apply_obj(void *obj)
{
    uintptr_t vt = 0;
    if (!obj || !g_glass_vt_expect || !ld_read_heap((uintptr_t)obj, &vt, 8) || vt != g_glass_vt_expect) return;
    ld_native_glass_apply((uintptr_t)obj, &g_home_options.native_glass, ld_read_heap, ld_native_glass_write);
}

static bool ld_glass_equals_hook(void *self, void *other)
{
    static unsigned calls;
    unsigned n = __atomic_fetch_add(&calls, 1u, __ATOMIC_RELAXED);
    if (g_home_options.glass_enabled && g_home_options.native_glass.mask) {
        ld_glass_apply_obj(self);
        ld_glass_apply_obj(other);
        if (n < 3 || n == 1000) ld_blog("glass Equals hook call=%u self=%p other=%p", n, self, other);
    }
    return g_orig_glass_equals(self, other);
}

static void ld_glass_filter_hook_install(const struct native_api *api)
{
    static bool installed;
    if (installed || !api || !api->hook || !g_home_options.glass_enabled) return;
    ld_lib_info lib = {0};
    if (!ld_resolve_so("libhyper_os_flutter.so", &lib) || !lib.elf_base) return;
    static const uint32_t pat[8] = {
        0xbd402800u, 0xbd402821u, 0x52824de8u, 0x72a75068u,
        0x7ea1d400u, 0x1e270101u, 0x1e212000u, 0x540028c8u,
    };
    uintptr_t target = ld_find_aot_pattern(&lib, 0x7f1540u, pat, 8);
    if (!target) { ld_blog("glass Equals pattern not found"); return; }
    g_glass_vt_expect = lib.elf_base + 0x1190e98u;
    void *backup = NULL;
    if (api->hook((void *)target, (void *)ld_glass_equals_hook, &backup) == 0 && backup) {
        g_orig_glass_equals = (bool (*)(void *, void *))backup;
        installed = true;
        ld_blog("glass Equals hook installed at %p", (void *)target);
    }
}
#endif
