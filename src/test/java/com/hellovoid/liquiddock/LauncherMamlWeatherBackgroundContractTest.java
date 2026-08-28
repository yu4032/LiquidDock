package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for the bundled HyperOS 3 "Today's weather" MAML background owner. */
public class LauncherMamlWeatherBackgroundContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void weatherMamlSuppressesOnlyItsSkyColorOwner() throws Exception {
        String suppressor = Files.readString(MAIN.resolve("LauncherMamlBackgroundSuppressor.java"));
        String vendor = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));

        assertTrue(suppressor.contains("b8006e83-c497-4642-9815-f674b82842b0"));
        assertTrue(suppressor.contains("sky_color_7x3ebn"));
        assertTrue(suppressor.contains("findElement"));
        assertTrue(suppressor.contains("show"));
        assertTrue(suppressor.contains("false"));
        assertTrue(suppressor.contains("mShow"));
        assertTrue(suppressor.contains("release"));
        assertTrue(vendor.contains("LauncherMamlBackgroundSuppressor.claim(host)"));
        assertTrue(vendor.contains("LauncherMamlBackgroundSuppressor.release(host)"));

        // Background ownership must stay element-specific. Do not walk the whole MAML tree,
        // hide the host, or color-filter provider content as part of background suppression.
        assertFalse(suppressor.contains("acceptVisitor"));
        assertFalse(suppressor.contains("setVisibility"));
        assertFalse(suppressor.contains("setColorFilter"));
    }

    @Test
    public void weatherMamlClaimsLoadedRootAtDeterministicSelfInitBoundary() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        String suppressor = Files.readString(MAIN.resolve("LauncherMamlBackgroundSuppressor.java"));

        assertTrue(hook.contains("com.miui.maml.component.MamlView"));
        assertTrue(hook.contains("\"initMamlview\""));
        assertTrue(hook.contains("com.miui.maml.ScreenElementRoot"));
        assertTrue(hook.contains("LauncherMamlBackgroundSuppressor.claimLoadedRoot"));
        assertTrue(suppressor.contains("claimLoadedRoot(View host, Object root)"));
        assertTrue(suppressor.contains("[MamlWidgetBg]"));
        assertTrue(suppressor.contains("targetFound="));
        assertTrue(suppressor.contains("suppressed="));
    }
}
