package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Dock icons share Launcher component toggles while the Dock body keeps its own material. */
public class DockIconHighlightProfileContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void dockIconConfigCarriesLauncherHighlightProfileIntoPerItemDraw() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));

        assertTrue(view.contains("dockCompositor.setIconStyle(\n"
                + "                glassConfig.iconStyle, glassConfig.launcherHighlightProfile)"));
        assertTrue(compositor.contains("params, iconHighlightProfile, item.opacity"));
    }
}
