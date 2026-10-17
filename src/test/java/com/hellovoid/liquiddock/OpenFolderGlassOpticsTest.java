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
    @Test public void folderOpticsBecomeWorkspaceOpticsAtNativeCloseEndpoint() {
        PrismalParams workspace = PrismalParams.builder().build();
        PrismalParams folder = OpenFolderGlassOptics.apply(workspace, 48, 180, 34, 150, 2f);
        PrismalParams[] steps = OpenFolderGlassOptics.closeToWorkspace(folder, workspace);
        assertSame(folder, steps[0]);
        assertSame(workspace, steps[64]);
        assertEquals(1.8f, steps[0].ior, .0001f);
        assertEquals(workspace.ior, steps[64].ior, 0f);
        assertEquals((folder.tintA + workspace.tintA) / 2f, steps[32].tintA, .0001f);
        assertEquals((folder.glassThicknessPx + workspace.glassThicknessPx) / 2f,
                steps[32].glassThicknessPx, .0001f);
        assertEquals(workspace.causticIntensity, steps[64].causticIntensity, 0f);
        assertEquals(workspace.blurRadiusPx, steps[64].blurRadiusPx, 0f);
    }

    @Test public void folderMaterialMustRemainStableAcrossNativeClose() {
        PrismalParams base = PrismalParams.builder().build();
        PrismalParams folder = OpenFolderGlassOptics.apply(base, 45, 180, 26, 125, 2f);
        assertEquals(45f, folder.blurRadiusPx, .0001f);
        assertEquals(1.8f, folder.ior, .0001f);
        assertEquals(52f, folder.glassThicknessPx, .0001f);
        assertEquals(125f / 255f, folder.tintA, .0001f);
        // Closing no longer runs a second gaussian pass at another radius.
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
