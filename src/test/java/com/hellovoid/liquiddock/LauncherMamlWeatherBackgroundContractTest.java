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

        assertFalse(suppressor.contains("acceptVisitor"));
        assertFalse(suppressor.contains("setVisibility"));
        assertFalse(suppressor.contains("setColorFilter"));
    }

    @Test
    public void weatherMamlClaimsLoadedRootAtDeterministicSelfInitBoundary() throws Exception {
        String rootHook = Files.readString(MAIN.resolve("LauncherMamlRootLoadedHook.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String suppressor = Files.readString(MAIN.resolve("LauncherMamlBackgroundSuppressor.java"));

        assertTrue(rootHook.contains("com.miui.maml.component.MamlView"));
        assertTrue(rootHook.contains("\"initMamlview\""));
        assertTrue(rootHook.contains("com.miui.maml.ScreenElementRoot"));
        assertTrue(rootHook.contains("LauncherMamlBackgroundSuppressor.claimLoadedRoot"));
        assertTrue(module.contains("LauncherMamlRootLoadedHook.install(classLoader)"));
        assertTrue(suppressor.contains("claimLoadedRoot(View host, Object root)"));
        assertTrue(suppressor.contains("[MamlWidgetBg]"));
        assertTrue(suppressor.contains("targetFound="));
        assertTrue(suppressor.contains("suppressed="));

        int original = rootHook.indexOf("chain.proceed(args)");
        int claim = rootHook.indexOf("LauncherMamlBackgroundSuppressor.claimLoadedRoot");
        assertTrue(original >= 0 && claim > original);
    }
}
