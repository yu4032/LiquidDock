#ifndef LD_NATIVE_GLASS_APPLY_H
#define LD_NATIVE_GLASS_APPLY_H
#include "native_glass_schema.h"
#include <string.h>
#include <math.h>
typedef int (*ld_glass_read_fn)(uintptr_t,void*,size_t);
typedef int (*ld_glass_write_fn)(const uintptr_t*,const float*,size_t);
/* Operate only on a freshly created, authenticated C++ filter, before it is used
 * for cache keys or drawing. No Dart objects, capture coordinates or HDR fields. */
static inline int ld_native_glass_apply(uintptr_t filter,const struct ld_native_glass_options *options,
        ld_glass_read_fn read,ld_glass_write_fn write)
{
    if(!options || !options->mask) return 0;
    if(options->mask>>LD_NATIVE_GLASS_COUNT) return -1;
    unsigned char record[0x140];
    if(!read(filter,record,sizeof record)) return -1;
    uintptr_t addresses[LD_NATIVE_GLASS_COUNT];float values[LD_NATIVE_GLASS_COUNT];size_t n=0;
    for(unsigned i=0;i<LD_NATIVE_GLASS_COUNT;i++) {
        if(!(options->mask&(1ull<<i))) continue;
        const struct ld_native_glass_field *field=ld_native_glass_fields+i;
        double value=options->values[i];
        if(!isfinite(value) || value<field->min || value>field->max) return -1;
        uintptr_t address=filter+field->offset;
        if(field->count) {
            uintptr_t vector[3];memcpy(vector,record+field->offset,sizeof vector);
            if(vector[0]<4096 || vector[0]%4 || vector[1]<vector[0] || vector[2]<vector[1]
                    || vector[1]-vector[0]!=field->count*4 || vector[2]-vector[0]>4096) return -1;
            address=vector[0]+field->component*4;
            float original;if(!read(address,&original,4)) return -1;
        }
        addresses[n]=address;values[n++]=(float)value;
    }
    /* A zero direction would make native normalize() undefined. */
    uintptr_t direction[3];memcpy(direction,record+0x50,sizeof direction);
    int direction_changed=0;
    for(unsigned i=0;i<LD_NATIVE_GLASS_COUNT;i++) if((options->mask&(1ull<<i))&&ld_native_glass_fields[i].offset==0x50) direction_changed=1;
    if(direction_changed) {
        float v[3];if(!read(direction[0],v,sizeof v))return -1;
        for(size_t i=0;i<n;i++) if(addresses[i]>=direction[0] && addresses[i]<direction[0]+12) v[(addresses[i]-direction[0])/4]=values[i];
        if(!isfinite(v[0])||!isfinite(v[1])||!isfinite(v[2])||v[0]*v[0]+v[1]*v[1]+v[2]*v[2]<1e-12f)return -1;
    }
    return write(addresses,values,n)?(int)n:-1;
}
#endif
