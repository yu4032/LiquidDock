#include <stdint.h>
#include <stdio.h>
#include "../native/dart_observer.h"

static unsigned calls;
static uint64_t seen[4] __attribute__((used));
static unsigned char dart_stack[65536] __attribute__((aligned(16)));
__attribute__((used)) static void observer(struct ld_dart_context *c) {
    calls++;
    if (c->x[0] != 101 || c->x[1] != 102 || c->x[2] != 103) calls = 999;
    __asm__ volatile("mov x15, #987\n fmov d0, x15" ::: "x15", "d0");
}
static void *original __attribute__((used));
LD_DART_OBSERVER(test_wrapper, observer, original)

__attribute__((naked)) static void target(void) {
    __asm__ volatile(
        "adrp x16, seen\n add x16, x16, :lo12:seen\n"
        "stp x0, x1, [x16]\n str x2, [x16, #16]\n"
        "str x15, [x16, #24]\n ret\n");
}
__attribute__((naked)) static void invoke(void *top) {
    __asm__ volatile(
        "stp x29, x30, [sp, #-16]!\n"
        "mov x15, x0\n mov x0, #101\n mov x1, #102\n mov x2, #103\n"
        "bl test_wrapper\n ldp x29, x30, [sp], #16\n ret\n");
}
int main(void) {
    original = (void *)target;
    void *top = dart_stack + sizeof dart_stack - 8;
    invoke(top);
    if (calls != 1 || seen[0] != 101 || seen[1] != 102 || seen[2] != 103
            || seen[3] != (uintptr_t)top) return 1;
    puts("PASS Dart observer preserves arguments, Dart SP and native return stack");
    return 0;
}
