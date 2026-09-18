package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static architecture contract for the v2 primary-lockscreen glyph glass path. */
public class LockScreenClockGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path PRISMAL =
            Path.of("prismal/src/main/java/com/hellovoid/prismal");

    @Test public void scopeIsPrimaryLockscreenOnlyAndHardDisposedOnExit() throws Exception {
        String hook = Files.readString(MAIN.resolve("LockScreenClockGlassHook.java"));
        String scene = Files.readString(MAIN.resolve("SystemUiKeyguardGoneSource.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));

        assertTrue(hook.contains("displayType != 0 && displayType != 64"));
        assertTrue(hook.contains("isMinuteCompanion"));
        assertTrue(hook.contains("clock-group-expanded"));
        assertTrue(hook.contains("NotificationShadeWindowView"));
        assertTrue(hook.contains("SystemUiKeyguardGoneSource.isLockscreenScene()"));
        assertTrue(hook.contains("disposeNow(true, \"scene-left-lockscreen\")"));
        assertFalse(hook.contains("suspendForScene"));
        assertTrue(scene.contains("\"LOCKSCREEN\".equals(to)"));
        assertTrue(scene.contains("\"FINISHED\".equals(state)"));
        assertTrue(module.contains("LockScreenClockGlassHook.install"));
        assertTrue(module.contains("LockScreenClockPassBlurContinuousAuthority.install"));
    }

    @Test public void animationChangesTransformNotSdfContent() throws Exception {
        String source = Files.readString(MAIN.resolve("LockScreenClockGlyphMaskSource.java"));
        String session = Files.readString(MAIN.resolve("LockScreenClockGlassSession.java"));

        assertTrue(source.contains("Stable local-space SDF"));
        assertTrue(source.contains("contentSignature(glyph)"));
        assertTrue(source.contains("semanticLeaf"));
        assertTrue(source.contains("colon_view"));
        assertTrue(source.contains("lastContentSignatures"));
        assertTrue(source.contains("glyph.transformMatrixToGlobal"));
        assertTrue(source.contains("maskToRoot"));
        assertTrue(source.contains("toExactSignedDistanceBitmap"));
        assertTrue(source.contains("exactSquaredDistance"));
        assertFalse(source.contains("chamferDistance"));

        assertTrue(session.contains("glyph.contentSignature"));
        assertTrue(session.contains("state.signature != glyph.contentSignature"));
        assertTrue(session.contains("rootPxToMaskUv"));
        assertTrue(session.contains("drawMaskGlass"));
    }

    @Test public void prismalMaskUsesSmoothedSdfNormals() throws Exception {
        String renderer = Files.readString(PRISMAL.resolve("PrismalRenderer.java"));
        String shader = Files.readString(PRISMAL.resolve("PrismalMaskShapeShader.java"));

        assertTrue(renderer.contains("drawMaskGlass"));
        assertTrue(shader.contains("float dTL"));
        assertTrue(shader.contains("float dTR"));
        assertTrue(shader.contains("float dBL"));
        assertTrue(shader.contains("float dBR"));
        assertTrue(shader.contains("float gx"));
        assertTrue(shader.contains("float gy"));
        assertTrue(shader.contains("gradHSig"));
    }
}
