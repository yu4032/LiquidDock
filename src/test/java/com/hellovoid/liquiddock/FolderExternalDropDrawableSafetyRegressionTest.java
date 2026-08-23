package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression for HyperOS large-folder drag enter requiring its concrete background Drawable type. */
public class FolderExternalDropDrawableSafetyRegressionTest {
    private static String folderHook() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java"));
    }

    @Test public void claimedFolderImageViewNeverReplacesVendorDrawableWithColorDrawable()
            throws Exception {
        String source = folderHook();
        int start = source.indexOf("private static void makeMaterialTransparent(View material)");
        int end = source.indexOf("private static void clearVendorBlur(View material)", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);

        assertTrue(method.contains("image.getImageAlpha()"));
        assertTrue(method.contains("image.setImageAlpha(0)"));
        assertFalse(method.contains("image.setImageDrawable(new ColorDrawable"));
    }

    @Test public void folderHookDoesNotGloballyRewriteVendorSetImageDrawableCalls()
            throws Exception {
        String source = folderHook();
        assertFalse(source.contains(
                "ImageView.class.getDeclaredMethod(\"setImageDrawable\", Drawable.class)"));
        assertFalse(source.contains("HookUtil.hook(setImageDrawable"));
    }

    @Test public void runtimeDisableRestoresImageAlphaWithoutRestoringStaleDrawable()
            throws Exception {
        String source = folderHook();
        assertTrue(source.contains("ORIGINAL_IMAGE_ALPHA"));
        int start = source.indexOf("private static void restoreMaterial(View material)");
        int end = source.indexOf("private static boolean isTransparentColorDrawable", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);

        assertTrue(method.contains("setImageAlpha"));
        assertFalse(method.contains("setImageDrawable(original)"));
    }

    @Test public void transparencyDoesNotUseViewAlphaBecauseGlassGeometryTracksIt()
            throws Exception {
        String source = folderHook();
        int start = source.indexOf("private static void makeMaterialTransparent(View material)");
        int end = source.indexOf("private static void clearVendorBlur(View material)", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);

        assertFalse(method.contains("material.setAlpha(0"));
        assertFalse(method.contains("image.setAlpha(0"));
    }
}
