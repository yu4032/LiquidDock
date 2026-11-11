#include <assert.h>
#include "../native/layout_changes.h"
int main(void) {
 struct ld_layout_memory m={.columns=4,.rows=8,.active_direction=0,.count={2,2},.items={{{1,2,0,0,1,1},{2,2,1,0,1,1}},{{1,2,7,0,1,1},{2,2,0,0,1,1}}}};
 struct ld_layout_item swap[]={{1,2,1,0,1,1},{2,2,0,0,1,1}};
 assert(ld_layout_record_changes(&m,swap,2));
 assert(m.items[0][0].x==1 && m.items[0][1].x==0 && m.items[1][0].x==7);
 struct ld_layout_item moved={1,3,3,3,1,1};
 assert(ld_layout_record_changes(&m,&moved,1));
 assert(m.items[0][0].screen==3 && m.items[1][0].screen==3);
 struct ld_layout_memory before=m;
 moved.x=4; assert(!ld_layout_record_changes(&m,&moved,1)); assert(!memcmp(&m,&before,sizeof m));
 struct ld_layout_item deleted={2,-1,0,0,0,0};
 assert(ld_layout_record_changes(&m,&deleted,1)); assert(m.count[0]==1 && m.count[1]==1);
 struct ld_layout_item replacement={3,2,0,0,1,1};
 assert(ld_layout_record_changes(&m,&replacement,1)); assert(m.count[0]==2 && m.items[0][1].id==3);
 struct ld_layout_memory squeezed={.columns=4,.rows=2,.active_direction=0,.count={4,4},.items={{{1,2,0,0,1,1},{2,2,1,0,1,1},{3,2,0,1,1,1},{4,2,1,1,1,1}},{{1,2,0,0,1,1},{2,2,0,1,1,1},{3,2,0,2,1,1},{4,2,0,3,1,1}}}};
 struct ld_layout_item widget={5,2,2,0,2,2};
 assert(ld_layout_record_changes(&squeezed,&widget,1));
 assert(squeezed.count[1]==5 && ld_layout_valid(squeezed.items[1],5,2,4));
 puts("PASS atomic squeeze updates, independent positions and shared page membership");
}
