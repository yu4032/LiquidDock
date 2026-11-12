/* OS4 Dock 分隔符：绘制期几何 + 颜色（8604，正式实现）。
 *
 * 注入点（全部只写 Dart 堆对象，不碰只读页、不做堆分配）：
 *   _paintBackgroundColor @0x15af7b4  (painter, canvas, rect, textDirection)
 *     - rect 是 _BoxDecorationPainter.paint 里 offset & size 新建的 Rect（sub_0x8c1df8）
 *       => 每次绘制可改：left/top/right/bottom 控制宽度、高度、上下偏移
 *     - painter+0x10 是缓存的 Paint；Paint+8 -> _data；颜色 float32 在
 *       buf + off + {4,8,12,16} = r,g,b,1-a （Paint.color= @0x94f5e8 实证）
 *   _getBackgroundPaint @0x15af990 后置：Paint 首次创建时补写颜色
 *
 * 识别分隔符：painter+0xc -> decoration，decoration+8 -> color，
 *            与内层闭包 PP[0xd4a30] 的厂商 const Color 比较。
 *
 * 门控：/data/user_de/0/com.miui.home/files/liquiddock-divider
 * 配置：liquiddock-native.conf（键必须写在 end=1 之前）
 *        dock_divider_width        线宽 dp（>0 生效，默认厂商 1.0）
 *        dock_divider_height_scale 高度百分比（>0 生效）
 *        dock_divider_y_offset     上下偏移 dp（0 不变）
 *        dock_divider_color_r/g/b  颜色（>=0 生效）
 *        dock_divider_alpha        不透明度 0..255（>=0 生效）
 */
#ifndef LD_DIVIDER_PAINT_H
#define LD_DIVIDER_PAINT_H

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>
#include "bootlog.h"
#include "native_api.h"
#include "lib_base.h"
#include "dart_observer.h"

#define LD_DP_MARKER "/data/user_de/0/com.miui.home/files/liquiddock-divider-force"
#define LD_DP_CONF   "/data/user/0/com.miui.home/files/liquiddock-native.conf"

#define LD_DP_BGPAINT  0x15af7b4u
#define LD_DP_GETPAINT 0x15af990u

struct ld_dp_cfg {
    int enabled;            /* dock_divider_enabled */
    int width_dp10;         /* GUI 以 dp×10 存整数 */
    int height_pct;
    int y_offset_dp10;
    int color_r, color_g, color_b, alpha;
    int loaded;
    long stamp;             /* conf mtime，用于热加载 */
};
static struct ld_dp_cfg g_dp_cfg = {0, -1, -1, 0, -1, -1, -1, -1, 0, 0};
static volatile unsigned g_dp_reload_ticks;
static uint64_t g_dp_const_color;
static volatile unsigned g_dp_hits, g_dp_rect_writes, g_dp_color_writes;
static volatile int g_dp_color_off;

/* 热路径直读：参数是当前函数活着的 Dart 对象指针，直接读，避免每帧 syscall。
 * （上一版用 process_vm_readv 读每个装饰盒 -> 每帧上千次 syscall -> 桌面卡死。） */
static inline uint32_t ld_dp_ld32(uintptr_t a)
{
    uint32_t v = 0;
    if (a) __builtin_memcpy(&v, (const void *)a, sizeof v);
    return v;
}

static inline double ld_dp_ldd(uintptr_t a)
{
    double v = 0.0;
    if (a) __builtin_memcpy(&v, (const void *)a, sizeof v);
    return v;
}

static inline uint64_t ld_dp_ld64(uintptr_t a)
{
    uint64_t v = 0;
    if (a) __builtin_memcpy(&v, (const void *)a, sizeof v);
    return v;
}

static int ld_dp_read(uintptr_t address, void *out, size_t len)
{
    if (!address) return 0;
    struct iovec local = { out, len };
    struct iovec remote = { (void *)address, len };
    return syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0) == (long)len;
}

static void ld_dp_load_cfg(void)
{
    if (g_dp_cfg.loaded) return;
    g_dp_cfg.loaded = 1;
    char buf[8192];
    FILE *f = fopen(LD_DP_CONF, "r");
    if (!f) { ld_blog("divider: conf open failed"); return; }
    size_t n = fread(buf, 1, sizeof buf - 1, f);
    fclose(f);
    buf[n] = '\0';
    struct ld_dp_cfg c = g_dp_cfg;
    const char *p = buf;
    while (p && *p) {
        const char *eol = strchr(p, '\n');
        size_t len = eol ? (size_t)(eol - p) : strlen(p);
        if (len > 21 && !strncmp(p, "dock_divider_enabled=", 21)) c.enabled = atoi(p + 21);
        else if (len > 26 && !strncmp(p, "dock_divider_height_scale=", 26)) c.height_pct = atoi(p + 26);
        else if (len > 22 && !strncmp(p, "dock_divider_y_offset=", 22)) c.y_offset_dp10 = atoi(p + 22);
        else if (len > 21 && !strncmp(p, "dock_divider_color_r=", 21)) c.color_r = atoi(p + 21);
        else if (len > 21 && !strncmp(p, "dock_divider_color_g=", 21)) c.color_g = atoi(p + 21);
        else if (len > 21 && !strncmp(p, "dock_divider_color_b=", 21)) c.color_b = atoi(p + 21);
        else if (len > 19 && !strncmp(p, "dock_divider_alpha=", 19)) c.alpha = atoi(p + 19);
        else if (len > 19 && !strncmp(p, "dock_divider_width=", 19)) c.width_dp10 = atoi(p + 19);
        p = eol ? eol + 1 : NULL;
    }
    struct stat st;
    c.stamp = stat(LD_DP_CONF, &st) == 0 ? (long)st.st_mtime : 0;
    g_dp_cfg = c;
    ld_blog("divider: cfg enabled=%d w=%d(0.1dp) h%%=%d y=%d(0.1dp) rgb=%d,%d,%d a=%d",
            c.enabled, c.width_dp10, c.height_pct, c.y_offset_dp10,
            c.color_r, c.color_g, c.color_b, c.alpha);
}

/* painter -> 是否分隔符 的直接映射缓存，避免每帧对每个装饰盒做两次读调用。 */
static uintptr_t g_dp_cache_key[64];
static signed char g_dp_cache_val[64];
static int g_dp_cache_n;


/* 后台轮询：桌面静止时 paint 钩子不会触发，热加载必须独立于绘制。
 * 500ms stat 一次 conf 的 mtime，变了就重读；下一次绘制即用新值。 */
static void *ld_dp_poll_thread(void *arg)
{
    (void)arg;
    for (;;) {
        usleep(500000);
        struct stat st;
        if (stat(LD_DP_CONF, &st) != 0) continue;
        if ((long)st.st_mtime == g_dp_cfg.stamp) continue;
        g_dp_cfg.loaded = 0;
        ld_dp_load_cfg();
        ld_blog("divider: cfg reloaded (mtime=%ld)", (long)st.st_mtime);
    }
    return NULL;
}

static void ld_dp_start_poller(void)
{
    static int started;
    if (started) return;
    started = 1;
    pthread_t t;
    if (pthread_create(&t, NULL, ld_dp_poll_thread, NULL) == 0) pthread_detach(t);
    else ld_blog("divider: poller thread failed");
}

/* GUI 改完 conf 后热加载：绘制路径每 120 次再兜底检查一次 mtime。 */
static void ld_dp_maybe_reload(void)
{
    unsigned n = __atomic_fetch_add(&g_dp_reload_ticks, 1u, __ATOMIC_RELAXED);
    if ((n % 120u) != 0u) return;
    struct stat st;
    if (stat(LD_DP_CONF, &st) != 0) return;
    if ((long)st.st_mtime == g_dp_cfg.stamp) return;
    g_dp_cfg.loaded = 0;
    ld_dp_load_cfg();
    ld_blog("divider: cfg reloaded (mtime=%ld)", (long)st.st_mtime);
}

static int ld_dp_is_divider_uncached(uintptr_t painter, uintptr_t heap);

static int ld_dp_is_divider(uintptr_t painter, uintptr_t heap)
{
    unsigned slot = (unsigned)((painter >> 4) & 63u);
    if (g_dp_cache_val[slot] >= 0 && g_dp_cache_key[slot] == painter)
        return g_dp_cache_val[slot];
    int v = ld_dp_is_divider_uncached(painter, heap);
    g_dp_cache_key[slot] = painter;
    g_dp_cache_val[slot] = (signed char)v;
    if (g_dp_cache_n < 64) g_dp_cache_n++;
    return v;
}

static int ld_dp_is_divider_uncached(uintptr_t painter, uintptr_t heap)
{
    if (!painter) return 0;
    uint32_t dref = ld_dp_ld32(painter + 0xc);
    if (!dref) return 0;
    uintptr_t deco = heap + ((uintptr_t)dref & ~(uintptr_t)1);
    uint32_t cref = ld_dp_ld32(deco + 8);
    if (!cref) return 0;
    uintptr_t color = heap + ((uintptr_t)cref & ~(uintptr_t)1);
    return color == ((uintptr_t)g_dp_const_color & ~(uintptr_t)1);
}

static void ld_dp_apply_rect(uintptr_t rect)
{
    double l = ld_dp_ldd(rect + 8), t = ld_dp_ldd(rect + 16);
    double r = ld_dp_ldd(rect + 24), b = ld_dp_ldd(rect + 32);
    double w = r - l, h = b - t;
    if (!(w > 0.0) || !(h > 0.0)) return;
    double nl = l, nr = r, nt = t, nb = b;
    double cx = (l + r) * 0.5;
    if (g_dp_cfg.width_dp10 >= 0) {
        double dw = (double)g_dp_cfg.width_dp10 / 10.0;   /* GUI: dp×10 */
        nl = cx - dw * 0.5;
        nr = cx + dw * 0.5;                               /* 0 => 不可见 */
    }
    if (g_dp_cfg.height_pct >= 0) {
        double cy = (t + b) * 0.5;
        double nh = h * (double)g_dp_cfg.height_pct / 100.0;  /* 0 => 不可见 */
        nt = cy - nh * 0.5;
        nb = cy + nh * 0.5;
    }
    if (g_dp_cfg.y_offset_dp10 != 0) {
        double dy = (double)g_dp_cfg.y_offset_dp10 / 10.0;
        nt += dy;
        nb += dy;
    }
    if (nl == l && nr == r && nt == t && nb == b) return;
    *(double *)(rect + 8) = nl;
    *(double *)(rect + 16) = nt;
    *(double *)(rect + 24) = nr;
    *(double *)(rect + 32) = nb;
    unsigned n = __atomic_fetch_add(&g_dp_rect_writes, 1u, __ATOMIC_RELAXED);
    if (n < 3u)
        ld_blog("divider: rect #%u %.2f,%.2f %.2fx%.2f -> %.2fx%.2f y%+d",
                n + 1u, l, t, w, h, nr - nl, nb - nt, g_dp_cfg.y_offset_dp10);
}

static void ld_dp_apply_color(uintptr_t painter, uintptr_t heap)
{
    if (g_dp_color_off) return;
    if (g_dp_cfg.color_r < 0 && g_dp_cfg.color_g < 0 && g_dp_cfg.color_b < 0 && g_dp_cfg.alpha < 0)
        return;
    /* painter.field_0x10 是 **压缩指针(u32)**：_getBackgroundPaint 里 `ldur w0,[x1,#0xf]`
     * 后 `add x0,x0,x28,lsl#32`。上一版按 8 字节读，拿到两个相邻字段拼成的
     * 0x808100008088 -> 解引用即 SEGV（tombstone_30: ld_dp_apply_color+92）。 */
    uint32_t pref = ld_dp_ld32(painter + 0x10);
    if (!pref) return;
    uintptr_t paint = heap + ((uintptr_t)pref & ~(uintptr_t)1);
    if ((paint & 7u) != 0 || paint < heap || paint - heap > 0x40000000u) return;
    uint32_t dref = ld_dp_ld32(paint + 8);
    if (!dref) return;
    uintptr_t data = heap + ((uintptr_t)dref & ~(uintptr_t)1);
    uint32_t bref = ld_dp_ld32(data + 0x18), off = ld_dp_ld32(data + 0x1c);
    if (!bref) return;
    uintptr_t backing = heap + ((uintptr_t)bref & ~(uintptr_t)1);
    uintptr_t buf = (uintptr_t)ld_dp_ld64(backing + 8);
    if (!buf) return;
    int o = (int)(int32_t)off >> 1;
    if (o < 0 || o > 600) return;
    float *dst = (float *)(buf + o);
    if (g_dp_cfg.color_r >= 0) dst[1] = (float)g_dp_cfg.color_r / 255.0f;
    if (g_dp_cfg.color_g >= 0) dst[2] = (float)g_dp_cfg.color_g / 255.0f;
    if (g_dp_cfg.color_b >= 0) dst[3] = (float)g_dp_cfg.color_b / 255.0f;
    if (g_dp_cfg.alpha >= 0) dst[4] = 1.0f - (float)g_dp_cfg.alpha / 255.0f;
    unsigned n = __atomic_fetch_add(&g_dp_color_writes, 1u, __ATOMIC_RELAXED);
    if (n < 3u)
        ld_blog("divider: color #%u buf=%p off=%d -> %.3f,%.3f,%.3f,%.3f", n + 1u,
                (void *)buf, o, dst[1], dst[2], dst[3], dst[4]);
}

/* ---- _paintBackgroundColor 入口 ---- */
static void *g_dp_bg_orig __attribute__((used));

__attribute__((used)) static void ld_dp_bg_cb(struct ld_dart_context *c)
{
    ld_dp_load_cfg();
    ld_dp_maybe_reload();
    if (!g_dp_cfg.enabled) return;
    if (!g_dp_const_color) return;
    uintptr_t heap = (uintptr_t)((uint64_t)c->x[28] << 32);
    uintptr_t painter = (uintptr_t)c->x[1] & ~(uintptr_t)1;
    uintptr_t rectp = (uintptr_t)c->x[3] & ~(uintptr_t)1;
    double w = ld_dp_ldd(rectp + 24) - ld_dp_ldd(rectp + 8);
    double h = ld_dp_ldd(rectp + 32) - ld_dp_ldd(rectp + 16);
    if (!(w > 0.0 && w <= 3.0 && h >= 18.0 && h <= 90.0)) return;
    if (!ld_dp_is_divider(painter, heap)) return;
    unsigned n = __atomic_fetch_add(&g_dp_hits, 1u, __ATOMIC_RELAXED);
    if (n < 3u) ld_blog("divider: hit #%u painter=%p", n + 1u, (void *)painter);
    ld_dp_apply_rect((uintptr_t)c->x[3] & ~(uintptr_t)1);
    ld_dp_apply_color(painter, heap);
}

LD_DART_OBSERVER(repl_dp_bg, ld_dp_bg_cb, g_dp_bg_orig)

/* ---- _getBackgroundPaint 后置：首次创建 Paint 时补写颜色 ---- */
static void *g_dp_gp_orig __attribute__((used));
static unsigned char g_dp_gp_save[0x100] __attribute__((aligned(16), used));

#define LD_DP_POST(name, callback, original, save) \
__attribute__((naked, used)) static void name(void) { \
    __asm__ volatile( \
        "adrp x16, " #save "\n" \
        "add x16, x16, :lo12:" #save "\n" \
        "stp x0, x1, [x16, #0]\n" \
        "stp x2, x3, [x16, #16]\n" \
        "stp x4, x5, [x16, #32]\n" \
        "stp x6, x7, [x16, #48]\n" \
        "str x8, [x16, #64]\n" \
        "str x15, [x16, #72]\n" \
        "str x30, [x16, #80]\n" \
        "str x28, [x16, #248]\n" \
        "stp q0, q1, [x16, #112]\n" \
        "stp q2, q3, [x16, #144]\n" \
        "stp q4, q5, [x16, #176]\n" \
        "stp q6, q7, [x16, #208]\n" \
        "ldp x0, x1, [x16, #0]\n" \
        "ldp x2, x3, [x16, #16]\n" \
        "ldp x4, x5, [x16, #32]\n" \
        "ldp x6, x7, [x16, #48]\n" \
        "ldr x8, [x16, #64]\n" \
        "ldr x15, [x16, #72]\n" \
        "ldr x30, [x16, #80]\n" \
        "ldp q0, q1, [x16, #112]\n" \
        "ldp q2, q3, [x16, #144]\n" \
        "ldp q4, q5, [x16, #176]\n" \
        "ldp q6, q7, [x16, #208]\n" \
        "adrp x17, " #original "\n" \
        "ldr x17, [x17, :lo12:" #original "]\n" \
        "blr x17\n" \
        "adrp x16, " #save "\n" \
        "add x16, x16, :lo12:" #save "\n" \
        "str x0, [x16, #88]\n" \
        "str x19, [x16, #96]\n" \
        "mov x19, sp\n" \
        "sub x17, x15, #1024\n" \
        "and x17, x17, #0xfffffffffffffff0\n" \
        "mov sp, x17\n" \
        "mov x0, x16\n" \
        "ldr x1, [x16, #88]\n" \
        "bl " #callback "\n" \
        "mov sp, x19\n" \
        "adrp x16, " #save "\n" \
        "add x16, x16, :lo12:" #save "\n" \
        "ldr x19, [x16, #96]\n" \
        "ldr x0, [x16, #88]\n" \
        "ret\n"); \
}

__attribute__((used)) static void ld_dp_gp_post(struct ld_dart_context *c, uintptr_t result)
{
    (void)result;
    if (!g_dp_const_color) return;
    uintptr_t heap = (uintptr_t)(((const uint64_t *)c)[31] << 32);
    uintptr_t painter = (uintptr_t)c->x[1] & ~(uintptr_t)1;
    uintptr_t rectp = (uintptr_t)c->x[3] & ~(uintptr_t)1;
    double w = ld_dp_ldd(rectp + 24) - ld_dp_ldd(rectp + 8);
    double h = ld_dp_ldd(rectp + 32) - ld_dp_ldd(rectp + 16);
    if (!(w > 0.0 && w <= 3.0 && h >= 18.0 && h <= 90.0)) return;
    if (!ld_dp_is_divider(painter, heap)) return;
    ld_dp_apply_color(painter, heap);
}

LD_DP_POST(repl_dp_gp, ld_dp_gp_post, g_dp_gp_orig, g_dp_gp_save)

/* ---- 构建期：捕获厂商 const Color（内层闭包 PP[0xd4a30]） ---- */
#define LD_DP_INNER 0x18893ccu
static void *g_dp_inner_orig __attribute__((used));
static volatile unsigned g_dp_inner_calls;

__attribute__((used)) static void ld_dp_inner_cb(struct ld_dart_context *c)
{
    unsigned n = __atomic_fetch_add(&g_dp_inner_calls, 1u, __ATOMIC_RELAXED);
    ld_dp_load_cfg();
    uintptr_t pp = (uintptr_t)c->x[27];
    if (!g_dp_const_color && pp) ld_dp_read(pp + 0xd4a30, &g_dp_const_color, 8);
    if (n == 0u) ld_blog("divider: inner first call const=%p", (void *)g_dp_const_color);
}

LD_DART_OBSERVER(repl_dp_inner, ld_dp_inner_cb, g_dp_inner_orig)

static void ld_dp_hook(const struct native_api *api, ld_lib_info *lib, uintptr_t vaddr,
                       const char *tag, void *repl, void **orig)
{
    uintptr_t fn = 0;
    if (!ld_vaddr_to_runtime(lib, vaddr, &fn)) {
        ld_blog("divider: %s vaddr 0x%lx not mapped", tag, (unsigned long)vaddr);
        return;
    }
    static const uint32_t head[2] = {0xa9bf79fdu, 0xaa0f03fdu};
    if (memcmp((const void *)fn, head, sizeof head) != 0) {
        ld_blog("divider: %s prologue mismatch at %p", tag, (void *)fn);
        return;
    }
    void *backup = NULL;
    if (api->hook((void *)fn, repl, &backup) == 0 && backup) {
        *orig = backup;
        ld_blog("divider: %s installed at %p", tag, (void *)fn);
    } else {
        ld_blog("divider: %s hook failed", tag);
    }
}

static void ld_divider_paint_install(const struct native_api *api, ld_lib_info *lib)
{
    static int state;
    if (state || !api || !api->hook || !lib) return;
    /* 不再依赖手工 marker：是否生效完全由 conf 的 dock_divider_enabled 决定，
     * 这样 GUI 里开关/拖滑块能热生效。marker 仍保留为“强制安装”开关。 */
    ld_dp_load_cfg();
    if (!g_dp_cfg.enabled && access(LD_DP_MARKER, F_OK) != 0) {
        ld_blog("divider: disabled in conf; hooks skipped");
        state = 2;
        return;
    }
    ld_dp_hook(api, lib, LD_DP_INNER, "inner", (void *)repl_dp_inner, &g_dp_inner_orig);
    ld_dp_hook(api, lib, LD_DP_BGPAINT, "bgPaint", (void *)repl_dp_bg, &g_dp_bg_orig);
    /* _getBackgroundPaint 后置钩子已停用：热路径 _paintBackgroundColor 已覆盖颜色写入，
     * 少一个裸跳板 = 少一类崩溃面。 */
    ld_dp_start_poller();
    state = 1;
}

#endif
