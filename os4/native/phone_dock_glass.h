#ifndef LIQUIDDOCK_PHONE_DOCK_GLASS_H
#define LIQUIDDOCK_PHONE_DOCK_GLASS_H

#include <stdint.h>
#include <stddef.h>
#include <math.h>
#include "dart_abi.h"

static void pin_installed_hook(uintptr_t site);

static void *g_orig_pd_hotseat_stack = NULL;
static void *g_orig_pd_calc_pos_x = NULL;

static uintptr_t g_pd_alloc_glass_fn = 0;
static uintptr_t g_pd_alloc_double_fn = 0;
static uintptr_t g_pd_alloc_pos_fn = 0;
static size_t g_pd_style_pp_off = 0;

static int g_pd_installed = 0;

struct ld_pd_pill_params {
    double horiz_inset;
    double bottom;
    double height;
    double radius;
    uintptr_t style_obj;
    uintptr_t alloc_glass;
    uintptr_t alloc_double;
    uintptr_t alloc_pos;
};

struct ld_pd_pill_objs {
    uintptr_t glass;
    uintptr_t horiz_box;
    uintptr_t bottom_box;
    uintptr_t height_box;
    uintptr_t pos;
};

static inline uintptr_t ld_pd_decode_bl(uintptr_t pc) {
    uint32_t insn = *(const uint32_t *)pc;
    if ((insn & 0xfc000000u) != 0x94000000u) return 0;
    int32_t imm = (int32_t)(insn & 0x03ffffffu);
    if (imm & 0x02000000) imm |= ~0x03ffffff;
    return (uintptr_t)((intptr_t)pc + ((intptr_t)imm << 2));
}

static inline size_t ld_pd_decode_pp_load(uintptr_t pc_add, uintptr_t pc_ldr) {
    uint32_t ldr_i = *(const uint32_t *)pc_ldr;
    if ((ldr_i & 0xffc003e0u) == 0xf9400360u) {
        return (size_t)((ldr_i >> 10) & 0xfffu) * 8u;
    }
    uint32_t add_i = *(const uint32_t *)pc_add;
    if ((add_i & 0xffc003e0u) == 0x91400360u &&
        (ldr_i & 0xffc00000u) == 0xf9400000u &&
        ((ldr_i >> 5) & 0x1fu) == (add_i & 0x1fu)) {
        return ((size_t)((add_i >> 10) & 0xfffu) << 12) +
               (size_t)((ldr_i >> 10) & 0xfffu) * 8u;
    }
    return 0;
}

static uintptr_t ld_pd_find_hotseat_stack_closure(uintptr_t build_items_rt) {
    if (!build_items_rt || !executable_range(build_items_rt - 0x400u, 0x400u)) return 0;
    uintptr_t bl_site = 0;
    for (uintptr_t a = build_items_rt - 4u; a >= build_items_rt - 0x400u; a -= 4u) {
        if (ld_pd_decode_bl(a) == build_items_rt) {
            bl_site = a;
            break;
        }
    }
    if (!bl_site) return 0;
    uintptr_t closure = 0;
    for (uintptr_t a = bl_site; a >= bl_site - 0x400u; a -= 4u) {
        if (*(const uint32_t *)a == 0xa9bf79fdu) {
            closure = a;
            break;
        }
    }
    for (uintptr_t a = bl_site; a < build_items_rt; a += 4u) {
        uintptr_t t = ld_pd_decode_bl(a);
        if (!t) continue;
        uint32_t prev1 = *(const uint32_t *)(a - 4u);
        uint32_t prev2 = *(const uint32_t *)(a - 8u);
        if (prev1 == 0xa9bf05e0u &&
            ((prev2 & 0xfffffc00u) == 0x3c9f0c00u || (prev2 & 0xfffffc00u) == 0xa9bf1000u)) {
            g_pd_alloc_double_fn = t;
        }
    }
    return closure;
}

static uintptr_t ld_pd_decode_calc_pos_x(uintptr_t bg_layout_rt, size_t bg_layout_sz) {
    if (!bg_layout_rt || bg_layout_sz < 64u || !executable_range(bg_layout_rt, bg_layout_sz)) return 0;
    uintptr_t end = bg_layout_rt + bg_layout_sz - 8u;
    for (uintptr_t a = bg_layout_rt + 4u; a < end; a += 4u) {
        uintptr_t t = ld_pd_decode_bl(a);
        if (!t) continue;
        uint32_t prev = *(const uint32_t *)(a - 4u);
        uint32_t next = *(const uint32_t *)(a + 4u);
        /* Match: ldr x4, [x27, #imm]; bl calculatePositionX; mov v1.16b, v0.16b */
        if ((prev & 0xffc003ffu) == 0xf9400364u && next == 0x4ea01c01u) {
            return t;
        }
    }
    return 0;
}

static void ld_pd_decode_strategy(uintptr_t strat_rt) {
    if (!strat_rt || !executable_range(strat_rt, 0x200u)) return;
    for (uintptr_t a = strat_rt; a < strat_rt + 0x1c0u; a += 4u) {
        uintptr_t t = ld_pd_decode_bl(a);
        if (!t) continue;
        /* Match mode==0 (_DockGlassWidget):
         *   bl <AllocDockGlass>
         *   stur xzr, [x0, #0x1f]       (0xf801f01f)
         *   ldur d0, [x29, #-0x20]
         *   stur d0, [x0, #0xb]         (0xfc00b000)
         *   add/ldr x1, [x27 + style_pp]
         *   stur w1, [x0, #0x13]        (0xb8013000)
         *   fmov d1, #1.0
         *   stur d1, [x0, #0x17]        (0xfc017000)
         */
        uint32_t i1 = *(const uint32_t *)(a + 4u);
        uint32_t i3 = *(const uint32_t *)(a + 12u);
        uint32_t i6 = *(const uint32_t *)(a + 24u);
        uint32_t i8 = *(const uint32_t *)(a + 32u);
        if (i1 == 0xf801f01fu &&
            (i3 & 0xffffffe0u) == 0xfc00b000u &&
            (i6 & 0xffffffe0u) == 0xb8013000u &&
            (i8 & 0xffffffe0u) == 0xfc017000u) {
            size_t pp = ld_pd_decode_pp_load(a + 16u, a + 20u);
            if (pp) {
                g_pd_alloc_glass_fn = t;
                g_pd_style_pp_off = pp;
                return;
            }
        }
    }
}

static void ld_pd_decode_positioned(uintptr_t cnt_rt, size_t cnt_sz) {
    if (!cnt_rt || cnt_sz < 64u || !executable_range(cnt_rt, cnt_sz)) return;
    uintptr_t end = cnt_rt + cnt_sz - 36u;
    for (uintptr_t a = cnt_rt; a < end; a += 4u) {
        uintptr_t t = ld_pd_decode_bl(a);
        if (!t) continue;
        /* Match Positioned allocation in _DockLightContentState._buildContent:
         *   bl <AllocPositioned>
         *   mov x2, x0
         *   ldr x0, [x27, #imm]
         *   stur x2, [x29, #-imm]
         *   stur w0, [x2, #0x13]   (left)
         *   stur w0, [x2, #0x1b]   (right)
         *   stur w0, [x2, #0x1f]   (bottom)
         *   ldur x1, [x29, #-imm]
         *   stur w1, [x2, #0xb]    (child)
         */
        uint32_t i4 = *(const uint32_t *)(a + 16u);
        uint32_t i5 = *(const uint32_t *)(a + 20u);
        uint32_t i6 = *(const uint32_t *)(a + 24u);
        uint32_t i8 = *(const uint32_t *)(a + 32u);
        if ((i4 & 0xfffffc00u) == 0xb8013000u &&
            (i5 & 0xfffffc00u) == 0xb801b000u &&
            (i6 & 0xfffffc00u) == 0xb801f000u &&
            (i8 & 0xfffffc00u) == 0xb800b000u) {
            g_pd_alloc_pos_fn = t;
            return;
        }
    }
}

__attribute__((used)) static int ld_pd_prepare_pill(
    uintptr_t stack, uintptr_t constraints, uintptr_t thread,
    uintptr_t pp, uintptr_t heap_base, struct ld_pd_pill_params *out)
{
    if (!g_home_options.phone_dock_glass) return 0;
    if (__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return 0;
    if (!g_pd_installed || !out) return 0;
    if ((stack & 1u) == 0 || (constraints & 1u) == 0 || !thread || !pp) return 0;

    /* Ensure TLAB has >= 512 bytes free so all 5 allocations hit lock-free fast path (zero GC). */
    uintptr_t tlab_top = 0, tlab_end = 0;
    if (!ld_read_heap(thread + 0x60u, &tlab_top, 8) ||
        !ld_read_heap(thread + 0x68u, &tlab_end, 8) ||
        tlab_end <= tlab_top || (tlab_end - tlab_top) < 0x200u) {
        return 0;
    }

    /* Validate Stack.children has room (len < cap). */
    uint32_t children_cptr = 0;
    if (!ld_read_heap(stack + 0xbu, &children_cptr, 4) || (children_cptr & 1u) == 0) return 0;
    uintptr_t children = ((uint64_t)heap_base << 32) | (uintptr_t)children_cptr;
    uint32_t len_smi = 0, data_cptr = 0;
    if (!ld_read_heap(children + 0xbu, &len_smi, 4) || (len_smi & 1u) != 0) return 0;
    if (!ld_read_heap(children + 0xfu, &data_cptr, 4) || (data_cptr & 1u) == 0) return 0;
    uintptr_t data = ((uint64_t)heap_base << 32) | (uintptr_t)data_cptr;
    uint32_t cap_smi = 0;
    if (!ld_read_heap(data + 0xbu, &cap_smi, 4) || (cap_smi & 1u) != 0) return 0;
    uint32_t len = len_smi >> 1;
    uint32_t cap = cap_smi >> 1;
    if (len == 0 || len >= cap || cap > 32) return 0;

    double max_w = 0.0;
    if (!ld_read_heap(constraints + 0xfu, &max_w, 8) || !isfinite(max_w) ||
        max_w < 200.0 || max_w > 1200.0) {
        return 0;
    }

    uintptr_t style_obj = 0;
    if (!ld_read_heap(pp + g_pd_style_pp_off, &style_obj, 8) || (style_obj & 1u) == 0) return 0;

    double width = max_w - 28.0;
    double height = 78.0;
    double radius = 28.0;
    double bottom = 22.0;

    if (g_home_options.dock.enabled) {
        if (isfinite(g_home_options.dock.width)) {
            width += g_home_options.dock.width;
            if (width < 40.0) width = 40.0;
            if (width > max_w) width = max_w;
        }
        double dh = 0.0;
        if (isfinite(g_home_options.dock.height)) {
            double h_new = height + g_home_options.dock.height;
            if (h_new < 24.0) h_new = 24.0;
            if (h_new > 240.0) h_new = 240.0;
            dh = h_new - height;
            height = h_new;
        }
        if (isfinite(g_home_options.dock.corner)) {
            radius += g_home_options.dock.corner;
            if (radius < 1.0) radius = 1.0;
            if (radius > height * 0.5) radius = height * 0.5;
        }
        /* Shift only the Phone Dock (both glass pill and Hotseat icons) by dock.bottom
         * without touching GridController.hotSeatsMarginBottom, so the page indicator
         * and workspace layout remain fixed. */
        double db = 0.0;
        if (isfinite(g_home_options.dock.bottom)) {
            db = g_home_options.dock.bottom;
        }
        bottom = bottom - dh * 0.5 + db;
        if (bottom < -60.0) bottom = -60.0;
        if (bottom > 200.0) bottom = 200.0;

        if (db != 0.0) {
            /* Walk Stack.children[len-1] -> wrap2 (+0xb/+0xf) -> wrap1 (+0xf)
             * -> CustomMultiChildLayout (+0xf) -> HotSeatLayoutDelegate (+0x2b = dockOffsetY). */
            uint32_t item_cptr = 0;
            if (ld_read_heap(data + 0xfu + (size_t)(len - 1u) * 4u, &item_cptr, 4) &&
                (item_cptr & 1u) != 0) {
                uintptr_t wrap2 = ((uint64_t)heap_base << 32) | (uintptr_t)item_cptr;
                static const size_t w2_offs[2] = { 0xbu, 0xfu };
                for (size_t k = 0; k < 2; k++) {
                    uint32_t w1_c = 0, mc_c = 0, del_c = 0;
                    if (!ld_read_heap(wrap2 + w2_offs[k], &w1_c, 4) || (w1_c & 1u) == 0) continue;
                    uintptr_t wrap1 = ((uint64_t)heap_base << 32) | (uintptr_t)w1_c;
                    if (!ld_read_heap(wrap1 + 0xfu, &mc_c, 4) || (mc_c & 1u) == 0) continue;
                    uintptr_t mc = ((uint64_t)heap_base << 32) | (uintptr_t)mc_c;
                    if (!ld_read_heap(mc + 0xfu, &del_c, 4) || (del_c & 1u) == 0) continue;
                    uintptr_t del = ((uint64_t)heap_base << 32) | (uintptr_t)del_c;
                    double oy = 0.0;
                    if (ld_read_heap(del + 0x2bu, &oy, 8) && isfinite(oy) &&
                        oy >= -100.0 && oy <= 200.0) {
                        double new_oy = oy - db;
                        memcpy((void *)(del + 0x2bu), &new_oy, 8);
                        static unsigned oy_logs = 0;
                        if (__atomic_fetch_add(&oy_logs, 1u, __ATOMIC_RELAXED) < 4) {
                            ld_blog("phone_dock_glass: shifted icon dockOffsetY %.1f->%.1f (db=%.1f w2+0x%zx)",
                                    oy, new_oy, db, w2_offs[k]);
                        }
                        break;
                    }
                }
            }
        }
    }

    out->horiz_inset = (max_w - width) * 0.5;
    out->bottom = bottom;
    out->height = height;
    out->radius = radius;
    out->style_obj = style_obj;
    out->alloc_glass = g_pd_alloc_glass_fn;
    out->alloc_double = g_pd_alloc_double_fn;
    out->alloc_pos = g_pd_alloc_pos_fn;
    return 1;
}

__attribute__((used)) static void ld_pd_wire_and_prepend(
    uintptr_t stack, uintptr_t heap_base,
    const struct ld_pd_pill_objs *o, const struct ld_pd_pill_params *p)
{
    if (!o || !p) return;
    if ((o->glass & 1u) == 0 || (o->horiz_box & 1u) == 0 ||
        (o->bottom_box & 1u) == 0 || (o->height_box & 1u) == 0 ||
        (o->pos & 1u) == 0) {
        return;
    }

    double opacity = 1.0;
    uint64_t zero64 = 0;
    uint32_t style_cptr = (uint32_t)p->style_obj;
    uint32_t glass_cptr = (uint32_t)o->glass;
    uint32_t horiz_cptr = (uint32_t)o->horiz_box;
    uint32_t bottom_cptr = (uint32_t)o->bottom_box;
    uint32_t height_cptr = (uint32_t)o->height_box;
    uint32_t pos_cptr = (uint32_t)o->pos;

    /* 1. _DockGlassWidget (mode==0 full Liquid Glass) */
    memcpy((void *)(o->glass + 0xbu), &p->radius, 8);
    memcpy((void *)(o->glass + 0x13u), &style_cptr, 4);
    memcpy((void *)(o->glass + 0x17u), &opacity, 8);
    memcpy((void *)(o->glass + 0x1fu), &zero64, 8);

    /* 2. BoxedDouble horiz_inset, bottom, height */
    memcpy((void *)(o->horiz_box + 0x7u), &p->horiz_inset, 8);
    memcpy((void *)(o->bottom_box + 0x7u), &p->bottom, 8);
    memcpy((void *)(o->height_box + 0x7u), &p->height, 8);

    /* 3. Positioned(left: horiz, right: horiz, bottom: bottom, height: height, child: glass) */
    memcpy((void *)(o->pos + 0xbu), &glass_cptr, 4);
    memcpy((void *)(o->pos + 0x13u), &horiz_cptr, 4);
    memcpy((void *)(o->pos + 0x1bu), &horiz_cptr, 4);
    memcpy((void *)(o->pos + 0x1fu), &bottom_cptr, 4);
    memcpy((void *)(o->pos + 0x27u), &height_cptr, 4);

    /* 4. Prepend Positioned to Stack.children */
    uint32_t children_cptr = 0;
    if (!ld_read_heap(stack + 0xbu, &children_cptr, 4) || (children_cptr & 1u) == 0) return;
    uintptr_t children = ((uint64_t)heap_base << 32) | (uintptr_t)children_cptr;
    uint32_t len_smi = 0, data_cptr = 0;
    if (!ld_read_heap(children + 0xbu, &len_smi, 4)) return;
    if (!ld_read_heap(children + 0xfu, &data_cptr, 4) || (data_cptr & 1u) == 0) return;
    uintptr_t data = ((uint64_t)heap_base << 32) | (uintptr_t)data_cptr;
    uint32_t cap_smi = 0;
    if (!ld_read_heap(data + 0xbu, &cap_smi, 4)) return;
    uint32_t len = len_smi >> 1;
    uint32_t cap = cap_smi >> 1;
    if (len >= cap) return;

    for (uint32_t i = len; i > 0; i--) {
        uint32_t prev = 0;
        memcpy(&prev, (void *)(data + 0xfu + (size_t)(i - 1u) * 4u), 4);
        memcpy((void *)(data + 0xfu + (size_t)i * 4u), &prev, 4);
    }
    memcpy((void *)(data + 0xfu), &pos_cptr, 4);
    uint32_t new_len_smi = (len + 1u) << 1;
    memcpy((void *)(children + 0xbu), &new_len_smi, 4);

    static unsigned logs = 0;
    if (__atomic_fetch_add(&logs, 1u, __ATOMIC_RELAXED) < 4) {
        ld_blog("phone_dock_glass: injected Positioned lr=%.1f h=%.1f r=%.1f mb=%.1f children=%u->%u",
                p->horiz_inset, p->height, p->radius, p->bottom, len, len + 1u);
    }
}

__attribute__((naked)) static void repl_pd_hotseat_stack(void) {
    /* Stack layout on native sp (192 bytes = 0xc0):
     *   [sp, #0x00..0x3f]: struct ld_pd_pill_params (64 bytes)
     *     +0x28: alloc_glass, +0x30: alloc_double, +0x38: alloc_pos
     *   [sp, #0x40..0x67]: struct ld_pd_pill_objs   (40 bytes)
     *   [sp, #0x70]: saved x15 (Dart SP)
     *   [sp, #0x78]: saved constraints
     *   [sp, #0x80]: saved Stack (ret x0)
     *   [sp, #0xa0]: saved x19, x20
     *   [sp, #0xb0]: saved x29, x30
     */
    __asm__ volatile(
        "sub sp, sp, #0xc0\n"
        "stp x29, x30, [sp, #0xb0]\n"
        "stp x19, x20, [sp, #0xa0]\n"
        "ldr x19, [x15, #0x0]\n"
        "str x19, [sp, #0x78]\n"
        "adrp x16, g_orig_pd_hotseat_stack\n"
        "ldr x16, [x16, :lo12:g_orig_pd_hotseat_stack]\n"
        "blr x16\n"
        "str x0, [sp, #0x80]\n"
        "str x15, [sp, #0x70]\n"
        "ldr x1, [sp, #0x78]\n"
        "mov x2, x26\n"
        "mov x3, x27\n"
        "mov x4, x28\n"
        "mov x5, sp\n"
        "bl ld_pd_prepare_pill\n"
        "cbz w0, 1f\n"
        "ldr x15, [sp, #0x70]\n"
        "ldr x16, [sp, #0x28]\n" /* alloc_glass */
        "blr x16\n"
        "str x0, [sp, #0x40]\n"
        "ldr x16, [sp, #0x30]\n" /* alloc_double (horiz) */
        "blr x16\n"
        "str x0, [sp, #0x48]\n"
        "ldr x16, [sp, #0x30]\n" /* alloc_double (bottom) */
        "blr x16\n"
        "str x0, [sp, #0x50]\n"
        "ldr x16, [sp, #0x30]\n" /* alloc_double (height) */
        "blr x16\n"
        "str x0, [sp, #0x58]\n"
        "ldr x16, [sp, #0x38]\n" /* alloc_pos */
        "blr x16\n"
        "str x0, [sp, #0x60]\n"
        "str x15, [sp, #0x70]\n"
        "ldr x0, [sp, #0x80]\n"
        "mov x1, x28\n"
        "add x2, sp, #0x40\n"
        "mov x3, sp\n"
        "bl ld_pd_wire_and_prepend\n"
        "1:\n"
        "ldr x15, [sp, #0x70]\n"
        "ldr x0, [sp, #0x80]\n"
        "ldp x19, x20, [sp, #0xa0]\n"
        "ldp x29, x30, [sp, #0xb0]\n"
        "add sp, sp, #0xc0\n"
        "ret\n"
    );
}

__attribute__((used)) static double ld_pd_adjust_pos_x(double x, int64_t count, int64_t index) {
    if (!g_home_options.dock.enabled || !isfinite(g_home_options.dock.spacing) ||
        g_home_options.dock.spacing == 0.0) {
        return x;
    }
    if (__atomic_load_n(&g_ws_is_laptop_mode, __ATOMIC_RELAXED)) return x;
    if (!isfinite(x) || count <= 1 || count > 10 || index < 0 || index >= count) return x;
    double center = (double)(count - 1) * 0.5;
    double shifted = x + ((double)index - center) * (2.0 * g_home_options.dock.spacing);
    return isfinite(shifted) ? shifted : x;
}

__attribute__((naked)) static void repl_pd_calc_pos_x(void) {
    __asm__ volatile(
        "sub sp, sp, #0x30\n"
        "stp x29, x30, [sp, #0x20]\n"
        "stp x2, x3, [sp, #0x10]\n"
        "adrp x16, g_orig_pd_calc_pos_x\n"
        "ldr x16, [x16, :lo12:g_orig_pd_calc_pos_x]\n"
        "blr x16\n"
        "str x15, [sp, #0x00]\n"
        "ldp x0, x1, [sp, #0x10]\n"
        "bl ld_pd_adjust_pos_x\n"
        "ldr x15, [sp, #0x00]\n"
        "ldp x29, x30, [sp, #0x20]\n"
        "add sp, sp, #0x30\n"
        "ret\n"
    );
}

static void ld_install_phone_dock_glass(const struct native_api *api, const ld_lib_info *lib) {
    if (!api || !api->hook || !lib) return;
    if (!g_home_options.phone_dock_glass && !g_home_options.dock.enabled) return;
    if (!g_home_options.phone_dock_targets_present) {
        ld_blog("phone_dock_glass: targets not resolved, skipped");
        return;
    }
    if (g_pd_installed) return;

    uintptr_t items_rt = 0, bg_layout_rt = 0, strat_rt = 0, content_rt = 0;
    if (!ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.phone_dock_vaddr[0], &items_rt) ||
        !ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.phone_dock_vaddr[1], &bg_layout_rt) ||
        !ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.phone_dock_vaddr[2], &strat_rt) ||
        !ld_vaddr_to_runtime(lib, (uintptr_t)g_home_options.phone_dock_vaddr[3], &content_rt)) {
        ld_blog("phone_dock_glass: vaddr->runtime failed");
        return;
    }

    uintptr_t stack_closure = ld_pd_find_hotseat_stack_closure(items_rt);
    uintptr_t calc_pos_x_rt = ld_pd_decode_calc_pos_x(bg_layout_rt, g_home_options.phone_dock_size[1]);
    ld_pd_decode_strategy(strat_rt);
    ld_pd_decode_positioned(content_rt, g_home_options.phone_dock_size[3]);

    int r_stack = -1, r_pos = -1;
    if (g_home_options.phone_dock_glass && stack_closure && g_pd_alloc_glass_fn &&
        g_pd_alloc_double_fn && g_pd_alloc_pos_fn && g_pd_style_pp_off &&
        executable_range(stack_closure, 16)) {
        r_stack = api->hook((void *)stack_closure, (void *)repl_pd_hotseat_stack, &g_orig_pd_hotseat_stack);
        if (r_stack == 0 && g_orig_pd_hotseat_stack) pin_installed_hook(stack_closure);
    }
    if (g_home_options.dock.enabled && calc_pos_x_rt && executable_range(calc_pos_x_rt, 16)) {
        r_pos = api->hook((void *)calc_pos_x_rt, (void *)repl_pd_calc_pos_x, &g_orig_pd_calc_pos_x);
        if (r_pos == 0 && g_orig_pd_calc_pos_x) pin_installed_hook(calc_pos_x_rt);
    }

    g_pd_installed = (r_stack == 0 || r_pos == 0) ? 1 : 0;
    ld_blog("phone_dock_glass: installed=%d closure=%p pos_x=%p glass=%p pos=%p style_pp=0x%zx r=(%d,%d)",
            g_pd_installed, (void *)stack_closure, (void *)calc_pos_x_rt,
            (void *)g_pd_alloc_glass_fn, (void *)g_pd_alloc_pos_fn,
            g_pd_style_pp_off, r_stack, r_pos);
}

#endif
