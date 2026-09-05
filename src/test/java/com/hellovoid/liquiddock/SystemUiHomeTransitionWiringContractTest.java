package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static API/wiring contract for the decompiled WMShell HOME integration. */
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
}
