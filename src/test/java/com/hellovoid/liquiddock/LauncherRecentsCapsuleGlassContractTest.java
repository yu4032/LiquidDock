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

    @Test public void recentsDefersBindingUntilAttachedAndLaidOut() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        assertTrue(capsule.contains("PendingBinding"));
        assertTrue(capsule.contains("OnAttachStateChangeListener"));
        assertTrue(capsule.contains("OnLayoutChangeListener"));
        assertTrue(capsule.contains("isAttachedToWindow()"));
        assertTrue(capsule.contains("tryBindIfReady"));
        assertFalse(capsule.contains("Recents root unavailable; stock retained"));
    }

    @Test public void sinksNeverConsumeLinearLayoutSpace() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        String sink = read("RecentsCapsuleGlassSinkView.java");
        assertTrue(sink.contains("attachInsideTarget"));
        assertTrue(sink.contains("new ViewGroup.LayoutParams(0, 0)"));
        assertTrue(sink.contains("layout(0, 0, width, height)"));
        assertTrue(sink.contains("targetGroup.addView(sink, 0"));
        assertFalse(sink.contains("parent.addView(sink, index"));
        assertFalse(capsule.contains("attachBehindTarget"));
        assertTrue(capsule.contains("clearAllSink = installSink(clearAll"));
        assertTrue(capsule.contains("worldSink = installSink(world"));
    }

    @Test public void localSinkKeepsNativeContentAboveGlassAndUsesTargetVisualBounds() throws Exception {
        String sink = read("RecentsCapsuleGlassSinkView.java");
        assertTrue(sink.contains("targetRef"));
        assertTrue(sink.contains("syncFromTarget"));
        assertTrue(sink.contains("target.getGlobalVisibleRect"));
        assertTrue(sink.contains("LauncherGlassScreenSpace.relativeToRoot"));
        assertFalse(sink.contains("setScaleX(target.getScaleX())"));
        assertFalse(sink.contains("setScaleY(target.getScaleY())"));
        assertFalse(sink.contains("setRotation(target.getRotation())"));
    }

    @Test public void recentsUsesOnePrismalSessionForBothLocalSinks() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        String sink = read("RecentsCapsuleGlassSinkView.java");
        String session = read("RecentsCapsuleGlassSession.java");
        assertTrue(capsule.contains("RecentsCapsuleGlassSession"));
        assertTrue(capsule.contains("RecentsCapsuleGlassSinkView"));
        assertTrue(sink.contains("extends TextureView"));
        assertTrue(sink.contains("setOpaque(false)"));
        assertTrue(sink.contains("setClickable(false)"));
        assertTrue(sink.contains("setFocusable(false)"));
        assertTrue(sink.contains("session.attachOutput(targetId"));
        assertTrue(session.contains("RootPassBlurBackend"));
        assertTrue(session.contains("PassBlurBindRequest.recentsCapsule"));
        assertTrue(session.contains("Miuix307PrismalMaterial.fromConfig"));
        assertTrue(session.contains("Miuix307PrismalAdapter.toPortable"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(session.contains("drawGlass"));
        assertTrue(session.contains("launcherHighlightProfile"));
        assertTrue(session.contains("Target.CLEAR_ALL"));
        assertTrue(session.contains("Target.WORLD"));
        assertTrue(session.contains("g.cropLeft"));
        assertTrue(session.contains("g.cropBottom"));
    }

    @Test public void recentsShowReclaimsContinuousSourceAfterWorkspaceCoverage() throws Exception {
        String recents = read("LauncherGlassRecentsHook.java");
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        String session = read("RecentsCapsuleGlassSession.java");
        String bridge = read("Miuix307PassBlurBridge.java");

        assertTrue(recents.contains("LauncherRecentsCapsuleGlassHook.onRecentsShown()"));
        assertTrue(capsule.contains("binding.onRecentsShown()"));
        assertTrue(session.contains(
                "sourceBackend.setUpdatesEnabled(true, \"recents-capsule-visible\")"));
        assertTrue(session.contains("sourceBackend.requestFresh(GENERATION)"));
        assertTrue(bridge.contains("binding.domain == PassBlurDomain.RECENTS_CAPSULE"));
    }

    @Test public void nativeBlurStaysUntilPrismalActuallyPresents() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        assertTrue(capsule.contains("MiBlurBridge.applyPassWindowBlur"));
        assertTrue(capsule.contains("MiBlurBridge.clearPassWindowBlur"));
        assertTrue(capsule.contains("onFirstFramePresented"));
        assertTrue(capsule.contains("clearNativeFallback"));
    }

    @Test public void stockBackgroundIsRemovedOnlyAfterPrismalPresentationAndNativeContentRemains() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        assertTrue(capsule.contains("clearAllStockBackground"));
        assertTrue(capsule.contains("worldStockBackground"));
        assertTrue(capsule.contains("onFirstFramePresented"));
        assertTrue(capsule.contains("setBackground(null)"));
        assertTrue(capsule.contains("restoreStockBackground"));
        assertFalse(capsule.contains("clearAll.setVisibility"));
        assertFalse(capsule.contains("world.setVisibility"));
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
