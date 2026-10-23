#ifndef LD_NATIVE_GLASS_SCHEMA_H
#define LD_NATIVE_GLASS_SCHEMA_H
#include <stdint.h>
#include <stddef.h>
#define LD_NATIVE_GLASS_COUNT 39
struct ld_native_glass_field {const char *name; unsigned offset, count, component; double min,max;};
/* 8583 ImageFilter::initGlassV7 -> DlGlassImageFilter -> native Render/Tint.
 * count=0: scalar float32; otherwise an owned std::vector<float>. */
static const struct ld_native_glass_field ld_native_glass_fields[LD_NATIVE_GLASS_COUNT]={
    {"edge_px",0x3c,0,0,0.1,128.0},
    {"edge_pow",0x40,0,0,0.1,8.0},
    {"thickness_px",0x44,0,0,0.0,480.0},
    {"reflect_offset_px",0x48,0,0,0.0,400.0},
    {"ior",0x90,0,0,1.0,3.0},
    {"light_x",0x50,3,0,-2.0,2.0},
    {"light_y",0x50,3,1,-2.0,2.0},
    {"light_z",0x50,3,2,-2.0,2.0},
    {"light_intensity",0x68,0,0,0.0,4.0},
    {"light_opposite",0x6c,0,0,0.0,4.0},
    {"light_range",0x70,0,0,0.01,2.0},
    {"light_edge_pow",0x9c,0,0,0.1,8.0},
    {"reflection_strength",0x98,0,0,0.0,2.0},
    {"reflection_lighten",0x94,0,0,0.0,2.0},
    {"inner_bottom",0xe0,0,0,0.0,2.0},
    {"glass_r",0xa8,4,0,0.0,1.0},
    {"glass_g",0xa8,4,1,0.0,1.0},
    {"glass_b",0xa8,4,2,0.0,1.0},
    {"glass_a",0xa8,4,3,0.0,1.0},
    {"alpha",0xf4,0,0,0.0,1.0},
    {"unshade",0xfc,0,0,0.0,1.0},
    {"color_pow",0xf0,0,0,0.1,4.0},
    {"blur_refract",0x28,0,0,0.0,1000.0},
    {"blur_background",0x2c,0,0,0.0,1000.0},
    {"darker",0x74,0,0,0.0,1.0},
    {"darker_low",0x78,2,0,0.0,1.0},
    {"darker_high",0x78,2,1,0.0,1.0},
    {"bg_saturation",0xa4,0,0,0.0,3.0},
    {"bg_brightness",0xc0,0,0,0.0,3.0},
    {"saturation",0xe8,0,0,0.0,3.0},
    {"brightness",0xec,0,0,0.0,3.0},
    {"inner_white",0xc4,0,0,0.0,1.0},
    {"inner_mix",0xa0,0,0,0.0,1.0},
    {"luminance_amount",0xe4,0,0,0.0,1.0},
    {"luminance_c0",0xc8,4,0,-8.0,8.0},
    {"luminance_c1",0xc8,4,1,-8.0,8.0},
    {"luminance_c2",0xc8,4,2,-8.0,8.0},
    {"luminance_c3",0xc8,4,3,-8.0,8.0},
    {"burn",0xf8,0,0,0.1,8.0},
};
struct ld_native_glass_options {uint64_t mask; double values[LD_NATIVE_GLASS_COUNT];};
#endif
