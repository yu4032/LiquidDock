# Third-Party Glass Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add independent MIUI Searchbox blur/tint controls and a reusable hidden configuration/profile contract for code-registered third-party glass adapters, then make Searchbox transient presentation snapshot-based so its backdrop does not chase the entrance animation.

**Architecture:** Keep adapter-specific target discovery and lifecycle code, but move appearance/quality configuration into a common `ThirdPartyGlassAppearance` + `ThirdPartyGlassProfiles` model. Dispatch remains code-registered through `ThirdPartyGlassAdapterRegistry`; configuration can tune or enable registered adapters but cannot inject arbitrary hook classes or method names. Searchbox consumes one authoritative fresh PassBlur frame per visible cycle and then pauses its producer while the window animates.

**Tech Stack:** Java/Kotlin Android module, LSPosed hook runtime, `RootPassBlurBackend`, Prismal renderer, bounded configuration codec, JUnit4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-third-party-glass-profiles-design.md`

## Completed work

- [x] Shared `ThirdPartyGlassAppearance` / `ThirdPartyGlassProfiles` runtime model.
- [x] Searchbox independent blur and tint RGBA controls with global inheritance.
- [x] Bounded hidden profile namespace for capture scale, render FPS, corner radius, freshness and highlight toggles.
- [x] Code-owned `ThirdPartyGlassAdapterRegistry` for Gboard and Searchbox.
- [x] Bounded `ThirdPartyGlassConfigCodec`; arbitrary hook class/method declarations are not accepted.
- [x] Gboard compatibility bridge into the shared profile model.
- [x] Searchbox exact root-space crop and fresh-frame barrier retained.
- [x] `MiuiSearchboxSnapshotState` added: one fresh frame is accepted per visible capture cycle.
- [x] Searchbox Session latches that first frame, renders Prismal once, then pauses producer updates with `searchbox-snapshot-latched`.
- [x] Searchbox PassBlur authority now owns desired update state as well as producer Surface/scale, so vendor transactions cannot re-enable updates after latch.
- [x] No fixed delays or animation-duration guesses introduced.

## Verification

The snapshot behavior was developed RED/GREEN:

- RED run #5555 / `35048686169`: failed because `MiuiSearchboxSnapshotState` did not exist.
- GREEN run #5561 / `35048885550`: `testDebugUnitTest assembleDebug --stacktrace` passed, as did the Security Center/root PassBlur regression audit.
- Verified code head: `9dd0cc315139836949a36b4406878664cd66b636`.
- Debug artifact: `LiquidDock-api101-debug`, artifact ID `10428027793`, SHA-256 `52385345e53635695708ec989beb3419865395403045cf599b8107db338e53f4`.

## Device validation targets

- Searchbox upward entrance animation should no longer show backdrop twitch or a visibly delayed moving backdrop.
- Closing Searchbox, changing the desktop behind it, and reopening must capture a new snapshot.
- Repeated open/close cycles must not reuse the previous cycle's stale frame.
- Exact crop/UV alignment must remain unchanged.
