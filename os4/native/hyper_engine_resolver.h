#ifndef LIQUIDDOCK_HYPER_ENGINE_RESOLVER_H
#define LIQUIDDOCK_HYPER_ENGINE_RESOLVER_H

/* Cross-version dynamic resolver for libhyper_os_flutter.so.
 * Uses masked ARM64 instruction signatures (ignoring PC-relative ADRP/ADD/B/BL
 * immediates) across the executable .text segment discovered from /proc/self/maps,
 * with optional hint vaddr fast-path so identical builds resolve in O(1) while
 * newer/older HyperOS 4 builds resolve dynamically with zero fixed-offset assumptions. */

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

struct ld_hyper_engine_info {
    uintptr_t elf_base;
    uintptr_t text_start;
    uintptr_t text_end;
};

static int ld_hyper_engine_locate(struct ld_hyper_engine_info *out)
{
    if (!out) return 0;
    memset(out, 0, sizeof *out);
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return 0;
    char line[512];
    while (fgets(line, sizeof line, maps)) {
        if (!strstr(line, "libhyper_os_flutter.so")) continue;
        unsigned long start = 0, end = 0;
        char perms[8] = {0};
        if (sscanf(line, "%lx-%lx %7s", &start, &end, perms) != 3) continue;
        if (!out->elf_base) out->elf_base = (uintptr_t)start;
        if (perms[0] == 'r' && perms[2] == 'x') {
            /* Merge contiguous r-xp VMAs (Dobby mprotect splits .text into adjacent VMAs). */
            if (!out->text_start) {
                out->text_start = (uintptr_t)start;
                out->text_end = (uintptr_t)end;
            } else if ((uintptr_t)start == out->text_end) {
                out->text_end = (uintptr_t)end;
            } else if ((uintptr_t)(end - start) > (out->text_end - out->text_start)) {
                out->text_start = (uintptr_t)start;
                out->text_end = (uintptr_t)end;
            }
        }
    }
    fclose(maps);
    return out->elf_base != 0 && out->text_start != 0 && out->text_end > out->text_start;
}

static inline int ld_he_match_masked(uintptr_t addr, const uint32_t *pat,
                                     const uint32_t *msk, size_t n)
{
    const uint32_t *p = (const uint32_t *)addr;
    for (size_t i = 0; i < n; i++) {
        uint32_t m = msk ? msk[i] : 0xffffffffu;
        if ((p[i] & m) != (pat[i] & m)) return 0;
    }
    return 1;
}

/* Find all occurrences (up to max_out) of a masked ARM64 pattern in [start, end). */
static size_t ld_he_scan_masked_range(uintptr_t start, uintptr_t end,
                                      const uint32_t *pat, const uint32_t *msk,
                                      size_t n, uintptr_t *out, size_t max_out)
{
    if (!pat || n == 0 || !out || max_out == 0 || start >= end) return 0;
    size_t bytes = n * sizeof(uint32_t);
    if ((size_t)(end - start) < bytes) return 0;

    size_t anchor = 0;
    uint32_t anchor_mask = msk ? msk[0] : 0xffffffffu;
    for (size_t i = 0; i < n; i++) {
        uint32_t m = msk ? msk[i] : 0xffffffffu;
        if (m == 0xffffffffu) { anchor = i; anchor_mask = m; break; }
    }
    uint32_t anchor_val = pat[anchor] & anchor_mask;

    const uint32_t *base = (const uint32_t *)start;
    size_t total_words = (size_t)(end - start) / sizeof(uint32_t);
    size_t limit = total_words - n;
    size_t found = 0;
    for (size_t i = 0; i <= limit; i++) {
        if ((base[i + anchor] & anchor_mask) != anchor_val) continue;
        int ok = 1;
        for (size_t k = 0; k < n; k++) {
            if (k == anchor) continue;
            uint32_t m = msk ? msk[k] : 0xffffffffu;
            if ((base[i + k] & m) != (pat[k] & m)) { ok = 0; break; }
        }
        if (ok) {
            out[found++] = start + i * sizeof(uint32_t);
            if (found >= max_out) break;
        }
    }
    return found;
}

/* Fast-path check at elf_base + hint_vaddr; falls back to full .text scan. */
static uintptr_t ld_he_resolve_unique(const struct ld_hyper_engine_info *eng,
                                      uintptr_t hint_vaddr,
                                      const uint32_t *pat, const uint32_t *msk,
                                      size_t n)
{
    if (!eng || !eng->text_start || !pat || n == 0) return 0;
    size_t bytes = n * sizeof(uint32_t);
    if (hint_vaddr) {
        uintptr_t cand = eng->elf_base + hint_vaddr;
        if (cand >= eng->text_start && cand + bytes <= eng->text_end &&
            ld_he_match_masked(cand, pat, msk, n)) {
            return cand;
        }
    }
    uintptr_t hits[2] = {0, 0};
    size_t cnt = ld_he_scan_masked_range(eng->text_start, eng->text_end, pat, msk, n, hits, 2);
    return (cnt == 1) ? hits[0] : 0;
}

/* Resolve a function expected to lie near a known anchor (e.g. BindUMaskTex near BindUBO). */
static uintptr_t ld_he_resolve_near(const struct ld_hyper_engine_info *eng,
                                    uintptr_t hint_vaddr,
                                    uintptr_t anchor_addr, size_t back_span, size_t fwd_span,
                                    const uint32_t *pat, const uint32_t *msk, size_t n)
{
    if (!eng || !eng->text_start || !pat || n == 0) return 0;
    size_t bytes = n * sizeof(uint32_t);
    if (hint_vaddr) {
        uintptr_t cand = eng->elf_base + hint_vaddr;
        if (cand >= eng->text_start && cand + bytes <= eng->text_end &&
            ld_he_match_masked(cand, pat, msk, n)) {
            return cand;
        }
    }
    if (!anchor_addr) return 0;
    uintptr_t lo = (anchor_addr > eng->text_start + back_span) ? (anchor_addr - back_span) : eng->text_start;
    uintptr_t hi = (anchor_addr + fwd_span < eng->text_end) ? (anchor_addr + fwd_span) : eng->text_end;
    uintptr_t hits[2] = {0, 0};
    size_t cnt = ld_he_scan_masked_range(lo, hi, pat, msk, n, hits, 2);
    return (cnt == 1) ? hits[0] : 0;
}

#endif /* LIQUIDDOCK_HYPER_ENGINE_RESOLVER_H */
