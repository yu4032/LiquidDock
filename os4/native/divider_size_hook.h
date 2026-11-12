/* OS4 Dock 分隔符尺寸：后置写 HotseatItemSizeProvider.sizeOf 的返回值。
 *
 * sizeOf @0x9c2430 为每个 hotseat item 构造 Size；分隔符是其中的一个 item。
 * 用后置观察器拿到返回的 Size 对象（字段 +7 width / +0xf height），
 * 只对「很窄」的结果（width < 10，即分隔符）覆写宽高。
 *
 * 配置：liquiddock-native.conf 的 dock_divider_width / dock_divider_height_scale
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-divider-size
 */
#ifndef LD_DIVIDER_SIZE_HOOK_H
#define LD_DIVIDER_SIZE_HOOK_H

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"
#include "dart_heap.h"
#include "dart_post_observer.h"

#define LD_DSIZE_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-divider-size"
#define LD_DSIZE_CONF "/data/user/0/com.miui.home/files/liquiddock-native.conf"
#define LD_DSIZE_VADDR 0x9c2430u

static double g_dsize_width __attribute__((used)) = 10.0;
static double g_dsize_height __attribute__((used)) = 40.0;
static int g_dsize_enabled __attribute__((used));
static volatile unsigned g_dsize_logs;

__attribute__((used)) static void ld_dsize_callback(struct ld_dart_context *c, uint64_t result)
{
    (void)c;
    uintptr_t size_obj = (uintptr_t)result;
    if (!g_dsize_enabled || !size_obj) return;
    double w = 0, h = 0;
    if (!ld_read_heap(size_obj + 7, &w, 8) || !ld_read_heap(size_obj + 0xf, &h, 8)) return;
    if (!(w > 0.0 && w < 10.0)) return;      /* 只改很窄的 item（分隔符） */
    double nw = g_dsize_width, nh = g_dsize_height;
    __builtin_memcpy((void *)(size_obj + 7), &nw, 8);
    __builtin_memcpy((void *)(size_obj + 0xf), &nh, 8);
    unsigned n = __atomic_fetch_add(&g_dsize_logs, 1u, __ATOMIC_RELAXED);
    if (n < 4u) ld_blog("divider-size: item %.1fx%.1f -> %.1fx%.1f", w, h, nw, nh);
}

static void *g_dsize_orig __attribute__((used));
LD_DART_POST_OBSERVER(repl_dsize_sizeof, ld_dsize_callback, g_dsize_orig)

static void ld_divider_size_load_conf(void)
{
    FILE *f = fopen(LD_DSIZE_CONF, "r");
    if (!f) return;
    char line[160];
    while (fgets(line, sizeof line, f)) {
        int v = 0;
        if (sscanf(line, "dock_divider_enabled=%d", &v) == 1) g_dsize_enabled = v != 0;
        else if (sscanf(line, "dock_divider_width=%d", &v) == 1) g_dsize_width = (double)v;
        else if (sscanf(line, "dock_divider_height_scale=%d", &v) == 1) g_dsize_height = (double)v;
    }
    fclose(f);
    ld_blog("divider-size: enabled=%d w=%.1f h=%.1f", g_dsize_enabled, g_dsize_width, g_dsize_height);
}

static void ld_divider_size_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    if (access(LD_DSIZE_MARKER, F_OK) != 0) { state = 2; return; }
    ld_divider_size_load_conf();
    if (!g_dsize_enabled) { state = 2; return; }
    uintptr_t fn = 0;
    if (!ld_vaddr_to_runtime(lib, LD_DSIZE_VADDR, &fn)) return;
    static const uint32_t prologue[3] = {0xa9bf79fdu, 0xaa0f03fdu, 0xd100c1efu};
    if (memcmp((const void *)fn, prologue, sizeof prologue) != 0) {
        state = 2;
        ld_blog("divider-size: prologue mismatch at %p", (void *)fn);
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)fn, (void *)repl_dsize_sizeof, &backup) == 0 && backup) {
        g_dsize_orig = backup;
        state = 1;
        ld_blog("divider-size: sizeOf hooked at %p", (void *)fn);
    } else {
        state = 2;
        ld_blog("divider-size: hook failed");
    }
}

#endif
