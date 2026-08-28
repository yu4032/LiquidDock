package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for HyperOS 3 Weather MAML background ownership across widget sizes. */
public class LauncherMamlWeatherBackgroundContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void weatherMamlSuppressesOnlyItsCompleteSkyOwner() throws Exception {
        String suppressor = Files.readString(MAIN.resolve("LauncherMamlBackgroundSuppressor.java"));
        String vendor = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));

        assertTrue(suppressor.contains("b8006e83-c497-4642-9815-f674b82842b0"));
        assertTrue(suppressor.contains("\"skyColor\""));
        assertFalse(suppressor.contains("sky_color_ou1b4i"));
        assertFalse(suppressor.contains("sky_color_7x3ebn"));
        assertFalse(suppressor.contains("\"bg_old_ou1b4i\""));
        assertFalse(suppressor.contains("\"bg_ou1b4i\""));
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
    public void weatherMamlRecognizesOtherSizeProductsByExactBoundAppPackage() throws Exception {
        String suppressor = Files.readString(MAIN.resolve("LauncherMamlBackgroundSuppressor.java"));

        // Launcher 4.50 MaMlWidgetInfo persists appPackage through its intent and the inspected
        // Weather MAML description binds com.miui.weather2. This lets other size/product variants
        // share the same exact skyColor ownership rule without guessing their product IDs.
        assertTrue(suppressor.contains("com.miui.weather2"));
        assertTrue(suppressor.contains("appPackage"));
        assertTrue(suppressor.contains("isWeatherIdentity"));
        assertTrue(suppressor.contains("WEATHER_PRODUCT_ID.equals(productId)"));
        assertTrue(suppressor.contains("WEATHER_APP_PACKAGE.equals(appPackage)"));

        // Keep one-time size diagnostics so device logs prove which product/root each size uses.
        assertTrue(suppressor.contains("spanX"));
        assertTrue(suppressor.contains("spanY"));
        assertTrue(suppressor.contains("configSpanX"));
        assertTrue(suppressor.contains("configSpanY"));
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

    @Test
    public void missingWeatherTargetDumpsRealNamedElementRegistryOnceWithoutMutatingIt()
            throws Exception {
        String suppressor = Files.readString(MAIN.resolve("LauncherMamlBackgroundSuppressor.java"));

        assertTrue("Launcher 4.50 ScreenElementRoot registry should be read directly",
                suppressor.contains("mElements"));
        assertTrue(suppressor.contains("[MamlWidgetBgDump]"));
        assertTrue(suppressor.contains("dumpNamedElementsOnce"));
        assertTrue(suppressor.contains("WeakReference"));
        assertTrue(suppressor.contains("DUMPED_ROOTS"));
        assertTrue(suppressor.contains("targetFound=false"));

        int miss = suppressor.indexOf("targetFound=false");
        int dump = suppressor.indexOf("dumpNamedElementsOnce", miss);
        assertTrue("registry dump must happen only on the confirmed missing-target path",
                miss >= 0 && dump > miss);

        assertFalse(suppressor.contains("acceptVisitor("));
        assertFalse(suppressor.contains("HookUtil.invoke(root, \"removeElement\""));
        assertFalse(suppressor.contains("elements.remove("));
        assertFalse(suppressor.contains("elements.clear("));
    }
}
