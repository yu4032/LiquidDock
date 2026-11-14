#ifndef LIQUIDDOCK_XZ_MINI_H
#define LIQUIDDOCK_XZ_MINI_H

/*
 * Minimal, self-contained XZ + LZMA2 decompressor for .gnu_debugdata.
 * Supports standard single-block or multi-block XZ streams using LZMA2 (0x21).
 */

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#define XZ_MINI_MAX_OUT (32u * 1024u * 1024u)

#define LZMA_NUM_STATES     12u
#define LZMA_NUM_POS_BITS   4u
#define LZMA_POS_STATES_MAX (1u << LZMA_NUM_POS_BITS)
#define LZMA_NUM_LEN_TO_POS 4u
#define LZMA_MATCH_MIN_LEN  2u
#define LZMA_NUM_ALIGN_BITS 4u
#define LZMA_ALIGN_SIZE     (1u << LZMA_NUM_ALIGN_BITS)
#define LZMA_START_POS_MODEL 4u
#define LZMA_END_POS_MODEL   14u
#define LZMA_NUM_FULL_DIST   (1u << (LZMA_END_POS_MODEL / 2u))
#define LZMA_NUM_LIT_PROBS   (0x300u << 4u) /* max lc+lp = 4 */

typedef struct {
    const uint8_t *src;
    size_t src_len;
    size_t pos;
    uint32_t range;
    uint32_t code;
    bool err;
} xz_rc_t;

typedef struct {
    uint16_t choice;
    uint16_t choice2;
    uint16_t low[LZMA_POS_STATES_MAX][8];
    uint16_t mid[LZMA_POS_STATES_MAX][8];
    uint16_t high[256];
} xz_len_dec_t;

typedef struct {
    uint32_t lc, lp, pb;
    uint32_t pos_mask;
    uint32_t lit_pos_mask;
    uint16_t is_match[LZMA_NUM_STATES][LZMA_POS_STATES_MAX];
    uint16_t is_rep[LZMA_NUM_STATES];
    uint16_t is_rep0[LZMA_NUM_STATES];
    uint16_t is_rep1[LZMA_NUM_STATES];
    uint16_t is_rep2[LZMA_NUM_STATES];
    uint16_t is_rep0_long[LZMA_NUM_STATES][LZMA_POS_STATES_MAX];
    uint16_t pos_slot[LZMA_NUM_LEN_TO_POS][64];
    uint16_t pos_decoders[LZMA_NUM_FULL_DIST - LZMA_END_POS_MODEL];
    uint16_t pos_align[LZMA_ALIGN_SIZE];
    xz_len_dec_t match_len;
    xz_len_dec_t rep_len;
    uint16_t lit_probs[LZMA_NUM_LIT_PROBS];
} xz_lzma_state_t;

static inline void xz_prob_init(uint16_t *p, size_t count) {
    for (size_t i = 0; i < count; i++) p[i] = 1024u;
}

static void xz_lzma_reset_state(xz_lzma_state_t *st) {
    xz_prob_init(&st->is_match[0][0], LZMA_NUM_STATES * LZMA_POS_STATES_MAX);
    xz_prob_init(st->is_rep, LZMA_NUM_STATES);
    xz_prob_init(st->is_rep0, LZMA_NUM_STATES);
    xz_prob_init(st->is_rep1, LZMA_NUM_STATES);
    xz_prob_init(st->is_rep2, LZMA_NUM_STATES);
    xz_prob_init(&st->is_rep0_long[0][0], LZMA_NUM_STATES * LZMA_POS_STATES_MAX);
    xz_prob_init(&st->pos_slot[0][0], LZMA_NUM_LEN_TO_POS * 64u);
    xz_prob_init(st->pos_decoders, LZMA_NUM_FULL_DIST - LZMA_END_POS_MODEL);
    xz_prob_init(st->pos_align, LZMA_ALIGN_SIZE);
    st->match_len.choice = 1024u;
    st->match_len.choice2 = 1024u;
    xz_prob_init(&st->match_len.low[0][0], LZMA_POS_STATES_MAX * 8u);
    xz_prob_init(&st->match_len.mid[0][0], LZMA_POS_STATES_MAX * 8u);
    xz_prob_init(st->match_len.high, 256u);
    st->rep_len.choice = 1024u;
    st->rep_len.choice2 = 1024u;
    xz_prob_init(&st->rep_len.low[0][0], LZMA_POS_STATES_MAX * 8u);
    xz_prob_init(&st->rep_len.mid[0][0], LZMA_POS_STATES_MAX * 8u);
    xz_prob_init(st->rep_len.high, 256u);
    xz_prob_init(st->lit_probs, LZMA_NUM_LIT_PROBS);
}

static inline uint8_t xz_rc_byte(xz_rc_t *rc) {
    if (rc->pos >= rc->src_len) {
        rc->err = true;
        return 0;
    }
    return rc->src[rc->pos++];
}

static inline void xz_rc_norm(xz_rc_t *rc) {
    if (rc->range < 0x01000000u) {
        rc->range <<= 8;
        rc->code = (rc->code << 8) | xz_rc_byte(rc);
    }
}

static inline int xz_rc_bit(xz_rc_t *rc, uint16_t *prob) {
    xz_rc_norm(rc);
    uint32_t bound = (rc->range >> 11) * (*prob);
    if (rc->code < bound) {
        rc->range = bound;
        *prob = (uint16_t)(*prob + ((2048u - *prob) >> 5));
        return 0;
    } else {
        rc->range -= bound;
        rc->code -= bound;
        *prob = (uint16_t)(*prob - (*prob >> 5));
        return 1;
    }
}

static inline uint32_t xz_rc_direct(xz_rc_t *rc, uint32_t bits) {
    uint32_t res = 0;
    for (uint32_t i = 0; i < bits; i++) {
        xz_rc_norm(rc);
        rc->range >>= 1;
        uint32_t t = (rc->code - rc->range) >> 31;
        rc->code -= rc->range & (t - 1u);
        res = (res << 1) | (1u - t);
    }
    return res;
}

static inline uint32_t xz_rc_bittree(xz_rc_t *rc, uint16_t *probs, uint32_t bits) {
    uint32_t m = 1u;
    for (uint32_t i = 0; i < bits; i++) {
        m = (m << 1) | (uint32_t)xz_rc_bit(rc, &probs[m]);
    }
    return m - (1u << bits);
}

static inline uint32_t xz_rc_bittree_rev(xz_rc_t *rc, uint16_t *probs, uint32_t bits) {
    uint32_t m = 1u, sym = 0;
    for (uint32_t i = 0; i < bits; i++) {
        int b = xz_rc_bit(rc, &probs[m]);
        m = (m << 1) | (uint32_t)b;
        sym |= ((uint32_t)b << i);
    }
    return sym;
}

static uint32_t xz_dec_len(xz_rc_t *rc, xz_len_dec_t *ld, uint32_t pos_state) {
    if (!xz_rc_bit(rc, &ld->choice)) {
        return xz_rc_bittree(rc, ld->low[pos_state], 3u) + LZMA_MATCH_MIN_LEN;
    }
    if (!xz_rc_bit(rc, &ld->choice2)) {
        return xz_rc_bittree(rc, ld->mid[pos_state], 3u) + LZMA_MATCH_MIN_LEN + 8u;
    }
    return xz_rc_bittree(rc, ld->high, 8u) + LZMA_MATCH_MIN_LEN + 16u;
}

static bool xz_read_vli(const uint8_t *src, size_t len, size_t *pos, uint64_t *out) {
    uint64_t val = 0;
    for (int i = 0; i < 9; i++) {
        if (*pos >= len) return false;
        uint8_t b = src[(*pos)++];
        val |= ((uint64_t)(b & 0x7fu)) << (i * 7);
        if ((b & 0x80u) == 0) {
            *out = val;
            return true;
        }
    }
    return false;
}

static bool xz_decode_lzma2(const uint8_t *in, size_t in_len,
                            uint8_t **out_buf, size_t *out_len) {
    xz_lzma_state_t *st = (xz_lzma_state_t *)calloc(1, sizeof(*st));
    if (!st) return false;
    xz_lzma_reset_state(st);

    size_t cap = 65536u;
    uint8_t *out = (uint8_t *)malloc(cap);
    if (!out) { free(st); return false; }

    size_t pos = 0;
    size_t out_pos = 0;
    size_t dict_reset_pos = 0;
    uint32_t state = 0;
    uint32_t rep0 = 0, rep1 = 0, rep2 = 0, rep3 = 0;
    bool props_ok = false;

    while (pos < in_len) {
        uint8_t ctrl = in[pos++];
        if (ctrl == 0x00u) {
            *out_buf = out;
            *out_len = out_pos;
            free(st);
            return true;
        }
        if (ctrl == 0x01u || ctrl == 0x02u) {
            if (pos + 2 > in_len) goto fail;
            size_t chunk_len = (((size_t)in[pos] << 8) | in[pos + 1]) + 1u;
            pos += 2;
            if (pos + chunk_len > in_len) goto fail;
            if (out_pos + chunk_len > XZ_MINI_MAX_OUT) goto fail;
            while (out_pos + chunk_len > cap) {
                cap *= 2u;
                uint8_t *nb = (uint8_t *)realloc(out, cap);
                if (!nb) goto fail;
                out = nb;
            }
            if (ctrl == 0x01u) dict_reset_pos = out_pos;
            memcpy(out + out_pos, in + pos, chunk_len);
            pos += chunk_len;
            out_pos += chunk_len;
            continue;
        }
        if ((ctrl & 0x80u) == 0) goto fail;

        if (pos + 4 > in_len) goto fail;
        size_t uncomp_size = (((size_t)(ctrl & 0x1fu) << 16) |
                              ((size_t)in[pos] << 8) | in[pos + 1]) + 1u;
        size_t comp_size = (((size_t)in[pos + 2] << 8) | in[pos + 3]) + 1u;
        pos += 4;

        uint8_t reset_mode = (ctrl >> 5) & 3u;
        if (reset_mode == 3u) {
            dict_reset_pos = out_pos;
        }
        if (reset_mode >= 1u) {
            xz_lzma_reset_state(st);
            state = 0;
            rep0 = rep1 = rep2 = rep3 = 0;
        }
        if (reset_mode >= 2u) {
            if (pos >= in_len) goto fail;
            uint8_t prop = in[pos++];
            if (prop >= 225u) goto fail;
            st->lc = prop % 9u;
            prop /= 9u;
            st->lp = prop % 5u;
            st->pb = prop / 5u;
            if (st->lc + st->lp > 4u || st->pb > LZMA_NUM_POS_BITS) goto fail;
            st->pos_mask = (1u << st->pb) - 1u;
            st->lit_pos_mask = (1u << st->lp) - 1u;
            props_ok = true;
        }
        if (!props_ok || pos + comp_size > in_len) goto fail;
        if (out_pos + uncomp_size > XZ_MINI_MAX_OUT) goto fail;

        while (out_pos + uncomp_size > cap) {
            cap *= 2u;
            uint8_t *nb = (uint8_t *)realloc(out, cap);
            if (!nb) goto fail;
            out = nb;
        }

        if (comp_size < 5u) goto fail;
        xz_rc_t rc;
        rc.src = in + pos;
        rc.src_len = comp_size;
        rc.pos = 0;
        rc.range = 0xffffffffu;
        rc.code = 0;
        rc.err = false;
        if (xz_rc_byte(&rc) != 0) goto fail;
        for (int i = 0; i < 4; i++) rc.code = (rc.code << 8) | xz_rc_byte(&rc);

        size_t chunk_end = out_pos + uncomp_size;
        while (out_pos < chunk_end && !rc.err) {
            uint32_t rel_pos = (uint32_t)(out_pos - dict_reset_pos);
            uint32_t pos_state = rel_pos & st->pos_mask;

            if (!xz_rc_bit(&rc, &st->is_match[state][pos_state])) {
                uint8_t prev = (rel_pos > 0) ? out[out_pos - 1] : 0;
                uint32_t lit_idx = ((rel_pos & st->lit_pos_mask) << st->lc) +
                                   (prev >> (8u - st->lc));
                uint16_t *probs = &st->lit_probs[lit_idx * 0x300u];
                uint32_t sym = 1u;
                if (state >= 7u) {
                    if (rel_pos < (size_t)rep0 + 1u) goto fail;
                    uint8_t match_byte = out[out_pos - (size_t)rep0 - 1u];
                    do {
                        uint32_t match_bit = (match_byte >> 7) & 1u;
                        match_byte <<= 1;
                        uint32_t bit = (uint32_t)xz_rc_bit(&rc, &probs[((1u + match_bit) << 8) + sym]);
                        sym = (sym << 1) | bit;
                        if (match_bit != bit) break;
                    } while (sym < 0x100u);
                }
                while (sym < 0x100u) {
                    sym = (sym << 1) | (uint32_t)xz_rc_bit(&rc, &probs[sym]);
                }
                out[out_pos++] = (uint8_t)(sym - 0x100u);
                state = (state < 4u) ? 0u : (state < 10u ? state - 3u : state - 6u);
            } else {
                uint32_t len;
                if (xz_rc_bit(&rc, &st->is_rep[state])) {
                    if (!xz_rc_bit(&rc, &st->is_rep0[state])) {
                        if (!xz_rc_bit(&rc, &st->is_rep0_long[state][pos_state])) {
                            state = (state < 7u) ? 9u : 11u;
                            if (rel_pos < (size_t)rep0 + 1u) goto fail;
                            out[out_pos] = out[out_pos - (size_t)rep0 - 1u];
                            out_pos++;
                            continue;
                        }
                    } else {
                        uint32_t dist;
                        if (!xz_rc_bit(&rc, &st->is_rep1[state])) {
                            dist = rep1;
                        } else {
                            if (!xz_rc_bit(&rc, &st->is_rep2[state])) {
                                dist = rep2;
                            } else {
                                dist = rep3;
                                rep3 = rep2;
                            }
                            rep2 = rep1;
                        }
                        rep1 = rep0;
                        rep0 = dist;
                    }
                    len = xz_dec_len(&rc, &st->rep_len, pos_state);
                    state = (state < 7u) ? 8u : 11u;
                } else {
                    rep3 = rep2;
                    rep2 = rep1;
                    rep1 = rep0;
                    len = xz_dec_len(&rc, &st->match_len, pos_state);
                    state = (state < 7u) ? 7u : 10u;
                    uint32_t len_state = len - LZMA_MATCH_MIN_LEN;
                    if (len_state >= LZMA_NUM_LEN_TO_POS) len_state = LZMA_NUM_LEN_TO_POS - 1u;
                    uint32_t pos_slot = xz_rc_bittree(&rc, st->pos_slot[len_state], 6u);
                    if (pos_slot >= LZMA_START_POS_MODEL) {
                        uint32_t num_direct = (pos_slot >> 1) - 1u;
                        rep0 = (2u | (pos_slot & 1u)) << num_direct;
                        if (pos_slot < LZMA_END_POS_MODEL) {
                            rep0 += xz_rc_bittree_rev(&rc,
                                st->pos_decoders + rep0 - pos_slot - 1u, num_direct);
                        } else {
                            rep0 += xz_rc_direct(&rc, num_direct - LZMA_NUM_ALIGN_BITS) << LZMA_NUM_ALIGN_BITS;
                            rep0 += xz_rc_bittree_rev(&rc, st->pos_align, LZMA_NUM_ALIGN_BITS);
                        }
                    } else {
                        rep0 = pos_slot;
                    }
                }
                if (rc.err || rel_pos < (size_t)rep0 + 1u || out_pos + len > chunk_end) goto fail;
                for (uint32_t i = 0; i < len; i++) {
                    out[out_pos] = out[out_pos - (size_t)rep0 - 1u];
                    out_pos++;
                }
            }
        }
        if (rc.err) goto fail;
        pos += comp_size;
    }

fail:
    free(out);
    free(st);
    return false;
}

static bool xz_mini_decompress(const uint8_t *in, size_t in_len,
                               uint8_t **out_buf, size_t *out_len) {
    static const uint8_t xz_magic[6] = { 0xfdu, '7', 'z', 'X', 'Z', 0x00u };
    if (!in || in_len < 24 || memcmp(in, xz_magic, 6) != 0) return false;

    size_t pos = 12; /* skip 6-byte magic + 2-byte flags + 4-byte CRC32 */
    if (pos >= in_len) return false;
    uint8_t hdr_size_b = in[pos];
    if (hdr_size_b == 0) return false; /* empty index */
    size_t hdr_size = ((size_t)hdr_size_b + 1u) * 4u;
    if (pos + hdr_size > in_len) return false;

    size_t p = pos + 1;
    uint8_t flags = in[p++];
    uint8_t num_filters = (flags & 0x03u) + 1u;
    if (num_filters != 1u) return false; /* expect single LZMA2 filter */
    if (flags & 0x40u) {
        uint64_t dummy;
        if (!xz_read_vli(in, pos + hdr_size - 4, &p, &dummy)) return false;
    }
    if (flags & 0x80u) {
        uint64_t dummy;
        if (!xz_read_vli(in, pos + hdr_size - 4, &p, &dummy)) return false;
    }
    uint64_t filter_id = 0, props_sz = 0;
    if (!xz_read_vli(in, pos + hdr_size - 4, &p, &filter_id) || filter_id != 0x21u) return false;
    if (!xz_read_vli(in, pos + hdr_size - 4, &p, &props_sz) || props_sz != 1u) return false;

    pos += hdr_size;
    return xz_decode_lzma2(in + pos, in_len - pos, out_buf, out_len);
}

#endif /* LIQUIDDOCK_XZ_MINI_H */
