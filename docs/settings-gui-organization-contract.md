# Settings GUI organization — ownership and search contract

## Existing architecture (preserve, don't replace)

- `Page` and `canonicalSettingsParent` define routes and back navigation.
- `ConfigSchema` owns persisted keys, range, defaults and import/export contract.
- `IntSpec`, grouped `LiquidSpecGroup`, and `SettingsMeta` provide stable user-visible descriptors.
- `DeclaredSettingsPage` renders declarative settings and knows precise row position.
- Specialized panels retain their own business logic (`GridWidget4x2PreflightClient`, popup session scope, folder snapshots, keyboard profiles) and reuse `SettingsCard`, `IntSetting` / `BooleanSetting` or their established scoped-slider equivalents. Do **not** flatten them into generic cards.
- A long page has one vertical scrolling root, with semantic headings and independently rendered cards; Prismal parameter groups contain at most 3 controls. No blind `chunked(3)` grouping or giant offscreen blur card.
- `SettingsSearch` indexes actual `SettingsBlock.Row` or custom descriptors derived from rendered `ConfigKey` / `IntSpec`; never add arbitrary search keywords as a substitute for a real control.
- A parameter-level result must have a real owned preference key and a visible Lazy Cell focus target. Dynamic item collections (widgets, recent-app blacklist) use their owning page rather than fake static preference rows.
- Changes to a custom page's control order, or a new setting, require updating its typed Cell mapping and regression test **in the same commit**. The expanded-folder page uses `FolderGlassCell` as the shared Cell identity source.
- Standalone activities (e.g., system search) retain their own settings navigation and must use the same `SettingsCard` and semantic grouping rules. They are not part of `Page.entries` and must not be given a fake `Page` ID merely for search.

## Regression gates

`SettingsGuiOrganizationContractTest` classifies all 68 `Page` routes explicitly as declarative or custom and verifies that the two sets are disjoint and exhaustive. It asserts parameter/focus parity for the fixed custom glass pages, and preserves the unique semantic grouping contract. `SettingsMetaTest` guards actual custom cell/search mapping and declared-page row ownership.

Do not inspect production files from runtime-behavior tests: test typed code, not source slicing. Avoid changes to PassBlur, API 102 Hook ownership, or existing material when organizing the GUI.
