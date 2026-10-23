#include <stdint.h>
#include <stdio.h>
#include "../native/free_place_wrapper.h"
static volatile int enabled __attribute__((used))=1;
static volatile unsigned calls __attribute__((used));
__attribute__((naked)) static void fallback(void) { __asm__ volatile("mov x0, #777\n ret"); }
static void *original __attribute__((used))=(void *)fallback;
LD_FREE_PLACE_WRAPPER(replacement,enabled,original,calls)
static uint64_t fake[12];
__attribute__((naked)) static uint64_t invoke(void *object) {
 __asm__ volatile("stp x22, x30, [sp, #-16]!\n mov x1, x0\n mov x22, #65536\n bl replacement\n ldp x22, x30, [sp], #16\n ret");
}
int main(void) {
 fake[6]=100;
 if(invoke((char *)fake+1)!=65568 || calls!=1) return 1;
 fake[6]=101;
 if(invoke((char *)fake+1)!=777) return 2;
 fake[6]=100; enabled=0;
 if(invoke((char *)fake+1)!=777) return 3;
 puts("PASS HOME rectangular placement route, other surfaces and disabled fallback");
}
