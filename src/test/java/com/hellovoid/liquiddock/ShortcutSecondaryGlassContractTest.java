package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static HyperOS 4.50 vendor/API boundary contract for shortcut-menu popup glass. */
public class ShortcutSecondaryGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void shortcutMenuUsesEarlyWorkspaceLatchAndPublishedDockAuthority()
            throws Exception {
        String hook = SourceContractText.read(MAIN.resolve("MiuixShortcutMenuGlassHook.java"));
        String coordinator = Files.readString(MAIN.resolve("ShortcutPopupGlassCoordinator.java"));
        String session = Files.readString(MAIN.resolve("ShortcutPopupGlassSession.java"));
        String request = Files.readString(MAIN.resolve("PassBlurBindRequest.java"));

        // Workspace keeps the proven early timing path.
        assertTrue(hook.contains("com.miui.home.launcher.CellLayout"));
        assertTrue(hook.contains("getDeclaredMethod(\"dispatchTouchEvent\", MotionEvent.class)"));
        assertTrue(hook.contains("getDeclaredMethod(\"lastDownOnOccupiedCell\")"));
        assertTrue(hook.contains("ShortcutPopupGlassCoordinator.armTouch(launcherRoot, glassConfig)"));
        assertTrue(hook.contains("com.miui.home.launcher.Launcher"));
        assertTrue(hook.contains("com.miui.home.launcher.CellLayout$CellInfo"));
        assertTrue(hook.contains("getDeclaredMethod(\"dragSingleItem\", cellInfoClass, View.class)"));
        assertTrue(hook.contains(
                "ShortcutPopupGlassCoordinator.latchBeforeDrag(draggedView.getRootView())"));

        // Dock must preserve the published v2.4.5 authority contract: the actual
        // ShortcutMenuLayer root from setRequestingItemInfo(), not a guessed Dock touch root.
        assertTrue(hook.contains("com.miui.home.launcher.dock.DockContainerView"));
        assertTrue(hook.contains("\"setRequestingItemInfo\""));
        assertTrue(hook.contains("dockContainerClass.isInstance(owner)"));
        assertTrue(hook.contains("ShortcutPopupGlassCoordinator.prepareAuthoritativeRoot("));
        assertTrue(hook.contains("Dock authoritative menu root prepared"));
        assertFalse(hook.contains("dispatchTouchEventFromHome"));
        assertFalse(hook.contains("HotSeatsListContent"));
        assertFalse(hook.contains("dock touch prewarm armed"));
        assertFalse(hook.contains("dock pre-show backdrop latch="));
        assertFalse(coordinator.contains("cancelAttemptIfPopupNotBound"));

        assertTrue(coordinator.contains("static synchronized void armTouch("));
        assertTrue(coordinator.contains("static synchronized void prepareAuthoritativeRoot("));
        assertTrue(coordinator.contains("prepareInternal(captureRoot, glassConfig, true"));
        assertTrue(coordinator.contains("state.session.beginPrewarm()"));
        assertTrue(coordinator.contains("state.session.captureFirstFrameAndFreeze()"));
        assertTrue(coordinator.contains("boolean outputReady = ensurePopupOutput(state)"));
        assertTrue(coordinator.contains("state.latched = true"));
        assertTrue(coordinator.contains("if (!state.authoritativeFirstFrame"));
        assertTrue(coordinator.contains(
                "(state.session == null || !state.session.hasFrozenBackdrop())"));
        assertTrue(coordinator.contains("contentGroup.addView(layer, 0"));
        assertTrue(coordinator.contains("captureRoot.getLocationOnScreen(root)"));

        assertTrue(session.contains("void beginPrewarm()"));
        assertTrue(session.contains("boolean latchPreDragBackdrop()"));
        assertTrue(session.contains("void captureFirstFrameAndFreeze()"));
        assertTrue(session.contains("freezeOnNextFrame = true"));
        assertTrue(session.contains("\"shortcut-popup-authoritative-first-frame\""));
        assertTrue(session.contains("authoritative-root backdrop frozen"));
        assertTrue(session.contains("private final Object prewarmLock = new Object()"));
        assertTrue(session.contains("synchronized (prewarmLock)"));
        assertFalse(session.contains("shortcut-popup-frozen"));

        assertTrue(request.contains("static PassBlurBindRequest shortcutPopup(View authoritativeRoot)"));
        assertFalse(hook.contains("LauncherGlassSinkView.attachToMaterial"));
        assertFalse(hook.contains("attachToExternalMaterial"));
    }

    @Test public void popupDetachDefersCleanupOutsideVendorRemoveViewTraversal() throws Exception {
        String coordinator = Files.readString(MAIN.resolve("ShortcutPopupGlassCoordinator.java"));
        assertTrue(coordinator.contains("postDismissCleanup(state)"));
        assertTrue(coordinator.contains("private static void postDismissCleanup(State state)"));
        assertTrue(coordinator.contains("decor.post(() -> release(state, \"popup-detached\"))"));
    }

    @Test public void shortcutGlassUsesLocalCropAndLeavesDismissAnimationToMiuix()
            throws Exception {
        String hook = SourceContractText.read(
                MAIN.resolve("MiuixShortcutMenuGlassHook.java"));
        String coordinator = Files.readString(MAIN.resolve("ShortcutPopupGlassCoordinator.java"));
        String layer = SourceContractText.read(MAIN.resolve("ShortcutPopupGlassLayer.java"));
        String session = Files.readString(MAIN.resolve("ShortcutPopupGlassSession.java"));

        assertTrue(hook.contains(
                "Preserve MIUIX PopupAnimHelper as the sole dismiss presentation authority"));
        assertFalse(hook.contains("ShortcutPopupGlassCoordinator.beginDismissFade"));
        assertFalse(coordinator.contains("beginDismissFade"));
        assertFalse(layer.contains("FAST_DISMISS_FADE_MS"));
        assertFalse(layer.contains("fadeOutFast()"));
        assertFalse(layer.contains("DecelerateInterpolator"));

        // The replacement is a material child, so mContentView's vendor bounds/alpha/radius
        // animation carries it automatically instead of running a parallel animation.
        assertTrue(coordinator.contains("contentGroup.addView(layer, 0"));
        assertTrue(layer.contains("Local ShortcutMenu material output"));
        assertTrue(layer.contains("lives inside MIUIX mContentView at index 0"));

        // The local Surface samples only the popup's root-space crop. Never scale the entire
        // full-screen scene into local menu bounds.
        assertTrue(session.contains("presentCrop(prismalRenderer.outputTexture(), currentGeometry"));
        assertTrue(session.contains("geometry.cropLeft"));
        assertTrue(session.contains("geometry.cropBottom"));
        assertTrue(session.contains("geometry.cropWidth"));
        assertTrue(session.contains("geometry.cropHeight"));
        assertFalse(session.contains(
                "glUniform4f(requireUniform(compositeProgram, \"uCropRect\"), 0f, 0f, 1f, 1f)"));
    }

    @Test public void shortcutPopupReplacementHasDedicatedDefaultOnSetting() throws Exception {
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String hook = Files.readString(MAIN.resolve("MiuixShortcutMenuGlassHook.java"));
        String settings = SourceContractText.read(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(schema.contains("SHORTCUT_POPUP_GLASS = bool("));
        assertTrue(schema.contains("\"liquid_shortcut_popup_glass\", true, true, true"));
        assertTrue(hook.contains("ConfigSchema.Glass.SHORTCUT_POPUP_GLASS"));
        assertTrue(settings.contains("ConfigSchema.Glass.SHORTCUT_POPUP_GLASS"));
        assertTrue(settings.contains("桌面快捷菜单玻璃背景"));
        assertTrue(settings.contains("重启桌面后生效"));
    }

    @Test public void shortcutMenuDarkModeHasDedicatedDefaultOffSettingAfterGlassToggle() throws Exception {
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String settings = SourceContractText.read(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(schema.contains("SHORTCUT_POPUP_DARK_TEXT = bool("));
        assertTrue(schema.contains("\"liquid_shortcut_popup_dark_text\", false, false, false"));
        assertTrue(schema.contains("Glass.SHORTCUT_POPUP_GLASS, Glass.SHORTCUT_POPUP_DARK_TEXT"));
        assertTrue(settings.contains("ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT"));
        assertTrue(settings.contains("快捷菜单深色模式适配"));
        assertTrue(settings.contains("将快捷菜单文字和图标统一改为白色"));
        assertTrue(settings.contains(
                "ConfigSchema.Glass.SHORTCUT_POPUP_GLASS,\n"
                        + "            \"桌面快捷菜单玻璃背景\",\n"
                        + "            \"替换长按桌面图标弹出的快捷菜单背景；关闭后保留系统原生材质，重启桌面后生效\",\n"
                        + "            masterEnabled && liquidGlass,\n"
                        + "        )\n"
                        + "        BooleanSetting(\n"
                        + "            prefs,\n"
                        + "            ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT,"));
    }


    @Test public void launcherUninstallDialogUsesDialogRootBehindContentAuthority() throws Exception {
        String hook = Files.readString(MAIN.resolve("LauncherUninstallDialogGlassHook.java"));
        String coordinator = Files.readString(MAIN.resolve("LauncherDialogGlassCoordinator.java"));
        String sink = Files.readString(MAIN.resolve("LauncherGlassSinkView.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String bindRequest = Files.readString(MAIN.resolve("PassBlurBindRequest.java"));
        String domains = Files.readString(MAIN.resolve("PassBlurDomain.java"));
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String settings = SourceContractText.read(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        // Canonical HyperOS 4.50: Delete/Remove/SecondConfirm all extend BaseUninstallDialog.
        assertTrue(hook.contains(
                "com.miui.home.launcher.uninstall.BaseUninstallDialog"));
        assertTrue(hook.contains("base.getDeclaredConstructors()"));
        assertTrue(hook.contains("HookUtil.hook(constructor"));
        assertTrue(hook.contains(
                "com.miui.home.launcher.uninstall.DeleteDialog"));
        assertTrue(hook.contains(
                "com.miui.home.launcher.uninstall.RemoveDialog"));
        assertTrue(hook.contains(
                "com.miui.home.launcher.uninstall.SecondConfirmDialog"));
        // showDialog is supplemental only: it may invalidate a stale preloaded DeleteDialog when
        // the construction-time native-night setting changed. Glass ownership still comes solely
        // from the BaseUninstallDialog constructor and never from showDialog.
        assertTrue(hook.contains("\"showDialog\""));
        assertTrue(hook.contains("releasePreloadedDialog"));
        assertFalse(hook.contains("\"mDeleteDialog\""));
        assertFalse(hook.contains("\"mRemoveDialog\""));
        assertFalse(hook.contains("android.app.AlertDialog"));

        // Scope remains uninstall-only and MIUIX parentPanel remains the visual material host.
        assertTrue(coordinator.contains(
                "com.miui.home.launcher.uninstall.UninstallDialogViewContainer"));
        assertTrue(coordinator.contains(
                "miuix.appcompat.internal.widget.DialogParentPanel2"));
        assertTrue(coordinator.contains("DIALOG_PARENT_PANEL_ID = \"parentPanel\""));
        assertTrue(coordinator.contains("findExactClass(decor, UNINSTALL_CONTENT)"));
        assertTrue(coordinator.contains("findExactClass(decor, DIALOG_PARENT_PANEL)"));

        // The dialog ViewRoot, not the Launcher Activity root, is the behind-content authority.
        assertTrue(domains.contains("LAUNCHER_DIALOG"));
        assertTrue(bindRequest.contains(
                "static PassBlurBindRequest launcherDialog(View authoritativeRoot)"));
        assertTrue(bindRequest.contains("PassBlurDomain.LAUNCHER_DIALOG"));
        assertTrue(coordinator.contains("View dialogRoot = decor.getRootView()"));
        assertTrue(coordinator.contains("PassBlurBindRequest.launcherDialog(dialogRoot)"));
        assertTrue(coordinator.contains("new LauncherGlassSession("));
        assertTrue(coordinator.contains("authority.requestFreshBackdrop()"));
        assertFalse(coordinator.contains("LauncherGlassSessionRegistry.acquire("));
        assertFalse(coordinator.contains("LauncherGlassSceneController.requestFreshForRoot"));

        // Output is in the same dialog ViewRoot, so its own pre-draw drives animation geometry.
        assertTrue(coordinator.contains("LauncherGlassSinkView.attachToExternalMaterial"));
        assertTrue(coordinator.contains("sink.runWhenOutputLost"));
        assertTrue(coordinator.contains("setTerminalFailureListener"));
        assertTrue(coordinator.contains("installMaterialGuard(binding)"));
        assertTrue(coordinator.contains("OnPreDrawListener"));
        assertTrue(session.contains("void setTerminalFailureListener(Runnable listener)"));
        assertTrue(session.contains("void requestFreshBackdrop()"));
        assertTrue(sink.contains("void runWhenOutputLost(Runnable listener)"));

        // Stock MIUIX material is suppressed in the same layout pass that creates the
        // replacement output, so dialog open cannot flash stock.
        assertTrue(coordinator.contains("claimVendorMaterial(dialog, binding);"));
        assertFalse(coordinator.contains(
                "if (owner != null) claimVendorMaterial(owner, binding)"));
        assertTrue(coordinator.contains("MiBlurBridge.getPassWindowBlurEnabled(panel)"));
        assertTrue(coordinator.contains("MiBlurBridge.setPassWindowBlurEnabled(panel, false)"));
        assertTrue(coordinator.contains("MiBlurBridge.setPassWindowBlurEnabled(panel, true)"));
        assertTrue(coordinator.contains("cloneTransparent(background, panel)"));
        assertTrue(coordinator.contains("panel.setBackground(transparentBackground)"));
        assertTrue(coordinator.contains("panel.setBackground(binding.originalBackground)"));
        assertFalse(coordinator.contains("background.setAlpha(0)"));
        assertFalse(coordinator.contains("int originalBackgroundAlpha"));
        assertTrue(coordinator.contains("binding.transparentBackground.setAlpha(0)"));
        assertTrue(coordinator.contains("MAIN.post(() -> releaseObserved(binding))"));
        assertTrue(coordinator.contains("OnGlobalLayoutListener"));
        assertFalse(coordinator.contains("postDelayed("));

        assertTrue(schema.contains("UNINSTALL_DIALOG_GLASS = bool("));
        assertTrue(schema.contains("\"liquid_uninstall_dialog_glass\", true, true, true"));
        assertTrue(settings.contains("Page.DialogCustomization -> DialogGlassSettingsPage("));
        assertTrue(settings.contains("openDialogCustomization = { page = Page.DialogCustomization }"));
    }

    @Test public void launcherDialogGlassFollowsMiuixDimViewAndHasIndependentAppearancePage()
            throws Exception {
        String coordinator = Files.readString(MAIN.resolve("LauncherDialogGlassCoordinator.java"));
        String preferences = Files.readString(MAIN.resolve("LauncherDialogGlassPreferences.java"));
        String hook = Files.readString(MAIN.resolve("LauncherUninstallDialogGlassHook.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String settings = SourceContractText.read(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String dialogPage = SourceContractText.read(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/DialogGlassSettingsPage.kt"));
        String params = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java"));
        String nativeNight = Files.readString(
                MAIN.resolve("LauncherDialogNativeNightBridge.java"));

        // Canonical MIUIX immersive AlertDialog uses @id/dialog_dim_bg as the real dim
        // authority and animates its alpha. Window DIM_BEHIND remains fallback-only.
        assertTrue(coordinator.contains("DIALOG_DIM_BG_ID = \"dialog_dim_bg\""));
        assertTrue(coordinator.contains("findByResourceEntryName(decor, DIALOG_DIM_BG_ID)"));
        assertTrue(coordinator.contains("dimBg.getAlpha()"));
        assertTrue(coordinator.contains("syncDialogDim(binding, dialog, false)"));
        assertTrue(coordinator.contains("syncDialogDim(binding, owner, true)"));
        assertTrue(coordinator.contains("applyDialogMaterial(binding, dialogRoot, effectiveDim)"));
        assertTrue(coordinator.contains("restoreDialogDim(binding)"));
        assertTrue(coordinator.contains("WindowManager.LayoutParams.FLAG_DIM_BEHIND"));
        assertTrue(coordinator.contains("attributes.dimAmount"));
        assertTrue(preferences.contains(
                "out.brightness = source.brightness * (1f - safeDim)"));
        assertTrue(session.contains("void setPrismalParams(PrismalParams params)"));
        assertTrue(session.contains("if (backdropPrepared) requestSceneRedraw();"));
        assertTrue(params.contains("public static Builder builder(PrismalParams source)"));

        // The opt-out suppresses the actual MIUIX dim View without destroying its animated alpha,
        // while non-immersive variants retain the WindowManager fallback.
        assertTrue(schema.contains("DIALOG_DISABLE_DIMMING = bool("));
        assertTrue(schema.contains("\"liquid_dialog_disable_dimming\", false, false, false"));
        assertTrue(coordinator.contains("dimBg.setAlpha(0f)"));
        assertFalse(coordinator.contains("dimBg.setVisibility(View.INVISIBLE)"));
        assertTrue(coordinator.contains("binding.dimViewSuppressed = true"));
        assertTrue(coordinator.contains("lastVendorDimAlpha"));
        assertTrue(coordinator.contains(
                "window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)"));
        assertTrue(coordinator.contains("binding.windowDimSuppressed = true"));
        assertTrue(dialogPage.contains("关闭对话时背景压暗"));
        assertTrue(dialogPage.contains("点击对话框外部关闭弹窗"));

        // Blur/color are optional dialog-local overrides; absence means inheritance from global.
        assertTrue(schema.contains("DIALOG_BLUR = integer("));
        assertTrue(schema.contains("\"liquid_dialog_blur\", 0, null, 0, 0, 60"));
        assertTrue(schema.contains("DIALOG_TINT_RED = integer("));
        assertTrue(schema.contains("DIALOG_TINT_GREEN = integer("));
        assertTrue(schema.contains("DIALOG_TINT_BLUE = integer("));
        assertTrue(schema.contains("DIALOG_TINT_ALPHA = integer("));
        assertTrue(preferences.contains("reader.has(BLUR_KEY) ? reader.f(BLUR_KEY, baseBlur) : baseBlur"));
        assertTrue(preferences.contains("out.blurRadiusPx = resolved.blur"));
        assertTrue(preferences.contains("out.tintR = resolved.tintR / 255f"));
        assertTrue(preferences.contains("out.tintA = resolved.tintAlpha / 255f"));

        // Settings are resolved for every new dialog rather than frozen at Launcher hook install.
        assertTrue(hook.contains("ConfigReader liveReader = ConfigReader.load()"));
        assertTrue(hook.contains("LiquidDockConfig liveConfig = LiquidDockConfig.from(liveReader)"));
        assertTrue(hook.contains("ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.name()"));

        // UI lives under Liquid Glass as the requested child page.
        assertTrue(settings.contains("DialogCustomization(R.string.page_dialog_customization)"));
        assertTrue(settings.contains(
                "Page.DialogCustomization, Page.ThirdPartyApps,"));
        assertTrue(settings.contains("title = stringResource(R.string.page_dialog_customization)"));
        assertTrue(dialogPage.contains("ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS"));
        assertTrue(dialogPage.contains("ConfigSchema.Glass.DIALOG_DISABLE_DIMMING"));
        assertTrue(dialogPage.contains("DialogAppearanceValueSlider("));
        assertTrue(dialogPage.contains("对话背景模糊"));
        assertTrue(dialogPage.contains("恢复继承全局外观"));

        // Native dark mode is decided before MIUIX inflates the dialog hierarchy. The exact
        // AlertController Context is replaced with the same dialog theme on a -night
        // Configuration, so MIUIX remains the sole owner of text/button/icon/selectors.
        assertTrue(schema.contains("DIALOG_DARK_MODE = bool("));
        assertTrue(schema.contains("\"liquid_dialog_dark_mode\", false, false, false"));
        assertTrue(dialogPage.contains("对话框深色模式"));
        assertTrue(dialogPage.contains("原生夜间资源"));
        assertFalse(preferences.contains("out.tintR *= 0.22f"));
        assertFalse(preferences.contains("out.tintA = Math.max(out.tintA, 0.58f)"));

        assertTrue(hook.contains("LauncherDialogNativeNightBridge.install(classLoader)"));
        assertTrue(hook.contains("LauncherDialogNativeNightBridge.enter()"));
        assertTrue(hook.contains("requestNativeNight"));
        assertTrue(hook.contains("result = chain.proceed(args)"));
        assertTrue(hook.contains("nightScope.close()"));
        assertTrue(hook.contains("installDeleteDialogCacheInvalidationHook(classLoader)"));
        assertTrue(hook.contains("\"showDialog\""));
        assertTrue(hook.contains("releasePreloadedDialog"));
        assertTrue(hook.contains("cachedDeleteDialogNativeNight"));
        assertTrue(hook.contains("constructedNight.booleanValue() == desiredNight"));

        assertTrue(nativeNight.contains(
                "ALERT_CONTROLLER = \"miuix.appcompat.app.AlertController\""));
        assertTrue(nativeNight.contains(
                "APP_COMPAT_DIALOG = \"androidx.appcompat.app.AppCompatDialog\""));
        assertTrue(nativeNight.contains(
                "getDeclaredConstructor(\n                    Context.class, appCompatDialog, Window.class)"));
        assertTrue(nativeNight.contains("original.createConfigurationContext(override)"));
        assertTrue(nativeNight.contains("Configuration.UI_MODE_NIGHT_YES"));
        assertTrue(nativeNight.contains("resolveThemeResId(original)"));
        assertTrue(nativeNight.contains("new ForcedNightDialogContext(configured, themeResId)"));
        assertTrue(nativeNight.contains("UNINSTALL_LAYOUT = \"shortcut_uninstall_dialog\""));
        assertTrue(nativeNight.contains("LayoutInflater.class"));
        assertTrue(nativeNight.contains("cloneInContext(forced)"));
        assertTrue(nativeNight.contains("NIGHT_INFLATE_REENTRY"));
        assertTrue(nativeNight.contains("getThemeResId"));
        assertTrue(nativeNight.contains("mThemeResource"));
        assertFalse(nativeNight.contains("import android.content.pm.ActivityInfo"));
        assertFalse(nativeNight.contains("import android.content.pm.ApplicationInfo"));
        assertFalse(nativeNight.contains("getActivityInfo("));
        assertFalse(nativeNight.contains("getApplicationInfo().theme"));
        assertFalse(nativeNight.contains("AlertDialog_Theme_Dark"));
        assertFalse(nativeNight.contains("setTextColor("));
        assertFalse(nativeNight.contains("setBackgroundTintList("));
        assertFalse(nativeNight.contains("setImageTintList("));
        assertFalse(nativeNight.contains("setCompoundDrawableTintList("));
        assertFalse(nativeNight.contains("injected AlertController context"));
        assertFalse(nativeNight.contains("inflated native night uninstall content"));

        assertFalse(coordinator.contains("LauncherDialogDarkModeController"));
        assertFalse(coordinator.contains("darkModeSession"));
        assertFalse(coordinator.contains("logNativeNightSnapshot"));
        assertFalse(coordinator.contains("logTree("));
    }

    @Test public void shortcutMenuDarkModeSamplesAndCachesOnlyNearBlackIcons() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixShortcutMenuGlassHook.java"));
        String controller = Files.readString(MAIN.resolve("ShortcutMenuDarkModeController.java"));

        assertTrue(hook.contains("ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT"));
        assertTrue(hook.contains("ShortcutMenuDarkModeController.attach"));
        assertTrue(controller.contains("TextView"));
        assertTrue(controller.contains("setTextColor(Color.WHITE)"));
        assertTrue(controller.contains("setCompoundDrawableTintList"));
        assertTrue(controller.contains("ImageView"));
        assertTrue(controller.contains("WeakHashMap<Drawable, Boolean>"));
        assertTrue(controller.contains("shouldTintIcon(Drawable drawable)"));
        assertTrue(controller.contains("Bitmap.createBitmap"));
        assertTrue(controller.contains("Color.red"));
        assertTrue(controller.contains("Color.green"));
        assertTrue(controller.contains("Color.blue"));
        assertTrue(controller.contains("setImageTintList(WHITE_TINT)"));
        assertFalse(controller.contains("setImageTintList(null)"));
        assertTrue(controller.contains("OnGlobalLayoutListener"));
        assertFalse(controller.contains("if (view instanceof ImageView) {\n            ((ImageView) view).setImageTintList(WHITE_TINT);"));
        assertFalse(hook.contains("Class.forName(\"com.hellovoid.liquiddock"));
        assertFalse(controller.contains("Class.forName("));
        assertFalse(controller.contains("getDeclaredField("));
        assertFalse(controller.contains("getDeclaredMethod("));
    }
}
