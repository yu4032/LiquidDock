package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source-level contract for the decompiled WMShell HOME timing integration. */
public class SystemUiHomeTransitionWiringContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void sourceHooksActualHomeTransitionObserverLifecycle() throws Exception {
        String source = Files.readString(MAIN.resolve("SystemUiHomeTransitionSource.java"));
        assertTrue(source.contains("com.android.wm.shell.transition.HomeTransitionObserver"));
        assertTrue(source.contains("\"onTransitionReady\""));
        assertTrue(source.contains("\"notifyHomeVisibilityChanged\""));
        assertTrue(source.contains("\"onTransitionStarting\""));
        assertTrue(source.contains("\"onTransitionFinished\""));
        assertTrue(source.contains("\"onTransitionMerged\""));
        assertTrue(source.contains("tracker.beginReady"));
        assertTrue(source.contains("tracker.recordCurrentReadyVisibility"));
        assertTrue(source.contains("tracker.onStarting"));
        assertTrue(source.contains("tracker.onFinished"));
        assertTrue(source.contains("tracker.onMerged"));
    }

    @Test public void protocolCarriesPhaseVisibilitySerialAndMonotonicTimestamp() throws Exception {
        String protocol = Files.readString(MAIN.resolve("SystemUiHomeTransitionProtocol.java"));
        assertTrue(protocol.contains("PHASE_START"));
        assertTrue(protocol.contains("PHASE_FINISH"));
        assertTrue(protocol.contains("EXTRA_HOME_VISIBLE"));
        assertTrue(protocol.contains("EXTRA_SERIAL"));
        assertTrue(protocol.contains("EXTRA_EVENT_TIME_NANOS"));
        assertTrue(protocol.contains("com.miui.home"));
    }

    @Test public void moduleInstallsSystemUiSourceAndLauncherReceiver() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        assertTrue(module.contains("SystemUiHomeTransitionSource.install(param.getClassLoader())"));
        assertTrue(module.contains("SystemUiHomeTransitionRuntime.install()"));
    }

    @Test public void workspaceRootRetriesReceiverRegistrationWithLiveContext() throws Exception {
        String scene = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        assertTrue("PackageReady may run before ActivityThread.currentApplication exists; a live"
                        + " Workspace root must retry receiver registration",
                scene.contains("SystemUiHomeTransitionRuntime.ensureRegistered(root.getContext())"));
    }

    @Test public void launcherTreatsSystemUiHomeStartAsAuthorityWithVendorFallback() throws Exception {
        String runtime = Files.readString(MAIN.resolve("SystemUiHomeTransitionRuntime.java"));
        String hook = Files.readString(MAIN.resolve("LauncherGlassHomePresentationHook.java"));
        assertTrue(runtime.contains("onSystemUiHomeTransitionStarted"));
        assertTrue(runtime.contains("onSystemUiHomeTransitionFinished"));
        assertTrue(hook.contains("onSystemUiHomeTransitionStarted"));
        assertTrue(hook.contains("onSystemUiHomeTransitionFinished"));
        assertTrue(hook.contains("beginHomeReturnRevealForAll()"));
        assertTrue(hook.contains("lastLauncherHomeEndElapsedNanos"));
        assertTrue("Launcher WindowElement marker must remain as fallback",
                hook.contains("WINDOW_ELEMENT") && hook.contains("containsHomeClose(args)"));
        assertFalse("SystemUI timing must not disable the Launcher fallback hook",
                hook.contains("SYSTEMUI_ONLY"));
    }
}
