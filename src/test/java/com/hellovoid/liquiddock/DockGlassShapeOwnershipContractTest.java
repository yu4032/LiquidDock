package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * Static rendering-ownership contract for the Dock body.
 *
 * <p>The Workspace path already uses a transparent TextureView whose glass boundary is produced by
 * Prismal geometry. Dock must follow the same single-shape-owner rule: the Android host may keep an
 * outline for native shadow geometry, but it must not clip the TextureView a second time or render
 * the legacy foreground stroke over the Prismal edge. Native/non-glass Dock stroke support remains
 * available through DockStrokeRenderer's vendor hook.
 */
public class DockGlassShapeOwnershipContractTest {
    private static final Path DOCK_HOST =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");
    private static final Path PASS_BLUR_TEXTURE =
            Path.of("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");
    private static final Path DOCK_COMPOSITOR =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockGlassCompositor.java");
    private static final Path DOCK_STROKE_RENDERER =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java");

    @Test
    public void prismalOwnsDockBodyShapeWithoutAndroidCanvasClip() throws Exception {
        String host = Files.readString(DOCK_HOST);
        String texture = Files.readString(PASS_BLUR_TEXTURE);
        String compositor = Files.readString(DOCK_COMPOSITOR);

        assertTrue(texture.contains("PrismalGeometry prismalGeometry = createPrismalGeometry(mapping);"));
        assertTrue(compositor.contains("renderer.drawGlass(dockBody, params);"));
        assertFalse(host.contains("canvas.clipPath(clipPath);"));
        assertFalse(host.contains("dispatchDraw(Canvas canvas)"));
    }

    @Test
    public void zeroCopyGlassHostRejectsLegacyForegroundStroke() throws Exception {
        String host = Files.readString(DOCK_HOST);
        String strokeRenderer = Files.readString(DOCK_STROKE_RENDERER);

        assertTrue(host.contains("public void setForeground(Drawable foreground)"));
        assertTrue(host.contains("super.setForeground(null);"));
        assertFalse(host.contains("DockStrokeRenderer.updateRadius("));
        assertTrue(strokeRenderer.contains("static void installNativeHook("));
        assertTrue(strokeRenderer.contains("NATIVE_BACKGROUND_CLASS"));
    }
}
