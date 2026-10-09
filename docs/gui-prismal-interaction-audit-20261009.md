# LiquidDock Prismal GUI audit — 2026-10-09

Scope: full settings shell and shared Prismal control adapters, plus widget detail settings, upstream PrismalAGSL Toggle/Slider/Stepper/BottomTabs/Button and related MIUIX containers. Static/source-level audit; **not yet a device verification**.

## Evidenced issue: restart selection fails only with glass on

- User's glass-off GUI can restart selected scopes; glass-on previously restarted only Launcher.
- The recorded glass-on trace is `UI_REQUEST|[com.miui.home]` → `ROOT_START` → Launcher STEP/RESTARTED → `ROOT_DONE`. This proves the shell ran correctly for the **one ID actually submitted**; SystemUI was never requested in that run.
- RestartScopesDialog previously used `ModernSurface(onClick = {})`; its glass-on path creates a clickable `PrismalGlassSurface` with a parent-level press ripple/gesture modifier spanning the popup. The same surface with glass off has no Prismal gesture. A hit-test conflict with child toggles is a **strong cause candidate**, but still requires A/B device confirmation.
- Current PR remedy: passive Prismal optics on the dialog background with an ordinary Compose blank-space click shield; glass-on PrismalGlassToggle switches with `rememberUpdatedState` callback/state bridges (glass-off MIUIX fallback); explicit `onToggle(id, checked)`; confirm passes `selected.toSet()` directly. The parent click shield should consume blank-space taps without competing in Prismal's gesture recognizer.

## Reuse/interaction audit

| Priority | Location | Source finding | Action / status |
| --- | --- | --- | --- |
| P0 | `RestartScopesDialog` | Interactive glass parent can interfere with child switches; glass-off removes this modifier. | **Changed** parent to passive glass. Validate full five-item UI_REQUEST and tap targets on device. |
| P1 | `SwitchPreference` ↔ upstream PrismalGlassToggle | Upstream `PrismalSpringMotion` is remembered with `animationScope`, not current `onSelect`/selected callbacks; gestures risk retaining stale closures. | **Changed** wrapper to stable callbacks reading `rememberUpdatedState`. Test repeated switching and external config changes. |
| P1 | `SliderPreference`, `ModernGlassSlider` ↔ upstream PrismalGlassSlider | Remembered drag controller retains initial `onValueChange` and value reader. | **Changed** wrappers to stable state/callback readers; verify slider/stepper synchrony and hot config updates. |
| P1 | `ModernBottomNavigation` ↔ PrismalGlassBottomTabs | Reallocated selected provider on every recomposition triggers upstream keyed collector restarts, potentially affecting drag/selection continuity. | **Changed** to stable provider and callback; device test rapid tabs and long-press. |
| P1, watch | `GuiUnclippedSmallTopAppBar` | Full-header `detectTapGestures` is installed as a parent; may interfere with child action taps depending on pass/consumption. | **Not changed** without device evidence; tap repeatedly on Back and Restart Scope with glass on/off. |
| P2, watch | `GuiPrismalFlatHeader` | Top/side slab overscan and status-bar opaque cap depend on safe inset dimensions. | Keep user-approved visuals; test rotation, dark/light, display cutout. |
| P2, watch | `ModernSettingsScaffold` | Two full-screen Prismal capture layers feed merged top/bottom source. Correct for visibility but potentially expensive during scroll. | Do not add MIUIX blur/capture; profile GPU/render thread before redesign. |
| P2, watch | `DenseSettingsList` | A single LazyColumn now scrolls glass cells together; each visible item has its own glass surface/animation. | Profile frame pacing and offscreen disposal, preserve lazy composition. |
| P2, watch | Glass slider/stepper disabled visuals | Guarded mutation callbacks do not necessarily disable upstream gesture animation/hit targets. | Review separately; avoid changing touch behavior without a regression run. |
| P3, watch | Glass disabled buttons / accessibility | `isInteractive=false` disables press effect but upstream clickable semantics may remain. | Accessibility test needed; defer semantics redesign. |

## Cleanup decisions

- Removed unused Kotlin imports in existing GUI files.
- Kept PR's scoped root restart verification and opt-in `LD_SCOPE_RESTART` diagnostics **temporarily**; the prior one-ID trace shows they were not the source of the glass-only failure. Reevaluate simplification after device confirmation, rather than removing useful diagnostic evidence prematurely.
- Did not modify upstream PrismalAGSL, introduce bitmap/screen capture, or add a new UI gesture framework.
- PR #296 stays Draft.

## Device/CI acceptance before merge

1. Build/test the final head commit (not intermediate builds).
2. With GUI glass **on**, open restart dialog from a child page, explicitly enable all five, confirm `UI_TOGGLE` and `UI_REQUEST` have all IDs; repeat with one disabled. Compare glass **off**.
3. Monitor PID changes; SystemUI last and expected lockscreen transition when selected.
4. Repeated switch taps, rapid sliders and stepper + slider adjustments, rapid bottom tabs, Back and Restart buttons, long list scrolling and top status-bar colors in both themes.
5. Follow up on any P1/P2 watch items only after reproducing a failure; clean temporary diagnostic code before eventual merge.
