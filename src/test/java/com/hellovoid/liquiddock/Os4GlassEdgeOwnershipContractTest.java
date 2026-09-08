package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * Static edge-ownership contract for the zero-copy Dock material. Android/vendor edge owners must
 * not remain visible around Prismal, otherwise transparent anti-aliased pixels can reveal a second
 * bright outline independently of the OS4 soft-edge setting.
 */
public class Os4GlassEdgeOwnershipContractTest {
    private static final Path GLASS_HOST =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");
    private static final Path MIUIX_GLASS_HOOK =
            Path.of("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java");

    @Test
    public void glassHostPhysicallyRejectsForegroundEdgeLayer() throws Exception {
        String source = Files.readString(GLASS_HOST);

        assertTrue(source.contains("public void setForeground(Drawable foreground)"));
        assertTrue(source.contains("super.setForeground(null);"));
        assertFalse(source.contains("public void onDrawForeground(Canvas canvas)"));
    }

    @Test
    public void zeroCopySuppressesBothSupportedVendorMaterialBodies() throws Exception {
        String source = Files.readString(MIUIX_GLASS_HOOK);

        assertTrue(source.contains(
                "return dockBg != null && isNativeVisualOwner(dockBg);"));
    }
}
