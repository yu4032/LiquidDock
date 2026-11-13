/* OS4 Dart-side RoundedRectangleBorder -> RoundedSuperellipseBorder probe.
 *
 * The launcher already ships RoundedSuperellipseBorder (continuous corners) in
 * its Flutter AOT snapshot next to RoundedRectangleBorder.  Both are ShapeBorder
 * implementations with the same public surface (paint / getOuterPath /
 * paintInterior / scale / copyWith), so if their Dart object layouts match, the
 * Dock border instance can be turned into the superellipse one by rewriting the
 * class id in its object header.
 *
 * This header only observes: it logs the border object header and first fields
 * for both classes so the class ids and field layout can be compared on device.
 *
 * Gated by /data/user_de/0/com.miui.home/files/liquiddock-superellipse.
 */
#ifndef LD_DART_SUPERELLIPSE_H
#define LD_DART_SUPERELLIPSE_H

#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "dart_heap.h"
#include "dart_observer.h"
#include "lib_base.h"

#define LD_DSE_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-superellipse"
#define LD_DSE_RRECT_PAINT_VADDR 0xf87818u
#define LD_DSE_SUPER_PAINT_VADDR 0xf87a08u
#define LD_DSE_RRECT_PATH_VADDR 0x10bc464u
#define LD_DSE_SUPER_PATH_VADDR 0x10bc61cu

static void *g_orig_dse_rrect_paint __attribute__((used));
static void *g_orig_dse_super_paint __attribute__((used));
static void *g_orig_dse_rrect_path __attribute__((used));
static void *g_orig_dse_super_path __attribute__((used));
static volatile unsigned g_dse_rrect_logs, g_dse_super_logs;

static void ld_dse_dump(const char *tag, struct ld_dart_context *c, volatile unsigned *counter)
{
    unsigned n = __atomic_fetch_add(counter, 1u, __ATOMIC_RELAXED);
    if (n >= 6u) return;
    uintptr_t self = (uintptr_t)c->x[0];
    uint64_t words[4] = {0, 0, 0, 0};
    if (!self || !ld_read_heap(self, words, sizeof words)) {
        ld_blog("dse: %s self=%p unreadable", tag, (void *)self);
        return;
    }
    ld_blog("dse: %s #%u self=%p hdr=%llx f0=%llx f1=%llx f2=%llx",
            tag, n + 1u, (void *)self,
            (unsigned long long)words[0], (unsigned long long)words[1],
            (unsigned long long)words[2], (unsigned long long)words[3]);
}

__attribute__((used)) static void ld_observe_dse_rrect_paint(struct ld_dart_context *c)
{
    ld_dse_dump("rrect.paint", c, &g_dse_rrect_logs);
}

__attribute__((used)) static void ld_observe_dse_super_paint(struct ld_dart_context *c)
{
    ld_dse_dump("super.paint", c, &g_dse_super_logs);
}

__attribute__((used)) static void ld_observe_dse_rrect_path(struct ld_dart_context *c)
{
    ld_dse_dump("rrect.getOuterPath", c, &g_dse_rrect_logs);
}

__attribute__((used)) static void ld_observe_dse_super_path(struct ld_dart_context *c)
{
    ld_dse_dump("super.getOuterPath", c, &g_dse_super_logs);
}

LD_DART_OBSERVER(repl_dse_rrect_paint, ld_observe_dse_rrect_paint, g_orig_dse_rrect_paint)
LD_DART_OBSERVER(repl_dse_super_paint, ld_observe_dse_super_paint, g_orig_dse_super_paint)
LD_DART_OBSERVER(repl_dse_rrect_path, ld_observe_dse_rrect_path, g_orig_dse_rrect_path)
LD_DART_OBSERVER(repl_dse_super_path, ld_observe_dse_super_path, g_orig_dse_super_path)

static void ld_dart_superellipse_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_DSE_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t rrect = 0, super = 0;
    if (!ld_vaddr_to_runtime(lib, LD_DSE_RRECT_PAINT_VADDR, &rrect) ||
        !ld_vaddr_to_runtime(lib, LD_DSE_SUPER_PAINT_VADDR, &super)) return;
    if (*(const uint32_t *)rrect != 0xa9bf79fdu || *(const uint32_t *)super != 0xa9bf79fdu) return;
    if (!g_orig_dse_rrect_paint) {
        void *backup = NULL;
        if (api->hook((void *)rrect, (void *)repl_dse_rrect_paint, &backup) == 0 && backup) {
            g_orig_dse_rrect_paint = backup;
            ld_blog("dse: rrect.paint hooked at %p", (void *)rrect);
        }
    }
    if (!g_orig_dse_super_paint) {
        void *backup = NULL;
        if (api->hook((void *)super, (void *)repl_dse_super_paint, &backup) == 0 && backup) {
            g_orig_dse_super_paint = backup;
            ld_blog("dse: super.paint hooked at %p", (void *)super);
        }
    }
    uintptr_t rpath = 0, spath = 0;
    if (!g_orig_dse_rrect_path &&
        ld_vaddr_to_runtime(lib, LD_DSE_RRECT_PATH_VADDR, &rpath)) {
        void *backup = NULL;
        if (api->hook((void *)rpath, (void *)repl_dse_rrect_path, &backup) == 0 && backup) {
            g_orig_dse_rrect_path = backup;
            ld_blog("dse: rrect.getOuterPath hooked at %p", (void *)rpath);
        }
    }
    if (!g_orig_dse_super_path &&
        ld_vaddr_to_runtime(lib, LD_DSE_SUPER_PATH_VADDR, &spath)) {
        void *backup = NULL;
        if (api->hook((void *)spath, (void *)repl_dse_super_path, &backup) == 0 && backup) {
            g_orig_dse_super_path = backup;
            ld_blog("dse: super.getOuterPath hooked at %p", (void *)spath);
        }
    }
    if (g_orig_dse_rrect_paint && g_orig_dse_super_paint) state = 1;
}

#endif
