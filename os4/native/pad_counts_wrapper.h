/* PadRowColHandler at +0x80: x2 is raw rows; x0 is raw columns.
 * The vendor has selected portrait/split (6 rows x 4 columns) or landscape
 * (4 rows x 6 columns). Substitute module landscape counts before the ORIGINAL
 * tagging/copyWith instructions. No calls, stack access or heap mutations here.
 */
#ifndef LD_PAD_COUNTS_WRAPPER_H
#define LD_PAD_COUNTS_WRAPPER_H
#define LD_PAD_COUNTS_WRAPPER(name, cols, rows, orig, pool, trace) \
__attribute__((naked, used)) static void name(void) { \
    __asm__ volatile( \
        "mrs x3, nzcv\n" \
        "adrp x10, " #trace "\n" \
        "add x10, x10, :lo12:" #trace "\n" \
        "ldr w11, [x10]\n" \
        "add w11, w11, #1\n" \
        "str w11, [x10]\n" \
        "str w0, [x10, #4]\n" \
        "str w2, [x10, #8]\n" \
        "adrp x16, " #pool "\n" \
        "add x16, x16, :lo12:" #pool "\n" \
        "str x27, [x16]\n" \
        "adrp x16, " #cols "\n" \
        "add x16, x16, :lo12:" #cols "\n" \
        "ldr w16, [x16]\n" \
        "adrp x17, " #rows "\n" \
        "add x17, x17, :lo12:" #rows "\n" \
        "ldr w17, [x17]\n" \
        "cmp x2, #6\n" \
        "b.ne 1f\n" \
        "cbz w16, 2f\n" \
        "mov x2, x16\n" \
        "2:\n" \
        "cbz w17, 3f\n" \
        "mov x0, x17\n" \
        "b 3f\n" \
        "1:\n" \
        "cbz w17, 4f\n" \
        "mov x2, x17\n" \
        "4:\n" \
        "cbz w16, 3f\n" \
        "mov x0, x16\n" \
        "3:\n" \
        "str w2, [x10, #12]\n" \
        "str w0, [x10, #16]\n" \
        "msr nzcv, x3\n" \
        "adrp x16, " #orig "\n" \
        "add x16, x16, :lo12:" #orig "\n" \
        "ldr x16, [x16]\n" \
        "br x16\n"); \
}
#endif
