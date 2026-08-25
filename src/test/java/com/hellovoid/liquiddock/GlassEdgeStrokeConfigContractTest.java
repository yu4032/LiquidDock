package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class GlassEdgeStrokeConfigContractTest {
    @Test
    public void glassStrokeHasIndependentDefaultsAndChildPage() throws Exception {
        assertEquals("glass_stroke_enabled", ConfigSchema.GlassStroke.ENABLED.name());
        assertEquals(Boolean.TRUE, ConfigSchema.GlassStroke.ENABLED.uiDefault());
        assertEquals(Boolean.TRUE, ConfigSchema.GlassStroke.FILL_DIFF.uiDefault());
        assertEquals(Integer.valueOf(1), ConfigSchema.GlassStroke.FILL_DIFF_WIDTH.uiDefault());
        assertEquals(Integer.valueOf(1), ConfigSchema.GlassStroke.STANDARD_WIDTH.uiDefault());
        assertEquals(Integer.valueOf(64), ConfigSchema.GlassStroke.ALPHA.uiDefault());

        String ui = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        assertTrue(ui.contains("GlassStroke(R.string.page_glass_stroke)"));
        assertTrue(ui.contains("Page.GlassStroke -> Page.Stroke"));
        assertTrue(ui.contains("GlassStrokePage("));
        assertTrue(ui.contains("ConfigSchema.GlassStroke.ENABLED"));
    }
}
