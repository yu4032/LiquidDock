#ifndef LD_OS4_GLASS_TRACE_H
#define LD_OS4_GLASS_TRACE_H
#include "dart_observer.h"
#include "dart_heap.h"
#include "glass_light_sample.h"
#include <string.h>
#include <stddef.h>

/* Four Dart AOT pipeline observers; strict safety mode checks their exact
 * function bodies. They preserve registers, allocate nothing and do not mutate
 * material or config. */
static volatile unsigned g_os4_pipeline_counts[4] __attribute__((used));
static unsigned g_os4_pipeline_mask;
static void *g_os4_trace_light_original __attribute__((used));
static void *g_os4_trace_dark_original __attribute__((used));
static void *g_os4_trace_filter_original __attribute__((used));
static void *g_os4_trace_apply_original __attribute__((used));

/* A few guarded read-only snapshots of the *input* to applyTo.
 * The research source confirms a 5-double GlassLightConfig record but
 * does not reliably identify its actual Dart register. Never write data.
 * Avoid addresses in logs; save one plausible candidate by register index.
 */
static volatile unsigned g_os4_light_sample_attempts __attribute__((used));
static volatile unsigned g_os4_light_sample_status __attribute__((used));
static int g_os4_light_sample_register __attribute__((used));
static double g_os4_light_sample_fields[5] __attribute__((used));
static void ld_os4_collect_light_sample(struct ld_dart_context *c)
{
    if(__atomic_load_n(&g_os4_light_sample_status,__ATOMIC_ACQUIRE) != 0) return;
    unsigned attempt=__atomic_fetch_add(&g_os4_light_sample_attempts,1u,__ATOMIC_RELAXED);
    if(attempt>=12) return;
    /* Do not probe TLAB/current stack or arbitrary nested heap references.
     * Four conventional argument registers suffice for preliminary evidence.
     * process_vm_readv fails cleanly for invalid/unmapped pointers. */
    for(unsigned reg=0;reg<4;reg++) {
        uintptr_t object=c->x[reg];
        if(!(object&1) || object == c->x[22]) continue;
        if(object < 0x100000000ull) object += (uintptr_t)(c->x[28]<<32);
        double local[5]={0};
        if(!ld_read_heap(object+7,local,sizeof local)
                || !ld_os4_plausible_light_record(local)) continue;
        unsigned expected=0;
        if(!__atomic_compare_exchange_n(&g_os4_light_sample_status,&expected,1,
                false,__ATOMIC_ACQ_REL,__ATOMIC_ACQUIRE)) return;
        g_os4_light_sample_register=(int)reg;
        memcpy(g_os4_light_sample_fields,local,sizeof local);
        __atomic_store_n(&g_os4_light_sample_status,2u,__ATOMIC_RELEASE);
        return;
    }
}


__attribute__((used)) static void ld_observe_glass_light(struct ld_dart_context *c) {
    (void)c; __atomic_add_fetch(&g_os4_pipeline_counts[0],1u,__ATOMIC_RELAXED);
}
__attribute__((used)) static void ld_observe_glass_dark(struct ld_dart_context *c) {
    (void)c; __atomic_add_fetch(&g_os4_pipeline_counts[1],1u,__ATOMIC_RELAXED);
}
__attribute__((used)) static void ld_observe_glass_filter(struct ld_dart_context *c) {
    (void)c; __atomic_add_fetch(&g_os4_pipeline_counts[2],1u,__ATOMIC_RELAXED);
}
__attribute__((used)) static void ld_observe_glass_apply(struct ld_dart_context *c) {
    __atomic_add_fetch(&g_os4_pipeline_counts[3],1u,__ATOMIC_RELAXED);
    ld_os4_collect_light_sample(c);
}
LD_DART_OBSERVER(repl_os4_trace_light,ld_observe_glass_light,g_os4_trace_light_original)
LD_DART_OBSERVER(repl_os4_trace_dark,ld_observe_glass_dark,g_os4_trace_dark_original)
LD_DART_OBSERVER(repl_os4_trace_filter,ld_observe_glass_filter,g_os4_trace_filter_original)
LD_DART_OBSERVER(repl_os4_trace_apply,ld_observe_glass_apply,g_os4_trace_apply_original)
#endif
