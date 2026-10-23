#include <stdint.h>
#include <stdio.h>
#include "../native/free_place_wrapper.h"
static volatile int enabled __attribute__((used)) = 1;
/* Exact parity restrictions from the attested 8583 isLegalXY method. */
__attribute__((naked,used)) static void stock(void) {
 __asm__ volatile(
 "cmp x5,#1\n b.le 1f\n tbz w2,#0,1f\n b 9f\n"
 "1: cmp x6,#1\n b.le 2f\n tbz w3,#0,2f\n b 9f\n"
 "2: cmp x5,#4\n b.ne 3f\n cbnz x2,9f\n"
 "3: cmp x5,#4\n b.ne 8f\n cbz x3,8f\n cmp x3,#2\n b.ne 9f\n"
 "8: add x0,x22,#0x20\n ret\n 9: add x0,x22,#0x30\n ret");
}
static void *original __attribute__((used)) = (void *)stock;
LD_WIDGET_LATTICE_WRAPPER(replacement,enabled,original)
__attribute__((naked)) static uint64_t invoke(long x,long y,long sx,long sy) {
 __asm__ volatile("stp x22,x30,[sp,#-16]!\n mov x5,x2\n mov x6,x3\n mov x2,x0\n mov x3,x1\n mov x22,#65536\n bl replacement\n ldp x22,x30,[sp],#16\n ret");
}
int main(void) {
 (void)original;
 if(invoke(1,0,2,1)!=65568) { puts("FAIL 2x1 widget rejected at odd column"); return 1; }
 if(invoke(0,1,2,2)!=65568) return 2;
 if(invoke(1,1,4,2)!=65568) return 3;
 enabled=0;
 if(invoke(1,0,2,1)!=65584 || invoke(0,1,2,2)!=65584) return 4;
 if(invoke(0,0,2,2)!=65568) return 5;
 puts("PASS odd-cell widgets and disabled stock fallback");
}
