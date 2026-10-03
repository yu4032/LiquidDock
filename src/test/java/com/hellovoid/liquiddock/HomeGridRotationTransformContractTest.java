package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static guardrails for the vendor LayoutTransformInfo[][] contract. */
public class HomeGridRotationTransformContractTest {
    @Test
    public void rotationNeverReplacesDenseTransformWrappersWithNullCells() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"));

        assertTrue(source.contains("HookUtil.setField(destination[x][y], \"mData\""));
        assertTrue(source.contains("if (destination[x][y] == null) return false"));
        assertFalse(source.contains("planned[x][y] = item.info"));
        assertFalse(source.contains("System.arraycopy(planned[x], 0, destination[x]"));
    }

    @Test
    public void asynchronousRefreshDoesNotCallVendorCoordinateRebuild() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridRotationRefreshHook.java"));

        assertFalse(source.contains("HookUtil.tryInvoke(page, \"calculateXsAndYs\")"));
        assertTrue(source.contains("HomeGridCellGeometryHook.topologyReady(page)"));
    }
}
