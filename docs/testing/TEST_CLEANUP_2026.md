# Test suite cleanup — first batch (PR #445)

Baseline: `main` commit `0dad310776702a1903a170918f7ba8d641130904` (2026-10-20).

## Scope and decisions

This batch removes **10 Java test files** and no production source. It removes **15
source-level `@Test` methods**. The original main tree has 224 Java source files
under `src/test/` (220 `*Test.java` classes and four helpers/fixtures), plus
16 Prismal `*Test.java` classes. After the batch: 214 app test-tree Java files
(210 `*Test.java`) and 16 Prismal test classes. These are **source-file and
annotation counts**, not the number of cases executed by Gradle.

| Removed test | Reason / remaining coverage |
| --- | --- |
| `DockMirrorShortcutReflectionContractTest` | Static source text and implementation-name checks; runtime registration must be verified on the target ROM |
| `DockShadowArchitectureTest` | Historical negative name bans; `DockShadowRuntimePolicyTest` covers shadow behavior |
| `LauncherWidgetTransitionWiringContractTest` | Exact Launcher 4.50 source target strings; `LauncherWidgetTransitionStateTest` covers transition policy |
| `LauncherWorkspaceGeometryHotPathContractTest` | Checks only a private method's reflection signature; geometry tests cover actual transforms |
| `R8SceneControllerReflectionContractTest` | Internal accessor spelling; `LauncherGlassSceneControllerTest` covers scene behavior |
| `SecurityCenterLegacyLifecycleRemovalTest` | Historical absence-of-old-code assertion; semantic resolver/state tests cover the supported path |
| `ThirdPartyAppRestartDescriptorTest` | Static implementation text; `HookScopeRestartShellTest` covers restart behavior |
| `WidgetBackgroundRankingUiContractTest` | UI source-string wiring; `WidgetComponentRankingTest` and rule engine tests cover ranking |
| `WidgetRemoteViewsRootBackgroundContractTest` | Duplicate of `LauncherGlassVendorMaterialSuppressionContractTest.standardWidgetTargetsOnlyTaggedDirectRemoteViewsRoot` (which checks the same direct child tag and destructive-API bans) |
| `SecurityCenterNoNameAnchorContractTest` | Obsolete forbidden-name strings; `SecurityCenterSemanticCompatibilityTest` validates renamed vendor contracts and fail-closed ambiguity; `SecurityCenterHookSpecTest` guards the stable entry anchors |

The `RuntimeBehaviorTestPolicyContractTest` allowlist/debt entries for removed
files are removed in the same change. No new exceptions or skipped tests are added.

## Protected regression domains

Preserve typed behavior tests for PassBlur, Prismal, Workstation/taskbar fly
motion, Dock animations, orientation, config persistence/migration, Hook safety,
Security Center semantic resolution and launcher scene ownership. Source-string
checks are not deleted merely because they read production files: vendor API,
R8 keep-rule and destructive-API boundaries may remain valuable.

## Repeatable measurements

CI runs `scripts/test_suite_inventory.py` after
`./gradlew testDebugUnitTest assembleDebug --stacktrace`, and uploads
`LiquidDock-test-inventory` with `source-inventory.csv` and `summary.json`.
The script reports both source `@Test` annotation count and **executed**
JUnit XML testcase count. Parameterized/inherited cases can differ from source
annotations. CI job wall-clock duration is separate from summed testcase time.

No JaCoCo coverage baseline or per-test historical flakiness data is available
from this change. Therefore it does **not** claim coverage is unchanged.
Subsequent batches should obtain baseline and post-cleanup coverage, and stop
if a protected behavioral scenario loses its only assertion.

## Validation

- First eight removals: CI run #8957 passed.
- Expanded ten-file batch: [CI #8958 passed](https://github.com/yu4032/LiquidDock/actions/runs/39453692672) on commit `338b159910d0a8ee9fd3c2b8f27e07342f5f47f2`.
- CI #8958: **1,126 executed JUnit cases, 0 failed, 0 skipped**, 229 JUnit XML reports; 1,126 source `@Test` annotations across 233 test-tree source files (including Kotlin and helpers). Gradle reported `BUILD SUCCESSFUL in 36s`; this is build/test task wall time, not sum of individual testcase durations.
- Before-cleanup **source annotation count inferred as 1,141** from the unchanged baseline plus 15 removed annotations; a separate baseline JUnit XML execution count was not collected, so 1,141 must **not** be represented as a measured baseline executed-case count.
- Real-device HyperOS/LSPosed behavior is not verified by JVM CI.
