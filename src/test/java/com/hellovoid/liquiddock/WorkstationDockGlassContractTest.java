package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Workstation Dock keeps the shared zero-copy body and icon compositor alive and visible. */
public class WorkstationDockGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void workstationNeverHidesTheViewThatOwnsPrismalOutput() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertFalse(main.contains("dockBg.setAlpha(0f)"));
        assertTrue(main.contains("dockBg.setAlpha(1f)"));
    }

    @Test
    public void workstationColdStartUsesNormalGlassBindingLifecycle() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertFalse(pipeline.contains(
                "Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n"
                        + "                        if (MainHook.isWorkstationMode()) return result;\n"
                        + "                        try {"));
        assertFalse(pipeline.contains(
                "Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n"
                        + "                if (MainHook.isWorkstationMode()) return result;\n"
                        + "                Object hotSeats"));
    }

    @Test
    public void workstationBindCannotInheritNormalDockSnapshotPause() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue(pipeline.replaceAll("\\s+", " ").contains(
                "MainHook.isWorkstationMode() || !vendorStaticSnapshotMode"));
    }
}
