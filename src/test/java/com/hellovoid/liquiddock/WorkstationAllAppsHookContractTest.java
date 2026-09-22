package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Wiring regression for the laptop All Apps CellLayout. */
public class WorkstationAllAppsHookContractTest {
    private static String source() throws IOException {
        Path path = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/HomeGridCellGeometryHook.java");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String policy() throws IOException {
        Path path = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/HomeGridCellGeometryPolicy.java");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    public void allAppsIdentityReadsCellLayoutGridTypeInsteadOfGridConfig() throws IOException {
        String source = source();
        assertTrue("All Apps identity must read CellLayout.mGridType",
                source.contains("HookUtil.getField(cellLayout, \"mGridType\")"));
        assertFalse("GridConfig does not own the All Apps grid type",
                source.contains("HookUtil.getField(config, \"mGridType\")"));
        assertFalse("GridConfig has no getGridType() contract in this Launcher",
                source.contains("HookUtil.tryInvoke(config, \"getGridType\")"));
        assertTrue("CellLayout getGridType is an optional vendor probe",
                source.contains("HookUtil.InvocationResult<Object> gridTypeResult = HookUtil.tryInvoke(cellLayout, \"getGridType\")")
                        && source.contains("gridTypeResult.succeeded()"));
    }

    @Test
    public void allAppsIdentityIsNotSuppressedByWorkstationStateTiming() throws IOException {
        String source = source();
        assertTrue("All Apps page identity must be evaluated independently",
                source.contains("boolean workstationAllApps = isLaptopAllApps(cellLayout);"));
        assertFalse("All Apps must not require the global workstation flag first",
                source.contains("workstation && isLaptopAllApps(cellLayout)"));
    }

    @Test
    public void normalWorkspaceOrientationGuardDoesNotDiscardAllAppsLayout() throws IOException {
        String source = source();
        assertTrue("All Apps must bypass the normal Workspace orientation-bounds guard",
                source.contains("if (!workstationAllApps && !sizeMatchesOrientation(layout, width, height)) return;"));
    }

    @Test
    public void verticalSpacingControlsBothOuterEdgesInsteadOfOnlyTheTopOrigin() throws IOException {
        String source = source();
        String policy = policy();
        assertTrue("All Apps must derive an inner height from the two absolute edge spacings",
                policy.contains("int allAppsInnerHeight = Math.max(in.countY, in.height - top - bottom);"));
        assertTrue("All Apps must redistribute the remaining vertical span into row gaps",
                policy.contains("if (in.workstationAllApps && in.countY > 1)"));
        assertTrue("the final policy height gap must be written to CellLayout",
                source.contains("HookUtil.setIntField(cellLayout, \"mHeightGap\", geometry.heightGap);"));
    }
}
