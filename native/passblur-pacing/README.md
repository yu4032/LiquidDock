# LiquidDock PassBlur pacing companion

This is an experimental **systemless** companion for the HyperOS PassBlur producer.

## Reverse-engineered basis

The matching `libsurfaceflinger.so` input has SHA-256:

`407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32`

and GNU Build ID:

`39691db140edc0663fab2fde2475c091`

For this binary:

- `android::PassBlur::PassBlur` is at `0x421730`.
- It calls `property_get_int32("persist.sys.sf.draw.texture", 30)`.
- The value is converted from milliseconds to nanoseconds and stored at `PassBlur + 0xf8`.
- `MiOutputManager::drawPassBlurIfNeed` is at `0x54d2d0`.
- When `sfScale == 1.0f`, its normal time gate compares against `threshold / 2`.
- Therefore the stock default `30 ms` property corresponds to an effective `~15 ms` PassBlur gate.
- `Output::present` calls `MiOutputManager::releaseCurRes` near the end of each present. `releaseCurRes` consumes the current PassBlur `future<bool>` results and clears the future vector. The worker FIFO therefore does not justify treating old work as an unbounded cross-frame backlog.

The first experiment changes only the existing native pacing authority. It does **not** patch SurfaceFlinger code or change RenderEngine, BufferQueue, fences, blur shaders, or PassBlur job lifetime.

## Default

`interval_ms` is `12`.

On the decompiled `sfScale == 1.0` path this corresponds to an effective gate of approximately `6 ms`, close to a 165 Hz display period.

This value is intentionally configurable and is not yet the final general solution. The later native fork should use SurfaceFlinger's own active pacesetter VSync period instead of a fixed interval.

## Install

From this directory:

```bash
bash install-adb.sh
adb reboot
```

The module stores the previous property value once and restores it from `uninstall.sh`.

## Change interval

Edit `interval_ms` before installation. Values outside 4–50 ms are rejected and fall back to 12 ms.

## Scope

Do not combine this experiment with permanent `setForceRefresh` pumping. The property already controls the native normal pacing gate; `setForceRefresh` bypasses that gate rather than making it VSync-aware.
