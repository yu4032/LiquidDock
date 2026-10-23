#ifndef LD_DOCK_Y_DIRECT_H
#define LD_DOCK_Y_DIRECT_H
#include <math.h>
#include <stddef.h>
#include <stdint.h>

/*
 * Experimental direct Dart AOT Y hook.
 *
 * Research device: HotseatLayerGetxController.calculateDockOffsetY
 * 0xa8a03c / 240 bytes, returns a raw double in d0. On the fuxi 8583 ELF
 * this is ATTEMPTED only after whole-ELF identity verification and a
 * recognisable function prologue+tail match; no guessed fallback addresses.
 *
 * Unlike GridConfig Insets.bottom, this is the vendor's own Dock Y result.
 * Screen Y grows downward, so a positive GUI bottom distance subtracts Y.
 */
#define LD_DOCK_Y_VADDR 0xa8a03cu
#define LD_DOCK_Y_SIZE 240u
#define LD_DOCK_Y_TAIL_WORD 56u
static int ld_dock_y_delta_for_setting(double setting, double *out)
{
    if(!out || !isfinite(setting) || setting < -30.0 || setting > 40.0) return 0;
    *out=-setting;
    return 1;
}
static int ld_dock_y_matches(const uint32_t *words, size_t bytes)
{
    if(!words || bytes<LD_DOCK_Y_SIZE) return 0;
    return words[0]==0xa9bf79fdu &&
        words[LD_DOCK_Y_TAIL_WORD+0]==0x1e643820u && /* fsub d0,d1,d4 */
        words[LD_DOCK_Y_TAIL_WORD+1]==0xaa1d03efu && /* mov x15,x29 */
        words[LD_DOCK_Y_TAIL_WORD+2]==0xa8c179fdu && /* ldp x29,x30,[x15],#16 */
        words[LD_DOCK_Y_TAIL_WORD+3]==0xd65f03c0u;   /* ret */
}

#ifndef LD_DOCK_Y_TEST_ONLY
static void *g_orig_dock_y __attribute__((used));
static double g_dock_y_delta __attribute__((used));
static volatile unsigned g_dock_y_calls __attribute__((used));
static bool g_dock_y_installed;

/*
 * x15 is the Dart managed stack pointer, not the native hardware SP.
 * The original function's args are in registers and its return is d0.
 * Save the Dart LR/frame in Dart's own frame convention, call original,
 * then add a signed delta to the returned double without allocating.
 * x9/x10 are caller-saved scratch; their count is observational only.
 */
__attribute__((naked,used)) static void repl_dock_y_direct(void)
{
    __asm__ volatile(
        "stp x29, x30, [x15, #-0x10]!\n"
        "mov x29, x15\n"
        "adrp x16, g_orig_dock_y\n"
        "ldr x16, [x16, :lo12:g_orig_dock_y]\n"
        "blr x16\n"
        "adrp x9, g_dock_y_calls\n"
        "add x9, x9, :lo12:g_dock_y_calls\n"
        "ldr w10, [x9]\n"
        "add w10, w10, #1\n"
        "str w10, [x9]\n"
        "adrp x16, g_dock_y_delta\n"
        "ldr d1, [x16, :lo12:g_dock_y_delta]\n"
        "fadd d0, d0, d1\n"
        "mov x15, x29\n"
        "ldp x29, x30, [x15], #0x10\n"
        "ret\n"
    );
}
#endif
#endif
