package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Auto icon shape must use Drawable/Outline data and stay GPU/readback safe. */
public class LauncherGlassIconShapeResolverContractTest {
    @Test public void resolverUsesFrameworkShapeMetadataWithoutPixelReadback() throws Exception {
        Path path = Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassIconShapeResolver.java");
        assertTrue("missing icon shape resolver", Files.exists(path));
        String source = Files.readString(path);
        assertTrue(source.contains("AdaptiveIconDrawable"));
        assertTrue(source.contains("Outline"));
        assertTrue(source.contains("getOutline"));
        assertFalse(source.contains("Bitmap"));
        assertFalse(source.contains("PixelCopy"));
        assertFalse(source.contains("glReadPixels"));
    }
}
