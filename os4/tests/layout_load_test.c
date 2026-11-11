#include <assert.h>
#include <stdarg.h>
#include "../native/layout_memory.h"
#include "../native/dart_heap.h"
#undef LD_DART_OBSERVER
#define LD_DART_OBSERVER(...)
#define LD_LAYOUT_FILE "/tmp/ld-load-fixture.bin"
static int g_grid_cols=4,g_grid_rows=8,g_layout_hooks_ready=1;
int g_runtime_columns,g_runtime_rows,g_runtime_direction;
static int g_initial_restore,g_initial_columns,g_initial_rows,g_initial_direction;
static void ld_blog(const char *fmt,...) { (void)fmt; }
#include "../native/layout_load.h"
#include "../native/layout_save.h"
static uint64_t fixture[12][40];
static uintptr_t obj(int i,unsigned cid) { fixture[i][0]=cid<<12; return (uintptr_t)&fixture[i][0]+1; }
static void word(uintptr_t p,int offset,int32_t value) { memcpy((void *)(p+offset),&value,4); }
static void number(uintptr_t p,int offset,int value) { word(p,offset,value*2); }
int main(void) {
 uintptr_t list=obj(0,0x5c),array=obj(1,0x59),a=obj(2,0xf26),b=obj(3,0xf26),dock=obj(4,0xf26),grid=obj(5,0x500);
 number(list,0xb,3); word(list,0xf,(uint32_t)array);
 word(array,0xf,(uint32_t)a); word(array,0x13,(uint32_t)b); word(array,0x17,(uint32_t)dock);
 uintptr_t items[]={a,b,dock};
 for(int i=0;i<3;i++) { number(items[i],7,i+1); number(items[i],0x1f,2); number(items[i],0x2b,i==2?-101:-100); number(items[i],0x17,i==0?2:1); number(items[i],0x1b,1); }
 number(a,0x23,1); number(a,0x27,10); number(b,0x23,7); number(b,0x27,10);
 int64_t cols=4,rows=8; memcpy((void *)(grid+0x1b),&cols,8); memcpy((void *)(grid+0x23),&rows,8);
 struct ld_dart_context c={0}; c.x[28]=list>>32; c.x[0]=grid; c.x[2]=list;
 ld_filter_grid(&c);
 struct ld_layout_memory memory={.columns=4,.rows=8,.active_direction=1,.count={2,2},.items={{{1,2,1,3,2,1},{2,2,0,7,1,1}},{{1,2,1,1,2,1},{2,2,7,3,1,1}}}};
 assert(ld_layout_save(LD_LAYOUT_FILE,&memory));
 g_load_columns=8; g_load_rows=4; g_runtime_columns=4; g_runtime_rows=8; g_runtime_direction=0;
 ld_load_items(&c);
 assert(g_load_prepared && g_initial_restore==1 && g_initial_columns==4 && g_initial_rows==8);
 assert(ld_dto_number(&c,a,0x23)==1 && ld_dto_number(&c,a,0x27)==3);
 assert(ld_dto_number(&c,b,0x23)==0 && ld_dto_number(&c,b,0x27)==7);
 assert(ld_dto_number(&c,dock,0x23)==0 && ld_dto_number(&c,dock,0x2b)==-101);
 assert(ld_layout_load(LD_LAYOUT_FILE,&memory) && memory.items[1][1].x==7 && memory.active_direction==0);
 struct ld_layout_item deletion={.id=1,.screen=-1};
 g_load_prepared=0; ld_capture_saved_items(&deletion,1);
 assert(ld_layout_load(LD_LAYOUT_FILE,&memory) && memory.count[0]==2);
 g_load_prepared=1; g_layout_hooks_ready=0; ld_capture_saved_items(&deletion,1);
 assert(ld_layout_load(LD_LAYOUT_FILE,&memory) && memory.count[0]==2);
 g_layout_hooks_ready=1;
 uintptr_t mint=obj(6,0x3d),null=obj(7,0x170); int64_t big=INT64_C(2147483648);
 memcpy((void *)(mint+7),&big,8); word(a,7,(uint32_t)mint);
 assert(ld_dto_number(&c,a,7)==big);
 c.x[22]=(uint32_t)null; word(a,0x17,(uint32_t)null);
 struct ld_layout_item malformed; assert(!ld_read_dto_item(&c,a,&malformed));
 number(a,7,1); number(a,0x17,2);
 uintptr_t variables=obj(8,0x501),orientation=obj(9,0x502);
 word(grid,0x8f,(uint32_t)variables); word(variables,0x23,(uint32_t)orientation);
 int64_t index=1; memcpy((void *)(orientation+7),&index,8);
 assert(ld_grid_direction(&c,grid)==1);
 g_runtime_columns=g_runtime_rows=0;
 g_grid_cols=6; g_grid_rows=6; cols=rows=6;
 memcpy((void *)(grid+0x1b),&cols,8); memcpy((void *)(grid+0x23),&rows,8);
 memory=(struct ld_layout_memory){.columns=6,.rows=6,.active_direction=0,.count={2,2},.items={{{1,2,1,3,2,1},{2,2,0,5,1,1}},{{1,2,3,1,2,1},{2,2,5,0,1,1}}}};
 assert(ld_layout_save(LD_LAYOUT_FILE,&memory));
 g_load_prepared=0; ld_filter_grid(&c); ld_load_items(&c);
 assert(g_load_prepared && g_initial_direction==1);
 assert(ld_dto_number(&c,a,0x23)==3 && ld_dto_number(&c,a,0x27)==1);
 number(list,0xb,0); g_load_prepared=0; ld_load_items(&c);
 assert(g_load_prepared && ld_layout_load(LD_LAYOUT_FILE,&memory) && memory.count[1]==0);
 unlink(LD_LAYOUT_FILE);
 puts("PASS full DTO batch restore, unboxed/Smi separation, dock isolation and native-save request");
}
