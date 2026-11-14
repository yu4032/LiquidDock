#ifndef LIQUIDDOCK_AOT_SCANNER_H
#define LIQUIDDOCK_AOT_SCANNER_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <string.h>
#include "lib_base.h"
#include "bootlog.h"
#include "executable_range.h"

static inline uint32_t ld_aot_inst_mask(uint32_t w)
{
    /* 1. B / BL (unconditional branch 26-bit imm at bits [25:0]) */
    if ((w & 0x7c000000u) == 0x14000000u)
        return 0xfc000000u;

    /* 2. B.cond (0x54000000) and CBZ/CBNZ (0x34000000): mask imm19 at bits [23:5] */
    if ((w & 0xff000000u) == 0x54000000u || (w & 0x7e000000u) == 0x34000000u)
        return 0xff00001fu;

    /* 3. TBZ / TBNZ (0x36000000): mask imm14 at bits [18:5] */
    if ((w & 0x7e000000u) == 0x36000000u)
        return 0xfff8001fu;

    /* 4. ADR / ADRP (0x10000000 / 0x90000000): mask immhi:immlo */
    if ((w & 0x1f000000u) == 0x10000000u)
        return 0x9f00001fu;

    /* 5. ADD / SUB immediate (all registers): mask imm12 at bits [21:10] */
    if ((w & 0x1f800000u) == 0x11000000u)
        return 0xffc003ffu;

    /* 6. All LDR / STR unsigned immediate (int & FP, 32/64-bit): mask imm12 at bits [21:10] */
    if ((w & 0x3b000000u) == 0x39000000u)
        return 0xffc003ffu;

    /* 7. LDUR / STUR unscaled immediate (int & FP): mask imm9 at bits [20:12] */
    if ((w & 0x3b800c00u) == 0x38000000u)
        return 0xffe00fffu;

    /* 8. MOVZ / MOVK / MOVN: mask imm16 at bits [20:5] */
    if ((w & 0x1f800000u) == 0x12800000u)
        return 0xffe0001fu;

    return 0xffffffffu;
}

static inline bool ld_aot_match_words(const uint32_t *live, const uint32_t *exp, size_t count)
{
    for (size_t i = 0; i < count; i++) {
        uint32_t m = ld_aot_inst_mask(exp[i]);
        if (((live[i] ^ exp[i]) & m) != 0)
            return false;
    }
    return true;
}

/* Internal helper: scan segments for a specific prefix count, collecting candidates */
#define LD_AOT_MAX_CANDS 128
static uintptr_t ld_scan_aot_pass(const ld_lib_info *lib, uintptr_t hint_vaddr,
                                  const uint32_t *words, size_t count,
                                  uintptr_t candidates[LD_AOT_MAX_CANDS], size_t *out_matches)
{
    (void)hint_vaddr;
    uint32_t m0 = ld_aot_inst_mask(words[0]);
    uint32_t exp0 = words[0];
    size_t match_count = 0;

    for (uint16_t i = 0; i < lib->phnum; i++) {
        const unsigned char *ph = ld_phdr_at(lib, i);
        uint32_t p_type, p_flags;
        uint64_t p_vaddr, p_filesz;
        memcpy(&p_type, ph + 0, 4);
        memcpy(&p_flags, ph + 4, 4);
        if (p_type != 1 || !(p_flags & 1)) continue; /* PT_LOAD && PF_X */

        memcpy(&p_vaddr, ph + 16, 8);
        memcpy(&p_filesz, ph + 32, 8);

        uintptr_t seg_start = lib->elf_base + (uintptr_t)p_vaddr;
        size_t seg_len = (size_t)p_filesz;
        if (seg_len < count * sizeof(uint32_t)) continue;

        size_t max_scan = seg_len - count * sizeof(uint32_t);
        for (size_t off = 0; off <= max_scan; off += 4) {
            const uint32_t *live = (const uint32_t *)(seg_start + off);
            if (((live[0] ^ exp0) & m0) != 0) continue;
            if (ld_aot_match_words(live, words, count)) {
                if (match_count < LD_AOT_MAX_CANDS) candidates[match_count] = (uintptr_t)live;
                match_count++;
            }
        }
    }
    *out_matches = match_count;
    if (match_count == 1) return candidates[0];
    return 0;
}

/* Find pattern in libapp.so:
 * 1. Checks hint_vaddr (0-cost fast path if address matches).
 * 1b. Secondary fast path for known version migrations.
 * 2. If mismatch, scans executable PT_LOAD segment(s).
 * 3. If no match and word_count > 24, tries adaptive prefix fallback (48, 32, 24).
 * 4. Disambiguates twins (e.g. delete_item vs save_single) by delta 0x1f4.
 * Returns runtime address if unique match found, or 0 if not found / ambiguous.
 */
static uintptr_t ld_find_aot_pattern(const ld_lib_info *lib, uintptr_t hint_vaddr,
                                     const uint32_t *words, size_t word_count)
{
    if (!lib || !words || word_count == 0) return 0;

    /* 1. Fast path: check hint_vaddr */
    if (hint_vaddr) {
        uintptr_t hint_rt = 0;
        if (ld_vaddr_to_runtime(lib, hint_vaddr, &hint_rt) &&
            executable_range(hint_rt, word_count * sizeof(uint32_t))) {
            if (ld_aot_match_words((const uint32_t *)hint_rt, words, word_count)) {
                return hint_rt;
            }
        }
    }

    /* 1b. Secondary fast path for known version migrations (8604 -> 9239) */
    if (hint_vaddr == 0x92113cu || hint_vaddr == 0x8f2b6cu ||
        hint_vaddr == 0x910768u || hint_vaddr == 0xb47388u) {
        uintptr_t v9239 = (hint_vaddr == 0x92113cu) ? 0x9bace8u :
                          (hint_vaddr == 0x8f2b6cu) ? 0x9baaf4u :
                          (hint_vaddr == 0x910768u) ? 0x93ce24u : 0xb72cb8u;
        uintptr_t rt = 0;
        if (ld_vaddr_to_runtime(lib, v9239, &rt) &&
            executable_range(rt, word_count * sizeof(uint32_t))) {
            if (ld_aot_match_words((const uint32_t *)rt, words, word_count)) {
                ld_blog("AOT pattern relocated (v9239 table): hint 0x%lx -> live 0x%lx (vaddr 0x%lx)",
                        (unsigned long)hint_vaddr, (unsigned long)rt, (unsigned long)v9239);
                return rt;
            }
        }
    }

    /* 2. Scan executable PT_LOAD segments with full word_count */
    uintptr_t cands[LD_AOT_MAX_CANDS] = {0};
    size_t match_count = 0;
    uintptr_t single = ld_scan_aot_pass(lib, hint_vaddr, words, word_count, cands, &match_count);
    if (single) {
        ld_blog("AOT pattern relocated: hint 0x%lx -> live 0x%lx (vaddr 0x%lx)",
                (unsigned long)hint_vaddr, (unsigned long)single,
                (unsigned long)(single - lib->elf_base));
        return single;
    }

    /* 3. Adaptive prefix fallback if word_count > 24 */
    if (match_count == 0 && word_count > 24) {
        static const size_t prefixes[] = { 48, 32, 24 };
        for (size_t p = 0; p < sizeof prefixes / sizeof prefixes[0]; p++) {
            if (prefixes[p] >= word_count) continue;
            single = ld_scan_aot_pass(lib, hint_vaddr, words, prefixes[p], cands, &match_count);
            if (single) {
                ld_blog("AOT pattern relocated (prefix %zu words): hint 0x%lx -> live 0x%lx (vaddr 0x%lx)",
                        prefixes[p], (unsigned long)hint_vaddr, (unsigned long)single,
                        (unsigned long)(single - lib->elf_base));
                return single;
            }
        }
    }

    /* 4. Disambiguate twin functions (delete_item vs save_single) by partner delta 0x1f4 */
    if (match_count > 1 && (hint_vaddr == 0x92113cu || hint_vaddr == 0x8f2b6cu)) {
        size_t limit = match_count < LD_AOT_MAX_CANDS ? match_count : LD_AOT_MAX_CANDS;
        for (size_t a = 0; a < limit; a++) {
            for (size_t b = 0; b < limit; b++) {
                if (cands[b] == cands[a] + 0x1f4u) {
                    uintptr_t chosen = (hint_vaddr == 0x92113cu) ? cands[b] : cands[a];
                    ld_blog("AOT pattern disambiguated twin (+0x1f4): hint 0x%lx -> live 0x%lx (vaddr 0x%lx)",
                            (unsigned long)hint_vaddr, (unsigned long)chosen,
                            (unsigned long)(chosen - lib->elf_base));
                    return chosen;
                }
            }
        }
    }

    if (match_count == 0) {
        ld_blog("AOT pattern not found for hint 0x%lx (%zu words)",
                (unsigned long)hint_vaddr, word_count);
    } else {
        ld_blog("AOT pattern ambiguous for hint 0x%lx (%zu matches)",
                (unsigned long)hint_vaddr, match_count);
    }
    return 0;
}

/* Verify the 16-byte detour prologue does not clobber x17 (used by the
 * trampoline) or contain PC-relative branches/loads that break relocation. */
static inline int ld_hook_site_safe(uintptr_t site, const char *label)
{
    if (!site || !executable_range(site, 16)) return 0;
    const uint32_t *insns = (const uint32_t *)site;
    for (int i = 0; i < 4; i++) {
        uint32_t w = insns[i];
        int pc_rel =
            ((w & 0x7c000000u) == 0x14000000u) || /* B / BL */
            ((w & 0xff000010u) == 0x54000000u) || /* B.cond */
            ((w & 0x7e000000u) == 0x34000000u) || /* CBZ / CBNZ */
            ((w & 0x7e000000u) == 0x36000000u) || /* TBZ / TBNZ */
            ((w & 0x1f000000u) == 0x10000000u) || /* ADR / ADRP */
            ((w & 0x3b000000u) == 0x18000000u);   /* LDR literal */
        if (pc_rel) {
            ld_blog("hook site unsafe (%s +0x%x): pc-rel insn %08x",
                    label ? label : "?", i * 4, w);
            return 0;
        }
        uint32_t rd = w & 0x1fu;
        uint32_t rn = (w >> 5) & 0x1fu;
        int writes_x17 =
            (rd == 17u) && (
                ((w & 0x1f800000u) == 0x12800000u) || /* MOVZ / MOVN / MOVK */
                ((w & 0x1f000000u) == 0x11000000u) || /* ADD / SUB imm */
                ((w & 0x1f000000u) == 0x0b000000u) || /* ADD / SUB reg */
                ((w & 0x3b000000u) == 0x39000000u) || /* LDR / STR uimm */
                ((w & 0x3b600c00u) == 0x38400000u)    /* LDUR */
            );
        int reads_x17 = (rn == 17u) && ((w & 0x3a000000u) == 0x38000000u);
        if (writes_x17 || reads_x17) {
            ld_blog("hook site unsafe (%s +0x%x): x17 used by insn %08x",
                    label ? label : "?", i * 4, w);
            return 0;
        }
    }
    return 1;
}

#endif /* LIQUIDDOCK_AOT_SCANNER_H */
