package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static HyperOS 4.50 vendor/API boundary contract for shortcut-menu popup glass. */
public class ShortcutSecondaryGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void shortcutMenuUsesAsyncPreShowSourceAndShowTimeStableOutput() throws Exception {
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
        assertTrue(coordinator.contains("decorGroup.addView(layer, popupIndex"));
        assertTrue(coordinator.contains("ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue(coordinator.contains("private static void onPresented(State state)"));
        assertTrue(coordinator.contains("MiBlurBridge.clearContentBlur(content)"));
        assertFalse(coordinator.contains("if (!state.session.hasFrozenBackdrop())"));
        assertFalse(coordinator.contains("pre-show-backdrop-not-ready"));
        assertTrue(session.contains("PassBlurBindRequest.shortcutPopup(sourceRoot)"));
        assertTrue(session.contains("setUpdatesEnabled(false, \"shortcut-popup-frozen\")"));
        assertTrue(request.contains("static PassBlurBindRequest shortcutPopup(View authoritativeRoot)"));
        assertFalse(hook.contains("LauncherGlassSinkView.attachToMaterial"));
        assertFalse(hook.contains("attachToExternalMaterial"));
    }
}
