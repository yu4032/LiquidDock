/* Generated from the SHA-256 pinned turner 8.01.02.8604 ELF.
 * Closure bodies were re-located from fuxi 8584 by masked-instruction match
 * (0 differing words): body 0x1786e18->0x1789758, radius 0x14e9b78->0x14e3334,
 * Hero flight 0xa06648->0xa053cc (SfAnimConfig._checkSystemSupport). The radius
 * closure exists once per ShortCutCommonWidgetMixin.buildBackground copy; the
 * ShortcutIconWidget copy is at 0x1789ad8. Both share the first 436 bytes shape. */
#ifndef LD_ICON_GLASS_FINGERPRINTS_H
#define LD_ICON_GLASS_FINGERPRINTS_H
#include <stdint.h>

#define LD_ICON_GLASS_BODY_START 0x1789758u
#define LD_ICON_GLASS_BODY_SIZE 796u
#define LD_ICON_GLASS_BODY_FLAG_SITE 0x17897a4u
#define LD_ICON_GLASS_BODY_SIZE_SITE 0x17898d0u
#define LD_ICON_GLASS_RADIUS_SIZE 436u
#define LD_ICON_GLASS_RADIUS_A_START 0x14e3334u
#define LD_ICON_GLASS_RADIUS_A_SITE 0x14e344cu
#define LD_ICON_GLASS_RADIUS_B_START 0x1789ad8u
#define LD_ICON_GLASS_RADIUS_B_SITE 0x1789bf0u
#define LD_ICON_GLASS_FLIGHT_START 0xa053ccu
#define LD_ICON_GLASS_FLIGHT_SIZE 136u

/* Container bypass patches: force unconditional branch to background builder
 * when creating a folder or dragging in/out of a folder.
 * Site A: ShortcutIconWidget.buildDesktopIcon (0x1789520) + 0xd0 = 0x17895f0:
 *         b.le #0x1789618 -> b #0x1789618
 * Site B: ShortcutIconWidget.wrapWithFolderCreateAnim (0x178e9ac) + 0x68 = 0x178ea14:
 *         b.le #0x178ea30 -> b #0x178ea30 */
#define LD_ICON_GLASS_CONTAINER_A_SITE 0x17895f0u
#define LD_ICON_GLASS_CONTAINER_A_OLD  0x5400014du   /* b.le #0x1789618 */
#define LD_ICON_GLASS_CONTAINER_A_NEW  0x1400000au   /* b    #0x1789618 */

#define LD_ICON_GLASS_CONTAINER_B_SITE 0x178ea14u
#define LD_ICON_GLASS_CONTAINER_B_OLD  0x540000edu   /* b.le #0x178ea30 */
#define LD_ICON_GLASS_CONTAINER_B_NEW  0x14000007u   /* b    #0x178ea30 */

/* Body & Common background patches:
 * 1. Force radius closure creation (nop the skip branch):
 *    Body:   0x1789888: tbnz w0, #4, 0x17898bc -> nop (0xd503201f)
 *    Common: 0x14e313c: tbnz w0, #4, 0x14e315c -> nop (0xd503201f)
 * 2. Force scale and opacity to 1.0 (override animation lerp values d113, d10f):
 *    Body scale:     0x1789958: ldur d0, [x0, #7] -> fmov d0, #1.0 (0x1e6e1000)
 *    Body opacity:   0x1789978: ldur d0, [x1, #7] -> fmov d0, #1.0 (0x1e6e1000)
 *    Common scale:   0x14e31f8: ldur d0, [x0, #7] -> fmov d0, #1.0 (0x1e6e1000)
 *    Common opacity: 0x14e3218: ldur d0, [x1, #7] -> fmov d0, #1.0 (0x1e6e1000)
 */
#define LD_ICON_GLASS_BODY_RADIUS_BRANCH_SITE 0x1789888u
#define LD_ICON_GLASS_BODY_RADIUS_BRANCH_OLD  0x372001a0u   /* tbnz w0, #4, #0x17898bc */
#define LD_ICON_GLASS_BODY_RADIUS_BRANCH_NEW  0xd503201fu   /* nop */

#define LD_ICON_GLASS_BODY_SCALE_SITE         0x1789958u
#define LD_ICON_GLASS_BODY_SCALE_OLD          0xfc407000u   /* ldur d0, [x0, #7] */
#define LD_ICON_GLASS_BODY_SCALE_NEW          0x1e6e1000u   /* fmov d0, #1.00000000 */

#define LD_ICON_GLASS_BODY_OPACITY_SITE       0x1789978u
#define LD_ICON_GLASS_BODY_OPACITY_OLD        0xfc407020u   /* ldur d0, [x1, #7] */
#define LD_ICON_GLASS_BODY_OPACITY_NEW        0x1e6e1000u   /* fmov d0, #1.00000000 */

#define LD_ICON_GLASS_COMMON_RADIUS_BRANCH_SITE 0x14e313cu
#define LD_ICON_GLASS_COMMON_RADIUS_BRANCH_OLD  0x37200100u /* tbnz w0, #4, #0x14e315c */
#define LD_ICON_GLASS_COMMON_RADIUS_BRANCH_NEW  0xd503201fu /* nop */

#define LD_ICON_GLASS_COMMON_SCALE_SITE         0x14e31f8u
#define LD_ICON_GLASS_COMMON_SCALE_OLD          0xfc407000u /* ldur d0, [x0, #7] */
#define LD_ICON_GLASS_COMMON_SCALE_NEW          0x1e6e1000u /* fmov d0, #1.00000000 */

#define LD_ICON_GLASS_COMMON_OPACITY_SITE       0x14e3218u
#define LD_ICON_GLASS_COMMON_OPACITY_OLD        0xfc407020u /* ldur d0, [x1, #7] */
#define LD_ICON_GLASS_COMMON_OPACITY_NEW        0x1e6e1000u /* fmov d0, #1.00000000 */

/* FolderBlurUtils.buildFolderGlass material injection & radius bypass patches:
 * Direct replacement of the Clip child with FolderBlurUtils.buildFolderGlass (0x11aebdc).
 *
 * System A (ShortcutIconWidget radius closure 0x1789ad8):
 * - Bypass conditions at 0x1789b40: tbnz w0, #4, #0x1789b58 -> b #0x1789b80 (0x14000010)
 * - Replace Obx child builder (0x1789c28 ~ 0x1789c48) with buildFolderGlass:
 *   0x1789c28: add  x4, x27, #0xc, lsl #12 (0x91403364)
 *   0x1789c2c: ldr  x4, [x4, #0x6f0]       (0xf9437884)
 *   0x1789c30: bl   #0x11aebdc             (0x97e893eb)
 *   0x1789c34: stur x0, [x29, #-8]         (0xf81f83a0)
 *   0x1789c38: b    #0x1789c4c             (0x14000005)
 *   0x1789c3c: nop                         (0xd503201f)
 *   0x1789c40: nop                         (0xd503201f)
 *   0x1789c44: nop                         (0xd503201f)
 *   0x1789c48: nop                         (0xd503201f)
 *
 * System B (ShortCutCommonWidgetMixin radius closure 0x14e3334):
 * - Bypass conditions at 0x14e339c: tbnz w0, #4, #0x14e33b4 -> b #0x14e33dc (0x14000010)
 * - Replace Obx child builder (0x14e3484 ~ 0x14e34a4) with buildFolderGlass:
 *   0x14e3484: add  x4, x27, #0xc, lsl #12 (0x91403364)
 *   0x14e3488: ldr  x4, [x4, #0x6f0]       (0xf9437884)
 *   0x14e348c: bl   #0x11aebdc             (0x97f32dd4)
 *   0x14e3490: stur x0, [x29, #-8]         (0xf81f83a0)
 *   0x14e3494: b    #0x14e34a8             (0x14000005)
 *   0x14e3498: nop                         (0xd503201f)
 *   0x14e349c: nop                         (0xd503201f)
 *   0x14e34a0: nop                         (0xd503201f)
 *   0x14e34a4: nop                         (0xd503201f)
 */
#define LD_ICON_GLASS_A_RADIUS_CHECK_SITE   0x1789b40u
#define LD_ICON_GLASS_A_RADIUS_CHECK_OLD    0x372000c0u  /* tbnz w0, #4, #0x1789b58 */
#define LD_ICON_GLASS_A_RADIUS_CHECK_NEW    0x14000010u  /* b    #0x1789b80 */

#define LD_ICON_GLASS_B_RADIUS_CHECK_SITE   0x14e339cu
#define LD_ICON_GLASS_B_RADIUS_CHECK_OLD    0x372000c0u  /* tbnz w0, #4, #0x14e33b4 */
#define LD_ICON_GLASS_B_RADIUS_CHECK_NEW    0x14000010u  /* b    #0x14e33dc */

#define LD_ICON_GLASS_A_CHILD_START         0x1789c28u
#define LD_ICON_GLASS_B_CHILD_START         0x14e3484u
#define LD_ICON_GLASS_CHILD_WORDS           9u

static const uint32_t ld_icon_glass_a_child_old[9] = {
    0x940c68ecu, /* bl #0x1aa3fd8 */
    0xf85f03a2u, /* ldur x2, [x29, #-0x10] */
    0x9142b761u, /* add x1, x27, #0xad, lsl #12 */
    0xf941a821u, /* ldr x1, [x1, #0x350] */
    0xf81f83a0u, /* stur x0, [x29, #-8] */
    0x94114b35u, /* bl #0x1bdc910 */
    0xaa0003e1u, /* mov x1, x0 */
    0xf85f83a0u, /* ldur x0, [x29, #-8] */
    0xb800b001u, /* stur w1, [x0, #0xb] */
};

static const uint32_t ld_icon_glass_a_child_new[9] = {
    0x91403364u, /* add x4, x27, #0xc, lsl #12 */
    0xf9437884u, /* ldr x4, [x4, #0x6f0] */
    0x97e893ebu, /* bl #0x11aebdc (FolderBlurUtils.buildFolderGlass) */
    0xf81f83a0u, /* stur x0, [x29, #-8] */
    0x14000005u, /* b #0x1789c4c (proceed to ClipRRect constructor) */
    0xd503201fu, /* nop */
    0xd503201fu, /* nop */
    0xd503201fu, /* nop */
    0xd503201fu, /* nop */
};

static const uint32_t ld_icon_glass_b_child_old[9] = {
    0x941702d5u, /* bl #0x1aa3fd8 */
    0xf85f03a2u, /* ldur x2, [x29, #-0x10] */
    0x9142f761u, /* add x1, x27, #0xbd, lsl #12 */
    0xf9460821u, /* ldr x1, [x1, #0xc10] */
    0xf81f83a0u, /* stur x0, [x29, #-8] */
    0x941be51eu, /* bl #0x1bdc910 */
    0xaa0003e1u, /* mov x1, x0 */
    0xf85f83a0u, /* ldur x0, [x29, #-8] */
    0xb800b001u, /* stur w1, [x0, #0xb] */
};

static const uint32_t ld_icon_glass_b_child_new[9] = {
    0x91403364u, /* add x4, x27, #0xc, lsl #12 */
    0xf9437884u, /* ldr x4, [x4, #0x6f0] */
    0x97f32dd4u, /* bl #0x11aebdc (FolderBlurUtils.buildFolderGlass) */
    0xf81f83a0u, /* stur x0, [x29, #-8] */
    0x14000005u, /* b #0x14e34a8 (proceed to ClipRRect constructor) */
    0xd503201fu, /* nop */
    0xd503201fu, /* nop */
    0xd503201fu, /* nop */
    0xd503201fu, /* nop */
};

/* 0x1789758 SHA256 5dc3d428b6f2c6152cfd028b85e0cea237ef8135cf84760a647ddd5cfd71da7e */
static const unsigned char ld_icon_glass_body_sha[32]={
  0x5d, 0xc3, 0xd4, 0x28, 0xb6, 0xf2, 0xc6, 0x15,
  0x2c, 0xfd, 0x02, 0x8b, 0x85, 0xe0, 0xce, 0xa2,
  0x37, 0xef, 0x81, 0x35, 0xcf, 0x84, 0x76, 0x0a,
  0x64, 0x7d, 0xdd, 0x5c, 0xfd, 0x71, 0xda, 0x7e,
};

/* 0x14e3334 SHA256 f3b99b6545dbb3a53c69a5b402f7e4e35cd770eb9f5352cfdbb73c963385242b */
static const unsigned char ld_icon_glass_radius_a_sha[32]={
  0xf3, 0xb9, 0x9b, 0x65, 0x45, 0xdb, 0xb3, 0xa5,
  0x3c, 0x69, 0xa5, 0xb4, 0x02, 0xf7, 0xe4, 0xe3,
  0x5c, 0xd7, 0x70, 0xeb, 0x9f, 0x53, 0x52, 0xcf,
  0xdb, 0xb7, 0x3c, 0x96, 0x33, 0x85, 0x24, 0x2b,
};

/* 0x1789ad8 SHA256 93dde383c8fa14b38faf45125f5d77f628d9b8196619841620af972a045e3f45 */
static const unsigned char ld_icon_glass_radius_b_sha[32]={
  0x93, 0xdd, 0xe3, 0x83, 0xc8, 0xfa, 0x14, 0xb3,
  0x8f, 0xaf, 0x45, 0x12, 0x5f, 0x5d, 0x77, 0xf6,
  0x28, 0xd9, 0xb8, 0x19, 0x66, 0x19, 0x84, 0x16,
  0x20, 0xaf, 0x97, 0x2a, 0x04, 0x5e, 0x3f, 0x45,
};

/* 0xa053cc SHA256 d1deef04d2272204bc42a6c6a4d6ace0d5a6739e994f69e0e917344377a3c279 */
static const unsigned char ld_icon_glass_flight_sha[32]={
  0xd1, 0xde, 0xef, 0x04, 0xd2, 0x27, 0x22, 0x04,
  0xbc, 0x42, 0xa6, 0xc6, 0xa4, 0xd6, 0xac, 0xe0,
  0xd5, 0xa6, 0x73, 0x9e, 0x99, 0x4f, 0x69, 0xe0,
  0xe9, 0x17, 0x34, 0x43, 0x77, 0xa3, 0xc2, 0x79,
};

/* SfAnimConfig._checkSystemSupport (8604: 0xa053cc, 34 words) */
static const uint32_t ld_pat_flight[34] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10061efu, 0x9400001fu,
    0xf81f83a0u, 0xf9403f40u, 0xf9529000u, 0xf9402370u,
    0x6b10001fu, 0x54000061u, 0xf9403362u, 0x944758cbu,
    0xaa1603e1u, 0xd28000c2u, 0xf81f03a0u, 0x94476075u,
    0x91409370u, 0xf9475610u, 0xb800f010u, 0x91409370u,
    0xf9475a10u, 0xb8013010u, 0xf85f83a1u, 0xb8017001u,
    0xf90001e0u, 0x97fa4839u, 0xf85f03a1u, 0xaa0003e2u,
    0xf9403b64u, 0x9401f93fu, 0xf85f83a0u, 0xaa1d03efu,
    0xa8c179fdu, 0xd65f03c0u,
};

/* AppItemFactory.build around bl HotSeatsConstants2.calcScaledIconSize (0x183ebe4, 11 words).
 * Hook site is +0x08 (0x183ebec: mov v1.16b, v0.16b) where d0 holds the scaled Dock icon size. */
#define LD_ICON_GLASS_DOCK_SCALE_START 0x183ebe4u
static const uint32_t ld_pat_dock_scaled_size[11] = {
    0xfc40f000u, 0x97c9a6c6u, 0x4ea01c01u, 0x1e621000u,
    0xfc1e03a1u, 0x1e602822u, 0xf85f83a0u, 0xb840f001u,
    0x8b1c8021u, 0xf81f03a1u, 0xa9460b40u,
};

/* GetStream.add (0x897800, 16 words) used by RxObjectMixin.refresh to notify Obx. */
#define LD_ICON_GLASS_GETSTREAM_ADD_START 0x897800u
static const uint32_t ld_pat_getstream_add[16] = {
    0xa9bf79fdu, 0xaa0f03fdu, 0xd10041efu, 0xaa0103e4u,
    0xaa0203e3u, 0xf81f83a1u, 0xf81f03a2u, 0xb8407082u,
    0x8b1c8042u, 0xaa0303e0u, 0xaa1603e1u, 0x6b16005fu,
    0x540000e0u, 0xb8417044u, 0x8b1c8084u, 0xf940f768u,
};

#endif
