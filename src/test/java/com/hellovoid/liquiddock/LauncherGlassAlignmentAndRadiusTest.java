package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts screen-space wallpaper alignment and the shared folder-radius override. */
public class LauncherGlassAlignmentAndRadiusTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void screenSpaceMappingUsesStableRootScreenOrigin() {
        LauncherGlassScreenSpace.Bounds bounds = LauncherGlassScreenSpace.relativeToRoot(
                24, 48, 124, 148, 324, 348);
        assertEquals(100f, bounds.left, 0.001f);
        assertEquals(100f, bounds.top, 0.001f);
        assertEquals(300f, bounds.right, 0.001f);
        assertEquals(300f, bounds.bottom, 0.001f);
    }

    @Test
    public void folderRadiusZeroPreservesAutoAndPositiveValueOverridesInDp() {
        assertEquals(22f,
                LauncherGlassCornerRadiusPolicy.resolve(0f, 2f, 22f, 18f), 0.001f);
        assertEquals(18f,
                LauncherGlassCornerRadiusPolicy.resolve(0f, 2f, Float.NaN, 18f), 0.001f);
        assertEquals(48f,
                LauncherGlassCornerRadiusPolicy.resolve(24f, 2f, 22f, 18f), 0.001f);
    }

    @Test
    public void sinkWiresGlobalMaterialRectToRootLocationNotVisibleCrop() throws Exception {
        String sink = read("src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java");
        assertTrue(sink.contains("root.getLocationOnScreen(rootLocation)"));
        assertTrue(sink.contains("LauncherGlassScreenSpace.relativeToRoot("));
        assertFalse(sink.contains("root.getGlobalVisibleRect(rootRect)"));
    }

    @Test public void folderRadiusIsPersistedPerCanonicalFolderKind() throws Exception {
 String s=read("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"),r=read("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"),f=read("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java"); assertTrue(s.contains("SMALL_FOLDER_CORNER_RADIUS")&&s.contains("LARGE_FOLDER_CORNER_RADIUS")); assertTrue(r.contains("smallFolderStyle = new GlassComponentStyle")&&r.contains("largeFolderStyle = new GlassComponentStyle")); assertTrue(f.contains("smallFolder ? glassConfig.smallFolderStyle : glassConfig.largeFolderStyle"));
    }

    @Test public void composeAndLegacyGuiExposePerKindFolderStyleControls() throws Exception {
 String c=read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"),x=read("src/main/res/xml/preferences.xml"); assertTrue(c.contains("SMALL_FOLDER_SIZE_OFFSET")&&c.contains("SMALL_FOLDER_CORNER_RADIUS")&&c.contains("LARGE_FOLDER_SIZE_OFFSET")&&c.contains("LARGE_FOLDER_CORNER_RADIUS")); assertTrue(x.contains("liquid_small_folder_corner_radius")&&x.contains("liquid_large_folder_corner_radius"));
    }

}
