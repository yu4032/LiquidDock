/* OS4 continuous-corner (superellipse) port.
 *
 * The launcher's Flutter engine already ships a full RoundSuperellipse pipeline
 * (impeller::RoundSuperellipse*, flutter::DisplayListMatrixClipState::clipRSuperellipse,
 * DisplayListBuilder::ClipRoundSuperellipse / DrawRoundSuperellipse).  The Dock
 * glass, however, is recorded as a rounded rect:
 *
 *   flutter::DisplayListMatrixClipState::clipRRect(const impeller::RoundRect&,
 *                                                  DlClipOp, bool)   @0x7f699c
 *
 * This header observes that call first (geometry only, marker gated), so the
 * dock's exact bounds and corner radii can be identified before the conversion
 * to clipRSuperellipse() is switched on.
 *
 * Gated by /data/user_de/0/com.miui.home/files/liquiddock-superellipse.
 */
#ifndef LD_SUPERELLIPSE_H
#define LD_SUPERELLIPSE_H

#include <math.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"

/* First mapping of the engine library is its ELF base (first PT_LOAD, offset 0). */
static uintptr_t ld_se_engine_base(void)
{
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return 0;
    char line[512];
    uintptr_t base = 0;
    while (fgets(line, sizeof line, maps)) {
        if (!strstr(line, "libhyper_os_flutter.so")) continue;
        unsigned long start = 0;
        if (sscanf(line, "%lx-", &start) == 1) { base = (uintptr_t)start; break; }
    }
    fclose(maps);
    return base;
}

#define LD_SUPERELLIPSE_VADDR 0x7f699cu
#define LD_SUPERELLIPSE_MARKER \
    "/data/user_de/0/com.miui.home/files/liquiddock-superellipse"

typedef void (*ld_clip_rrect_fn)(void *self, const void *rrect, int op, int aa);

static void *g_se_orig_clip_rrect;
static volatile unsigned g_se_calls;

/* impeller::RoundRect: { TRect bounds; RoundingRadii radii; }
 * TRect is origin+size (4 floats); RoundingRadii is 4 corner radii (8 floats). */
typedef struct {
    float bounds[4];
    float radii[8];
} ld_round_rect;

__attribute__((used)) static void ld_se_clip_rrect(void *self, const void *rrect, int op, int aa)
{
    ld_round_rect r;
    __builtin_memset(&r, 0, sizeof r);
    if (rrect) __builtin_memcpy(&r, rrect, sizeof r);
    unsigned n = __atomic_fetch_add(&g_se_calls, 1u, __ATOMIC_RELAXED);
    int wide = r.bounds[2] > 600.0f && r.bounds[3] < 320.0f;
    if (n < 8u || wide) {
        if (!(wide && n >= 8u && (n % 40u) != 0u))
        ld_blog("superellipse: clipRRect #%u bounds=%.1f,%.1f %.1fx%.1f "
                "radii=%.1f,%.1f,%.1f,%.1f op=%d",
                n + 1u, r.bounds[0], r.bounds[1], r.bounds[2], r.bounds[3],
                r.radii[0], r.radii[1], r.radii[2], r.radii[3], op);
    }
    if (g_se_orig_clip_rrect)
        ((ld_clip_rrect_fn)g_se_orig_clip_rrect)(self, rrect, op, aa);
}

static void ld_superellipse_install(const struct native_api *api)
{
    static int state;
    if (state || !api || !api->hook) return;
    if (access(LD_SUPERELLIPSE_MARKER, F_OK) != 0) { state = 2; return; }
    uintptr_t base = ld_se_engine_base();
    if (!base) return;
    void *fn = (void *)(base + LD_SUPERELLIPSE_VADDR);
    /* sub sp,sp,#0x50 / stp x30,x21,[sp,#0x30] / stp x20,x19,[sp,#0x40] */
    static const uint32_t prologue[3] = {0xd10143ffu, 0xa90357feu, 0xa9044ff4u};
    if (memcmp(fn, prologue, sizeof prologue) != 0) {
        state = 2;
        ld_blog("superellipse: prologue mismatch at %p", fn);
        return;
    }
    void *backup = NULL;
    if (api->hook(fn, (void *)ld_se_clip_rrect, &backup) == 0 && backup) {
        g_se_orig_clip_rrect = backup;
        state = 1;
        ld_blog("superellipse: clipRRect probe installed at %p", fn);
    } else {
        state = 2;
        ld_blog("superellipse: hook failed");
    }
}

#endif
