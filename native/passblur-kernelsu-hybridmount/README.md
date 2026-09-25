# LiquidDock PassBlur KernelSU HybridMount

This module follows the validated
`LiquidDock-PassBlur-KernelSU-HybridMount-OS3.0.310.0.WAOCNXM.zip` deployment model,
but no longer ships a complete SurfaceFlinger binary inside the ZIP.

Instead, KernelSU verifies the device's exact currently mounted binary, copies it into
the module staging tree, applies a small exact-offset patch set, verifies the final
SHA-256, removes the installer-only patch blobs, and lets Hybrid Mount mount the
generated payload on the next reboot.

## Exact target

- ROM: HyperOS OS3.0.310.0.WAOCNXM
- target: `/system_ext/lib64/libsurfaceflinger.so`
- stock SHA-256: `407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32`
- GNU Build ID: `39691db140edc0663fab2fde2475c091`
- predecessor LiquidDock native payload SHA-256:
  `ef523f9d57ebe5c3af2ec39747b07ab6d70bd69165461e09ddc0fc8f13f5d563`
- final pacing12 payload SHA-256:
  `7cb2123d0b5d5cfd9ae63699c9624ebe306392b88f642ec609fdb5fc2247e30f`

Any other input SHA is rejected before writing a module payload.

## Preserved native freshness patch

The predecessor HybridMount payload already implements the per-PassBlur freshness/latest-only
logic derived from the matching SurfaceFlinger binary:

- register each `drawPassBlurInternal` closure against its PassBlur instance;
- reject stale work before the expensive render body;
- make a second freshness decision around `queuePassBlurBuffer`;
- clean per-instance state from the PassBlur destructor.

The sparse patch manifest reproduces those exact predecessor changes when installation starts
from stock.

If installation starts while the predecessor module is already mounted, only the additional
pacing patch is applied.

## Pacing change

The stock constructor contains:

```text
0x4217a4  mov w1,#30
0x421804  bl property_get_int32
0x421818  mul w8,w0,w8
0x421824  stp x8,x9,[x19,#0xf8]
```

The pacing12 build changes:

```text
0x421804  mov w0,#12
```

The original x1,000,000 conversion remains unchanged, so `PassBlur + 0xf8`
receives 12,000,000 ns.

The decompiled `drawPassBlurIfNeed` normal `sfScale == 1.0f` path uses
`threshold / 2`, giving an approximately 6 ms normal gate.

The exact-build experiment deliberately ignores `persist.sys.sf.draw.texture` at this
constructor site so an existing property cannot silently restore the stock 30 ms value.

## Flashable ZIP

CI emits:

`LiquidDock-PassBlur-KernelSU-HybridMount-OS3.0.310.0.WAOCNXM-Pacing12.zip`

The ZIP contains the original module metadata/customize flow plus installer-only patch blobs:

```text
module.prop
customize.sh
original.sha256
previous.sha256
patched.sha256
patches/*.bin
```

During installation, `customize.sh` creates:

```text
system/system_ext/lib64/libsurfaceflinger.so
```

then verifies that it hashes exactly to the final pacing12 SHA and deletes `patches/`.
The installed module layout therefore returns to the same HybridMount shape as the
validated predecessor package.

## Requirements

- KernelSU
- arm64
- enabled Hybrid Mount metamodule
- exact supported OS3.0.310.0.WAOCNXM SurfaceFlinger binary, or the exact predecessor/current payload

No property service, Recents timing hook, permanent `setForceRefresh`, ScreenCapture,
PixelCopy or Bitmap path is involved.
