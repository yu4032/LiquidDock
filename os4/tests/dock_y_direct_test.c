#define LD_DOCK_Y_TEST_ONLY
#include "../native/dock_y_direct.h"
#include <math.h>
#include <stdio.h>
#include <string.h>

int main(void)
{
    const struct { double setting, delta; } values[]={
        {0,0}, {10,-10}, {-10,10}, {40,-40}, {-30,30}, {0.5,-0.5}
    };
    for(size_t i=0;i<sizeof values/sizeof *values;i++) {
        double delta=123;
        if(!ld_dock_y_delta_for_setting(values[i].setting,&delta)
                || fabs(delta-values[i].delta)>0.000001) {
            fprintf(stderr,"FAIL: signed Dock Y delta case %zu\n",i);return 1;
        }
    }
    double delta=123;
    if(ld_dock_y_delta_for_setting(41,&delta)
            || ld_dock_y_delta_for_setting(-31,&delta)
            || ld_dock_y_delta_for_setting(NAN,&delta)
            || ld_dock_y_delta_for_setting(INFINITY,&delta)
            || ld_dock_y_delta_for_setting(1,NULL)
            || delta!=123) return 1;
    uint32_t function[LD_DOCK_Y_SIZE/4]={0};
    function[0]=0xa9bf79fd;
    function[LD_DOCK_Y_TAIL_WORD+0]=0x1e643820;
    function[LD_DOCK_Y_TAIL_WORD+1]=0xaa1d03ef;
    function[LD_DOCK_Y_TAIL_WORD+2]=0xa8c179fd;
    function[LD_DOCK_Y_TAIL_WORD+3]=0xd65f03c0;
    if(!ld_dock_y_matches(function,sizeof function)) return 1;
    for(size_t i=0;i<4;i++) {
        function[LD_DOCK_Y_TAIL_WORD+i]^=1u;
        if(ld_dock_y_matches(function,sizeof function)) return 1;
        function[LD_DOCK_Y_TAIL_WORD+i]^=1u;
    }
    if(ld_dock_y_matches(function,sizeof function-4)) return 1;
    function[0]^=1u;
    if(ld_dock_y_matches(function,sizeof function)) return 1;
    puts("PASS: signed direct Dock Y delta and AOT entry/tail guard");
    return 0;
}
