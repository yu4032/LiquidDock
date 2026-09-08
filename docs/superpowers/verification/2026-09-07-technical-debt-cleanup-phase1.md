# Technical-Debt Cleanup Phase 1 Verification

## Status

**PHASE_1_DEVICE_GATE_PENDING**

Tasks 1–5 have code/CI evidence. Phase 1 is not complete until the final-head true-device matrix below is recorded. No EGL/OES/producer lifecycle extraction may begin while this gate is pending.

## Tested revision

- Branch: `refactor/technical-debt-cleanup-phase1`
- PR: `#137`
- Branch head under verification: `9ec9f3573e6b198fce30976a59b4007d2e517d69`
- PR merge ref used by CI #4327: `81dbecf98a49afef7809ce09479615ee5792a8e3`
- Baseline main: `be1b4ba77955fe0ebebec67362a09643b69634bd`

After CI #4327, the branch received documentation-only commits that align `TODO.md`, `ARCHITECTURE.md`, `HOOKS.md`, `CONTRIBUTING.md`, and this verification record with the verified implementation. Those commits do not modify production or test source; the APK used for the device matrix remains the CI #4327 artifact built from production head `9ec9f357`.

## Automated verification

### Full unit suite + Debug build

Workflow: API101 migration build #4327

Command:

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

Result: **PASS / exit 0**

CI evidence:

- `testDebugUnitTest` completed successfully;
- `assembleDebug` completed successfully;
- Gradle reported `BUILD SUCCESSFUL`;
- 73 actionable tasks: 39 executed, 34 from cache;
- no test failure was reported.

### Source-reader debt gate

`RuntimeBehaviorTestPolicyContractTest` passed as part of the full suite.

`LEGACY_SOURCE_DEBT` remains exactly 13 entries:

1. `GlassConfigGenerationContractTest.java`
2. `HomeGridOrientationMemoryHookContractTest.java`
3. `HomeGridProfileOverlayContractTest.java`
4. `Miuix307EdgeOverscanContractTest.java`
5. `PrismalModuleBoundaryContractTest.java`
6. `PrismalOfficialParityV3Test.java`
7. `RestartBoundSettingsContractTest.java`
8. `WidgetBackgroundRankingUiContractTest.java`
9. `WidgetComponentDiscoveryContractTest.java`
10. `WidgetComponentSelectionContractTest.java`
11. `WidgetMamlRenderTreeDiscoveryContractTest.java`
12. `WorkspaceDropRuleHookContractTest.java`
13. `WorkstationAllAppsHookContractTest.java`

No new source-reader debt exception was added during Phase 1.

### Build artifacts

- `LiquidDock-api101-debug.zip`
  - artifact id: `10042446842`
  - artifact SHA-256: `d132724a6e64f091df48b4f357ffb381b627789a8d610a41b515c1e9f0ec7028`
  - contains: `LiquidDock-debug.apk`
  - APK SHA-256: `1e89cd194e4a3521643ec56ee2b86828bf1bcba8bedc314dcf1c6ed192953e9a`
- `LiquidDock-build-source.zip`
  - artifact id: `10042447144`
  - artifact SHA-256: `a06487b6fb4829b0548a4a34b0c66275c7426bcb977aea0cc5ae66ae6f67c02c`

## Known CI warnings outside Phase 1 scope

These remain tracked engineering-hygiene debt and do not count as Phase 1 verification failures:

- `actions/setup-java@v4` deprecation / migration to v5;
- Node 20-targeted action warnings while runners force Node 24;
- workflow retention requests clamped to the repository 2-day maximum;
- existing `MiuixLauncherStaticGlassHook.installMamlBackgroundOwnershipHook(...)` inexact-varargs javac warning.

They must not be silently removed from `TODO.md` by Phase 1 closure.

## Final-head device metadata

Record before closing the phase:

- Device: **PENDING**
- ROM / HyperOS build: **PENDING**
- MIUI Home / Launcher version: **PENDING**
- LSPosed version: **PENDING**
- Installed APK SHA-256: must equal `1e89cd194e4a3521643ec56ee2b86828bf1bcba8bedc314dcf1c6ed192953e9a`

## Device smoke evidence — 2026-09-08

The user reported no obvious visible issue during this smoke run. The attached filtered LiquidDock log covers approximately `14:06:35.985` through `14:06:37.371` and contains no `DC-MATRIX` markers, so it is treated as a bounded smoke sample rather than evidence for every matrix case.

Observed sequence:

- Workstation mode published `false`;
- HotSeats hierarchy recovery/rebind completed;
- normal 8×4 layout restoration ran with `items=4`;
- the restoration was intentionally reasserted by the existing `post()` / `+250 ms` / `+700 ms` schedule rather than representing three distinct Workstation transitions;
- Workspace/static-glass reconciliation reached generation 2;
- PassBlur producer bound at native scale `1.0`;
- a first OES frame and first EGL material draw were received;
- zero-copy became active;
- no LiquidDock error/exception/failure line was present in the supplied filtered capture.

This is sufficient to record:

- CASE01 normal startup: **PASS (manual smoke + log)**;
- CASE05 normal-layout restore after leaving Workstation: **PASS (manual smoke + log)**;
- CASE10 normal non-Workstation mode: **PASS (manual smoke + log)**.

Cases 2–4, 6–9, and 11 remain **PENDING**. This capture does not identify quick re-entry / 2-second stale-fallback timing, Recents, rotation, Widget span/disabled behavior, or bundled-rule normal-load diagnostics.

## Workstation / Recents evidence — 2026-09-08

The user reported no visible abnormality while exercising the Workstation/Recents batch. The attached marker-based log supports the following bounded conclusions:

- CASE02 quick Workstation re-entry: **PASS**. The published mode sequence is `true -> false -> true`; the final `true` remains authoritative through the case end without a later stale reversal.
- CASE03 vendor-before-fallback stability: **PASS**. In the final marked interval, Workstation publishes `true` and remains in that state for more than the 2-second fallback window until case end. The later `false` occurs after CASE03 ended, during the subsequent transition.
- CASE06 HOME -> Recents -> HOME x5: **PASS**. The log contains exactly five `recents-wallpaper` pending generations `5/7/9/11/13` and five matching settled generations `6/8/10/12/14`. No producer bind/unbind churn is present inside that interval, and the user observed no stale/frozen glass.
- CASE04 stale delayed fallback: **PENDING**. The final `Mingou workstation mode changed=true` is only about 1.5 seconds before the CASE04 END marker, so this capture does not observe past the required 2-second stale-fallback deadline after the final re-entry.
- CASE07 Recents-adjacent rotation: **PENDING**. No CASE07 marker or identifiable rotation sequence is present in the supplied file.

The device gate therefore advances from 3/11 to **6/11**. Remaining cases are 4, 7, 8, 9, and 11.

## True-device matrix

All results below must be taken on final production head `9ec9f357` (or a later documentation-only descendant with identical production bytecode).

| # | Case | Status | Required evidence |
|---|---|---|---|
| 1 | Normal Launcher startup | **PASS (manual smoke + log)** | HOME usable; hierarchy rebind completes; first OES/EGL frame arrives; zero-copy active; no visible issue reported |
| 2 | Workstation enter → exit → quick re-enter | **PASS (manual + marker log)** | published sequence `true -> false -> true`; final `true` persists through case end with no stale reversal |
| 3 | Vendor Workstation callback before delayed fallback | **PASS (manual + marker log)** | final Workstation `true` remains stable beyond the 2 s fallback window until case end; no later reversal occurs inside the case |
| 4 | Stale delayed fallback after a newer transition | **PENDING** | no later stale mode flip/restore after the newer transition |
| 5 | Normal-layout backup / restore | **PASS (manual smoke + log)** | leaving Workstation restores the saved normal layout; the existing immediate / +250 ms / +700 ms reassert schedule may log the same `items=N` restore three times |
| 6 | HOME → Recents → HOME ×5 | **PASS (manual + marker log)** | five pending/settled generation pairs complete; no producer bind/unbind churn in the case; no stale/frozen glass reported |
| 7 | Recents-adjacent rotation | **PENDING** | correct orientation geometry and fresh glass after settle |
| 8 | 1×1 / 2×1 / 2×2 / 4×2 Widgets, portrait + landscape | **PENDING** | all supported spans tile/size correctly; no placement/occupancy regression |
| 9 | Widget adaptation disabled | **PENDING** | MIUI native Widget frame/layout remains untouched |
| 10 | Normal mode without Workstation | **PASS (manual smoke + log)** | final published mode is non-Workstation; Dock/Grid/Glass recovered and no visible issue was reported |
| 11 | Normal bundled Widget-rule load | **PENDING** | no `[DC][WidgetRules] bundled_rules_unavailable` warning |
| 12 | Missing/malformed bundled-rule test paths | **PASS (unit test only)** | `MISSING_RESOURCE` / `PARSE_FAILED`, one-shot diagnostic primitive, EMPTY fail-safe covered by unit tests; production APK is not intentionally corrupted on-device |

### Additional regression evidence already collected

The earlier Widget-drag freeze regression was tested on-device after the drag-source fix and passed two consecutive drag cycles. Both cycles showed:

```text
PBGL updates=false
LauncherGlass DragSource action=pause result=PAUSED
DragGlass begin ... sourceCaptureBlocked=true
LauncherGlass DragSource action=request-fresh
DragGlass end
PBGL updates=true
```

This proves the regression fix on that tested build, but it is **supplemental evidence** and does not replace the final-head matrix above.

## Device log collection protocol

Install the exact CI #4327 APK and restart the Launcher process so the updated Xposed code is loaded:

```bash
sha256sum LiquidDock-debug.apk
adb install -r LiquidDock-debug.apk
adb shell am force-stop com.miui.home
adb shell input keyevent KEYCODE_HOME
```

Expected SHA-256:

```text
1e89cd194e4a3521643ec56ee2b86828bf1bcba8bedc314dcf1c6ed192953e9a
```

Start one capture for the whole matrix:

```bash
adb logcat -c
adb logcat -v time | grep -E 'DC-MATRIX|\[DC\]' | tee phase1-device-matrix.log
```

Use explicit markers from another terminal before and after every case:

```bash
mark() { adb shell log -t DC-MATRIX "$*"; }

mark 'CASE01 START normal-startup'
# perform case
mark 'CASE01 END normal-startup'
```

Repeat with `CASE02` … `CASE11`. Preserve the unedited log file if any case fails.

### Recommended execution order

1. Record device / ROM / Launcher / LSPosed versions.
2. CASE01 normal startup.
3. CASE10 normal non-Workstation baseline.
4. CASE11 normal bundled Widget-rule load; verify there is no degradation warning.
5. CASE08 supported Widget spans in portrait, then landscape.
6. CASE09 disable Widget adaptation, restart Launcher, verify native behavior, then restore the setting and restart Launcher.
7. CASE02 Workstation enter → exit → quick re-enter.
8. CASE03 perform a Workstation transition early enough that the real vendor callback arrives before the 2-second fallback window; observe state through the full window.
9. CASE04 trigger a newer Workstation transition before an older delayed fallback can fire; observe beyond the old 2-second deadline and confirm no stale state change.
10. CASE05 validate normal-layout backup/restore across Workstation enter/exit.
11. CASE06 perform HOME → Recents → HOME five consecutive times without killing Launcher.
12. CASE07 rotate while adjacent to / returning from Recents and verify settled geometry + glass freshness.

For Workstation transitions, the current observable publication log is:

```text
[DC] Mingou workstation mode changed=true|false
```

A vendor-confirmed newer state must not be reversed later merely because the older delayed fallback reaches its timer deadline.

## Correctness boundaries checked by review

Phase 1 intentionally does **not** change the following authorities:

- zero-copy remains mandatory; no ScreenCapture / bitmap fallback was introduced;
- scene/wallpaper generation plus fresh OES frame remains the reveal authority;
- Workstation Recents keeps the existing covered authority, bind epoch and freshness semantics;
- no second producer-recovery episode state machine or terminal aggregate was introduced;
- MIUI retains Grid placement / occupancy ownership;
- Widget adaptation changes allocation/frame only;
- Task 5 changes rule-load observability only; rule matching and MAML mutation semantics remain fail-safe.

## Closure rule

Do not change this document to `PASS`, mark Phase 1 complete in `TODO.md`, or begin Phase 2/3 GPU ownership extraction until cases 1–11 are recorded on the final production head and all failures are either fixed and reverified or explicitly block the phase.
