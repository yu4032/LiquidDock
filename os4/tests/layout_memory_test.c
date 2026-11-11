#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "../native/layout_memory.h"
#include "../native/layout_profiles.h"

int main(void) {
    struct ld_layout_item source[] = {{17,2,7,3,1,1},{18,2,0,0,2,2}};
    struct ld_layout_item remembered[] = {{17,2,0,7,1,1},{18,2,2,2,2,2}};
    struct ld_layout_item out[4];
    assert(ld_layout_plan(source,2,4,8,remembered,2,out));
    assert(out[0].x==0 && out[0].y==7 && out[0].screen==2);
    assert(out[1].x==2 && out[1].y==2);
    struct ld_layout_item roundtrip[2];
    assert(ld_layout_plan(out,2,8,4,source,2,roundtrip));
    assert(roundtrip[0].x==7 && roundtrip[0].y==3);
    source[0].screen=3;
    assert(ld_layout_plan(source,1,4,8,remembered,2,out));
    assert(out[0].screen==3); /* no deleted id 18 resurrected */
    struct ld_layout_item impossible[]={{1,1,0,0,5,1}};
    memset(out,0x55,sizeof out);
    struct ld_layout_item before[4]; memcpy(before,out,sizeof out);
    assert(!ld_layout_plan(impossible,1,4,8,NULL,0,out));
    assert(!memcmp(before,out,sizeof out));
    struct ld_layout_memory memory={0}, loaded={0};
    memory.columns=4; memory.rows=8;
    memory.count[0]=2; memcpy(memory.items[0],remembered,sizeof remembered);
    memory.count[1]=2; memcpy(memory.items[1],roundtrip,sizeof roundtrip);
    const char *path="/tmp/ld-layout-memory-test.bin";
    assert(ld_layout_save(path,&memory));
    assert(ld_layout_load(path,&loaded));
    assert(loaded.items[0][0].x==0 && loaded.items[1][0].x==7);
    assert(ld_layout_select_profile(path,6,6));
    struct ld_layout_memory square={.columns=6,.rows=6};
    assert(ld_layout_save(path,&square));
    assert(ld_layout_select_profile(path,4,8));
    assert(ld_layout_load(path,&loaded) && loaded.columns==4 && loaded.rows==8 && loaded.items[1][0].x==7);
    unlink("/tmp/ld-layout-memory-test.bin.4x8"); unlink("/tmp/ld-layout-memory-test.bin.6x6");
    FILE *f=fopen(path,"r+b"); assert(f); fputc('x',f); fclose(f);
    assert(!ld_layout_load(path,&loaded));
    unlink(path);
    puts("PASS independent layouts, page identity, atomic failure and persistent corruption rejection");
}
