#ifndef LD_HM_TRANSITION_H
#define LD_HM_TRANSITION_H

#include <stdint.h>

/* Provisional (transition) baselines are keyed by controller identity.
 * A page switch can visit controllers A -> B -> A inside the window where a
 * new-direction GridConfig has not been committed yet; a single slot would
 * let B evict A and make A's next callback mistake geometry this hook already
 * shifted for native geometry. Keep one slot per controller instead. */
struct ld_hm_trans_ident {
    uintptr_t identity;
    int kind;
};

static inline int ld_hm_trans_pick(const struct ld_hm_trans_ident *slots,
                                   const unsigned char *valid, int n,
                                   struct ld_hm_trans_ident want,
                                   unsigned *next)
{
    if (!slots || !valid || !next || n <= 0) return -1;
    for (int i = 0; i < n; i++)
        if (valid[i] && slots[i].identity == want.identity &&
            slots[i].kind == want.kind)
            return i;
    for (int i = 0; i < n; i++)
        if (!valid[i]) return i;
    return (int)((*next)++ % (unsigned)n);
}

#endif
