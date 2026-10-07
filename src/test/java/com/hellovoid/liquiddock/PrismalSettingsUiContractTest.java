package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class PrismalSettingsUiContractTest {
    private static final Path UI =
            Path.of("src/main/kotlin/com/hellovoid/liquiddock/MiuixSettingsUi.kt");

    @Test
    public void refractingTopBarUsesPrismalSupportedShape() throws Exception {
        String source = Files.readString(UI);

        assertTrue(source.contains("PrismalGlassSurface("));
        assertTrue(source.contains("shape = { PrismalRoundedRectangle(0.dp) }"));
        assertFalse(source.contains("shape = { RectangleShape }"));
    }
}
