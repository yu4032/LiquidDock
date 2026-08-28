package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for declarative HyperOS MAML widget background ownership. */
public class LauncherMamlWeatherBackgroundContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path RULES = Path.of("src/main/resources/widget_background_rules.xml");

    @Test
    public void knownWeatherOwnershipLivesInXmlAndExecutorStaysGeneric() throws Exception {
        String rules = Files.readString(RULES);
        String executor = Files.readString(MAIN.resolve("LauncherMamlBackgroundRuleExecutor.java"));

        assertTrue(rules.contains("b8006e83-c497-4642-9815-f674b82842b0"));
        assertTrue(rules.contains("name=\"skyColor\""));
        assertTrue(rules.contains("name=\"background\""));
        assertTrue(executor.contains("findElement"));
        assertTrue(executor.contains("\"show\", false"));
        assertTrue(executor.contains("mShow"));
        assertTrue(executor.contains("release"));

        assertFalse(executor.contains("b8006e83-c497-4642-9815-f674b82842b0"));
        assertFalse(executor.contains("c989887f-fa0d-4963-8c57-896c03e37efc"));
        assertFalse(executor.contains("bc0f0cd2-43fd-4323-8061-55a8bc997e1f"));
        assertFalse(executor.contains("com.miui.weather2"));
        assertFalse(executor.contains("acceptVisitor"));
        assertFalse(executor.contains("setVisibility"));
        assertFalse(executor.contains("setColorFilter"));
    }

    @Test
    public void unknownWeatherProductUsesDiagnosticOnlyPackageRule() throws Exception {
        String rules = Files.readString(RULES);
        String executor = Files.readString(MAIN.resolve("LauncherMamlBackgroundRuleExecutor.java"));

        assertTrue(rules.contains("id=\"miui-weather-diagnostic\""));
        assertTrue(rules.contains("appPackage=\"com.miui.weather2\""));
        assertTrue(executor.contains("elementNames.isEmpty()"));
        assertTrue(executor.contains("diagnosticOnly=true"));
        assertTrue(executor.contains("dumpNamedElementsOnce"));
    }

    @Test
    public void mamlClaimsLoadedRootAtDeterministicSelfInitBoundary() throws Exception {
        String rootHook = Files.readString(MAIN.resolve("LauncherMamlRootLoadedHook.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String executor = Files.readString(MAIN.resolve("LauncherMamlBackgroundRuleExecutor.java"));

        assertTrue(rootHook.contains("com.miui.maml.component.MamlView"));
        assertTrue(rootHook.contains("\"initMamlview\""));
        assertTrue(rootHook.contains("com.miui.maml.ScreenElementRoot"));
        assertTrue(rootHook.contains("LauncherMamlBackgroundRuleExecutor.claimLoadedRoot"));
        assertTrue(module.contains("LauncherMamlRootLoadedHook.install(classLoader)"));
        assertTrue(executor.contains("claimLoadedRoot(View host, Object root)"));
        assertTrue(executor.contains("[MamlWidgetBg]"));
        assertTrue(executor.contains("targetFound=false"));
        assertTrue(executor.contains("suppressed="));

        int original = rootHook.indexOf("chain.proceed(args)");
        int claim = rootHook.indexOf("LauncherMamlBackgroundRuleExecutor.claimLoadedRoot");
        assertTrue(original >= 0 && claim > original);
    }

    @Test
    public void missingConfiguredTargetDumpsRealRegistryOnceWithoutMutatingIt()
            throws Exception {
        String executor = Files.readString(MAIN.resolve("LauncherMamlBackgroundRuleExecutor.java"));

        assertTrue("Launcher 4.50 ScreenElementRoot registry should be read directly",
                executor.contains("mElements"));
        assertTrue(executor.contains("[MamlWidgetBgDump]"));
        assertTrue(executor.contains("dumpNamedElementsOnce"));
        assertTrue(executor.contains("WeakReference"));
        assertTrue(executor.contains("DUMPED_ROOTS"));
        assertTrue(executor.contains("targetFound=false"));

        assertFalse(executor.contains("acceptVisitor("));
        assertFalse(executor.contains("removeElement"));
        assertFalse(executor.contains("elements.remove("));
        assertFalse(executor.contains("elements.clear("));
    }
}
