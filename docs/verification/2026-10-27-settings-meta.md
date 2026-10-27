# Declarative settings/search migration

Base: origin/main `57b831cf`. Branch: `codex/settings-declarative-model`.

## Behavior

- `SettingsMeta.blocks` is the ordered, immutable source for rendering, option search and configuration-key position lookup. Headers, summaries, links and reset actions are included in the list positions.
- Catalog options come only from declared rows. No title deduplication, rendered-row discovery or separately maintained seed option lists.
- Each result retains the selected preference key. Page ownership uses explicit canonical parents; overview cards remain shortcuts.
- Search uses `scrollToItem` before the target is composed, waits for its stable item key, then attempts centering using lazy viewport coordinates. Normal list boundaries apply; strict centering is not required by the user.
- Once positioned, the row flashes white three times (120 ms rise / 240 ms fade) then returns to its normal appearance. Normal navigation clears the focus request.

## Migration boundary

32 destinations migrated:

- Liquid material, refraction, color, lighting, internal shadow, sampling/performance.
- Global edge decoration and ten component edge scopes.
- Dock behavior/geometry; workstation Dock and both All Apps layouts; Recents.
- Landscape, portrait and split grid.
- All five animation leaf pages.
- Menus/popups hub including wallpaper flicker fix.

Complex custom pages (GridBasics preflight, per-app editors, component discovery, sidebar, Gboard and scoped optics, import/export, theme/license pages) and remaining navigation hubs expose **page-level search only**. Their custom controls remain unchanged. They do not advertise non-locatable control matches. This is the report's explicit transitional contract, not completion of migration for every page.

## Validation

- `./gradlew :testDebugUnitTest :assembleDebug :assembleDebugAndroidTest`
- `scripts/verify_settings_search.sh H645HMEQTWMBRG6D`
- JVM tests cover declaration key uniqueness, scoped isolation, complete indices, canonical paths, moved options, search key retention, animation dependencies and centering direction. Prior animation source-text layout tests replaced with actual declaration assertions; runtime authority tests retained.
- Accessibility instrumentation sets Chinese text through `ACTION_SET_TEXT`, selects global and Dock scoped offscreen rows by config-key searches, verifies destination and visible bounds, and reports measured center error without imposing exact centering. No special keyboard is installed and no preferences are changed.
- Read-only independent review found no actionable issues in the migrated defaults, gates, reset behavior or initial positioning implementation.

Final result: 864 JVM tests, 0 failures/errors/skips; debug and instrumentation APKs built successfully. Device H645HMEQTWMBRG6D passed Unicode input and global/scoped offscreen navigation. Portrait global target center error 0 px; Dock target remained fully visible with 276 px center offset at the normal list boundary (accepted by the user). White flash and overall behavior accepted by the user before PR creation.
