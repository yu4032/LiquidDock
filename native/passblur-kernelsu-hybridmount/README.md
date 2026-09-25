# LiquidDock PassBlur KernelSU HybridMount

This directory reproduces the same module layout as the validated
`LiquidDock-PassBlur-KernelSU-HybridMount-OS3.0.310.0.WAOCNXM.zip` template.

## Exact target

- ROM: HyperOS OS3.0.310.0.WAOCNXM
- path: `/system_ext/lib64/libsurfaceflinger.so`
- stock SHA-256: `407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32`
- GNU Build ID: `39691db140edc0663fab2fde2475c091`
- predecessor LiquidDock native payload SHA-256:
  `ef523f9d57ebe5c3af2ec39747b07ab6d70bd69165461e09ddc0fc8f13f5d563`
- this payload SHA-256:
  `7cb2123d0b5d5cfd9ae63699c9624ebe306392b88f642ec609fdb5fc2247e30f`

## What is preserved

The uploaded HybridMount template already implements the per-PassBlur native freshness state:

- register each `drawPassBlurInternal` closure against its PassBlur instance;
- reject stale work before the expensive render body;
- perform a second freshness decision around `queuePassBlurBuffer`;
- clean the per-instance state when PassBlur is destroyed.

The build script reproduces those exact sparse binary changes.

## Pacing change added here

The stock constructor contains:

```text
0x4217a4  mov w1,#30
0x421804  bl property_get_int32
0x421818  mul w8,w0,w8
0x421824  stp x8,x9,[x19,#0xf8]
```

This build changes only the call at `0x421804` to:

```text
mov w0,#12
```

The original conversion then stores `12,000,000 ns` in `PassBlur + 0xf8`.
The decompiled `drawPassBlurIfNeed` normal `sfScale == 1.0f` path uses
`threshold / 2`, so the effective normal gate becomes approximately 6 ms.

This intentionally ignores `persist.sys.sf.draw.texture` for this exact-build
experiment, preventing an existing property value from silently restoring the
stock 30 ms pacing.

## Module layout

The CI artifact is directly flashable from KernelSU Manager:

```text
module.prop
customize.sh
original.sha256
patched.sha256
system/
  system_ext/
    lib64/
      libsurfaceflinger.so
```

KernelSU and the enabled Hybrid Mount metamodule are required.
