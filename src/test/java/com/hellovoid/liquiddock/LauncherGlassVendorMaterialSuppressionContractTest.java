package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static HyperOS 4.50 material targets and destructive-API bans. Ownership is typed state-tested. */
public class LauncherGlassVendorMaterialSuppressionContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void standardWidgetTargetsOnlyTaggedDirectRemoteViewsRoot() throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));

        assertTrue(helper.contains("android.R.id.widget_frame"));
        assertTrue(helper.contains("resolveRemoteViewsContent"));
        assertTrue(helper.contains("child.getTag(android.R.id.widget_frame)"));
        assertFalse(helper.contains("findViewById(android.R.id.widget_frame)"));
        assertFalse(helper.contains("setAlpha(0"));
        assertFalse(helper.contains("removeAllViews"));
        assertFalse(helper.contains("setVisibility(View.INVISIBLE"));
        assertFalse(helper.contains("setVisibility(View.GONE"));
    }

    @Test public void mamlUsesLaunchersBackgroundBlurVariableBoundary() throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));

        assertTrue(helper.contains("putVariableNumber"));
        assertTrue(helper.contains("enable_background_blur"));
        assertFalse(helper.contains("LauncherMamlBackgroundRuleExecutor"));
        assertFalse(helper.contains("WidgetBackgroundRule"));
        assertFalse(helper.contains("findElement"));
    }

    @Test public void folderHookTargetsConcreteLauncher450VariantsWithoutDrawableReplacement()
            throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(folder.contains("com.miui.home.launcher.folder.FolderIcon1x1"));
        assertTrue(folder.contains("com.miui.home.launcher.folder.FolderIcon2x2"));
        assertTrue(folder.contains("setIconImageView"));
        assertFalse(folder.contains(
                "ImageView.class.getDeclaredMethod(\"setImageDrawable\", Drawable.class)"));
        assertFalse(folder.contains("mPreviewContainer.setAlpha(0"));
        assertFalse(folder.contains("mPreviewContainer.setVisibility"));
    }

    @Test public void largeFolderDrawableUsesConcretePaintApiBecauseSetAlphaIsVendorNoOp()
            throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(folder.contains("FolderIcon4x4NormalBackgroundDrawable"));
        assertTrue(folder.contains("FolderIcon4x4DefaultBackgroundDrawable"));
        assertTrue(folder.contains("HookUtil.tryInvoke(drawable, \"getPaint\")"));
        assertTrue(folder.contains("paintResult.succeeded()"));
    }
}
