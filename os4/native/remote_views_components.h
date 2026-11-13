#ifndef LD_REMOTE_VIEWS_COMPONENTS_H
#define LD_REMOTE_VIEWS_COMPONENTS_H

#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include "dart_heap.h"
#include "dart_observer.h"

#define LD_REMOTE_VIEWS_FILE "/data/user_de/0/com.miui.home/files/liquiddock-remote-views.txt"
#define LD_REMOTE_HIDDEN_FILE "/data/user_de/0/com.miui.home/files/liquiddock-remote-hidden.conf"
#define LD_RV_MAX_SEEN 512
#define LD_RV_MAX_HIDDEN 256

static void *g_orig_flutter_view_apply __attribute__((used));

struct ld_rv_entry {
    int64_t app_widget_id;
    int64_t view_id;
    int has_bg;
};

static pthread_mutex_t g_rv_lock = PTHREAD_MUTEX_INITIALIZER;
static struct ld_rv_entry g_rv_seen[LD_RV_MAX_SEEN];
static size_t g_rv_seen_count = 0;
static struct ld_rv_entry g_rv_hidden[LD_RV_MAX_HIDDEN];
static size_t g_rv_hidden_count = 0;
static time_t g_rv_hidden_mtime = 0;
static long g_rv_hidden_nsec = 0;
static int g_rv_log_reset = 0;

static inline int64_t ld_rv_int(struct ld_dart_context *c, uintptr_t obj, size_t off) {
    uint32_t word = ld_heap_u32(obj, off);
    if (!(word & 1)) return (int32_t)word / 2;
    uintptr_t ptr = ld_heap_pointer(c, obj, off);
    if (ptr && ((ld_heap_u32(ptr, (size_t)-1) >> 12) & 0xfffff) == 0x3d) {
        return ld_heap_i64(ptr, 7);
    }
    return 0;
}

static void ld_rv_reload_hidden_locked(void) {
    struct stat st;
    if (stat(LD_REMOTE_HIDDEN_FILE, &st) != 0) {
        g_rv_hidden_count = 0;
        g_rv_hidden_mtime = 0;
        g_rv_hidden_nsec = 0;
        return;
    }
    if (st.st_mtime == g_rv_hidden_mtime && st.st_mtim.tv_nsec == g_rv_hidden_nsec) {
        return;
    }
    g_rv_hidden_mtime = st.st_mtime;
    g_rv_hidden_nsec = st.st_mtim.tv_nsec;
    g_rv_hidden_count = 0;
    FILE *fp = fopen(LD_REMOTE_HIDDEN_FILE, "r");
    if (!fp) return;
    char line[128];
    while (fgets(line, sizeof line, fp) && g_rv_hidden_count < LD_RV_MAX_HIDDEN) {
        long long wid = 0, vid = 0;
        if (sscanf(line, "%lld %lld", &wid, &vid) == 2 && wid > 0 && vid != 0) {
            g_rv_hidden[g_rv_hidden_count++] = (struct ld_rv_entry){
                .app_widget_id = (int64_t)wid,
                .view_id = (int64_t)vid,
                .has_bg = 1,
            };
        }
    }
    fclose(fp);
}

__attribute__((used)) static void ld_observe_flutter_view(struct ld_dart_context *c) {
    if (!c) return;
    uintptr_t view = c->x[1];
    if (!(view & 1) || view == c->x[22]) return;
    uintptr_t props = ld_heap_pointer(c, view, 0xb);
    if (!props) return;
    int64_t app_widget_id = ld_rv_int(c, props, 0xcb);
    int64_t view_id = ld_rv_int(c, props, 0xc3);
    if (app_widget_id <= 0 || view_id == 0) return;

    uintptr_t bg_override = ld_heap_pointer(c, props, 0xeb);
    uintptr_t bg_default = ld_heap_pointer(c, props, 0xd7);
    int has_bg = (bg_override != 0 || bg_default != 0) ? 1 : 0;

    int hide = 0;
    pthread_mutex_lock(&g_rv_lock);
    if (!g_rv_log_reset) {
        g_rv_log_reset = 1;
        FILE *fp = fopen(LD_REMOTE_VIEWS_FILE, "w");
        if (fp) fclose(fp);
    }
    int seen = 0;
    for (size_t i = 0; i < g_rv_seen_count; i++) {
        if (g_rv_seen[i].app_widget_id == app_widget_id && g_rv_seen[i].view_id == view_id) {
            if (has_bg && !g_rv_seen[i].has_bg) {
                g_rv_seen[i].has_bg = 1;
            } else {
                seen = 1;
            }
            break;
        }
    }
    if (!seen && g_rv_seen_count < LD_RV_MAX_SEEN) {
        g_rv_seen[g_rv_seen_count++] = (struct ld_rv_entry){
            .app_widget_id = app_widget_id,
            .view_id = view_id,
            .has_bg = has_bg,
        };
        FILE *fp = fopen(LD_REMOTE_VIEWS_FILE, "a");
        if (fp) {
            fprintf(fp, "%lld\t%lld\t%d\n",
                    (long long)app_widget_id, (long long)view_id, has_bg);
            fclose(fp);
        }
    }
    ld_rv_reload_hidden_locked();
    for (size_t i = 0; i < g_rv_hidden_count; i++) {
        if (g_rv_hidden[i].app_widget_id == app_widget_id && g_rv_hidden[i].view_id == view_id) {
            hide = 1;
            break;
        }
    }
    pthread_mutex_unlock(&g_rv_lock);

    if (hide) {
        uint32_t dart_null = (uint32_t)c->x[22];
        memcpy((void *)(props + 0xeb), &dart_null, 4);
        memcpy((void *)(props + 0xd7), &dart_null, 4);
    }
}

LD_DART_OBSERVER(repl_flutter_view_apply, ld_observe_flutter_view, g_orig_flutter_view_apply)

#endif
