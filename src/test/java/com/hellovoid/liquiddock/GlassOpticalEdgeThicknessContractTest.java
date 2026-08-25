package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class GlassOpticalEdgeThicknessContractTest {
    @Test
    public void guiControlsSharedOpticalEdgeThickness() throws Exception {
        assertEquals(Integer.valueOf(100), ConfigSchema.Glass.HIGHLIGHT_WIDTH.uiDefault());
        assertEquals(Integer.valueOf(50), ConfigSchema.Glass.HIGHLIGHT_WIDTH.minInt());
        assertEquals(Integer.valueOf(300), ConfigSchema.Glass.HIGHLIGHT_WIDTH.maxInt());

        String ui = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        assertTrue(ui.contains(
                "IntSpec(ConfigSchema.Glass.HIGHLIGHT_WIDTH, \"玻璃边缘厚度\", \"%\")"));

        String renderer = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        String shader = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307PrismalShader.java"));
        assertTrue(renderer.contains("PrismalOpticalEdgeShader.apply("));
        assertTrue(shader.contains("PrismalOpticalEdgeShader.apply("));
    }
}
