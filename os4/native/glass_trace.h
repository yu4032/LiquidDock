#ifndef LD_OS4_GLASS_TRACE_H
#define LD_OS4_GLASS_TRACE_H
#include "dart_observer.h"

/* Four verified Dart AOT pipeline entry observers; read-only, exact register
 * preservation, no allocation and no actual material/config mutation. */
static volatile unsigned g_os4_pipeline_counts[4] __attribute__((used));
static unsigned g_os4_pipeline_mask;
static void *g_os4_trace_light_original __attribute__((used));
static void *g_os4_trace_dark_original __attribute__((used));
static void *g_os4_trace_filter_original __attribute__((used));
static void *g_os4_trace_apply_original __attribute__((used));

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
    (void)c; __atomic_add_fetch(&g_os4_pipeline_counts[3],1u,__ATOMIC_RELAXED);
}
LD_DART_OBSERVER(repl_os4_trace_light,ld_observe_glass_light,g_os4_trace_light_original)
LD_DART_OBSERVER(repl_os4_trace_dark,ld_observe_glass_dark,g_os4_trace_dark_original)
LD_DART_OBSERVER(repl_os4_trace_filter,ld_observe_glass_filter,g_os4_trace_filter_original)
LD_DART_OBSERVER(repl_os4_trace_apply,ld_observe_glass_apply,g_os4_trace_apply_original)
#endif
