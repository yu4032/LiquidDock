package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static architecture bans for the generic root PassBlur backend. */
public class RootPassBlurBackendBoundaryTest {
    @Test
    public void genericBackendDoesNotReflectIntoViewRootOrSurfaceControl() throws Exception {
        Path main = Path.of("src/main/java/com/hellovoid/liquiddock");
        String backend = Files.readString(main.resolve("RootPassBlurBackend.java"));
        String bridge = Files.readString(main.resolve("RootPassBlurEndpointBridge.java"));

        assertFalse(backend.contains("java.lang.reflect"));
        assertFalse(backend.contains("getViewRootImpl"));
        assertFalse(backend.contains("mSurfaceSize"));
        assertFalse(backend.contains("mWindowAttributes"));
        assertFalse(backend.contains("android.view.SurfaceControl"));

        assertTrue(bridge.contains("getViewRootImpl"));
        assertTrue(bridge.contains("getSurfaceControl"));
    }

    @Test
    public void sourceRecoveryUsesFrameLifecycleInsteadOfFixedDelay() throws Exception {
        Path main = Path.of("src/main/java/com/hellovoid/liquiddock");
        String backend = Files.readString(main.resolve("RootPassBlurBackend.java"));

        assertFalse("root PassBlur recovery must never use a fixed-delay watchdog",
                backend.contains("postDelayed("));
        assertTrue("producer readiness retries must stay tied to real animation-frame opportunities",
                backend.contains("postOnAnimation("));
    }
}
