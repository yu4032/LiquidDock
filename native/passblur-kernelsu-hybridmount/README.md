# LiquidDock PassBlur KernelSU HybridMount

This module targets the exact HyperOS SurfaceFlinger build used by
OS3.0.310.0.WAOCNXM and keeps the validated KernelSU + Hybrid Mount deployment model.

The current patch no longer lowers the native PassBlur pacing interval. Instead it fixes
the structural synchronization bottleneck found in the decompiled SurfaceFlinger pipeline.

## Exact target

- ROM: HyperOS OS3.0.310.0.WAOCNXM
- target: `/system_ext/lib64/libsurfaceflinger.so`
- stock SHA-256:
  `407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32`
- GNU Build ID:
  `39691db140edc0663fab2fde2475c091`
- predecessor freshness payload:
  `ef523f9d57ebe5c3af2ec39747b07ab6d70bd69165461e09ddc0fc8f13f5d563`
- deprecated Pacing12 payload:
  `7cb2123d0b5d5cfd9ae63699c9624ebe306392b88f642ec609fdb5fc2247e30f`
- current async-release payload:
  `0ce7ceea23efd5a9f7bdd5f7858e3e8d21bb77c5d9261088a18726eeccf5bc48`

Any other input binary is rejected before a module payload is generated.

## Decompiled bottleneck

`MiOutputManager::drawPassBlurInternal()` submits PassBlur work to
`BackgroundExecutor::bgDrawPassBlur()` and stores a `future<bool>`.

At the end of the composition lifecycle, `MiOutputManager::releaseCurRes()` iterates those
futures and calls:

```text
std::__assoc_state<bool>::move()
```

The decompiled implementation of that function is blocking:

```text
mutex.lock()
__assoc_sub_state::__sub_wait(...)
read result
mutex.unlock()
```

Therefore SurfaceFlinger does not merely inspect whether PassBlur background work completed.
It synchronously waits for unfinished PassBlur work before continuing release/composition work.

The PassBlur worker has another possible wait in its RenderEngine path. RenderEngine vtable
slot `+0x68` returns either an immediate
`expected<sp<Fence>, int>` or a
`future<expected<sp<Fence>, int>>`. On the future branch the PassBlur worker immediately
calls the matching `__assoc_state<expected<...>>::move()`.

The resulting dependency is:

```text
SurfaceFlinger composition/release
        |
        v
releaseCurRes()
        |
        | blocking future<bool>::get/move
        v
PassBlur worker
        |
        | possible blocking RenderEngine future
        v
RenderEngine / GPU
```

This is the structural reason lowering the PassBlur interval caused severe whole-system
animation jank: more PassBlur work was inserted into a chain that SurfaceFlinger later joins.

## Current scheduler semantics

The predecessor HybridMount payload already added per-PassBlur freshness state:

- registration at `drawPassBlurInternal()` submission;
- worker-entry stale rejection;
- a late pre-queue stale check;
- per-instance cleanup from the PassBlur destructor.

The current patch keeps the useful **worker-entry stale rejection** so jobs that have not
started expensive rendering can be discarded cheaply.

It removes the predecessor's **late pre-queue cancellation**. Once a RenderEngine job has
actually started, it is allowed to queue its completed buffer. This prevents continuous motion
from causing starvation after SurfaceFlinger stops waiting synchronously for each render.

## Non-blocking release patch

The exact stock call site in `releaseCurRes()` is:

```text
0x54e5f4 -> std::__assoc_state<bool>::move()
```

It is redirected to a small helper in the verified RX code cave at `0xa4f660`.

Helper semantics:

```text
load shared-state flags at +0x70 with acquire semantics

if READY bit is clear:
    return false immediately

if READY bit is set:
    tail-call the original std::__assoc_state<bool>::move()
```

The original promise completion sets the state bits with `state |= 5`, so bit 0 is the ready
indicator used by the helper.

This preserves the original blocking/get behavior only for futures that are already ready.
An unfinished PassBlur future no longer makes SurfaceFlinger wait.

A non-ready result follows the existing `releaseCurRes()` false-result path, which clears the
old LayerSettings state and requests a redraw on a later frame.

## Stock pacing restored

The deprecated Pacing12 experiment replaced the constructor property read with `mov w0,#12`.

The current upgrade explicitly restores:

```text
0x421804 -> original BL property_get_int32
```

Therefore the original:

```text
persist.sys.sf.draw.texture
default 30 ms
```

authority is active again.

The current fix is intentionally about removing the blocking dependency, not increasing global
PassBlur submission frequency.

## Stock queue behavior restored

The predecessor freshness payload changed the post-render path around
`queuePassBlurBuffer()` so a job that became stale while rendering could be cancelled.

The current upgrade restores the original instructions at:

```text
0x55a040 -> original BL PassBlur::queuePassBlurBuffer
0x55a0d8 -> original mov w22,#1
```

Only the early stale gate remains.

## Supported upgrade sources

The KernelSU installer accepts:

1. exact stock SurfaceFlinger;
2. the predecessor freshness-only payload;
3. the deprecated Pacing12 payload;
4. the current async-release payload.

For stock it installs the early-freshness patch and async-release helper.

For either predecessor payload it restores stock pacing and stock post-render queue behavior,
then adds async release.

Every path must produce exactly:

`0ce7ceea23efd5a9f7bdd5f7858e3e8d21bb77c5d9261088a18726eeccf5bc48`

or installation aborts.

## Flashable ZIP

CI emits:

`LiquidDock-PassBlur-KernelSU-HybridMount-OS3.0.310.0.WAOCNXM-AsyncRelease.zip`

The ZIP carries exact patch blobs rather than a complete system library. During installation,
`customize.sh` verifies the currently mounted binary, stages a copy under:

```text
system/system_ext/lib64/libsurfaceflinger.so
```

applies only the patch set appropriate for that exact input SHA, verifies the final SHA, removes
the installation-only blobs, and leaves Hybrid Mount to mount the payload on reboot.

## Requirements

- KernelSU
- arm64
- enabled Hybrid Mount metamodule
- exact supported OS3.0.310.0.WAOCNXM SurfaceFlinger binary or one of the explicitly recognized
  predecessor payloads

No Recents timing hook, permanent `setForceRefresh`, global 12 ms pacing override,
ScreenCapture, PixelCopy or Bitmap capture is part of this native fix.
