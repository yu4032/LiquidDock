#include "../native/glass_light_sample.h"
#include <stdio.h>
#include <math.h>
int main(void) {
    const double sensible[5]={1.0,0.8,0.5,0.4,2.0};
    const double zeros[5]={0,0,0,0,0};
    const double nanv[5]={1.0,2.0,NAN,4.0,5.0};
    const double huge[5]={1.0,2.0,1000000.0,4.0,5.0};
    if(!ld_os4_plausible_light_record(sensible)
            || ld_os4_plausible_light_record(zeros)
            || ld_os4_plausible_light_record(nanv)
            || ld_os4_plausible_light_record(huge)
            || ld_os4_plausible_light_record(NULL)) return 1;
    puts("PASS: guarded 5-double glass-light diagnostic recognizes plausible records only");
    return 0;
}
