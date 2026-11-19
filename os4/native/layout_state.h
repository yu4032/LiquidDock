#ifndef LD_LAYOUT_STATE_H
#define LD_LAYOUT_STATE_H

#include <pthread.h>
#include "layout_memory.h"

#ifndef LD_LAYOUT_FILE
#define LD_LAYOUT_FILE "/data/user_de/0/com.miui.home/files/liquiddock-layout-v3.bin"
#endif
#ifndef LD_WS_LAYOUT_FILE
#define LD_WS_LAYOUT_FILE "/data/user_de/0/com.miui.home/files/liquiddock-ws-layout-v3.bin"
#endif
#ifndef LD_WS_DESK_LAYOUT_FILE
#define LD_WS_DESK_LAYOUT_FILE "/data/user_de/0/com.miui.home/files/liquiddock-ws-desk-v3.bin"
#endif

enum {
    LD_SLOT_NORMAL_HOME = 0,
    LD_SLOT_WS_DESK     = 1,
    LD_SLOT_WS_ALLAPPS  = 2,
    LD_SLOT_COUNT       = 3,
};

static inline const char *ld_layout_slot_path(int slot) {
    if (slot == LD_SLOT_WS_ALLAPPS) return LD_WS_LAYOUT_FILE;
    if (slot == LD_SLOT_WS_DESK) return LD_WS_DESK_LAYOUT_FILE;
    return LD_LAYOUT_FILE;
}

static pthread_mutex_t g_layout_io_lock = PTHREAD_MUTEX_INITIALIZER;
static struct ld_layout_memory g_pending_layout_mem[LD_SLOT_COUNT] __attribute__((unused));
static int g_pending_layout_valid[LD_SLOT_COUNT];
static volatile int g_initial_restore_mask;
static volatile int g_loaded_is_ws;
static volatile int g_loaded_ws_known;

static inline void ld_layout_invalidate_pending_locked(int slot) {
    if (slot >= 0 && slot < LD_SLOT_COUNT) {
        g_pending_layout_valid[slot] = 0;
    }
}

static inline void ld_layout_invalidate_all_pending(void) {
    pthread_mutex_lock(&g_layout_io_lock);
    for (int i = 0; i < LD_SLOT_COUNT; i++) {
        g_pending_layout_valid[i] = 0;
    }
    pthread_mutex_unlock(&g_layout_io_lock);
}

static inline int ld_is_workstation_layout_active(void) {
    if (__atomic_load_n(&g_loaded_ws_known, __ATOMIC_ACQUIRE)) {
        return __atomic_load_n(&g_loaded_is_ws, __ATOMIC_ACQUIRE) ? 1 : 0;
    }
    return __atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_ACQUIRE) ? 1 : 0;
}

#endif /* LD_LAYOUT_STATE_H */
