#include <stdint.h>
#include <stdio.h>
#include "../native/dart_post_observer.h"

static void *expected_object = (void *)(uintptr_t)0x12345678;
static volatile uintptr_t seen_result;
static volatile uint64_t seen_double_bits;
static volatile uintptr_t seen_dart_sp;
static volatile unsigned callback_calls;
static unsigned char dart_stack[65536] __attribute__((aligned(16)));

__attribute__((used)) static void observer_post(
        struct ld_dart_context *c, uintptr_t result, uint64_t fp_result)
{
    callback_calls++;
    if (!c || c->x[1] != 102) callback_calls = 999;
    seen_result = result;
    seen_double_bits = fp_result;
    __asm__ volatile("mov x15, #987" ::: "x15");
}

static void *original __attribute__((used));
LD_DART_POST_OBSERVER(test_post_wrapper, observer_post, original)

__attribute__((naked)) static void target(void)
{
    __asm__ volatile(
        "adrp x16, expected_object\n"
        "ldr x0, [x16, :lo12:expected_object]\n"
        "ldr x0, [x0]\n"
        "movz x16, #0x3ff8, lsl #48\n"
        "fmov d0, x16\n"
        "ret\n");
}

__attribute__((naked)) static void invoke(void *top)
{
    __asm__ volatile(
        "stp x29, x30, [sp, #-16]!\n"
        "mov x15, x0\n"
        "mov x0, #101\n"
        "mov x1, #102\n"
        "bl test_post_wrapper\n"
        "adrp x16, seen_dart_sp\n"
        "add x16, x16, :lo12:seen_dart_sp\n"
        "str x15, [x16]\n"
        "ldp x29, x30, [sp], #16\n"
        "ret\n");
}

int main(void)
{
    original = (void *)target;
    uintptr_t top = (uintptr_t)(dart_stack + sizeof dart_stack - 8);
    invoke((void *)top);
    if (callback_calls != 1 || seen_result != (uintptr_t)expected_object
            || seen_double_bits != 0x3ff8000000000000ULL
            || seen_dart_sp != top) return 1;
    puts("PASS Dart post-observer preserves object/double results and restores Dart SP/LR");
    return 0;
}
