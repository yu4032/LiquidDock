#ifndef LD_HOOK_PAGE_PIN_H
#define LD_HOOK_PAGE_PIN_H
#include <stdint.h>
#include <stddef.h>
#include <sys/mman.h>
#include <unistd.h>
#include <errno.h>
#include <pthread.h>

enum ld_pin_status {
    LD_PIN_FAILED = 0,
    LD_PIN_LOCKED = 1,
    LD_PIN_UNLOCKED_ENOMEM = 2,
};

struct ld_pin_patch_site {
    uintptr_t site;
    uint32_t w0;
    uint32_t w1;
    uint8_t n_words;
};

static unsigned g_pin_locked_count = 0;
static unsigned g_pin_enomem_count = 0;
static unsigned g_pin_failed_count = 0;
static pthread_mutex_t g_pin_sites_lock = PTHREAD_MUTEX_INITIALIZER;
static struct ld_pin_patch_site g_pin_sites[256];
static unsigned g_pin_site_count = 0;

/* Inline hooks modify MAP_PRIVATE file-backed executable pages. Discarding
 * those pages restores the APK bytes and silently removes the hooks. Keep only
 * the pages containing each 16-byte entry patch resident for this process.
 * Linux rejects MADV_DONTNEED on VM_LOCKED pages; normal unmapping at process
 * exit releases the lock. */
static inline int ld_pin_code_pages(uintptr_t address,size_t size) {
    long raw=sysconf(_SC_PAGESIZE);
    if(raw<=0 || !size || address>UINTPTR_MAX-size) return 0;
    uintptr_t page=(uintptr_t)raw;
    if(page&(page-1)) return 0;
    uintptr_t start=address&~(page-1),last=(address+size-1)|(page-1);
    if(last==UINTPTR_MAX) return 0;
    return mlock((void *)start,(size_t)(last+1-start))==0;
}

static inline int ld_pin_code_pages_status(uintptr_t address, size_t size) {
    if (ld_pin_code_pages(address, size)) {
        __atomic_fetch_add(&g_pin_locked_count, 1u, __ATOMIC_RELAXED);
        return LD_PIN_LOCKED;
    }
    if (errno == ENOMEM) {
        __atomic_fetch_add(&g_pin_enomem_count, 1u, __ATOMIC_RELAXED);
        return LD_PIN_UNLOCKED_ENOMEM;
    }
    __atomic_fetch_add(&g_pin_failed_count, 1u, __ATOMIC_RELAXED);
    return LD_PIN_FAILED;
}

/* Launcher processes on this platform cap RLIMIT_MEMLOCK at 64 KiB, far below
 * the hook set. Returns LD_PIN_LOCKED (1) or LD_PIN_UNLOCKED_ENOMEM (2) on
 * allowable outcomes, and LD_PIN_FAILED (0) on other errors. */
static inline int ld_pin_code_pages_soft(uintptr_t address,size_t size) {
    return ld_pin_code_pages_status(address, size);
}

static inline void ld_record_hook_site_words(uintptr_t site, uint8_t n_words) {
    if (!site || (site & 3u)) return;
    if (n_words < 1u) n_words = 1u;
    if (n_words > 2u) n_words = 2u;
    const uint32_t *words = (const uint32_t *)site;
    uint32_t w0 = words[0];
    uint32_t w1 = (n_words >= 2u) ? words[1] : 0u;
    pthread_mutex_lock(&g_pin_sites_lock);
    unsigned cnt = __atomic_load_n(&g_pin_site_count, __ATOMIC_RELAXED);
    for (unsigned i = 0; i < cnt; ++i) {
        if (g_pin_sites[i].site == site) {
            g_pin_sites[i].w0 = w0;
            g_pin_sites[i].w1 = w1;
            g_pin_sites[i].n_words = n_words;
            pthread_mutex_unlock(&g_pin_sites_lock);
            return;
        }
    }
    if (cnt < (unsigned)(sizeof(g_pin_sites) / sizeof(g_pin_sites[0]))) {
        g_pin_sites[cnt] = (struct ld_pin_patch_site){ site, w0, w1, n_words };
        __atomic_store_n(&g_pin_site_count, cnt + 1u, __ATOMIC_RELEASE);
    }
    pthread_mutex_unlock(&g_pin_sites_lock);
}

static inline void ld_record_hook_site(uintptr_t site) {
    ld_record_hook_site_words(site, 2u);
}

static inline int ld_is_page_protected_lockfree(uintptr_t page_start, uintptr_t page_mask) {
    unsigned cnt = __atomic_load_n(&g_pin_site_count, __ATOMIC_ACQUIRE);
    for (unsigned i = 0; i < cnt; ++i) {
        if ((g_pin_sites[i].site & page_mask) == page_start) {
            return 1;
        }
    }
    return 0;
}

static inline int ld_range_has_protected_page_lockfree(uintptr_t start, uintptr_t end, uintptr_t page_mask) {
    unsigned cnt = __atomic_load_n(&g_pin_site_count, __ATOMIC_ACQUIRE);
    for (unsigned i = 0; i < cnt; ++i) {
        uintptr_t p = g_pin_sites[i].site & page_mask;
        if (p >= start && p < end) {
            return 1;
        }
    }
    return 0;
}

static inline void ld_verify_hook_sites(unsigned *out_total, unsigned *out_intact) {
    unsigned total = 0, intact = 0;
    pthread_mutex_lock(&g_pin_sites_lock);
    total = g_pin_site_count;
    for (unsigned i = 0; i < g_pin_site_count; ++i) {
        const uint32_t *words = (const uint32_t *)g_pin_sites[i].site;
        if (words[0] == g_pin_sites[i].w0 &&
            (g_pin_sites[i].n_words < 2u || words[1] == g_pin_sites[i].w1)) {
            ++intact;
        }
    }
    pthread_mutex_unlock(&g_pin_sites_lock);
    if (out_total) *out_total = total;
    if (out_intact) *out_intact = intact;
}
#endif
