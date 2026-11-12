# SystemUI Top Gradient Blur Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a LiquidDock setting that enables SystemUI's native gradient blur at the top of Control Center and Notification Shade on supported devices where Xiaomi's status-default gate is off.

**Architecture:** Persist a default-off boolean in the existing config schema and expose it on the System UI settings page. When enabled, install a SystemUI-only API 102 hook for `SystemProperties.getBoolean`; override only `persist.sys.background_blur_status_default` to `true`, leaving all other property reads and SystemUI's own `miBlurOpen` logic intact. Require a SystemUI restart after changing the setting because the vendor capability is cached during class initialization.

**Tech Stack:** Kotlin Compose settings catalog, Java config schema, libxposed API 102, Android `SystemProperties`.

**Spec:** `docs/superpowers/specs/2026-11-06-systemui-header-gradient-blur-design.md`

## Global Constraints

- The setting defaults to off.
- Only the top gradient blur capability gate is overridden.
- Do not modify `persist.sys.background_blur_supported`, global window blur settings, or LiquidDock's captured background blur.
- Preserve SystemUI's own blur setting, power-save, and temporary disable conditions.
- The hook runs only in `com.android.systemui`; the user must enable that LiquidDock scope.
- State in the setting description that SystemUI must be restarted after changes.

## Review Focus

- Off by default or absent config: no SystemUI property value changes.
- Target property queried: return `true` only when the feature setting is enabled.
- Any other property queried: pass through to the original method unchanged.
- Hardware blur capability absent: preserve the vendor's `persist.sys.background_blur_supported` result.
- SystemUI blur disabled or blocked by system state: retain the original `miBlurOpen` behavior.

---

### Task 1: Register the setting

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/DeclaredSettingsPages.kt`

**Interfaces:**
- Produces: `ConfigSchema.Animation.SYSTEM_UI_HEADER_GRADIENT_BLUR`, boolean, default `false`.

- [x] Add the new boolean config key with default `false` and include it in the schema's registered key list.
- [x] Add a switch to `Page.AnimationSystem` titled `控制中心/通知中心顶部渐变模糊`.
- [x] Describe that it affects only the top gradient blur, requires LiquidDock's SystemUI scope, and takes effect after restarting SystemUI.
- [x] Build the settings module to confirm the new config row and key compile.

### Task 2: Add the SystemUI-only property override

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiHeaderGradientBlurHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`

**Interfaces:**
- Consumes: `ConfigSchema.Animation.SYSTEM_UI_HEADER_GRADIENT_BLUR` from `ConfigReader` in the `com.android.systemui` `onPackageLoaded` callback.
- Produces: `SystemUiHeaderGradientBlurHook.install(boolean enabled)`.

- [x] Implement an owner-scoped API 102 hook for `android.os.SystemProperties.getBoolean(String, boolean)` in the SystemUI process.
- [x] When enabled, return `true` only for `persist.sys.background_blur_status_default`; forward all other calls unchanged.
- [x] Install the hook only when LiquidDock's master setting and the new feature setting are enabled; on hook lookup/installation failure, preserve original SystemUI behavior.
- [x] Call the hook from SystemUI `onPackageLoaded`, before `AppComponentFactory` initialization, without adding another process or IPC channel.
- [x] Build and install the debug APK; confirm the original worktree's pending files remain untouched.
