#include <assert.h>
#include <stdio.h>
#include "../native/hm_transition.h"

#define N 8
#define MAX_AGE 1024u

static struct ld_hm_trans_key keys[N];
static unsigned char valid[N];
static unsigned seen[N];

static struct ld_hm_trans_key key(uintptr_t id, int kind, int dir, int cols, int rows)
{
    return (struct ld_hm_trans_key){id, kind, dir, cols, rows};
}

static int take(struct ld_hm_trans_key want, unsigned now)
{
    int i = ld_hm_trans_pick(keys, valid, seen, N, want, now, MAX_AGE);
    if (i >= 0) {
        keys[i] = want;
        valid[i] = 1;
        seen[i] = now;
    }
    return i;
}

int main(void)
{
    unsigned now = 1;

    /* Five controllers visited in one window, then revisited in the same
     * order: none may displace another (the A->B->A case generalised). */
    uintptr_t ids[5] = {100, 200, 300, 400, 500};
    int idx[5];
    for (int i = 0; i < 5; i++)
        idx[i] = take(key(ids[i], 1, i & 1, 4 + (i & 1), 8 - (i & 1)), now);
    for (int i = 0; i < 5; i++) {
        assert(idx[i] >= 0);
        assert(ld_hm_trans_pick(keys, valid, seen, N,
                                key(ids[i], 1, i & 1, 4 + (i & 1), 8 - (i & 1)),
                                now, MAX_AGE) == idx[i]);
    }

    /* The same controller in another direction or grid profile is a distinct
     * baseline; it must not overwrite the first one. */
    int turned = take(key(100, 1, 1, 8, 4), now);
    assert(turned >= 0 && turned != idx[0]);
    assert(ld_hm_trans_pick(keys, valid, seen, N, key(100, 1, 0, 4, 8),
                            now, MAX_AGE) == idx[0]);

    /* Fill the remaining slots (5 + turned = 6 already taken). */
    for (uintptr_t id = 600; id <= 700; id += 100)
        assert(take(key(id, 1, 1, 8, 4), now) >= 0);
    for (int i = 0; i < N; i++)
        assert(valid[i]);

    /* Full table: a new controller is refused instead of evicting a recent
     * baseline. Registering its geometry as native would apply the offsets
     * twice, so the caller must fail closed for that frame. */
    assert(ld_hm_trans_pick(keys, valid, seen, N, key(999, 1, 1, 8, 4),
                            now, MAX_AGE) == -1);
    assert(ld_hm_trans_pick(keys, valid, seen, N, key(100, 1, 0, 4, 8),
                            now, MAX_AGE) == idx[0]);

    /* Only slots idle longer than max_age are recycled. */
    now += MAX_AGE + 1;
    int aged = ld_hm_trans_pick(keys, valid, seen, N, key(999, 1, 1, 8, 4),
                                now, MAX_AGE);
    assert(aged >= 0 && aged < N);

    /* Degenerate arguments are rejected, never dereferenced. */
    assert(ld_hm_trans_pick(NULL, valid, seen, N, key(1, 1, 1, 4, 8), now, MAX_AGE) == -1);
    assert(ld_hm_trans_pick(keys, NULL, seen, N, key(1, 1, 1, 4, 8), now, MAX_AGE) == -1);
    assert(ld_hm_trans_pick(keys, valid, NULL, N, key(1, 1, 1, 4, 8), now, MAX_AGE) == -1);
    assert(ld_hm_trans_pick(keys, valid, seen, 0, key(1, 1, 1, 4, 8), now, MAX_AGE) == -1);

    printf("hm_transition_test: ok\n");
    return 0;
}
