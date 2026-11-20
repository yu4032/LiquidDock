#include <assert.h>
#include <stdio.h>
#include "../native/dart_heap.h"
#undef LD_DART_OBSERVER
#define LD_DART_OBSERVER(...)
#define LD_LAYOUT_FILE "/tmp/ld-runtime-batch-fixture.bin"
#define LD_WS_LAYOUT_FILE "/tmp/ld-runtime-ws-apps-fixture.bin"
#define LD_WS_DESK_LAYOUT_FILE "/tmp/ld-runtime-ws-desk-fixture.bin"
static int g_grid_cols=4,g_grid_rows=8;
static volatile int g_ws_is_laptop_mode=0;
static void *g_orig_orientation;
static void ld_blog(const char *format,...) { (void)format; }
#include "../native/layout_runtime.h"
static uint64_t heap[18][64];
static uintptr_t object(int i) { heap[i][0]=0x5c<<12; return (uintptr_t)&heap[i][0]+1; }
static void word(uintptr_t p,size_t o,uint32_t n) { memcpy((void *)(p+o),&n,4); }
static void integer(uintptr_t p,size_t o,int64_t n) { memcpy((void *)(p+o),&n,8); }
static void verify_page_fields(void)
{
    /* The fields in this fixture are deliberately shifted from both Launcher builds. */
    const uint32_t process[] = {
        0xb8400043u | (0xefu << 12), 0x8b1c8063u, 0xf80003a3u,
        0xb840b064u, 0x93417c85u,
    };
    uint32_t classifier[] = {
        0xb8400001u | (0xbu << 12), 0x8b1c8021u, 0xf8400020u | (0x57u << 12),
        0xb101a01fu, 0x910082d0u, 0x9100c2d1u, 0x9a910204u,
    };
    const uint32_t quick[] = {
        0xd63f03c0u, 0xb8400001u | (0x23u << 12), 0x8b1c8021u,
        0xb8400020u | (0x43u << 12), 0x8b1c8000u, 0xf9402370u, 0x6b10001fu,
        0x54000080u, 0xaa1d03efu,
    };
    struct ld_page_fields fields = {0};
    assert(ld_decode_page_fields(&fields, process, 5, classifier, 7, quick, 9));
    assert(fields.layout == 0x23 && fields.items == 0xef && fields.model == 0xb &&
           fields.container == 0x57 && fields.quick_call == 0x43);

    classifier[3] = 0;
    fields = (struct ld_page_fields){0};
    assert(!ld_decode_page_fields(&fields, process, 5, classifier, 7, quick, 9));
    assert(!fields.layout);
}

int main(void) {
 verify_page_fields();
 g_page_fields = (struct ld_page_fields){.layout=0x1b,.items=0xdb,.model=7,.container=0x47,.rx_value=0xf};
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

 /* Verify Workstation initial_restore bitmask preserves initial=1 across -100 then -104,
  * and never mutates Normal Home LD_LAYOUT_FILE. */
 struct ld_layout_memory normal_before;
 assert(ld_layout_load(LD_LAYOUT_FILE,&normal_before));
 struct ld_layout_memory ws_desk={.columns=4,.rows=8,.active_direction=1,.count={2,2}};
 struct ld_layout_item ws_d_p[]={{201,5,0,6,1,1},{202,5,3,7,1,1}};
 struct ld_layout_item ws_d_l[]={{201,5,6,1,1,1},{202,5,7,2,1,1}};
 memcpy(ws_desk.items[0],ws_d_p,sizeof ws_d_p); memcpy(ws_desk.items[1],ws_d_l,sizeof ws_d_l);
 assert(ld_layout_save(LD_WS_DESK_LAYOUT_FILE,&ws_desk));

 struct ld_layout_memory ws_apps={.columns=4,.rows=8,.active_direction=1,.count={1,1}};
 struct ld_layout_item ws_a_p[]={{301,2,0,7,1,1}};
 struct ld_layout_item ws_a_l[]={{301,2,5,3,1,1}};
 memcpy(ws_apps.items[0],ws_a_p,sizeof ws_a_p); memcpy(ws_apps.items[1],ws_a_l,sizeof ws_a_l);
 assert(ld_layout_save(LD_WS_LAYOUT_FILE,&ws_apps));

 g_ws_is_laptop_mode=1; g_loaded_ws_known=1; g_loaded_is_ws=1;
 g_layout_hooks_ready=1; g_initial_columns=8; g_initial_rows=4; g_initial_direction=1;
 __atomic_store_n(&g_initial_restore_mask,3,__ATOMIC_RELEASE);
 __atomic_store_n(&g_initial_restore,2,__ATOMIC_RELEASE);

 /* 1) Desktop (-100) runs first; source already planned to 8x4, remembers ws_d_l and keeps initial=1 bit 2 */
 integer(obj[15],7,201); integer(obj[15],0x1f,1); integer(obj[15],0x27,1);
 integer(obj[15],0x2f,5); integer(obj[15],0x37,0); integer(obj[15],0x3f,2); integer(obj[15],0x47,-100);
 integer(obj[12],11,0); integer(obj[12],19,2);
 integer(obj[16],7,202); integer(obj[16],0x1f,1); integer(obj[16],0x27,1);
 integer(obj[16],0x2f,5); integer(obj[16],0x37,3); integer(obj[16],0x3f,3); integer(obj[16],0x47,-100);
 integer(obj[13],11,3); integer(obj[13],19,3);
 word(obj[2],11,2); word(obj[8],11,4);
 ld_observe_process(&c);
 assert(g_restore_ready);
 assert(ld_heap_i64(obj[12],11)==ws_d_l[0].x && ld_heap_i64(obj[12],19)==ws_d_l[0].y);
 assert(ld_heap_i64(obj[13],11)==ws_d_l[1].x && ld_heap_i64(obj[13],19)==ws_d_l[1].y);
 /* Bit 1 consumed, bit 2 (-104) MUST remain armed! */
 assert(__atomic_load_n(&g_initial_restore_mask,__ATOMIC_ACQUIRE)==2);
 assert(__atomic_load_n(&g_initial_restore,__ATOMIC_ACQUIRE)==2);

 /* 2) AllApps (-104) runs second with valid 8x4 remembered slot in items[1]; portrait items[0] must stay intact */
 word(obj[2],11,2); word(obj[8],11,2);
 integer(obj[17],7,301); integer(obj[17],0x1f,1); integer(obj[17],0x27,1);
 integer(obj[17],0x2f,2); integer(obj[17],0x37,0); integer(obj[17],0x3f,0); integer(obj[17],0x47,-104);
 word(obj[14],7,(uint32_t)obj[17]); integer(obj[14],11,0); integer(obj[14],19,0);
 word(obj[10],15,(uint32_t)obj[14]);
 ld_observe_process(&c);
 /* The page already fits the live grid, so nothing was rewritten and the
  * Launcher keeps its own placement pass. */
 assert(!g_restore_ready);
 assert(ld_heap_i64(obj[14],11)==0 && ld_heap_i64(obj[14],19)==0); /* AllApps untouched */
 /* Verify initial=1 prevented overwriting items[0] (4x8) of ws_apps */
 struct ld_layout_memory ws_apps_after;
 assert(ld_layout_load(LD_WS_LAYOUT_FILE,&ws_apps_after));
 assert(ws_apps_after.items[0][0].x==ws_a_p[0].x && ws_apps_after.items[0][0].y==ws_a_p[0].y);
 /* Normal Home file untouched */
 struct ld_layout_memory normal_after;
 assert(ld_layout_load(LD_LAYOUT_FILE,&normal_after));
 assert(memcmp(&normal_before,&normal_after,sizeof normal_before)==0);

 /* Live rotation reflows each page using the old reading order. The first
  * page fits both shapes, and the second has an old column outside 4x8. */
 word(obj[2],11,4); word(obj[8],11,4);
 word(obj[10],15,(uint32_t)obj[12]);
 const int landscape_x[]={1,2,7}, landscape_y[]={0,0,1};
 for(int i=0;i<3;i++) {
  integer(obj[15+i],7,401+i); integer(obj[15+i],0x1f,1); integer(obj[15+i],0x27,1);
  integer(obj[15+i],0x2f,i<2?2:3); integer(obj[15+i],0x47,-104);
  integer(obj[12+i],11,landscape_x[i]); integer(obj[12+i],19,landscape_y[i]);
  integer(obj[15+i],0x37,landscape_x[i]); integer(obj[15+i],0x3f,landscape_y[i]);
 }
 uintptr_t grid=obj[6];
 integer(grid,0x1b,8); integer(grid,0x23,4);
 c.x[2]=grid; ld_observe_orientation(&c);
 integer(obj[0],7,8); integer(obj[0],15,4);
 integer(grid,0x1b,4); integer(grid,0x23,8);
 ld_observe_orientation(&c);
 c.x[2]=obj[1]; c.x[3]=4; c.x[5]=8;
 ld_observe_process(&c);
 assert(g_restore_ready);
 for(int i=0;i<3;i++) {
  int x=i==1?1:0;
  assert(ld_heap_i64(obj[12+i],11)==x && ld_heap_i64(obj[12+i],19)==0);
  assert(ld_heap_i64(obj[15+i],0x37)==x && ld_heap_i64(obj[15+i],0x3f)==0);
  assert(ld_heap_i64(obj[15+i],0x2f)==(i<2?2:3));
 }

 unlink(LD_LAYOUT_FILE); unlink(LD_WS_LAYOUT_FILE); unlink(LD_WS_DESK_LAYOUT_FILE);
 (void)g_orig_orientation; (void)g_layout_mid_ready;
 puts("PASS all pages and model coordinates mapped in one batch before vendor continuation");
}
