package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static HyperOS 4.50 vendor/API boundary contract for shortcut-menu popup glass. */
public class ShortcutSecondaryGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void shortcutMenuRendezvousDoesNotRequireSourceSessionBeforeShow() throws Exception {
        String hook = SourceContractText.read(MAIN.resolve("MiuixShortcutMenuGlassHook.java"));
        String coordinator = Files.readString(MAIN.resolve("ShortcutPopupGlassCoordinator.java"));
        String session = Files.readString(MAIN.resolve("ShortcutPopupGlassSession.java"));
        String request = Files.readString(MAIN.resolve("PassBlurBindRequest.java"));

        assertTrue(hook.contains("com.miui.home.launcher.ShortcutMenuLayer"));
        assertTrue(hook.contains("\"setRequestingItemInfo\""));
        assertTrue(hook.contains("ShortcutPopupGlassCoordinator.prepare"));
        assertTrue(hook.contains("ownerView.getRootView()"));
        assertTrue(hook.contains("ShortcutPopupGlassCoordinator.bindPopup"));
        assertTrue(coordinator.contains("ShortcutPopupSourceOverlay.attach"));
        assertTrue(coordinator.contains("ensurePopupOutput(state)"));
        assertTrue(coordinator.contains("private static boolean ensurePopupOutput(State state)"));
        assertTrue(coordinator.contains("decorGroup.addView(layer, popupIndex"));
        assertTrue(coordinator.contains("ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue(coordinator.contains("private static void onPresented(State state)"));
        assertTrue(coordinator.contains("MiBlurBridge.clearContentBlur(content)"));
        assertFalse(coordinator.contains("if (!state.session.hasFrozenBackdrop())"));
        assertFalse(coordinator.contains("pre-show-backdrop-not-ready"));
        assertFalse(coordinator.contains("|| state.session == null || popupView == null"));
        assertTrue(session.contains("PassBlurBindRequest.shortcutPopup(sourceRoot)"));
        assertTrue(session.contains("setUpdatesEnabled(false, \"shortcut-popup-frozen\")"));
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

    @Test public void dismissStartsFastFadeWithoutDestroyingGlassResources() throws Exception {
        String hook = SourceContractText.read(
                MAIN.resolve("MiuixShortcutMenuGlassHook.java"));
        String coordinator = Files.readString(MAIN.resolve("ShortcutPopupGlassCoordinator.java"));
        String layer = SourceContractText.read(MAIN.resolve("ShortcutPopupGlassLayer.java"));

        assertTrue(hook.contains(
                "Object menu = chain.getThisObject();\n"
                        + "                    ShortcutPopupGlassCoordinator.beginDismissFade(menu);\n"
                        + "                    Object result = chain.proceed"));
        assertTrue(coordinator.contains("static synchronized void beginDismissFade(Object menu)"));
        assertTrue(coordinator.contains("layer.fadeOutFast()"));
        assertTrue(layer.contains("private static final long FAST_DISMISS_FADE_MS = 90L"));
        assertTrue(layer.contains(
                "void fadeOutFast() {\n"
                        + "        if (disposed) return;\n"
                        + "        animate().cancel();\n"
                        + "        animate()\n"
                        + "                .alpha(0f)\n"
                        + "                .setDuration(FAST_DISMISS_FADE_MS)\n"
                        + "                .setInterpolator(new DecelerateInterpolator())\n"
                        + "                .start();\n"
                        + "    }"));
        assertFalse(layer.contains("fadeOutFast();\n        dispose()"));
        assertFalse(layer.contains("fadeOutFast();\n        session.shutdown()"));
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
        assertFalse(hook.contains("\"showDialog\""));
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
        assertTrue(coordinator.contains("sink.runWhenFirstFramePresented"));
        assertTrue(coordinator.contains("sink.runWhenOutputLost"));
        assertTrue(coordinator.contains("setTerminalFailureListener"));
        assertTrue(coordinator.contains("installMaterialGuard(binding)"));
        assertTrue(coordinator.contains("OnPreDrawListener"));
        assertTrue(session.contains("void setTerminalFailureListener(Runnable listener)"));
        assertTrue(session.contains("void requestFreshBackdrop()"));
        assertTrue(sink.contains("void runWhenOutputLost(Runnable listener)"));
        assertTrue(sink.contains("public void onSurfaceTextureUpdated(SurfaceTexture surface)"));

        // Stock MIUIX material is handed off only after a real frame and restored exactly.
        assertTrue(coordinator.contains("MiBlurBridge.getPassWindowBlurEnabled(panel)"));
        assertTrue(coordinator.contains("MiBlurBridge.setPassWindowBlurEnabled(panel, false)"));
        assertTrue(coordinator.contains("MiBlurBridge.setPassWindowBlurEnabled(panel, true)"));
        assertTrue(coordinator.contains("cloneTransparent(background, panel)"));
        assertTrue(coordinator.contains("panel.setBackground(transparentBackground)"));
        assertTrue(coordinator.contains("panel.setBackground(binding.originalBackground)"));
        assertFalse(coordinator.contains("background.setAlpha(0)"));
        assertFalse(coordinator.contains("originalBackgroundAlpha"));
        assertTrue(coordinator.contains("OnGlobalLayoutListener"));
        assertFalse(coordinator.contains("postDelayed("));

        assertTrue(schema.contains("UNINSTALL_DIALOG_GLASS = bool("));
        assertTrue(schema.contains("\"liquid_uninstall_dialog_glass\", true, true, true"));
        assertTrue(settings.contains("ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS"));
        assertTrue(settings.contains("桌面卸载弹窗玻璃背景"));
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
