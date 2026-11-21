#ifndef LD_ROTATION_PROVIDER_H
#define LD_ROTATION_PROVIDER_H

#include "screen_rotation.h"
#include "screen_rotation_jni.h"
#include "rust_symbol_resolver.h"
#include "dart_observer.h"

/*
 * RotationHelper.updateRotation receives an untagged Surface quarter turn
 * from the vendor's own Rust display callback or startup probe.
 *
 * Observe at ENTRY and immediately tail-call the original. Both entry and
 * post observers briefly use a C stack below Dart x15; the entry observer
 * has less state and does not retain LR or a shadow frame across a vendor
 * call. No claim is made that entry observation eliminates temporary frames.
 *
 * On each delivered rotation, pair Display.getSize() with getRotation()
 * on demand. If the framework has not published that same rotation,
 * the event's physical orientation is UNKNOWN until a subsequent event.
 *
 * This is a LAUNCHER event signal, not a framework DisplayInfo poll. A
 * symbol's name alone is never enough to hook: executable mapping and the
 * expected Dart prologue/ABI are checked before installation.
 */
static const uint32_t ld_rotation_update_words[] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10121efu, 0xf81f83a1u, 0xf81f03a2u,
    0xf9403f40u, 0xf952ac00u, 0xf9402370u, 0x6b10001fu, 0x54000061u,
};
#define LD_ROTATION_UPDATE_HINT 0xa02874u

static void *g_orig_rotation_update __attribute__((used));
static int g_rotation_provider_ready;

static bool ld_rotation_verify_target(const ld_lib_info *lib, uintptr_t target,
                                      uintptr_t vaddr, size_t size, bool named)
{
    if (!lib || !target || (target & 3u) ||
        !executable_range(target, 40u) ||
        !ld_hook_site_safe(target, "RotationHelper.updateRotation"))
        return false;
    const uint32_t *w = (const uint32_t *)target;
    /* Stable Dart AOT frame setup across the inspected Launcher variants. */
    if (w[0] != 0xa9bf79fdu || w[1] != 0xaa0f03fdu ||
        (w[2] & 0xffc003ffu) != 0xd10001efu)
        return false;
    if (named) {
        if (size < 40u || size > 16384u || size > UINTPTR_MAX - target ||
            !executable_range(target + size - 4u, 4u) ||
            vaddr > UINTPTR_MAX - (size - 4u) ||
            !ld_cross_check_strict(lib, vaddr, vaddr + size - 4u))
            return false;
    } else {
        /* A fingerprint fallback must match ALL 10 words, not just frame
         * setup. The scanner rejects duplicates and short-prefix fallback
         * only applies to patterns longer than 24 words. */
        for (size_t i = 0; i < sizeof ld_rotation_update_words / sizeof(uint32_t); i++)
            if (w[i] != ld_rotation_update_words[i]) return false;
    }
    return true;
}

__attribute__((used)) static void ld_rotation_update_cb(struct ld_dart_context *c)
{
    if (!c) return;
    uint64_t value = c->x[2];
    if (value > 3u) {
        static unsigned unexpected;
        if (__atomic_fetch_add(&unexpected, 1u, __ATOMIC_RELAXED) < 4u)
            ld_blog("rotation provider: unrecognized argument 0x%llx",
                    (unsigned long long)value);
        return;
    }
    struct ld_screen_dimensions size = ld_screen_query_framework_dimensions((int)value);
    ld_rotation_publish((int)value, size);
}
LD_DART_OBSERVER(repl_rotation_update, ld_rotation_update_cb, g_orig_rotation_update)

static void ld_rotation_provider_install(const struct native_api *api,
                                         const ld_lib_info *lib)
{
    if (g_rotation_provider_ready || !api || !api->hook || !lib) return;
    uintptr_t target = 0;
    const char *source = "name";
    ld_rust_sym_result_t sym;
    if (ld_resolve_launcher_symbol(lib, ld_launcher_apk_substr(),
                                   "RotationHelper.updateRotation", &sym)) {
        if (ld_rotation_verify_target(lib, sym.runtime, sym.vaddr, sym.size, true)) {
            target = sym.runtime;
            ld_blog("rotation provider: verified named symbol at %p vaddr=0x%lx size=%zu",
                    (void *)target, (unsigned long)sym.vaddr, sym.size);
        } else {
            /* A named symbol with an unexpected ABI is evidence of a real
             * semantic change: do not silently hook a nearby fingerprint. */
            ld_blog("rotation provider: named symbol failed code/ABI validation; disabled");
            return;
        }
    } else {
        source = "full-fingerprint";
        uintptr_t fallback = ld_find_aot_pattern(lib, LD_ROTATION_UPDATE_HINT,
                  ld_rotation_update_words,
                  sizeof ld_rotation_update_words / sizeof(uint32_t));
        if (ld_rotation_verify_target(lib, fallback, 0, 0, false))
            target = fallback;
    }
    if (!target) {
        ld_blog("rotation provider: no verified RotationHelper.updateRotation target");
        return;
    }

    void *backup = NULL;
    int rc = api->hook((void *)target, (void *)repl_rotation_update, &backup);
    if (rc == 0 && backup) {
        g_orig_rotation_update = backup;
        g_rotation_provider_ready = 1;
        pin_installed_hook(target);
        ld_blog("rotation provider: installed at %p via %s", (void *)target, source);
    } else {
        ld_blog("rotation provider: hook failed rc=%d backup=%p", rc, backup);
    }
}
#endif
