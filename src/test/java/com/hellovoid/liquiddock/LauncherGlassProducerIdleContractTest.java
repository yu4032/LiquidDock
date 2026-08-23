package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Workspace may explicitly pulse its producer, but Dock bind itself must remain continuous. */
public class LauncherGlassProducerIdleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void workspaceSessionOwnsExplicitPauseAndSingleRefreshCalls() throws Exception {
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue(session.contains("Miuix307PassBlurBridge.requestSingleUpdate"));
        assertTrue(session.contains("Miuix307PassBlurBridge.pauseUpdates"));
        assertTrue(bridge.contains("static void requestSingleUpdate(Binding binding, View host)"));
        assertTrue(bridge.contains("static void pauseUpdates(Binding binding)"));
        assertFalse(bridge.contains("callerManagedUpdates"));
    }

    @Test public void singleWorkspaceRefreshForcesOneViewRootDamageBeforePausing() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        int start = bridge.indexOf("static void requestSingleUpdate(Binding binding, View host)");
        int end = bridge.indexOf("static void pauseUpdates", start);
        assertTrue(start >= 0 && end > start);
        String method = bridge.substring(start, end);

        assertTrue(method.contains("setUpdatesEnabled(binding, true);"));
        assertTrue(method.contains("host.postInvalidateOnAnimation();"));
        assertTrue(method.contains("schedulePauseUpdates(host, binding, INITIAL_UPDATE_FRAMES);"));
    }
}
