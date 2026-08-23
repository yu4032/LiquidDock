package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Runtime contracts learned from device validation of the shared Workspace scene layer. */
public class LauncherGlassSceneRuntimeContractTest {
    @Test public void hiddenWorkspaceSceneKeepsTextureViewSurfaceAlive() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java"));

        assertTrue(source.contains("setVisibility(View.VISIBLE)"));
        assertTrue(source.contains("setAlpha(0f)"));
        assertTrue(source.contains("setAlpha(visible ? 1f : 0f)"));
        assertFalse(source.contains("visible ? View.VISIBLE : View.INVISIBLE"));
    }
}
