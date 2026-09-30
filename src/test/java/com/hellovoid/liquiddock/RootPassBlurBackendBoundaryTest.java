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
    public void gpuOwnersShareOnlyStatelessGlPrimitives() throws Exception {
        Path main = Path.of("src/main/java/com/hellovoid/liquiddock");
        String[] failFastOwners = {
                "RootPassBlurBackend.java",
                "LauncherGlassSession.java",
                "SecurityCenterGlassSession.java",
                "ShortcutPopupGlassSession.java",
                "MiuiSearchboxGlassSession.java",
                "RecentsCapsuleGlassSession.java",
                "GboardFloatingGlassSession.java"
        };
        for (String owner : failFastOwners) {
            String source = Files.readString(main.resolve(owner));
            assertFalse(owner + " must not own compileShader", source.contains("int compileShader("));
            assertFalse(owner + " must not own createProgram", source.contains("int createProgram("));
            assertFalse(owner + " must not own requireUniform", source.contains("int requireUniform("));
            assertFalse(owner + " must not own bindQuad", source.contains("void bindQuad("));
            assertFalse(owner + " must not own unbindQuad", source.contains("void unbindQuad("));
            assertTrue(owner + " must call shared GL program primitives",
                    source.contains("GlProgramUtils."));
            assertTrue(owner + " must bind its own quad buffer through the shared helper",
                    source.contains("GlQuadBindings.bind(quadBuffer, "));
            assertFalse(owner + " must not share a mutable static quad buffer",
                    source.contains("static final FloatBuffer"));
        }

        String legacyAdapter = Files.readString(main.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue("soft-fail legacy adapter keeps its distinct program error contract",
                legacyAdapter.contains("private static int createProgram("));
        assertFalse(legacyAdapter.contains("int requireUniform("));
        assertFalse(legacyAdapter.contains("void bindQuad("));
        assertFalse(legacyAdapter.contains("void unbindQuad("));
        assertTrue(legacyAdapter.contains("GlProgramUtils.requireUniform("));
        assertTrue(legacyAdapter.contains("GlQuadBindings.bind(quadBuffer, "));
        assertFalse(legacyAdapter.contains("static final FloatBuffer"));

        String programUtils = Files.readString(main.resolve("GlProgramUtils.java"));
        String quadBindings = Files.readString(main.resolve("GlQuadBindings.java"));
        assertTrue(programUtils.contains("program link failed: "));
        assertTrue(programUtils.contains("shader compile failed: "));
        assertFalse(programUtils.contains("Surface"));
        assertFalse(programUtils.contains("EGL"));
        assertFalse(quadBindings.contains("static final FloatBuffer"));
        assertFalse(quadBindings.contains("Surface"));
        assertFalse(quadBindings.contains("EGL"));
    }

}
