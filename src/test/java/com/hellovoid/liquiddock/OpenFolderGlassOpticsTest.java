package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalParams;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenFolderGlassOpticsTest {
    @Test public void folderOnlyOverridePreservesAllUnrelatedOptics() {
        PrismalParams base = PrismalParams.builder().build();
        PrismalParams result = OpenFolderGlassOptics.apply(base, 12, 170, 20, 80, 2f);
        assertEquals(12f, result.blurRadiusPx, 0.0001f);
        assertEquals(1.70f, result.ior, 0.0001f);
        assertEquals(40f, result.glassThicknessPx, 0.0001f);
        assertEquals(80f / 255f, result.tintA, 0.0001f);
        assertEquals(base.causticIntensity, result.causticIntensity, 0.0001f);
        assertEquals(base.blurRadiusPx, PrismalParams.builder(base).build().blurRadiusPx, 0.0001f);
    }
    @Test public void closingUsesWorkspaceBlurAndRetainsFolderLensSettings() {
        PrismalParams base = PrismalParams.builder().build();
        PrismalParams folder = OpenFolderGlassOptics.apply(base, 45, 180, 26, 125, 2f);
        PrismalParams closing = OpenFolderGlassOptics.restoreWorkspaceBlur(folder, 5.5f);
        assertEquals(5.5f, closing.blurRadiusPx, .0001f);
        assertEquals(1.8f, closing.ior, .0001f);
        assertEquals(52f, closing.glassThicknessPx, .0001f);
        assertEquals(125f / 255f, closing.tintA, .0001f);
        assertEquals("Immutable original folder material", 45f, folder.blurRadiusPx, .0001f);
        assertSame("Invalid Workspace settings must not overwrite the folder",
                folder, OpenFolderGlassOptics.restoreWorkspaceBlur(folder, Float.NaN));
    }
    @Test public void folderOpticsClampToSchemaLimits() {
        PrismalParams params = OpenFolderGlassOptics.apply(PrismalParams.builder().build(),
                -500, 9999, -4, 999, 1f);
        assertEquals(0f, params.blurRadiusPx, 0f);
        assertEquals(2f, params.ior, 0f);
        assertEquals(1f, params.glassThicknessPx, 0f);
        assertEquals(160f / 255f, params.tintA, 0.0001f);
    }
}
