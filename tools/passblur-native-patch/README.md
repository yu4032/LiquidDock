# PassBlur latest-only native patch

This tool patches one exact HyperOS `libsurfaceflinger.so` build. It is intentionally
version-locked and refuses every other SHA-256.

## Verified target

- SHA-256: `407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32`
- GNU Build ID: `39691db140edc0663fab2fde2475c091`
- Patch code RVA: `0xa4f200`
- Sidecar state RVA: `0xab1000`

The patch is derived from the decompiled PassBlur control flow, not from runtime log heuristics.

## Confirmed vendor behavior this patch changes

The vendor pipeline submits an independent copied `LayerSettings` snapshot for each
`MiOutputManager::drawPassBlurInternal()` call. `BackgroundExecutor::bgDrawPassBlur()`
puts each job into a dedicated PassBlur `LocklessQueue`. The single PassBlur worker drains that
queue in FIFO order. There is no pending-job replacement or latest-only coalescing before
RenderEngine.

Phase 1 keeps all vendor containers and future ownership intact. It adds a small sidecar keyed by
`PassBlur*`:

- `submittedSeq` increments on each vendor submission;
- `workerSeq` increments when that PassBlur's closure actually reaches the worker;
- if `workerSeq != submittedSeq`, the worker job is stale;
- `setTextureWin()` raises `minValidSeq`, invalidating jobs from the previous output binding.

A stale closure temporarily receives a zero layer count. Its
`PassBlur::dequeuePassBlurBuffer()` call is suppressed only for the same `PassBlur*` and the
same `TPIDR_EL0`. The original vendor closure then reaches its existing `result=false` future
completion path. The patch does not write libc++ future internals and does not delete vendor queue
nodes.

A current closure executes the original vendor implementation unchanged.

## Binary layout

No new ELF program header is added.

The existing RX `PT_LOAD` ends at `0xa4f150`. The verified bytes from there to the next LOAD at
`0xa50000` are zero. The patch extends that RX segment only far enough to contain the generated
trampoline blob beginning at `0xa4f200`.

The final RW `PT_LOAD` originally ends at `0xab04f0`. Its zero-fill `p_memsz` is extended to
`0xab4000`; the fixed sidecar occupies `0xab1000..0xab2040`.

## Hooks

| Vendor RVA | Patch RVA | Purpose |
| --- | --- | --- |
| `0x54eba0` | `0xa4f200` | register `drawPassBlurInternal` submission |
| `0x5591b0` | `0xa4f240` | classify background closure at worker entry |
| `0x422bc0` | `0xa4f2bc` | suppress dequeue for the currently stale closure |
| `0x422130` | `0xa4f2f0` | invalidate queued generations on `setTextureWin` |

Every trampoline reproduces the single overwritten vendor instruction and returns to the original
function at `RVA + 4`.

## Build

```bash
bash tools/passblur-native-patch/build.sh
```

This produces:

```text
build/passblur-native-patch/latest_only.o
build/passblur-native-patch/latest_only.elf
build/passblur-native-patch/latest_only.bin
```

The build always runs `verify_blob.py`, which decodes the critical AArch64 branches and verifies
all four original prologue instructions embedded in the trampolines.

To patch the verified original library:

```bash
bash tools/passblur-native-patch/build.sh /path/to/libsurfaceflinger.so
```

The patcher checks the complete input SHA, the expected LOAD layout, the zero-filled RX cave, and
all four original machine instructions before writing output.

## Scope

This is phase 1. It removes queued stale RenderEngine work while preserving vendor PassBlur
rendering and future lifetime.

It deliberately does not yet:

- alter the vendor time/pacing policy;
- cancel a render that becomes stale after RenderEngine has already started;
- change MIUI blur shaders, layer collection, crop/rotation, fences, or BufferQueue behavior.
