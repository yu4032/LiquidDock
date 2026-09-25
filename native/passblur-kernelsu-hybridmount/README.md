# LiquidDock PassBlur KernelSU HybridMount

Exact-build native patch for HyperOS OS3.0.310.0.WAOCNXM.

## Target

- `/system_ext/lib64/libsurfaceflinger.so`
- stock SHA-256: `407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32`
- GNU Build ID: `39691db140edc0663fab2fde2475c091`
- predecessor freshness payload: `ef523f9d57ebe5c3af2ec39747b07ab6d70bd69165461e09ddc0fc8f13f5d563`
- deprecated Pacing12 payload: `7cb2123d0b5d5cfd9ae63699c9624ebe306392b88f642ec609fdb5fc2247e30f`
- deprecated AsyncRelease payload: `0ce7ceea23efd5a9f7bdd5f7858e3e8d21bb77c5d9261088a18726eeccf5bc48`
- current One-In-Flight payload: `bf3a0565d7e7b1309b4a2a9027b73aab55dd66607a1bba381362c822d9456868`

Any unknown input SHA fails closed.

## Reverse-engineered bottleneck

`drawPassBlurInternal()` submits heavy PassBlur work to the single PassBlur worker and records
`future<bool>` objects. Stock `releaseCurRes()` later calls
`std::__assoc_state<bool>::move()`, whose implementation locks and calls `__sub_wait()`.
Therefore stock SurfaceFlinger can synchronously wait for PassBlur work.

The first AsyncRelease experiment removed that wait incorrectly: a non-ready future was converted
into `false`, after which `releaseCurRes()` still consumed the vector and bookkeeping. The next
composition frame could then submit more heavy work while the old worker job still existed. That
improved SurfaceFlinger responsiveness but allowed real cross-frame PassBlur backlog and stale
background latency.

## One-In-Flight fix

The current patch separates readiness from result consumption.

### Submission gate

At `drawPassBlurIfNeed @ 0x54d310`:

- inspect the future vector at `MiOutputManager + 0x40/+0x48`;
- when non-empty, return through the normal function epilogue without submitting another heavy
  PassBlur job;
- when empty, continue the original path.

### Release gate

At `releaseCurRes @ 0x54e5b0`:

- scan every future shared state;
- inspect READY bit 0 at shared-state `+0x70` using acquire semantics;
- if any future is not ready, close the inner trace and return without consuming, decrementing,
  clearing, or marking the batch processed;
- only when every future is ready does control return to the original
  `std::__assoc_state<bool>::move()` loop.

This yields:

```text
at most one PassBlur batch in flight
+
SurfaceFlinger present never waits for unfinished PassBlur
+
unfinished future ownership remains intact
+
no cross-frame heavy-job backlog
```

The existing worker-entry stale rejection is retained so work that has not started rendering can
still be discarded cheaply. Stock pacing and stock post-render queue behavior remain restored.

## Supported upgrades

The installer accepts exact:

1. stock;
2. predecessor freshness-only;
3. Pacing12;
4. AsyncRelease;
5. current One-In-Flight payload.

All upgrade paths must produce exactly:

`bf3a0565d7e7b1309b4a2a9027b73aab55dd66607a1bba381362c822d9456868`

or installation aborts.

CI emits:

`LiquidDock-PassBlur-KernelSU-HybridMount-OS3.0.310.0.WAOCNXM-OneInFlight.zip`

KernelSU, arm64 and the enabled Hybrid Mount metamodule are required.
