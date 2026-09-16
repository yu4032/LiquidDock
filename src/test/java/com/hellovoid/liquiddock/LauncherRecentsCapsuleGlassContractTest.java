package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static architecture contract for the two HyperOS Pad Recents action capsules. */
public class LauncherRecentsCapsuleGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    private static String read(Path root, String name) throws Exception {
        Path path = root.resolve(name);
        return Files.exists(path) ? Files.readString(path) : "";
    }

    private static String read(String name) throws Exception { return read(MAIN, name); }

    @Test public void recentsInstallsDedicatedStableCapsuleHook() throws Exception {
        String recents = read("LauncherGlassRecentsHook.java");
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        assertTrue(recents.contains("LauncherRecentsCapsuleGlassHook.install(classLoader)"));
        assertTrue(capsule.contains("com.miui.home.recents.views.RecentsDecorations"));
        assertTrue(capsule.contains("\"findAndSetupViews\""));
    }

    @Test public void targetsOnlyDecompiledStableResourceOwners() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        assertTrue(capsule.contains("recent_clear_all_task_container_for_pad"));
        assertTrue(capsule.contains("world_container"));
        assertFalse(capsule.contains("mClearAllTaskContainerForPad"));
        assertFalse(capsule.contains("mWorldContainer"));
        assertFalse(capsule.contains("getDeclaredField"));
        assertFalse(capsule.contains("0x7f"));
        assertFalse(capsule.contains("postDelayed"));
        assertFalse(capsule.contains("setOnClickListener"));
    }

    @Test public void recentsUsesOneDedicatedFullPrismalSessionForBothCapsules() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        String overlay = read("RecentsCapsuleGlassOverlay.java");
        String session = read("RecentsCapsuleGlassSession.java");
        assertTrue(capsule.contains("RecentsCapsuleGlassOverlay"));
        assertTrue(capsule.contains("RecentsCapsuleGlassSession"));
        assertFalse(capsule.contains("MiBlurBridge.applyPassWindowBlur"));
        assertTrue(overlay.contains("extends TextureView"));
        assertTrue(overlay.contains("setOpaque(false)"));
        assertTrue(overlay.contains("setClickable(false)"));
        assertTrue(overlay.contains("setFocusable(false)"));
        assertTrue(session.contains("RootPassBlurBackend"));
        assertTrue(session.contains("PassBlurBindRequest.recentsCapsule"));
        assertTrue(session.contains("Miuix307PrismalMaterial.fromConfig"));
        assertTrue(session.contains("Miuix307PrismalAdapter.toPortable"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(session.contains("drawGlass"));
        assertTrue(session.contains("launcherHighlightProfile"));
    }

    @Test public void stockBackgroundIsRemovedOnlyAfterPrismalPresentationAndRestored() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        assertTrue(capsule.contains("stockBackground"));
        assertTrue(capsule.contains("onFirstFramePresented"));
        assertTrue(capsule.contains("setBackground(null)"));
        assertTrue(capsule.contains("restoreStockBackground"));
    }

    @Test public void replacementHasIndependentRuntimeSwitch() throws Exception {
        String schema = read("config/ConfigSchema.java");
        String config = read("LiquidDockConfig.java");
        String runtime = read("GlassRuntimeState.java");
        String ui = read(KOTLIN, "ComposeSettingsActivity.kt");
        assertTrue(schema.contains("RECENTS_CAPSULE_GLASS"));
        assertTrue(schema.contains("liquid_recents_capsule_glass"));
        assertTrue(config.contains("recentsCapsuleEnabled"));
        assertTrue(runtime.contains("isRecentsCapsuleEnabled"));
        assertTrue(ui.contains("ConfigSchema.Glass.RECENTS_CAPSULE_GLASS"));
        assertTrue(ui.contains("多任务操作按钮玻璃"));
    }

    @Test public void recentsHasDedicatedPassBlurDomainAndNeverSharesHomeCoverageState() throws Exception {
        String domain = read("PassBlurDomain.java");
        String request = read("PassBlurBindRequest.java");
        String session = read("RecentsCapsuleGlassSession.java");
        assertTrue(domain.contains("RECENTS_CAPSULE"));
        assertTrue(request.contains("recentsCapsule"));
        assertFalse(session.contains("LauncherGlassSessionRegistry"));
        assertFalse(session.contains("setRecentsCovered"));
    }
}
