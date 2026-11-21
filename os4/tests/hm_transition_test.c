#include <assert.h>
#include <stdio.h>
#include "../native/hm_transition.h"

int main(void)
{
    struct ld_hm_trans_ident ids[4] = {{0}};
    unsigned char valid[4] = {0, 0, 0, 0};
    unsigned next = 0;

    /* A -> B -> A inside one transition window: A must keep its own slot so
     * its shifted geometry is still recognised on the third callback. */
    int a = ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){100, 1}, &next);
    assert(a == 0);
    ids[a] = (struct ld_hm_trans_ident){100, 1}; valid[a] = 1;

    int b = ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){200, 1}, &next);
    assert(b == 1);
    ids[b] = (struct ld_hm_trans_ident){200, 1}; valid[b] = 1;

    int a2 = ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){100, 1}, &next);
    assert(a2 == a);
    int b2 = ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){200, 1}, &next);
    assert(b2 == b);

    /* identity_kind separates a page from a same-address workspace grid */
    int k = ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){100, 2}, &next);
    assert(k == 2);
    ids[k] = (struct ld_hm_trans_ident){100, 2}; valid[k] = 1;

    /* Fill the table, then evict round-robin; existing identities still hit. */
    int c = ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){300, 1}, &next);
    assert(c == 3);
    ids[c] = (struct ld_hm_trans_ident){300, 1}; valid[c] = 1;

    assert(ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){100, 1}, &next) == a);
    assert(ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){200, 1}, &next) == b);

    unsigned before = next;
    int evicted = ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){400, 1}, &next);
    assert(evicted == 0);
    assert(next == before + 1);

    /* Degenerate arguments are rejected, never dereferenced. */
    assert(ld_hm_trans_pick(NULL, valid, 4, (struct ld_hm_trans_ident){1, 1}, &next) == -1);
    assert(ld_hm_trans_pick(ids, NULL, 4, (struct ld_hm_trans_ident){1, 1}, &next) == -1);
    assert(ld_hm_trans_pick(ids, valid, 4, (struct ld_hm_trans_ident){1, 1}, NULL) == -1);

    printf("hm_transition_test: ok\n");
    return 0;
}
