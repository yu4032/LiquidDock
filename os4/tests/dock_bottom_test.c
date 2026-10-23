#define LD_DOCK_BOTTOM_TEST_ONLY
#include "../native/dock_bottom_offset.h"
#include <stdio.h>
#include <math.h>

int main(void)
{
    struct {double old,offset,want;} cases[]={
        {-24,0,-24}, {-24,12,-36}, {-24,-8,-16}, {0,40,-40},
        {-24,-30,6}, {12,0,12}, {-6,0.5,-6.5}
    };
    for(size_t i=0;i<sizeof cases/sizeof *cases;i++) {
        double actual=0;
        if(!ld_dock_bottom_inset(cases[i].old,cases[i].offset,&actual)
                || fabs(actual-cases[i].want)>1e-9) {
            fprintf(stderr,"FAIL Dock bottom offset case %zu\n",i);
            return 1;
        }
    }
    double result=123;
    if(ld_dock_bottom_inset(-24,41,&result)
            || ld_dock_bottom_inset(-24,-31,&result)
            || ld_dock_bottom_inset(NAN,1,&result)
            || ld_dock_bottom_inset(-24,INFINITY,&result)
            || ld_dock_bottom_inset(-24,1,NULL)) return 1;
    if(result!=123) return 1;
    puts("PASS: Dock bottom signed inset, range and nonfinite guards");
    return 0;
}
