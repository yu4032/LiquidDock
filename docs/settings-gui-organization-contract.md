# Settings GUI organization — ownership and search contract

## Shared settings architecture

- `Page` and `canonicalSettingsParent` define routes and back navigation.
- `ConfigSchema` owns persisted keys, range, defaults and import/export contract.
- `IntSpec`, grouped `LiquidSpecGroup`, and `SettingsMeta` provide stable user-visible descriptors.
- `DeclaredSettingsPage` renders declarative settings and knows precise row position.
- Specialized panels own their feature logic (`GridWidget4x2PreflightClient`, popup scope, folder snapshots, keyboard profiles) and share `SettingsCard`, `IntSetting` / `BooleanSetting` and scoped slider components.
- Long pages use one vertical scrolling root, semantic headings and independent cells; Prismal parameter groups contain at most three related controls.
- `SettingsSearch` indexes actual `SettingsBlock.Row` and `ConfigKey` / `IntSpec` descriptors, with real control ownership and search targets.
- Parameter results include an owning preference key and Lazy Cell focus target. Dynamic widget and blacklist collections use page-level navigation.
- Changes to a custom page's control order, or a new setting, require updating its typed Cell mapping and regression test **in the same commit**. The expanded-folder page uses `FolderGlassCell` as the shared Cell identity source.
- Standalone activities (such as System Search) use independent navigation and the same `SettingsCard` / semantic grouping rules outside `Page.entries`.

## Regression gates

`SettingsGuiOrganizationContractTest` classifies all 68 `Page` routes explicitly as declarative or custom and verifies that the two sets are disjoint and exhaustive. It asserts parameter/focus parity for the fixed custom glass pages, and preserves the unique semantic grouping contract. `SettingsMetaTest` guards actual custom cell/search mapping and declared-page row ownership.

Runtime tests verify typed production interfaces and observable behavior. GUI organization changes are isolated from PassBlur, API 102 Hook ownership and material rendering.
