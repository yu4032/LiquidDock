#ifndef LD_HM_TRANSITION_H
#define LD_HM_TRANSITION_H

#include <stdint.h>

/* Provisional (transition) baselines are keyed by controller identity AND the
 * grid profile they were derived for. A page switch can visit controllers
 * A -> B -> A inside the window where a new-direction GridConfig has not been
 * committed yet; a single slot would let B evict A and make A's next callback
 * mistake geometry this hook already shifted for native geometry. */
struct ld_hm_trans_key {
    uintptr_t identity;
    int kind;
    int dir;
    int cols;
    int rows;
};

static inline int ld_hm_trans_same(struct ld_hm_trans_key a,
                                   struct ld_hm_trans_key b)
{
    return a.identity == b.identity && a.kind == b.kind && a.dir == b.dir &&
           a.cols == b.cols && a.rows == b.rows;
}

/* Returns a slot index, or -1 when the table is full.
 *
 * Full means every slot still belongs to a recent controller. Evicting one
 * would drop the record of geometry this hook already wrote, and that
 * controller's next callback would register the shifted cells as a new native
 * baseline and apply the offsets twice. Only slots older than max_age frames
 * are recycled; otherwise the caller must fail closed for this frame. */
static inline int ld_hm_trans_pick(const struct ld_hm_trans_key *slots,
                                   const unsigned char *valid,
                                   const unsigned *seen, int n,
                                   struct ld_hm_trans_key want,
                                   unsigned now, unsigned max_age)
{
    if (!slots || !valid || !seen || n <= 0) return -1;
    for (int i = 0; i < n; i++)
        if (valid[i] && ld_hm_trans_same(slots[i], want))
            return i;
    for (int i = 0; i < n; i++)
        if (!valid[i])
            return i;
    for (int i = 0; i < n; i++)
        if (now - seen[i] > max_age)
            return i;
    return -1;
}

#endif
