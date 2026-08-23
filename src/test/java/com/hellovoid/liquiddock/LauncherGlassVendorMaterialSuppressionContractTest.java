package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Prevents MIUI widget/1x1-folder vendor material from being left over above shared glass. */
public class LauncherGlassVendorMaterialSuppressionContractTest {
    @Test
    public void widgetSuppressionClearsOnlyVendorBlurAndKeepsWidgetContents() throws Exception {
        Path helperPath = Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassVendorMaterialSuppressor.java");
        assertTrue("missing vendor-material suppressor", Files.exists(helperPath));
        String helper = Files.readString(helperPath);
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java"));

        assertTrue(helper.contains("MiBlurBridge.clearContentBlur"));
        assertTrue(helper.contains("setMaMlBlurIfSupported"));
        assertTrue(helper.contains("setViewBlur"));
        assertTrue(helper.contains("setBlurIfNeed"));
        assertTrue(hook.contains("LauncherGlassVendorMaterialSuppressor.claimWidget"));

        assertFalse(helper.contains("setAlpha(0"));
        assertFalse(helper.contains("removeAllViews"));
        assertFalse(helper.contains("setVisibility(View.INVISIBLE"));
        assertFalse(helper.contains("setVisibility(View.GONE"));
    }

    @Test
    public void smallFolderClaimsDedicatedCoverWithoutHidingPreviewContainer() throws Exception {
        String folder = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java"));

        assertTrue(folder.contains("FolderIcon1x1"));
        assertTrue(folder.contains("mIconImageView"));
        assertTrue(folder.contains("mImageView"));
        assertTrue(folder.contains("resolveFolderMaterial"));
        assertTrue(folder.contains("observeFolderVariantConstructors"));
        // The 1x1 preview container owns the 4x4 miniature icons and must stay visible.
        assertFalse(folder.contains("mPreviewContainer.setAlpha(0"));
        assertFalse(folder.contains("mPreviewContainer.setVisibility"));
    }
}
