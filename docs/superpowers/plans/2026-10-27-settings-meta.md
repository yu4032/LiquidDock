# Declarative settings and search implementation plan

**Goal:** Follow the supplied report: render, index and locate settings from the same declaration.
**Architecture:** Immutable SettingsMeta/blocks describe each migrated page before composition. Search uses these blocks; the renderer uses their stable IDs and list positions. Unmigrated pages expose route results only. Canonical page parents are explicit and independent of overview shortcuts.
**Tech stack:** Kotlin, Compose LazyColumn, existing preference controls, JUnit.
**Spec:** User's root-cause report supplied in this chat (2026-10-27).

## Constraints and review focus
- Preserve config keys, persistence, defaults, dependencies and scope inheritance.
- No seed option catalog, composition recording or title-based deduplication.
- Stable key-to-index mapping includes headers, summaries and actions, including offscreen controls.
- Attempt centering against the actual lazy viewport with correct scroll direction; clamp naturally at list ends. User explicitly accepts visibility without strict centering (2026-10-27). Flash white three times after positioning; no persistent theme-colored fill.
- Scope titles and breadcrumbs distinguish global, Dock, icons and other destinations.
- Search selects the matched row, supports repeated navigation, and does not depend on Chinese keyboard injection.
- Complex pages not migrated in this change remain page-searchable without promising row focus.

## Tasks
- [x] Add regression tests for canonical routes, declaration-derived catalog, key positions, scope isolation and centering arithmetic; observe failure.
- [x] Build pure metadata model, canonical route catalog and shared renderer with scroll/focus lifecycle.
- [x] Migrate global/component edge pages and shared numeric pages, sampling/performance and ordinary animation/Dock settings. Keep complex custom pages on route-only search.
- [x] Remove the old hand-maintained option catalog; navigate with matched key and page identity.
- [x] Run unit suite and debug build, inspect diff and document migration coverage and device-verification limits.
