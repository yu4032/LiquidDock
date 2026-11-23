#include <assert.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

static void ld_blog(const char *fmt, ...) { (void)fmt; }
#include "prismal_glass.h"

static float read_f32(const uint8_t *buf, size_t off) {
    float v = 0.0f;
    memcpy(&v, buf + off, 4);
    return v;
}

static uint32_t read_u32(const uint8_t *buf, size_t off) {
    uint32_t v = 0;
    memcpy(&v, buf + off, 4);
    return v;
}

int main(void) {
    assert(sizeof(g_prismal_os4_spv) > 0);
    assert((sizeof(g_prismal_os4_spv) & 3u) == 0);
    assert(g_prismal_os4_spv[0] == LD_PRISMAL_SPV_MAGIC);

    /* Test ld_prismal_patch_rodata with an aligned buffer fitting LD_PRISMAL_SPV_MAX_BYTES */
    size_t map_sz = 32768u;
    uint8_t *buf = mmap(NULL, map_sz, PROT_READ | PROT_WRITE,
                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(buf != MAP_FAILED);

    uint32_t *size_ptr = (uint32_t *)(buf + 0x1000u);
    uint32_t *data_ptr = (uint32_t *)(buf + 0x1004u);
    *size_ptr = LD_PRISMAL_SPV_MAX_BYTES;
    data_ptr[0] = LD_PRISMAL_SPV_MAGIC;
    assert(mprotect(buf, map_sz, PROT_READ) == 0);

    static const uint32_t dummy_spv[4] = { LD_PRISMAL_SPV_MAGIC, 0x00010300u, 0u, 1u };
    int rc = ld_prismal_patch_rodata((uintptr_t)size_ptr, (uintptr_t)data_ptr,
                                     dummy_spv, sizeof(dummy_spv));
    assert(rc == 0);
    assert(*size_ptr == (uint32_t)sizeof(dummy_spv));
    assert(memcmp(data_ptr, dummy_spv, sizeof(dummy_spv)) == 0);
    munmap(buf, map_sz);

    /* Test ld_prismal_fill_ubo preserves Impeller slots & injects v2.7.9 Prismal uniforms */
    uint8_t ubo[0x180];
    memset(ubo, 0, sizeof(ubo));
    ld_write_vec2(ubo, 0x00u, 100.0f, 200.0f);         /* uResolution */
    ld_write_vec3(ubo, 0x10u, 400.0f, 180.0f, 32.0f);  /* uGeoPxSize */
    ld_write_f32(ubo, 0x1cu, 24.0f);                   /* orig uShapeEdgePx */
    ld_write_f32(ubo, 0x7cu, 0.95f);                   /* uAlpha */
    ld_write_vec2(ubo, 0x80u, 1.0f / 2560.0f, 1.0f / 1600.0f); /* uInvResolution */
    ld_write_vec2(ubo, 0x88u, 3.0f, 3.0f);             /* uScale */
    ld_write_u32(ubo, 0x90u, 0u);                      /* uUseMaskTex */
    uint32_t cs = 1u;
    memcpy(ubo + 0x160u, &cs, 4);                      /* colorSpaceConvertMode */

    struct ld_prismal_options opts;
    ld_prismal_options_defaults(&opts);
    opts.continuous_corners = 1;
    opts.show_normals = 1;
    opts.backlit_enabled = 1;

    ld_prismal_fill_ubo(ubo, &opts, 3.0f, 1);

    /* Impeller fields untouched */
    assert(fabsf(read_f32(ubo, 0x00u) - 100.0f) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x10u) - 400.0f) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x14u) - 180.0f) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x18u) - 32.0f) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x7cu) - 0.95f) < 1e-5f);
    assert(read_u32(ubo, 0x160u) == 1u);

    /* orig_edge_px preserved at 0xac; parallax at 0xa8 */
    assert(fabsf(read_f32(ubo, 0xa8u) - 1.0f) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0xacu) - 24.0f) < 1e-5f);

    /* refraction_inset and smin_smoothing match Miuix307PrismalMaterial (not density-scaled) */
    assert(fabsf(read_f32(ubo, 0x1cu) - 20.0f) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x20u) - 1.8f) < 1e-5f);

    /* PrismalRenderer.java u_lensRefractionPx = min(refractionHeight*2*1.15*1.30*1.0, 180*0.85) = 153.0 */
    assert(fabsf(read_f32(ubo, 0x48u) - 57.0f) < 1e-4f);
    assert(fabsf(read_f32(ubo, 0x4cu) - 153.0f) < 1e-3f);

    /* Optics fields forwarded for unified bevel+dome Snell refraction */
    assert(fabsf(read_f32(ubo, 0x24u) - (float)opts.edge_falloff) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x28u) - (float)opts.ior) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x44u) - (float)opts.displacement_scale) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x108u) - (float)opts.lens_refraction) < 1e-5f);

    /* PrismalOs4InvariantShader Fresnel R0 at ior=1.55 */
    float exp_r0 = ((1.0f - 1.55f) / (1.0f + 1.55f)) * ((1.0f - 1.55f) / (1.0f + 1.55f));
    assert(fabsf(read_f32(ubo, 0x9cu) - exp_r0) < 1e-5f);

    /* flags bits: bit0=cont(1), bit1=blur>=0.5(2), bit2=show_normals(4), bit3=has_sharp(8) */
    assert(read_u32(ubo, 0x94u) == 15u);

    /* p_backlitParams (0x110..0x11c): widthPx, strength, side_start, side_span */
    assert(fabsf(read_f32(ubo, 0x110u) - 1.2f) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x114u) - 0.20f) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x118u) - 0.10f) < 1e-5f);
    assert(fabsf(read_f32(ubo, 0x11cu) - (0.19f + 0.60f * 0.34f)) < 1e-5f);

    /* blur=0 clears bit1 */
    opts.blur = 0.0;
    opts.continuous_corners = 0;
    opts.show_normals = 0;
    ld_prismal_fill_ubo(ubo, &opts, 3.0f, 1);
    assert(read_u32(ubo, 0x94u) == 8u);

    (void)ld_prismal_glass_install;
    (void)ld_prismal_current_blur_px;
    puts("PASS prismal_glass_test");
    return 0;
}
