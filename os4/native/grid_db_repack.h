#ifndef LIQUIDDOCK_GRID_DB_REPACK_H
#define LIQUIDDOCK_GRID_DB_REPACK_H

#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "grid_repack.h"

#define LD_HOME_DB_DIR "/data/user_de/0/com.miui.home/databases"

typedef struct sqlite3 sqlite3;
typedef struct sqlite3_stmt sqlite3_stmt;

struct ld_sqlite_api {
    int (*open_v2)(const char *, sqlite3 **, int, const char *);
    int (*close_v2)(sqlite3 *);
    int (*exec)(sqlite3 *, const char *, void *, void *, char **);
    int (*prepare_v2)(sqlite3 *, const char *, int, sqlite3_stmt **, const char **);
    int (*finalize)(sqlite3_stmt *);
    int (*step)(sqlite3_stmt *);
    int (*reset)(sqlite3_stmt *);
    int (*bind_int)(sqlite3_stmt *, int, int);
    int (*bind_int64)(sqlite3_stmt *, int, int64_t);
    int64_t (*column_int64)(sqlite3_stmt *, int);
    int (*column_int)(sqlite3_stmt *, int);
    int (*changes)(sqlite3 *);
    int64_t (*last_insert_rowid)(sqlite3 *);
    const char *(*errmsg)(sqlite3 *);
    void (*free_fn)(void *);
};

static inline void *ld_sqlite_symbol(void *handle, const char *name)
{
    void *symbol = dlsym(handle, name);
    if (!symbol) LOGE("grid-repack: missing sqlite symbol %s", name);
    return symbol;
}

static inline int ld_sqlite_api_load(struct ld_sqlite_api *api)
{
    if (!api) return 0;
    memset(api, 0, sizeof(*api));
    void *handle = dlopen("libmisqlite3.so", RTLD_NOW | RTLD_NOLOAD);
    if (!handle) handle = dlopen("/system_ext/lib64/libmisqlite3.so", RTLD_NOW);
    if (!handle) {
        LOGE("grid-repack: libmisqlite3 load failed: %s", dlerror());
        return 0;
    }
#define LD_SQLITE_LOAD(field, symbol) \
    do { *(void **)(&api->field) = ld_sqlite_symbol(handle, symbol); \
         if (!api->field) return 0; } while (0)
    LD_SQLITE_LOAD(open_v2, "sqlite3_open_v2");
    LD_SQLITE_LOAD(close_v2, "sqlite3_close_v2");
    LD_SQLITE_LOAD(exec, "sqlite3_exec");
    LD_SQLITE_LOAD(prepare_v2, "sqlite3_prepare_v2");
    LD_SQLITE_LOAD(finalize, "sqlite3_finalize");
    LD_SQLITE_LOAD(step, "sqlite3_step");
    LD_SQLITE_LOAD(reset, "sqlite3_reset");
    LD_SQLITE_LOAD(bind_int, "sqlite3_bind_int");
    LD_SQLITE_LOAD(bind_int64, "sqlite3_bind_int64");
    LD_SQLITE_LOAD(column_int64, "sqlite3_column_int64");
    LD_SQLITE_LOAD(column_int, "sqlite3_column_int");
    LD_SQLITE_LOAD(changes, "sqlite3_changes");
    LD_SQLITE_LOAD(last_insert_rowid, "sqlite3_last_insert_rowid");
    LD_SQLITE_LOAD(errmsg, "sqlite3_errmsg");
    LD_SQLITE_LOAD(free_fn, "sqlite3_free");
#undef LD_SQLITE_LOAD
    return 1;
}

/*
 * 8583 on the verified fuxi tablet-mode target uses this database. Scanning
 * for the newest launcher*.db by mtime can select an inactive workspace and
 * corrupt unrelated layouts; unknown layouts must be rejected, not guessed.
 */
static inline int ld_pick_active_launcher_db(char *out, size_t out_size)
{
    static const char target[] = LD_HOME_DB_DIR "/launcher_rotatable4x6.db";
    struct stat st;
    if (!out || out_size < sizeof target ||
        stat(target, &st) != 0 || !S_ISREG(st.st_mode)) {
        LOGE("grid-repack: verified 8583 tablet DB not found; no mutation");
        return 0;
    }
    memcpy(out, target, sizeof target);
    return 1;
}

static inline int ld_collect_screens(
    const struct ld_sqlite_api *api, sqlite3 *db, int **out_screens, int *out_count)
{
    sqlite3_stmt *stmt = NULL;
    if (api->prepare_v2(
            db,
            "SELECT DISTINCT screen FROM favorites "
            "WHERE container=-100 AND screen>=0 ORDER BY screen",
            -1, &stmt, NULL) != 0) {
        return 0;
    }

    int capacity = 16;
    int count = 0;
    int *screens = (int *)malloc((size_t)capacity * sizeof(int));
    if (!screens) {
        api->finalize(stmt);
        return 0;
    }
    int rc;
    while ((rc = api->step(stmt)) == 100) {
        if (count == capacity) {
            capacity *= 2;
            int *grown = (int *)realloc(screens, (size_t)capacity * sizeof(int));
            if (!grown) {
                free(screens);
                api->finalize(stmt);
                return 0;
            }
            screens = grown;
        }
        screens[count++] = api->column_int(stmt, 0);
    }
    api->finalize(stmt);
    if (rc != 101) {
        free(screens);
        return 0;
    }
    *out_screens = screens;
    *out_count = count;
    return 1;
}

static inline int ld_load_workspace_items(
    const struct ld_sqlite_api *api, sqlite3 *db,
    struct ld_repack_item **out_items, size_t *out_count)
{
    sqlite3_stmt *stmt = NULL;
    if (api->prepare_v2(
            db,
            "SELECT _id,screen,cellX,cellY,spanX,spanY FROM favorites "
            "WHERE container=-100 AND screen>=0 "
            "ORDER BY screen,cellY,cellX,_id",
            -1, &stmt, NULL) != 0) {
        LOGE("grid-repack: item select failed: %s", api->errmsg(db));
        return 0;
    }

    size_t capacity = 128;
    size_t count = 0;
    struct ld_repack_item *items = (struct ld_repack_item *)malloc(
        capacity * sizeof(*items));
    if (!items) {
        api->finalize(stmt);
        return 0;
    }

    int rc;
    while ((rc = api->step(stmt)) == 100) {
        if (count == capacity) {
            capacity *= 2;
            struct ld_repack_item *grown = (struct ld_repack_item *)realloc(
                items, capacity * sizeof(*items));
            if (!grown) {
                free(items);
                api->finalize(stmt);
                return 0;
            }
            items = grown;
        }
        items[count++] = (struct ld_repack_item){
            api->column_int64(stmt, 0),
            api->column_int(stmt, 1),
            api->column_int(stmt, 2),
            api->column_int(stmt, 3),
            api->column_int(stmt, 4),
            api->column_int(stmt, 5),
        };
    }
    api->finalize(stmt);
    if (rc != 101) {
        free(items);
        return 0;
    }
    *out_items = items;
    *out_count = count;
    return 1;
}

static inline int ld_create_screen(
    const struct ld_sqlite_api *api, sqlite3 *db, int64_t *out_id)
{
    sqlite3_stmt *order_stmt = NULL;
    int64_t next_order = 0;
    if (api->prepare_v2(
            db,
            "SELECT COALESCE(MAX(screenOrder),-1)+1 FROM screens",
            -1, &order_stmt, NULL) != 0 ||
        api->step(order_stmt) != 100) {
        if (order_stmt) api->finalize(order_stmt);
        return 0;
    }
    next_order = api->column_int64(order_stmt, 0);
    api->finalize(order_stmt);

    sqlite3_stmt *insert_stmt = NULL;
    if (api->prepare_v2(
            db,
            "INSERT INTO screens(title,screenOrder,screenType) VALUES('',?1,0)",
            -1, &insert_stmt, NULL) != 0) {
        return 0;
    }
    api->bind_int64(insert_stmt, 1, next_order);
    int ok = api->step(insert_stmt) == 101;
    api->finalize(insert_stmt);
    if (ok) *out_id = api->last_insert_rowid(db);
    return ok;
}

/* Returns -1 on failure, otherwise the number of moved/reassigned items. */
static inline int ld_grid_db_repack_path(
    const char *db_path, int cols, int rows)
{
    if (!db_path || cols <= 0 || rows <= 0) return 0;

    struct ld_sqlite_api api;
    if (!ld_sqlite_api_load(&api)) return -1;

    sqlite3 *db = NULL;
    int rc = 0;
    for (int attempt = 0; attempt < 10; attempt++) {
        rc = api.open_v2(db_path, &db, 2, NULL);
        if (rc == 0) break;
        if (db) {
            api.close_v2(db);
            db = NULL;
        }
        usleep(100000);
    }
    if (rc != 0 || !db) {
        LOGE("grid-repack: open %s failed rc=%d", db_path, rc);
        return -1;
    }

    int *screens = NULL;
    int screen_count = 0;
    struct ld_repack_item *items = NULL;
    struct ld_repack_item *before = NULL;
    size_t *pending = NULL;
    struct ld_repack_item *temp_items = NULL;
    size_t *temp_orig = NULL;
    int ok = api.exec(db, "BEGIN IMMEDIATE", NULL, NULL, NULL) == 0;
    size_t item_count = 0;
    if (ok) ok = ld_collect_screens(&api, db, &screens, &screen_count);
    if (ok) ok = ld_load_workspace_items(&api, db, &items, &item_count);
    if (ok && item_count > 0) {
        before = (struct ld_repack_item *)malloc(item_count * sizeof(*before));
        pending = (size_t *)malloc(item_count * sizeof(*pending));
        temp_items = (struct ld_repack_item *)malloc(item_count * sizeof(*temp_items));
        temp_orig = (size_t *)malloc(item_count * sizeof(*temp_orig));
        if (!before || !pending || !temp_items || !temp_orig) ok = 0;
    }
    if (ok && item_count > 0) {
        memcpy(before, items, item_count * sizeof(*items));
    }

    size_t cursor = 0;
    size_t pending_count = 0;
    size_t last_start = cursor;
    size_t last_count = 0;
    for (int i = 0; ok && i < screen_count; i++) {
        size_t start = cursor;
        while (cursor < item_count && items[cursor].screen == screens[i]) cursor++;
        size_t count = cursor - start;
        if (i + 1 == screen_count) {
            last_start = start;
            last_count = count;
            break;
        }
        ld_repack_screen_relaxed(items + start, (int)count, cols, rows);
        for (size_t j = 0; j < count; j++) {
            if (items[start + j].x < 0) pending[pending_count++] = start + j;
        }
    }

    int64_t last_screen = screen_count > 0 ? screens[screen_count - 1] : -1;
    if (ok && last_screen >= 0) {
        size_t total = last_count + pending_count;
        for (size_t i = 0; i < last_count; i++) {
            temp_items[i] = items[last_start + i];
            temp_orig[i] = last_start + i;
        }
        for (size_t i = 0; i < pending_count; i++) {
            temp_items[last_count + i] = items[pending[i]];
            temp_orig[last_count + i] = pending[i];
        }
        ld_repack_screen_relaxed(temp_items, (int)total, cols, rows);
        pending_count = 0;
        for (size_t i = 0; i < total; i++) {
            size_t original = temp_orig[i];
            if (temp_items[i].x < 0) {
                pending[pending_count++] = original;
                continue;
            }
            items[original].x = temp_items[i].x;
            items[original].y = temp_items[i].y;
            items[original].screen = (int)last_screen;
        }
    } else if (screen_count == 0) {
        pending_count = 0;
    }

    while (ok && pending_count > 0) {
        int64_t new_screen = 0;
        if (!ld_create_screen(&api, db, &new_screen)) {
            ok = 0;
            break;
        }
        size_t total = pending_count;
        for (size_t i = 0; i < total; i++) {
            temp_items[i] = items[pending[i]];
            temp_orig[i] = pending[i];
        }
        int placed = ld_repack_screen_relaxed(temp_items, (int)total, cols, rows);
        if (placed <= 0) {
            LOGE("grid-repack: cannot place item on new screen %lld",
                 (long long)new_screen);
            ok = 0;
            break;
        }
        size_t next_count = 0;
        for (size_t i = 0; i < total; i++) {
            size_t original = temp_orig[i];
            if (temp_items[i].x < 0) {
                pending[next_count++] = original;
                continue;
            }
            items[original].x = temp_items[i].x;
            items[original].y = temp_items[i].y;
            items[original].screen = (int)new_screen;
        }
        pending_count = next_count;
    }

    int moved = 0;
    sqlite3_stmt *update_stmt = NULL;
    if (ok && api.prepare_v2(
            db,
            "UPDATE favorites SET cellX=?1,cellY=?2,screen=?3 "
            "WHERE _id=?4 AND container=-100",
            -1, &update_stmt, NULL) != 0) {
        ok = 0;
    }
    for (int i = 0; ok && i < (int)item_count; i++) {
        if (items[i].x == before[i].x && items[i].y == before[i].y &&
            items[i].screen == before[i].screen) {
            continue;
        }
        api.reset(update_stmt);
        api.bind_int(update_stmt, 1, items[i].x);
        api.bind_int(update_stmt, 2, items[i].y);
        api.bind_int(update_stmt, 3, items[i].screen);
        api.bind_int64(update_stmt, 4, items[i].id);
        if (api.step(update_stmt) != 101) {
            LOGE("grid-repack: update failed: %s", api.errmsg(db));
            ok = 0;
            break;
        }
        moved++;
    }
    if (update_stmt) api.finalize(update_stmt);
    if (ok) ok = api.exec(db, "COMMIT", NULL, NULL, NULL) == 0;
    else api.exec(db, "ROLLBACK", NULL, NULL, NULL);

    free(screens);
    free(items);
    free(before);
    free(pending);
    free(temp_items);
    free(temp_orig);
    api.close_v2(db);
    if (!ok) {
        LOGE("grid-repack: rollback for %dx%d", cols, rows);
        return -1;
    }
    LOGI("grid-repack: %dx%d moved=%d db=%s", cols, rows, moved, db_path);
    return moved;
}

static inline int ld_grid_db_repack(int cols, int rows)
{
    char db_path[512];
    if (!ld_pick_active_launcher_db(db_path, sizeof db_path)) {
        LOGE("grid-repack: active launcher DB not found");
        return -1;
    }
    return ld_grid_db_repack_path(db_path, cols, rows);
}

#endif
