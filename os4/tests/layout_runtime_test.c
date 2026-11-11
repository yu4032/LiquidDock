#include <assert.h>
#include <stdio.h>
#include "../native/dart_heap.h"
#undef LD_DART_OBSERVER
#define LD_DART_OBSERVER(...)
#define LD_LAYOUT_FILE "/tmp/ld-runtime-batch-fixture.bin"
static int g_grid_cols=4,g_grid_rows=8;
static void *g_orig_orientation;
static void ld_blog(const char *format,...) { (void)format; }
#include "../native/layout_runtime.h"
static uint64_t heap[18][64];
static uintptr_t object(int i) { heap[i][0]=0x5c<<12; return (uintptr_t)&heap[i][0]+1; }
static void word(uintptr_t p,size_t o,uint32_t n) { memcpy((void *)(p+o),&n,4); }
static void integer(uintptr_t p,size_t o,int64_t n) { memcpy((void *)(p+o),&n,8); }
int main(void) {
 uintptr_t obj[18]; for(int i=0;i<18;i++) obj[i]=object(i);
 integer(obj[0],7,4); integer(obj[0],15,8);
 word(obj[1],15,(uint32_t)obj[2]); word(obj[2],11,4); word(obj[2],15,(uint32_t)obj[3]);
 for(int i=0;i<2;i++) {
  word(obj[3],15+i*4,(uint32_t)obj[4+i]); word(obj[4+i],0x1b,(uint32_t)obj[6+i]);
  word(obj[6+i],0xdb,(uint32_t)obj[8+i]); word(obj[8+i],11,i?2:4); word(obj[8+i],15,(uint32_t)obj[10+i]);
 }
 word(obj[10],15,(uint32_t)obj[12]); word(obj[10],19,(uint32_t)obj[13]); word(obj[11],15,(uint32_t)obj[14]);
 struct ld_layout_item source[]={{1,2,0,6,2,1},{2,2,2,0,1,1},{3,3,3,7,1,1}};
 struct ld_layout_item target[]={{1,2,1,2,2,1},{2,2,0,2,1,1},{3,3,7,3,1,1}};
 for(int i=0;i<3;i++) {
  word(obj[12+i],7,(uint32_t)obj[15+i]); integer(obj[12+i],11,source[i].x); integer(obj[12+i],19,source[i].y);
  integer(obj[15+i],7,source[i].id); integer(obj[15+i],0x1f,source[i].sx); integer(obj[15+i],0x27,source[i].sy);
  integer(obj[15+i],0x2f,source[i].screen); integer(obj[15+i],0x37,source[i].x); integer(obj[15+i],0x3f,source[i].y); integer(obj[15+i],0x47,-100);
 }
 struct ld_layout_memory m={.columns=4,.rows=8,.active_direction=0,.count={3,3}};
 memcpy(m.items[0],source,sizeof source); memcpy(m.items[1],target,sizeof target); assert(ld_layout_save(LD_LAYOUT_FILE,&m));
 struct ld_dart_context c={0}; c.x[1]=obj[0]; c.x[2]=obj[1]; c.x[3]=8; c.x[5]=4; c.x[28]=obj[0]>>32;
 g_layout_hooks_ready=1; g_process_resume=0x1234; g_orig_process_mid=(void *)0x2222;
 assert(!g_restore_ready); ld_observe_process(&c); assert(g_restore_ready);
 for(int i=0;i<3;i++) {
  assert(ld_heap_i64(obj[12+i],11)==target[i].x && ld_heap_i64(obj[12+i],19)==target[i].y);
  assert(ld_heap_i64(obj[15+i],0x37)==target[i].x && ld_heap_i64(obj[15+i],0x3f)==target[i].y);
  assert(ld_heap_i64(obj[15+i],0x2f)==source[i].screen);
 }
 ld_process_mid(&c); assert(g_process_mid_next==(void *)g_process_resume);
 assert(ld_layout_load(LD_LAYOUT_FILE,&m) && m.active_direction==1 && m.count[1]==3);
 g_layout_hooks_ready=0; ld_observe_process(&c); ld_process_mid(&c); assert(g_process_mid_next==g_orig_process_mid);
 unlink(LD_LAYOUT_FILE); (void)g_orig_orientation; (void)g_layout_mid_ready;
 puts("PASS all pages and model coordinates mapped in one batch before vendor continuation");
}
