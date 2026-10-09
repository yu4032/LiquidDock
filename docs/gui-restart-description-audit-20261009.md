# GUI restart-description audit — 2026-10-09

Scope: all user-visible settings descriptions in `ComposeSettingsActivity.kt`, both string-resource locales, and related restart wording. Verified against installed-Hook and preference-notification routes; this audit changes text only, not runtime behavior.

## Runtime-live when the target Hook has already been installed

- **Launcher animation timings:** workspace glass visibility, Dock icon reveal, glass press-in/out, and shortcut-popup dismiss fade. `LiveGlassConfigState.ANIMATION_KEYS` triggers `AnimationRuntimeState.configure()` on a preference notification. Dock resize animation is **not** in that live key set.
- **Security Center exit fade:** `ExternalGlassLiveConfigState` calls `AnimationRuntimeState.configure(config.animation)` in its Security Center process.
- **Recents wallpaper blur and dimming:** `LiveGlassConfigState.RECENTS_KEYS` updates `RecentsBackgroundBlurHook.onLiveConfigChanged`, which retains volatile settings.
- **Shortcut menu glass/background and dark text:** `LiveGlassConfigState.POPUP_KEYS` updates `MiuixShortcutMenuGlassHook.onLivePreferences`, including an attached menu. **Exception:** the interceptor install is gated by initial master/glass enablement; a newly missing Hook requires process restart.
- **System UI handle menu:** `ExternalGlassLiveConfigState` dispatches to `SystemUiHandleMenuGlassHook.onLiveGlassConfigChanged` for an installed SystemUI Hook. If that Hook was never loaded, restart is still necessary.
- **PassBlur render-rate limit:** `LiveGlassConfigState.GLASS_KEYS` includes the global FPS setting, and independent app scopes receive `ExternalGlassLiveConfigState` notifications when connected.

## Legitimately restart-bound or conditional descriptions retained

- Structural grid mode and icon layout, grid dimension and widget stretching, workstation desktop/All Apps geometry and Dock icon glass corner radius (as opposed to the already-installed Dock icon vertical-offset path).
- Dock resize animation implementation/duration: `AnimationRuntimeState` does not expose it as a live key.
- Logging: `MainHook.debugLogging` is initialized at process Hook installation rather than updated by the live config subscriber.
- System Framework wallpaper flicker behavior requires device reboot for framework-level Hook loading.
- New or unavailable Hook installations cannot be created merely by live preference updates.
- Full JSON import and restore-default flows deliberately restart Launcher.
- System navigation/gesture scene integration might require initial SystemUI restart despite its runtime scene state gate.

This audit does not establish first-time bootstrapping or render-session re-creation for every scope. It only corrects claims of **unconditional restart** where a runtime listener and a running Hook exist. Hardware/device verification remains separate.
