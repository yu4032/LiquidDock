#ifndef LIQUIDDOCK_FRB_BOUNDARY_ADAPTER_H
#define LIQUIDDOCK_FRB_BOUNDARY_ADAPTER_H

/*
 * Rust/FRB Boundary Adapter (Stage 2: FRB Load Boundary Takeover + DB Sync)
 *
 * 1. Locates frb_pde_ffi_dispatcher_primary via libapp_launcher.so .dynsym,
 *    decodes its AArch64 uint16 jump table, and matches target addresses against
 *    .gnu_debugdata SHT_SYMTAB to bind launcher_model_* func_ids dynamically.
 * 2. Locates Vec<ItemInfo>::sse_encode & ItemInfo::sse_encode in .gnu_debugdata,
 *    extracts and verifies ItemInfo struct stride and Option<i32>/Option<i64> offsets
 *    from AArch64 machine instructions without hardcoding offsets.
 * 3. Intercepts Vec<ItemInfo>::sse_encode (Rust -> Dart push boundary) to plan
 *    and update HOME item coordinates in Rust memory before SSE serialization,
 *    bypassing Dart ItemInfo DTO heap offset hooks in layout_load.h.
 */

#include <elf.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "bootlog.h"
#include "executable_range.h"
#include "lib_base.h"
#include "rust_symbol_resolver.h"

typedef struct {
    int32_t start_load;
    int32_t bind_items_sub;
    int32_t bind_screens;
    int32_t update_item_batch;
    int32_t update_item;
    int32_t move_item;
} ld_frb_launcher_ids_t;

typedef struct {
    size_t stride;
    size_t id_off;
    size_t container_off;
    size_t screen_off;
    size_t x_off;
    size_t y_off;
    size_t sx_off;
    size_t sy_off;
    bool   valid;
} ld_frb_item_layout_t;

static ld_frb_launcher_ids_t g_frb_ids = {-1, -1, -1, -1, -1, -1};
static ld_frb_item_layout_t g_frb_item_layout = {0};
static void (*g_orig_frb_primary_adapter)(int32_t, int64_t, uint8_t *, int32_t, int32_t) = NULL;
static void (*g_orig_frb_vec_item_encode)(void *, void *) = NULL;
static void (*g_orig_frb_vec_item_decode)(void *, void *) = NULL;
static bool g_frb_adapter_installed = false;
static bool g_frb_sse_decode_synced = false;
static volatile int g_frb_start_cols = 0;
static volatile int g_frb_start_rows = 0;
static volatile int g_frb_start_dir = -1;

static int ld_frb_adapter_ready(void) {
    return (g_frb_adapter_installed && g_orig_frb_primary_adapter &&
            g_frb_ids.start_load >= 0 && g_frb_ids.update_item_batch >= 0) ? 1 : 0;
}

static uintptr_t ld_frb_find_dynsym(uintptr_t base, const char *target_name) {
    const Elf64_Ehdr *eh = (const Elf64_Ehdr *)base;
    if (!base || memcmp(eh->e_ident, ELFMAG, SELFMAG) != 0) return 0;
    const Elf64_Phdr *ph = (const Elf64_Phdr *)(base + eh->e_phoff);
    const Elf64_Dyn *dyn = NULL;
    for (uint16_t i = 0; i < eh->e_phnum; i++) {
        if (ph[i].p_type == PT_DYNAMIC) {
            dyn = (const Elf64_Dyn *)(base + ph[i].p_vaddr);
            break;
        }
    }
    if (!dyn) return 0;
    const char *strtab = NULL;
    const Elf64_Sym *symtab = NULL;
    uint32_t nchain = 0;
    for (; dyn->d_tag != DT_NULL; dyn++) {
        if (dyn->d_tag == DT_STRTAB) strtab = (const char *)(base + dyn->d_un.d_ptr);
        else if (dyn->d_tag == DT_SYMTAB) symtab = (const Elf64_Sym *)(base + dyn->d_un.d_ptr);
        else if (dyn->d_tag == DT_HASH) {
            const uint32_t *h = (const uint32_t *)(base + dyn->d_un.d_ptr);
            nchain = h[1];
        }
    }
    if (!strtab || !symtab) return 0;
    uint32_t limit = nchain ? nchain : 8192u;
    for (uint32_t i = 0; i < limit; i++) {
        if ((uintptr_t)&symtab[i] >= (uintptr_t)strtab) break;
        if (symtab[i].st_value == 0 || ELF64_ST_TYPE(symtab[i].st_info) != STT_FUNC) continue;
        if (strcmp(strtab + symtab[i].st_name, target_name) == 0) {
            return base + symtab[i].st_value;
        }
    }
    return 0;
}

static uintptr_t ld_frb_follow_b(uintptr_t addr) {
    uint32_t insn = 0;
    memcpy(&insn, (const void *)addr, 4);
    if ((insn & 0xfc000000u) == 0x14000000u) {
        int32_t imm26 = (int32_t)(insn & 0x03ffffffu);
        if (imm26 & 0x02000000) imm26 -= 0x04000000;
        return addr + ((int64_t)imm26 << 2);
    }
    return addr;
}

typedef struct {
    const char *needle;
    bool        exact_tail;
    int32_t    *out_id;
    uintptr_t   vaddr;
    size_t      size;
} ld_frb_target_desc_t;

static bool ld_frb_match_debugdata_vaddrs(const uint8_t *elf, size_t elf_len,
                                          ld_frb_target_desc_t *targets, size_t ntargets) {
    if (!elf || elf_len < 64) return false;
    uint64_t e_shoff = 0;
    uint16_t e_shentsize = 0, e_shnum = 0;
    memcpy(&e_shoff, elf + 0x28, 8);
    memcpy(&e_shentsize, elf + 0x3a, 2);
    memcpy(&e_shnum, elf + 0x3c, 2);
    if (!e_shoff || e_shentsize < 64 || !e_shnum || e_shoff + (uint64_t)e_shentsize * e_shnum > elf_len) {
        return false;
    }

    for (uint16_t i = 0; i < e_shnum; i++) {
        const uint8_t *sh = elf + e_shoff + (size_t)i * e_shentsize;
        uint32_t sh_type = 0, sh_link = 0;
        uint64_t sh_offset = 0, sh_size = 0;
        memcpy(&sh_type, sh + 4, 4);
        memcpy(&sh_offset, sh + 24, 8);
        memcpy(&sh_size, sh + 32, 8);
        memcpy(&sh_link, sh + 40, 4);
        if (sh_type != 2u || sh_link >= e_shnum || sh_offset + sh_size > elf_len) continue;

        const uint8_t *str_sh = elf + e_shoff + (size_t)sh_link * e_shentsize;
        uint64_t str_off = 0, str_size = 0;
        memcpy(&str_off, str_sh + 24, 8);
        memcpy(&str_size, str_sh + 32, 8);
        if (!str_size || str_off + str_size > elf_len) continue;

        const char *strtab = (const char *)(elf + str_off);
        size_t count = (size_t)(sh_size / 24u);
        for (size_t idx = 0; idx < count; idx++) {
            const uint8_t *ent = elf + sh_offset + idx * 24u;
            uint32_t st_name = 0;
            uint8_t st_info = ent[4];
            uint64_t st_value = 0, st_size = 0;
            memcpy(&st_name, ent, 4);
            memcpy(&st_value, ent + 8, 8);
            memcpy(&st_size, ent + 16, 8);
            if ((st_info & 0x0fu) != 2u || !st_value || st_name >= str_size) continue;
            const char *name = strtab + st_name;
            if (!memchr(name, '\0', (size_t)(str_size - st_name))) continue;
            if (strstr(name, "frb_generated") == NULL) continue;
            for (size_t t = 0; t < ntargets; t++) {
                const char *hit = strstr(name, targets[t].needle);
                if (!hit) continue;
                if (targets[t].exact_tail && strcmp(hit, targets[t].needle) != 0) continue;
                targets[t].vaddr = (uintptr_t)st_value;
                targets[t].size = (size_t)st_size;
            }
        }
        return true;
    }
    return false;
}

static bool ld_frb_decode_primary_table(uintptr_t disp_rt, uintptr_t rust_base,
                                        ld_frb_target_desc_t *targets, size_t ntargets) {
    const uint32_t *insns = (const uint32_t *)disp_rt;
    int32_t min_id = -1;
    uint32_t max_delta = 0;
    const uint16_t *table = NULL;
    uintptr_t stub_base = 0;
    uintptr_t adrp_base = 0;
    uint32_t adrp_rd = 32;

    for (size_t i = 0; i < 16; i++) {
        uint32_t w = insns[i];
        uintptr_t pc = disp_rt + i * 4u;
        if ((w & 0xffc003ffu) == 0x51000008u && min_id < 0) {
            min_id = (int32_t)((w >> 10) & 0xfffu);
        } else if ((w & 0xffc003ffu) == 0x7100011fu && max_delta == 0) {
            max_delta = (w >> 10) & 0xfffu;
        } else if ((w & 0x9f00001fu) == 0x90000009u) {
            int64_t imm = (int64_t)(((uint64_t)(w >> 5) & 0x7ffffu) << 2) | ((w >> 29) & 0x3u);
            if (imm & (1LL << 20)) imm -= (1LL << 21);
            adrp_base = (pc & ~0xfffULL) + (imm << 12);
            adrp_rd = w & 0x1fu;
        } else if ((w & 0xffc00000u) == 0x91000000u && adrp_rd == ((w >> 5) & 0x1fu) && (w & 0x1fu) == 9u) {
            uint32_t imm12 = (w >> 10) & 0xfffu;
            table = (const uint16_t *)(adrp_base + imm12);
        } else if ((w & 0x9f00001fu) == 0x1000000au) {
            int64_t imm = (int64_t)(((uint64_t)(w >> 5) & 0x7ffffu) << 2) | ((w >> 29) & 0x3u);
            if (imm & (1LL << 20)) imm -= (1LL << 21);
            stub_base = pc + imm;
        }
    }

    if (min_id < 0 || max_delta == 0 || max_delta > 4096u || !table || !stub_base) {
        return false;
    }

    for (uint32_t i = 0; i <= max_delta; i++) {
        uintptr_t stub = stub_base + ((uintptr_t)table[i] << 2);
        if (!executable_range(stub, 24u)) continue;
        const uint32_t *sw = (const uint32_t *)stub;
        for (size_t k = 0; k < 6; k++) {
            if ((sw[k] & 0xfc000000u) == 0x14000000u) {
                int32_t imm26 = (int32_t)(sw[k] & 0x03ffffffu);
                if (imm26 & 0x02000000) imm26 -= 0x04000000;
                uintptr_t target_rt = (stub + k * 4u) + ((int64_t)imm26 << 2);
                uintptr_t target_va = target_rt - rust_base;
                for (size_t t = 0; t < ntargets; t++) {
                    if (targets[t].out_id && targets[t].vaddr && targets[t].vaddr == target_va) {
                        *targets[t].out_id = min_id + (int32_t)i;
                    }
                }
                break;
            }
        }
    }
    return true;
}

static bool ld_frb_extract_item_layout(uintptr_t vec_encode_rt, size_t vec_encode_sz,
                                       uintptr_t item_encode_rt, size_t item_encode_sz,
                                       ld_frb_item_layout_t *out) {
    memset(out, 0, sizeof(*out));
    if (!executable_range(vec_encode_rt, vec_encode_sz) ||
        !executable_range(item_encode_rt, item_encode_sz)) {
        return false;
    }
    /* Extract stride from Vec<ItemInfo>::sse_encode: mov w8, #stride / add x22, x22, #stride */
    const uint32_t *vw = (const uint32_t *)vec_encode_rt;
    size_t vwords = vec_encode_sz / 4u;
    size_t stride = 0;
    for (size_t i = 0; i < vwords; i++) {
        uint32_t w = vw[i];
        /* movz w8, #imm16 -> 0x52800008 | (imm16 << 5) */
        if ((w & 0xffe0001fu) == 0x52800008u) {
            size_t cand = (size_t)((w >> 5) & 0xffffu);
            if (cand >= 256u && cand <= 2048u) { stride = cand; break; }
        }
    }
    if (!stride) return false;

    /* Extract first 5 Option<i32> offsets (ldr w22, [x20, #off]) and Option<i64> container offset (ldp x22, x21, [x20, #off]) */
    const uint32_t *iw = (const uint32_t *)item_encode_rt;
    size_t iwords = item_encode_sz / 4u;
    if (iwords > 160u) iwords = 160u;
    size_t i32_offs[7] = {0};
    size_t i32_cnt = 0;
    size_t cont_off = 0;
    bool seen_first_i64 = false;

    for (size_t i = 0; i < iwords; i++) {
        uint32_t w = iw[i];
        /* ldp x22, x21, [x0] (id at offset 0) */
        if (w == 0xa9405416u) {
            seen_first_i64 = true;
            out->id_off = 0;
        }
        /* ldr w22, [x20, #imm12*4] -> 0xb9400296 | (imm12 << 10) */
        else if ((w & 0xffc003ffu) == 0xb9400296u && i32_cnt < 7u) {
            size_t off = (size_t)(((w >> 10) & 0xfffu) * 4u);
            if (off + 8u <= stride) i32_offs[i32_cnt++] = off;
        }
        /* ldp x22, x21, [x20, #imm7*8] -> 0xa9405696 | (imm7 << 15) */
        else if ((w & 0xffc07fffu) == 0xa9405696u && i32_cnt >= 5u && cont_off == 0) {
            size_t off = (size_t)(((w >> 15) & 0x7fu) * 8u);
            if (off + 16u <= stride) cont_off = off;
        }
    }

    if (!seen_first_i64 || i32_cnt < 7u || !cont_off) return false;
    /* Verify all 7 Option<i32> fields are contiguous 8-byte slots; fields [2..6] are spanX, spanY, screen, cellX, cellY */
    for (size_t k = 1; k < 7u; k++) {
        if (i32_offs[k] != i32_offs[k - 1] + 8u) return false;
    }

    out->stride = stride;
    out->container_off = cont_off;
    out->sx_off = i32_offs[2];
    out->sy_off = i32_offs[3];
    out->screen_off = i32_offs[4];
    out->x_off = i32_offs[5];
    out->y_off = i32_offs[6];
    out->valid = true;
    return true;
}

static bool ld_frb_read_opt_i64(const uint8_t *item, size_t off, int64_t *val) {
    uint64_t tag = 0;
    memcpy(&tag, item + off, 8);
    if (tag != 1u) return false;
    memcpy(val, item + off + 8u, 8);
    return true;
}

static bool ld_frb_read_opt_i32_def(const uint8_t *item, size_t off, int32_t def_val, int32_t *val) {
    uint32_t tag = 0;
    memcpy(&tag, item + off, 4);
    if (tag == 0u) { *val = def_val; return true; }
    if (tag != 1u) return false;
    memcpy(val, item + off + 4u, 4);
    return true;
}

static void ld_frb_write_opt_i32(uint8_t *item, size_t off, int32_t val) {
    uint32_t tag = 1u;
    memcpy(item + off, &tag, 4);
    memcpy(item + off + 4u, &val, 4);
}

static int ld_frb_plan_container(uint8_t *base, uint64_t len, int cols, int rows, int direction,
                                 int64_t target_cont, const char *layout_file, int is_home) {
    struct ld_layout_item *source = calloc(LD_LAYOUT_MAX_ITEMS, sizeof *source);
    struct ld_layout_item *target = calloc(LD_LAYOUT_MAX_ITEMS, sizeof *target);
    uint8_t **ptrs = calloc(LD_LAYOUT_MAX_ITEMS, sizeof *ptrs);
    struct ld_layout_memory *memory = calloc(1, sizeof *memory);
    size_t count = 0;
    int planned = 0;
    if (!source || !target || !ptrs || !memory) goto done;

    const ld_frb_item_layout_t *L = &g_frb_item_layout;
    for (uint64_t i = 0; i < len && count < LD_LAYOUT_MAX_ITEMS; i++) {
        uint8_t *it = base + i * L->stride;
        uint64_t disc = 0;
        memcpy(&disc, it, 8);
        if (disc == 2u) { continue; }
        int64_t id = 0, cont = 0;
        int32_t scr = 0, cx = 0, cy = 0, sx = 1, sy = 1;
        if (!ld_frb_read_opt_i64(it, L->id_off, &id)) { continue; }
        if (!ld_frb_read_opt_i64(it, L->container_off, &cont)) { continue; }
        if (cont != target_cont) continue;
        if (!ld_frb_read_opt_i32_def(it, L->screen_off, -1, &scr) || scr < 0) continue;
        if (!ld_frb_read_opt_i32_def(it, L->x_off, 0, &cx) ||
            !ld_frb_read_opt_i32_def(it, L->y_off, 0, &cy) ||
            !ld_frb_read_opt_i32_def(it, L->sx_off, 1, &sx) ||
            !ld_frb_read_opt_i32_def(it, L->sy_off, 1, &sy)) continue;

        source[count].id = id;
        source[count].screen = scr;
        source[count].x = cx;
        source[count].y = cy;
        source[count].sx = sx;
        source[count].sy = sy;
        ptrs[count] = it;
        count++;
    }
    if (count == 0) goto done;

    if (is_home) ld_write_4x2_widget_state(source, count);

    int known = ld_layout_load(layout_file, memory) &&
                memory->columns == g_grid_cols && memory->rows == g_grid_rows;
    if (!known) {
        memset(memory, 0, sizeof *memory);
        memory->active_direction = -1;
    }
    int source_direction = memory->active_direction;
    if (source_direction < 0) {
        int p = ld_layout_valid(source, count, g_grid_cols, g_grid_rows);
        int l = ld_layout_valid(source, count, g_grid_rows, g_grid_cols);
        source_direction = (p && !l) ? 0 : (l && !p) ? 1 : direction;
    }
    int sc = source_direction ? g_grid_rows : g_grid_cols;
    int sr = source_direction ? g_grid_cols : g_grid_rows;
    memory->columns = g_grid_cols;
    memory->rows = g_grid_rows;

    if (known && source_direction >= 0) {
        for (size_t i = 0; i < count; i++) {
            for (size_t j = 0; j < memory->count[source_direction]; j++) {
                if (source[i].id == memory->items[source_direction][j].id) {
                    source[i].screen = memory->items[source_direction][j].screen;
                    break;
                }
            }
        }
    } else if (ld_layout_valid(source, count, sc, sr)) {
        memcpy(memory->items[source_direction], source, count * sizeof *source);
        memory->count[source_direction] = (uint32_t)count;
    }

    if (!ld_layout_plan(source, count, cols, rows, memory->items[direction], memory->count[direction], target)) {
        ld_blog("FRB load bail plan cont=%lld count=%zu cols=%d rows=%d dir=%d",
                (long long)target_cont, count, cols, rows, direction);
        goto done;
    }

    memcpy(memory->items[direction], target, count * sizeof *target);
    memory->count[direction] = (uint32_t)count;
    memory->active_direction = direction;
    if (!ld_layout_save(layout_file, memory)) {
        ld_blog("FRB load bail save cont=%lld", (long long)target_cont);
        goto done;
    }

    for (size_t i = 0; i < count; i++) {
        ld_frb_write_opt_i32(ptrs[i], L->screen_off, (int32_t)target[i].screen);
        ld_frb_write_opt_i32(ptrs[i], L->x_off, (int32_t)target[i].x);
        ld_frb_write_opt_i32(ptrs[i], L->y_off, (int32_t)target[i].y);
    }

    planned = 1;
    ld_blog("FRB boundary restored %zu items (cont=%lld) %dx%d source-direction=%d (bypassed Dart DTO hook)",
            count, (long long)target_cont, cols, rows, source_direction);

done:
    free(source);
    free(target);
    free(ptrs);
    free(memory);
    return planned;
}

static void ld_frb_plan_vec_items(void *vec_ptr) {
    if (!vec_ptr || !g_frb_item_layout.valid) return;
    if (g_load_prepared || !g_layout_hooks_ready || g_grid_cols <= 0 || g_grid_rows <= 0) return;

    uint64_t data_addr = 0, len = 0;
    memcpy(&data_addr, (const uint8_t *)vec_ptr + 8u, 8);
    memcpy(&len, (const uint8_t *)vec_ptr + 16u, 8);
    ld_blog("FRB vec_encode len=%llu hooks_ready=%d rt=%dx%d load=%dx%d frb=%dx%d",
            (unsigned long long)len, g_layout_hooks_ready ? 1 : 0,
            g_runtime_columns, g_runtime_rows, g_load_columns, g_load_rows,
            g_frb_start_cols, g_frb_start_rows);
    if (!data_addr || len == 0 || len > 4096u) return;

    int cols = g_runtime_columns ? g_runtime_columns : (g_load_columns ? g_load_columns : g_frb_start_cols);
    int rows = g_runtime_rows ? g_runtime_rows : (g_load_rows ? g_load_rows : g_frb_start_rows);
    int direction = g_grid_cols == g_grid_rows
        ? (g_runtime_columns ? g_runtime_direction : (g_load_columns ? g_load_direction : g_frb_start_dir))
        : (cols == g_grid_cols && rows == g_grid_rows ? 0 : 1);
    if (direction < 0 || direction > 1) return;
    if (direction && (cols != g_grid_rows || rows != g_grid_cols)) return;

    uint8_t *base = (uint8_t *)(uintptr_t)data_addr;
    int p_home = ld_frb_plan_container(base, len, cols, rows, direction, -100, LD_LAYOUT_FILE, 1);
    int p_ws   = ld_frb_plan_container(base, len, cols, rows, direction, -104, LD_WS_LAYOUT_FILE, 0);
    if (p_home || p_ws) {
        g_load_prepared = 1;
        g_initial_restore = 1;
        g_initial_columns = cols;
        g_initial_rows = rows;
        g_initial_direction = direction;
    }
}

static void ld_frb_sync_container(uint8_t *base, uint64_t len, int cols, int rows, int direction,
                                  int64_t target_cont, const char *layout_file) {
    struct ld_layout_memory *memory = calloc(1, sizeof *memory);
    if (!memory) return;
    if (!ld_layout_load(layout_file, memory) ||
        memory->columns != g_grid_cols || memory->rows != g_grid_rows) {
        memset(memory, 0, sizeof *memory);
        memory->columns = g_grid_cols;
        memory->rows = g_grid_rows;
        memory->active_direction = direction;
    }

    const ld_frb_item_layout_t *L = &g_frb_item_layout;
    size_t upserted = 0;
    for (uint64_t i = 0; i < len; i++) {
        uint8_t *it = base + i * L->stride;
        uint64_t disc = 0;
        memcpy(&disc, it, 8);
        if (disc == 2u) continue;
        int64_t id = 0, cont = 0;
        int32_t scr = 0, cx = 0, cy = 0, sx = 1, sy = 1;
        if (!ld_frb_read_opt_i64(it, L->id_off, &id) || id <= 0) continue;
        if (!ld_frb_read_opt_i64(it, L->container_off, &cont) || cont != target_cont) continue;
        if (!ld_frb_read_opt_i32_def(it, L->screen_off, -1, &scr) || scr < 0) continue;
        if (!ld_frb_read_opt_i32_def(it, L->x_off, 0, &cx) ||
            !ld_frb_read_opt_i32_def(it, L->y_off, 0, &cy) ||
            !ld_frb_read_opt_i32_def(it, L->sx_off, 1, &sx) ||
            !ld_frb_read_opt_i32_def(it, L->sy_off, 1, &sy)) continue;
        if (cx < 0 || cy < 0 || sx <= 0 || sy <= 0 || cx + sx > cols || cy + sy > rows) continue;

        int found = 0;
        for (uint32_t j = 0; j < memory->count[direction]; j++) {
            if (memory->items[direction][j].id == id) {
                memory->items[direction][j].screen = scr;
                memory->items[direction][j].x = cx;
                memory->items[direction][j].y = cy;
                memory->items[direction][j].sx = sx;
                memory->items[direction][j].sy = sy;
                found = 1;
                break;
            }
        }
        if (!found && memory->count[direction] < LD_LAYOUT_MAX_ITEMS) {
            uint32_t k = memory->count[direction]++;
            memory->items[direction][k].id = id;
            memory->items[direction][k].screen = scr;
            memory->items[direction][k].x = cx;
            memory->items[direction][k].y = cy;
            memory->items[direction][k].sx = sx;
            memory->items[direction][k].sy = sy;
        }
        upserted++;
    }

    if (upserted > 0) {
        memory->active_direction = direction;
        if (ld_layout_save(layout_file, memory)) {
            g_frb_sse_decode_synced = true;
            ld_blog("FRB vec_decode sync: cont=%lld len=%llu upserted=%zu dir=%d (%dx%d)",
                    (long long)target_cont, (unsigned long long)len, upserted, direction, cols, rows);
        }
    }
    free(memory);
}

static void ld_frb_sync_decoded_vec(void *out_vec) {
    if (!out_vec || !g_frb_item_layout.valid || !g_layout_hooks_ready ||
        g_grid_cols <= 0 || g_grid_rows <= 0) return;

    uint64_t data_addr = 0, len = 0;
    memcpy(&data_addr, (const uint8_t *)out_vec + 8u, 8);
    memcpy(&len, (const uint8_t *)out_vec + 16u, 8);
    if (!data_addr || len == 0 || len > 4096u) return;

    int cols = g_runtime_columns ? g_runtime_columns : (g_load_columns ? g_load_columns : g_frb_start_cols);
    int rows = g_runtime_rows ? g_runtime_rows : (g_load_rows ? g_load_rows : g_frb_start_rows);
    int direction = g_grid_cols == g_grid_rows
        ? (g_runtime_columns ? g_runtime_direction : (g_load_columns ? g_load_direction : g_frb_start_dir))
        : (cols == g_grid_cols && rows == g_grid_rows ? 0 : 1);
    if (direction < 0 || direction > 1) return;
    if (direction && (cols != g_grid_rows || rows != g_grid_cols)) return;
    if (!cols || !rows) { cols = direction ? g_grid_rows : g_grid_cols; rows = direction ? g_grid_cols : g_grid_rows; }

    uint8_t *base = (uint8_t *)(uintptr_t)data_addr;
    ld_frb_sync_container(base, len, cols, rows, direction, -100, LD_LAYOUT_FILE);
    ld_frb_sync_container(base, len, cols, rows, direction, -104, LD_WS_LAYOUT_FILE);
}

static void repl_frb_vec_item_decode(void *out_vec, void *deser) {
    if (g_orig_frb_vec_item_decode) g_orig_frb_vec_item_decode(out_vec, deser);
    ld_frb_sync_decoded_vec(out_vec);
}

static void repl_frb_vec_item_encode(void *vec_ptr, void *serializer) {
    ld_frb_plan_vec_items(vec_ptr);
    g_orig_frb_vec_item_encode(vec_ptr, serializer);
}

static void repl_frb_primary_adapter(int32_t func_id, int64_t port, uint8_t *ptr,
                                     int32_t rust_vec_len, int32_t data_len) {
    if (func_id == g_frb_ids.start_load && ptr && data_len >= 16) {
        int32_t args[4] = {0};
        memcpy(args, ptr, 16);
        if ((args[0] == g_grid_cols && args[1] == g_grid_rows) ||
            (args[0] == g_grid_rows && args[1] == g_grid_cols)) {
            g_frb_start_cols = args[0];
            g_frb_start_rows = args[1];
            g_frb_start_dir = (g_frb_start_cols == g_grid_cols && g_frb_start_rows == g_grid_rows) ? 0 : 1;
        } else if (args[2] > 0 && args[3] > 0) {
            g_frb_start_cols = args[2];
            g_frb_start_rows = args[3];
            g_frb_start_dir = (g_frb_start_cols == g_grid_cols && g_frb_start_rows == g_grid_rows) ? 0
                            : (g_frb_start_cols == g_grid_rows && g_frb_start_rows == g_grid_cols) ? 1 : -1;
        }
        ld_blog("FRB adapter start_load(id=%d): args=[%d,%d,%d,%d]",
                func_id, args[0], args[1], args[2], args[3]);
    } else if (func_id == g_frb_ids.update_item_batch) {
        uint32_t count = 0;
        if (ptr && data_len >= 4) memcpy(&count, ptr, 4);
        ld_blog("FRB adapter update_item_batch(id=%d): bytes=%d header_count=%u",
                func_id, data_len, count);
    } else if (func_id == g_frb_ids.update_item) {
        ld_blog("FRB adapter update_item(id=%d): bytes=%d", func_id, data_len);
    } else if (func_id == g_frb_ids.move_item) {
        ld_blog("FRB adapter move_item(id=%d): bytes=%d", func_id, data_len);
    }
    g_orig_frb_primary_adapter(func_id, port, ptr, rust_vec_len, data_len);
}

static void ld_frb_adapter_init(int (*hook_fn)(void *, void *, void **),
                                const ld_lib_info *rust,
                                const char *apk_substr) {
    if (g_frb_adapter_installed || !hook_fn || !rust || !apk_substr) return;

    uintptr_t rust_base = 0;
    if (!ld_vaddr_to_runtime(rust, 0, &rust_base)) return;

    uintptr_t p_sym = ld_frb_find_dynsym(rust_base, "frb_pde_ffi_dispatcher_primary");
    if (!p_sym || !executable_range(p_sym, 4u)) return;

    uintptr_t p_disp = ld_frb_follow_b(p_sym);
    if (!executable_range(p_disp, 64u)) return;

    uint8_t *mini_elf = NULL;
    size_t mini_len = 0;
    if (!ld_rust_decompress_debugdata(rust, apk_substr, &mini_elf, &mini_len)) return;

    ld_frb_target_desc_t targets[] = {
        {"launcher_model_start_load_impl", true, &g_frb_ids.start_load, 0, 0},
        {"launcher_model_set_bind_items_sub_callback_impl", true, &g_frb_ids.bind_items_sub, 0, 0},
        {"launcher_model_set_bind_screens_callback_impl", true, &g_frb_ids.bind_screens, 0, 0},
        {"launcher_model_update_item_database_batch_impl", true, &g_frb_ids.update_item_batch, 0, 0},
        {"launcher_model_update_item_database_impl", true, &g_frb_ids.update_item, 0, 0},
        {"launcher_model_move_item_in_database_impl", true, &g_frb_ids.move_item, 0, 0},
        {"database_info8ItemInfoENtB6_9SseEncode10sse_encode", true, NULL, 0, 0},
        {"database_info8ItemInfoNtB6_9SseEncode10sse_encode", true, NULL, 0, 0},
        {"database_info8ItemInfoENtB6_9SseDecode10sse_decode", true, NULL, 0, 0},
    };
    size_t ntargets = sizeof(targets) / sizeof(targets[0]);

    bool sym_ok = ld_frb_match_debugdata_vaddrs(mini_elf, mini_len, targets, ntargets);
    free(mini_elf);
    if (!sym_ok || !ld_frb_decode_primary_table(p_disp, rust_base, targets, ntargets)) {
        ld_blog("FRB adapter: table decode failed");
        return;
    }

    if (g_frb_ids.start_load < 0 || g_frb_ids.update_item_batch < 0) {
        ld_blog("FRB adapter: missing core func_ids start_load=%d batch=%d",
                g_frb_ids.start_load, g_frb_ids.update_item_batch);
        return;
    }

    g_frb_adapter_installed = true;
    int r = hook_fn((void *)p_disp, (void *)repl_frb_primary_adapter,
                    (void **)&g_orig_frb_primary_adapter);

    int r_vec = -1;
    int r_vec_dec = -1;
    uintptr_t vec_enc_rt = 0, item_enc_rt = 0, vec_dec_rt = 0;
    if (targets[6].vaddr && targets[7].vaddr &&
        ld_vaddr_to_runtime(rust, targets[6].vaddr, &vec_enc_rt) &&
        ld_vaddr_to_runtime(rust, targets[7].vaddr, &item_enc_rt) &&
        ld_frb_extract_item_layout(vec_enc_rt, targets[6].size, item_enc_rt, targets[7].size, &g_frb_item_layout)) {
        r_vec = hook_fn((void *)vec_enc_rt, (void *)repl_frb_vec_item_encode,
                        (void **)&g_orig_frb_vec_item_encode);
        if (targets[8].vaddr && ld_vaddr_to_runtime(rust, targets[8].vaddr, &vec_dec_rt)) {
            r_vec_dec = hook_fn((void *)vec_dec_rt, (void *)repl_frb_vec_item_decode,
                                (void **)&g_orig_frb_vec_item_decode);
        }
    }

    ld_blog("FRB adapter ready=%d hook_res=%d vec_hook=%d vec_dec_hook=%d layout_valid=%d stride=%zu offs={id=%zu,cont=%zu,scr=%zu,x=%zu,y=%zu,sx=%zu,sy=%zu} ids={start=%d,bind_items=%d,bind_scr=%d,batch=%d,upd=%d,mov=%d}",
            r == 0 ? 1 : 0, r, r_vec, r_vec_dec, g_frb_item_layout.valid ? 1 : 0, g_frb_item_layout.stride,
            g_frb_item_layout.id_off, g_frb_item_layout.container_off, g_frb_item_layout.screen_off,
            g_frb_item_layout.x_off, g_frb_item_layout.y_off, g_frb_item_layout.sx_off, g_frb_item_layout.sy_off,
            g_frb_ids.start_load, g_frb_ids.bind_items_sub, g_frb_ids.bind_screens,
            g_frb_ids.update_item_batch, g_frb_ids.update_item, g_frb_ids.move_item);
}

#endif /* LIQUIDDOCK_FRB_BOUNDARY_ADAPTER_H */
