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

    @Test
    public void securityCenterFreshRequestsUseDemandPulseProducer() throws Exception {
        Path main = Path.of("src/main/java/com/hellovoid/liquiddock");
        String backend = Files.readString(main.resolve("RootPassBlurBackend.java"));
        String bridge = Files.readString(main.resolve("Miuix307PassBlurBridge.java"));

        assertTrue("Security Center default fresh requests must select the existing single-frame pulse",
                backend.contains("PassBlurBindPolicy.shouldPauseAfterFreshFrame(bindRequest.domain())"));
        assertTrue("Security Center pulse must stop producer updates after the frame-bound pulse window",
                bridge.contains("binding.domain == PassBlurDomain.SECURITY_CENTER"));
        assertTrue("the pulse tail must use the existing native pause operation",
                bridge.contains("pauseUpdates(binding);"));
    }
}
