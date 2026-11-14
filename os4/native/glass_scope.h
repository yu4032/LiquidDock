#ifndef LD_OS4_GLASS_SCOPE_H
#define LD_OS4_GLASS_SCOPE_H

#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include "aot_scanner.h"
#include "dart_observer.h"
#include "dart_heap.h"
#include "native_api.h"
#include "native_glass_schema.h"

/* Scope ranges resolved dynamically in libapp.so */
static uintptr_t g_scope_dock_dark_start, g_scope_dock_dark_end;
static uintptr_t g_scope_dock_light_start, g_scope_dock_light_end;
static uintptr_t g_scope_recents_start, g_scope_recents_end;
static uintptr_t g_scope_folder_light_start, g_scope_folder_light_end;
static uintptr_t g_scope_sysa_start, g_scope_sysa_end;
static uintptr_t g_scope_sysb_start, g_scope_sysb_end;
static uintptr_t g_scope_build_folder_glass;

static __thread int g_ld_widget_build_scope;
static __thread int g_ld_pending_glass_scope;

static void *g_orig_glass_v7_entry __attribute__((used));
static void *g_orig_glass_widget_build_filter __attribute__((used));
static void *g_orig_build_folder_glass __attribute__((used));
static void *g_orig_folder_light_gate __attribute__((used));
static void *g_orig_recents_glass_entry __attribute__((used));

static inline void ld_glass_scope_set_pending(int scope) {
    g_ld_pending_glass_scope = scope;
}

static int g_glass_scope_installed;

static inline uint64_t ld_read_q0_u64(const struct ld_dart_context *c) {
    uint64_t bits = 0;
    memcpy(&bits, c->q[0], 8);
    return bits;
}

static inline void ld_write_q0_u64(struct ld_dart_context *c, uint64_t bits) {
    memcpy(c->q[0], &bits, 8);
}

static inline void ld_tag_q0_scope(struct ld_dart_context *c, int scope) {
    uint64_t bits = ld_read_q0_u64(c);
    bits = (bits & ~7ull) | ((uint64_t)scope & 7ull);
    ld_write_q0_u64(c, bits);
}

static inline int ld_in_range(uintptr_t addr, uintptr_t start, uintptr_t end) {
    return start != 0 && addr >= start && addr < end;
}

__attribute__((used)) static void ld_on_glass_v7_entry(struct ld_dart_context *c) {
    uint64_t bits = ld_read_q0_u64(c);
    int tag = (int)(bits & 7ull);
    int pending = g_ld_pending_glass_scope;
    g_ld_pending_glass_scope = 0;
    if (tag >= LD_GLASS_SCOPE_DOCK && tag < LD_GLASS_SCOPE_COUNT) {
        g_ld_active_glass_scope = tag;
    } else if (pending >= LD_GLASS_SCOPE_DOCK && pending < LD_GLASS_SCOPE_COUNT) {
        g_ld_active_glass_scope = pending;
        ld_tag_q0_scope(c, pending);
    } else {
        g_ld_active_glass_scope = LD_GLASS_SCOPE_GLOBAL;
    }
}

static inline void ld_glass_scope_on_image_filter_entry(struct ld_dart_context *c) {
    uintptr_t lr = (uintptr_t)c->x[30];
    int scope = LD_GLASS_SCOPE_GLOBAL;
    if (ld_in_range(lr, g_scope_dock_dark_start, g_scope_dock_dark_end) ||
        ld_in_range(lr, g_scope_dock_light_start, g_scope_dock_light_end)) {
        scope = LD_GLASS_SCOPE_DOCK;
    } else if (ld_in_range(lr, g_scope_recents_start, g_scope_recents_end)) {
        scope = LD_GLASS_SCOPE_RECENTS;
    } else if (ld_in_range(lr, g_scope_folder_light_start, g_scope_folder_light_end)) {
        scope = LD_GLASS_SCOPE_LARGE_FOLDER;
    } else if (ld_in_range(lr, g_scope_sysa_start, g_scope_sysa_end) ||
               ld_in_range(lr, g_scope_sysb_start, g_scope_sysb_end)) {
        scope = LD_GLASS_SCOPE_ICON;
    } else if (g_ld_widget_build_scope >= LD_GLASS_SCOPE_DOCK &&
               g_ld_widget_build_scope < LD_GLASS_SCOPE_COUNT) {
        scope = g_ld_widget_build_scope;
        g_ld_widget_build_scope = 0;
    }
    g_ld_pending_glass_scope = scope;
}

__attribute__((used)) static void ld_on_glass_widget_build_filter(struct ld_dart_context *c) {
    (void)c;
    if (g_ld_widget_build_scope != LD_GLASS_SCOPE_LARGE_FOLDER &&
        g_ld_widget_build_scope != LD_GLASS_SCOPE_ICON) {
        g_ld_widget_build_scope = LD_GLASS_SCOPE_WIDGET;
    }
}

__attribute__((used)) static void ld_on_recents_glass_entry(struct ld_dart_context *c) {
    (void)c;
    g_ld_pending_glass_scope = LD_GLASS_SCOPE_RECENTS;
}

__attribute__((used)) static void ld_on_build_folder_glass(struct ld_dart_context *c) {
    uintptr_t lr = (uintptr_t)c->x[30];
    int scope = LD_GLASS_SCOPE_WIDGET;
    if (ld_in_range(lr, g_scope_folder_light_start, g_scope_folder_light_end)) {
        scope = LD_GLASS_SCOPE_LARGE_FOLDER;
        if (g_home_options.glass_enabled && g_home_options.large_folder_corner > 0.0) {
            double corner = g_home_options.large_folder_corner;
            uint64_t bits = 0;
            memcpy(&bits, &corner, sizeof bits);
            ld_write_q0_u64(c, bits);
        }
    } else if (ld_in_range(lr, g_scope_sysa_start, g_scope_sysa_end) ||
               ld_in_range(lr, g_scope_sysb_start, g_scope_sysb_end)) {
        scope = LD_GLASS_SCOPE_ICON;
    }
    ld_tag_q0_scope(c, scope);
}

__attribute__((used)) static void ld_on_folder_light_gate(struct ld_dart_context *c) {
    (void)c;
    g_ld_widget_build_scope = LD_GLASS_SCOPE_LARGE_FOLDER;
}

LD_DART_OBSERVER(repl_glass_v7_entry, ld_on_glass_v7_entry, g_orig_glass_v7_entry)
LD_DART_OBSERVER(repl_glass_widget_build_filter, ld_on_glass_widget_build_filter, g_orig_glass_widget_build_filter)
LD_DART_OBSERVER(repl_build_folder_glass, ld_on_build_folder_glass, g_orig_build_folder_glass)
LD_DART_OBSERVER(repl_folder_light_gate, ld_on_folder_light_gate, g_orig_folder_light_gate)
LD_DART_OBSERVER(repl_recents_glass_entry, ld_on_recents_glass_entry, g_orig_recents_glass_entry)

static const uint32_t ld_pat_glass_widget_build_filter[] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd107e1efu, 0xaa0103e2u, 0xf81f03a1u, 0xb840b040u,
    0x8b1c8000u, 0x6b16001fu, 0x540030e0u, 0xb840b003u, 0x8b1c8063u, 0xf81f83a3u,
    0xb8427060u, 0x8b1c8000u, 0xf8407004u, 0x937f7880u, 0xeb80049fu, 0x54000060u,
    0x9423d6c4u, 0xf8007004u,
};
static const uint32_t ld_pat_recents_glass_build[] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10521efu, 0xd2800100u, 0xaa0103e3u, 0xf81e03a1u,
    0xb840b064u, 0x8b1c8084u, 0xf81e83a4u, 0x6b16009fu, 0x54001b40u, 0xf8417086u,
    0xf81f03a6u, 0xfc40f085u, 0xfc1b83a5u, 0xb840b085u,
};
static const uint32_t ld_pat_folder_light_build[] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10081efu, 0xaa0103e0u, 0xf81f83a1u, 0xb840b001u,
    0x8b1c8021u, 0x6b16003fu, 0x54000520u, 0xb8417022u, 0x8b1c8042u, 0x36200102u,
    0xb8413022u, 0x8b1c8042u, 0xd2802471u, 0xb8716841u, 0x8b1c8021u, 0x97f212abu,
    0x37200060u, 0x92800001u,
};

static void ld_glass_scope_install(
    const struct native_api *api,
    const ld_lib_info *lib,
    uintptr_t v7_entry,
    uintptr_t build_folder_glass,
    uintptr_t sysa_rt,
    uintptr_t sysb_rt,
    uintptr_t dock_light_start,
    uintptr_t dock_light_end,
    uintptr_t dock_dark_start,
    uintptr_t dock_dark_end
) {
    if (g_glass_scope_installed || !api || !api->hook || !lib) return;

    g_scope_sysa_start = sysa_rt;
    g_scope_sysa_end = sysa_rt ? sysa_rt + 0x300u : 0;
    g_scope_sysb_start = sysb_rt;
    g_scope_sysb_end = sysb_rt ? sysb_rt + 0x300u : 0;
    g_scope_dock_light_start = dock_light_start;
    g_scope_dock_light_end = dock_light_end;
    g_scope_dock_dark_start = dock_dark_start;
    g_scope_dock_dark_end = dock_dark_end;

    uintptr_t recents = ld_find_aot_pattern(lib, 0x11de554u, ld_pat_recents_glass_build, 16);
    if (recents) {
        g_scope_recents_start = recents;
        g_scope_recents_end = recents + 0x500u;
    }
    uintptr_t folder_light = ld_find_aot_pattern(lib, 0x11b26c0u, ld_pat_folder_light_build, 20);
    if (folder_light) {
        g_scope_folder_light_start = folder_light;
        g_scope_folder_light_end = folder_light + 0x100u;
    }

    uintptr_t build_filter = ld_find_aot_pattern(lib, 0x12e8730u, ld_pat_glass_widget_build_filter, 20);

    int ok = 0;
    if (v7_entry && !g_orig_glass_v7_entry && ld_hook_site_safe(v7_entry, "glass_v7")) {
        if (api->hook((void *)v7_entry, (void *)repl_glass_v7_entry, &g_orig_glass_v7_entry) == 0 &&
            g_orig_glass_v7_entry) {
            ld_pin_code_pages_soft(v7_entry, 16);
            ok++;
        }
    }
    if (build_filter && !g_orig_glass_widget_build_filter && ld_hook_site_safe(build_filter, "build_filter")) {
        if (api->hook((void *)build_filter, (void *)repl_glass_widget_build_filter, &g_orig_glass_widget_build_filter) == 0 &&
            g_orig_glass_widget_build_filter) {
            ld_pin_code_pages_soft(build_filter, 16);
            ok++;
        }
    }
    if (build_folder_glass && !g_orig_build_folder_glass && ld_hook_site_safe(build_folder_glass, "build_folder_glass")) {
        if (api->hook((void *)build_folder_glass, (void *)repl_build_folder_glass, &g_orig_build_folder_glass) == 0 &&
            g_orig_build_folder_glass) {
            ld_pin_code_pages_soft(build_folder_glass, 16);
            ok++;
        }
    }
    if (folder_light && !g_orig_folder_light_gate && ld_hook_site_safe(folder_light, "folder_light")) {
        uintptr_t gate = folder_light;
        if (api->hook((void *)gate, (void *)repl_folder_light_gate, &g_orig_folder_light_gate) == 0 &&
            g_orig_folder_light_gate) {
            ld_pin_code_pages_soft(gate, 16);
            ok++;
        }
    }
    if (recents && !g_orig_recents_glass_entry && ld_hook_site_safe(recents, "recents_glass")) {
        if (api->hook((void *)recents, (void *)repl_recents_glass_entry, &g_orig_recents_glass_entry) == 0 &&
            g_orig_recents_glass_entry) {
            ld_pin_code_pages_soft(recents, 16);
        }
    }

    g_glass_scope_installed = (ok >= 4);
    ld_blog("OS4 glass scope hooks installed=%d/4 (v7=%d bf=%d bfg=%d fl=%d)",
            ok, v7_entry != 0, build_filter != 0, build_folder_glass != 0, folder_light != 0);
}

#endif
