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
        String hook = Files.readString(MAIN.resolve("MiuixShortcutMenuGlassHook.java"));
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
        String hook = Files.readString(MAIN.resolve("MiuixShortcutMenuGlassHook.java"));
        String coordinator = Files.readString(MAIN.resolve("ShortcutPopupGlassCoordinator.java"));
        String layer = Files.readString(MAIN.resolve("ShortcutPopupGlassLayer.java"));

        assertTrue(hook.contains(
                "Object menu = chain.getThisObject();\n"
                        + "                ShortcutPopupGlassCoordinator.beginDismissFade(menu);\n"
                        + "                Object result = chain.proceed"));
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
        String settings = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(schema.contains("SHORTCUT_POPUP_GLASS = bool("));
        assertTrue(schema.contains("\"liquid_shortcut_popup_glass\", true, true, true"));
        assertTrue(hook.contains("ConfigSchema.Glass.SHORTCUT_POPUP_GLASS"));
        assertTrue(hook.contains("disabled by shortcut popup replacement setting"));
        assertTrue(settings.contains("ConfigSchema.Glass.SHORTCUT_POPUP_GLASS"));
        assertTrue(settings.contains("桌面快捷菜单玻璃背景"));
        assertTrue(settings.contains("重启桌面后生效"));
    }
}
