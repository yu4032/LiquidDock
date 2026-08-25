# Restart-Bound Settings UI Implementation Plan

**Goal:** Make settings that cannot be safely hot-toggled clearly communicate that Launcher restart is required, while leaving runtime-safe visual ownership switches immediate.

**Architecture:** Runtime semantics remain unchanged for structural settings. Restart wording lives in localized Android string resources already consumed by the Compose UI; no hardcoded Compose-only restart helper is added.

**Spec:** `docs/superpowers/specs/2026-08-25-runtime-toggle-ownership-design.md`

## Scope

Restart-bound Boolean summaries:
- custom home grid
- widget grid adaptation
- Dock resize-animation selection
- LiquidDock smooth Dock resize-animation selection
- Workstation Dock customization

Immediate visual ownership switches must not gain restart wording:
- Dock customization
- icon/widget/small-folder/large-folder glass
- Dock stroke/shadows
- divider

Debug logging keeps its existing restart wording.

## Files

- `src/main/res/values/strings.xml`
- `src/main/res/values-zh-rCN/strings.xml`
- `src/test/java/com/hellovoid/liquiddock/RestartBoundSettingsContractTest.java`

## TDD

1. Add a failing contract that requires restart wording for the five structural summaries in both English and zh-CN.
2. Assert live visual switch summaries/inline labels do not acquire restart wording.
3. Preserve the existing debug logging restart message.
4. Update localized resources only.
5. Run full unit tests and `assembleDebug`.

Status: RED was confirmed with exactly the new structural-summary contract failing; localized resource updates are the GREEN implementation.
