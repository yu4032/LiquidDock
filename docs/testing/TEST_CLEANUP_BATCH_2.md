# Batch 2 — focus GUI implementation snapshots

Baseline: PR #445 (1126 executed JUnit cases, 0 failures, CI #8959). This branch is stacked on PR #445 and must be merged only after it.

This change reduces **40 source-level JUnit tests** without touching production code, skipping tests, or changing the Gradle test selection. It reduces method-level snapshots, not assertion failures by renaming cases. All remaining tests still execute independently.

| Source | Before `@Test` | After `@Test` | Rationale |
| --- | ---: | ---: | --- |
| `ModernSettingsArchitectureTest.java` | 53 | 28 | Remove dated visual implementation snapshots; retain navigation, interactive controls, settings state, scoped glass and safety regressions |
| `AnimationSettingsContractTest.java` | 6 | 2 | Remove redundant source spelling / tombstone checks; retain representative safety and behavior-facing contracts |
| `RestartBoundSettingsContractTest.java` | 7 | 4 | Remove redundant source spelling / tombstone checks; retain representative safety and behavior-facing contracts |
| `SecurityCenterSettingsRestartContractTest.java` | 6 | 3 | Remove redundant source spelling / tombstone checks; retain representative safety and behavior-facing contracts |
| `SecurityCenterHookSpecTest.java` | 3 | 1 | Remove redundant source spelling / tombstone checks; retain representative safety and behavior-facing contracts |
| `LauncherGlassStaticBoundaryTest.java` | 4 | 3 | Remove redundant source spelling / tombstone checks; retain representative safety and behavior-facing contracts |
| `LauncherMamlBackgroundRuleExecutorContractTest.java` | 3 | 2 | Remove redundant source spelling / tombstone checks; retain representative safety and behavior-facing contracts |
| `LauncherGlassProducerRolloverContractTest.java` | 3 | 2 | Keep two typed recovery-state tests; remove private signature reflection |
| `SecurityCenterHookLifecycleContractTest.java` | 2 | 0 | Pure method-presence reflection; `SecurityCenterSemanticCompatibilityTest` and material authority tests exercise resolution and lifecycle policy |

Removed total including the complete method-presence class: **42**. [CI #8960](https://github.com/yu4032/LiquidDock/actions/runs/39462787915) verified the actual executed-case count **1,126 → 1,084** (42 fewer): **0 failed, 0 skipped**, 228 JUnit XML reports. Gradle `BUILD SUCCESSFUL in 28s` on this run; timing differences are not proof that fewer tests materially accelerate CI.

## Retained critical signals
- `ModernSettingsArchitectureTest`: retains slider preview, grid guard, root search/navigation, global preference refresh, glass off, emergency sampling guard, capsule tab sampler, restart descriptions and activity integration checks.
- `AnimationSettingsContractTest`: retained process-owned exit animation config and GUI/runtime separation.
- `RestartBoundSettingsContractTest`, `SecurityCenterSettingsRestartContractTest`: retained runtime-safe vs restart-bound semantics and scope-specific restart guards.
- `LauncherGlassStaticBoundaryTest`: retained no-screen-capture and scroll-backdrop anchoring bans.
- `LauncherGlassProducerRolloverContractTest`: retained true producer recovery and endpoint race state-machine tests.
- `SecurityCenterHookSpecTest`: retained stable vendor entrypoint anchors.

The PR uses `main` as the target branch to trigger the repository's existing CI workflow. It still depends on PR #445 and must not be merged before #445; GitHub will drop the earlier diff from PR #446 after #445 is merged.

## Risks / validation
This is a reduction in independent regression checks, not proof of unchanged test coverage. GUI source-text checks are not substitutes for instrumentation; JaCoCo and real-device visual baseline remain unavailable. CI must run `testDebugUnitTest assembleDebug` and publish the JUnit inventory. Do not force the <500 target until duplicated runtime scenarios are individually compared and benchmarked.
