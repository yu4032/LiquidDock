#ifndef LD_PAGE_FIELDS_H
#define LD_PAGE_FIELDS_H

#include <stddef.h>
#include <stdint.h>

struct ld_page_fields {
    unsigned layout, items, model, container, quick_call, rx_value;
};

static struct ld_page_fields g_page_fields;

/* Match LDUR while leaving its signed imm9 free. Only tagged field offsets
 * in the positive LDUR range are valid for the object reads below. */
static unsigned ld_page_field(uint32_t word, uint32_t opcode)
{
    unsigned offset = (word >> 12) & 0x1ffu;
    return (word & 0xffe00fffu) == opcode && offset < 256 && (offset & 3u) == 3u
        ? offset : 0;
}

static int ld_page_merge_field(unsigned *field, unsigned candidate)
{
    if (*field && *field != candidate) return 0;
    *field = candidate;
    return 1;
}

static unsigned ld_decode_page_rx_value(const uint32_t *getter, size_t count)
{
    unsigned value = 0;
    for (size_t i = 1; i + 7 < count; i++) {
        unsigned candidate = ld_page_field(getter[i], 0xb8400020u);
        if (candidate && (getter[i - 1] & 0xffe00fffu) == 0xf84003a1u &&
            getter[i + 1] == 0x8b1c8000u && getter[i + 2] == 0xf9402370u &&
            getter[i + 3] == 0x6b10001fu && (getter[i + 4] & 0xff00001fu) == 0x54000000u &&
            getter[i + 5] == 0xaa1d03efu && getter[i + 7] == 0xd65f03c0u &&
            !ld_page_merge_field(&value, candidate)) return 0;
    }
    return value;
}

static int ld_decode_page_fields(struct ld_page_fields *out,
                                 const uint32_t *process, size_t process_count,
                                 const uint32_t *classifier, size_t classifier_count,
                                 const uint32_t *quick, size_t quick_count)
{
    struct ld_page_fields fields = {0};
    for (size_t i = 0; i + 4 < process_count; i++) {
        unsigned items = ld_page_field(process[i], 0xb8400043u); /* ldur w3, [x2, #items] */
        if (items && process[i + 1] == 0x8b1c8063u &&
            (process[i + 2] & 0xffe00fffu) == 0xf80003a3u &&
            process[i + 3] == 0xb840b064u && process[i + 4] == 0x93417c85u &&
            !ld_page_merge_field(&fields.items, items)) return 0;
    }
    for (size_t i = 0; i + 6 < classifier_count; i++) {
        unsigned model = ld_page_field(classifier[i], 0xb8400001u);
        unsigned container = ld_page_field(classifier[i + 2], 0xf8400020u);
        /* Read the cell model, then classify its container with -104. */
        if (model && container && classifier[i + 1] == 0x8b1c8021u &&
            classifier[i + 3] == 0xb101a01fu && classifier[i + 4] == 0x910082d0u &&
            classifier[i + 5] == 0x9100c2d1u && classifier[i + 6] == 0x9a910204u &&
            (!ld_page_merge_field(&fields.model, model) ||
             !ld_page_merge_field(&fields.container, container))) return 0;
    }
    for (size_t i = 1; i + 7 < quick_count; i++) {
        unsigned layout = ld_page_field(quick[i], 0xb8400001u);
        unsigned flag = ld_page_field(quick[i + 2], 0xb8400020u);
        /* The indexed screen comes back from the Dart dispatch call. */
        if (layout && flag && quick[i - 1] == 0xd63f03c0u && quick[i + 1] == 0x8b1c8021u &&
            quick[i + 3] == 0x8b1c8000u && quick[i + 4] == 0xf9402370u &&
            quick[i + 5] == 0x6b10001fu && (quick[i + 6] & 0xff00001fu) == 0x54000000u &&
            quick[i + 7] == 0xaa1d03efu &&
            (!ld_page_merge_field(&fields.layout, layout) ||
             !ld_page_merge_field(&fields.quick_call, flag))) return 0;
    }
    if (!fields.items || !fields.model || !fields.container || !fields.layout || !fields.quick_call)
        return 0;
    *out = fields;
    return 1;
}

#endif
