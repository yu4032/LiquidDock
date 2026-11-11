#ifndef LD_FREE_PLACE_WRAPPER_H
#define LD_FREE_PLACE_WRAPPER_H
/* Select the vendor rectangular drop rule for custom HOME grids. Keep the
 * native squeeze engine and all occupancy/collision checks intact. */
#define LD_FREE_PLACE_WRAPPER(name, enabled, original, counter) \
__attribute__((naked,used)) static void name(void) { \
    __asm__ volatile( \
        "adrp x16, " #enabled "\n" \
        "ldr w16, [x16, :lo12:" #enabled "]\n" \
        "cbz w16, 1f\n" \
        "ldur x16, [x1, #0x2f]\n" \
        "cmp x16, #100\n" \
        "b.ne 1f\n" \
        "adrp x16, " #counter "\n" \
        "ldr w17, [x16, :lo12:" #counter "]\n" \
        "add w17, w17, #1\n" \
        "str w17, [x16, :lo12:" #counter "]\n" \
        "add x0, x22, #0x20\n" \
        "ret\n" \
        "1: adrp x16, " #original "\n" \
        "ldr x16, [x16, :lo12:" #original "]\n" \
        "br x16\n"); \
}
/* 8583 shares this parity-only predicate between rectangular and swap rules.
 * Bounds and collision resolution belong to the callers and remain intact.
 * x2/x3 are coordinates; x5/x6 are spans, all unboxed Dart integers. */
#define LD_WIDGET_LATTICE_WRAPPER(name, enabled, original) \
__attribute__((naked,used)) static void name(void) { \
    __asm__ volatile( \
        "adrp x16, " #enabled "\n" \
        "ldr w16, [x16, :lo12:" #enabled "]\n" \
        "cbz w16, 1f\n" \
        "add x0, x22, #0x20\n" \
        "ret\n" \
        "1: adrp x16, " #original "\n" \
        "ldr x16, [x16, :lo12:" #original "]\n" \
        "br x16\n"); \
}
#endif
