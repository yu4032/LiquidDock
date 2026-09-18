package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Device-targeted contracts for HyperOS Launcher 4.50 icon sizing and Dock icon ownership. */
public class Launcher450IconSizeContractTest {
    @Test
    public void policyClampsToSupportedPercentRange() throws Exception {
        Class<?> policy;
        try {
            policy = Class.forName("com.hellovoid.liquiddock.Launcher450IconSizePolicy");
        } catch (ClassNotFoundException missing) {
            fail("Launcher450IconSizePolicy must exist");
            return;
        }
        Method scale = policy.getDeclaredMethod("scale", boolean.class, int.class);
        scale.setAccessible(true);
        assertEquals(1.0f, (Float) scale.invoke(null, false, 120), 0.0001f);
        assertEquals(0.8f, (Float) scale.invoke(null, true, 60), 0.0001f);
        assertEquals(1.0f, (Float) scale.invoke(null, true, 100), 0.0001f);
        assertEquals(1.2f, (Float) scale.invoke(null, true, 150), 0.0001f);
    }

    @Test
    public void runtimeUsesLauncher450MeasureAuthoritiesOnly() throws Exception {
        Path hookPath = Path.of("src/main/java/com/hellovoid/liquiddock/Launcher450IconSizeHook.java");
        assertTrue("Launcher 4.50 hook must exist", Files.exists(hookPath));
        String source = Files.readString(hookPath);

        assertTrue(source.contains("com.miui.home.launcher.ShortcutIcon"));
        assertTrue(source.contains("com.miui.home.launcher.folder.FolderIcon1x1"));
        assertTrue(source.contains("com.miui.home.launcher.grid.GridConfig"));
        assertTrue(source.contains("onMeasure"));
        assertTrue(source.contains("getIconSize"));
        assertTrue(source.contains("ThreadLocal"));
        assertTrue(source.contains("finally"));

        assertFalse("4.50 implementation must not use the failed drawable-bind experiment",
                source.contains("setIconImageView"));
        assertFalse("4.50 implementation must not visually scale Views",
                source.contains("setScaleX") || source.contains("setScaleY"));
        assertFalse("4.50 implementation must not hook OS4/native Flutter paths",
                source.contains("libapp_launcher.so") || source.contains("Flutter"));
    }

    @Test
    public void dockIconScalingPreservesVendorSlotWidth() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Launcher450IconSizeHook.java"));

        assertTrue("Dock icon pixels still scale through the scoped getIconSize authority",
                source.contains("getIconSize"));
        assertFalse("Dock slot width must remain vendor-owned so scaled icons stay centered",
                source.contains("\"getDockIconWidth\""));
        assertFalse("Do not reconstruct GridConfig private slot geometry in LiquidDock",
                source.contains("dockBarHeight") || source.contains("getIntField(grid, \"iconSize\")"));
    }

    @Test
    public void openFolderShortcutIconsJoinTheSameMeasureTransaction() throws Exception {
        Path hookPath = Path.of("src/main/java/com/hellovoid/liquiddock/Launcher450IconSizeHook.java");
        assertTrue("Launcher 4.50 hook must exist", Files.exists(hookPath));
        String source = Files.readString(hookPath);

        assertTrue("4.50 Folder inner icons are ShortcutIcon children of FolderGridView",
                source.contains("com.miui.home.launcher.FolderGridView"));
        assertTrue("folder inner ShortcutIcon must have an explicit measure domain",
                source.contains("MeasureDomain.FOLDER_CONTENT"));
    }

    @Test
    public void workstationAppPageShortcutIconsJoinTheSameMeasureTransaction() throws Exception {
        Path hookPath = Path.of("src/main/java/com/hellovoid/liquiddock/Launcher450IconSizeHook.java");
        assertTrue("Launcher 4.50 hook must exist", Files.exists(hookPath));
        String source = Files.readString(hookPath);

        assertTrue("4.50 workstation app page is the laptop launchpad AllAppsWorkspace",
                source.contains("com.miui.home.launcher.laptop.launchpad.AllAppsWorkspace"));
        assertTrue("workstation app-page ShortcutIcon must have an explicit measure domain",
                source.contains("MeasureDomain.WORKSTATION_APPS"));
        assertFalse("ordinary drawer/search All Apps must remain vendor-sized",
                source.contains("com.miui.home.launcher.allapps.AllAppsContainerView"));
    }

    @Test
    public void functionalDockGlassUsesVendorAdapterViewTypesNotLabelsOrPositions() throws Exception {
        Path registryPath = Path.of(
                "src/main/java/com/hellovoid/liquiddock/Launcher450DockFunctionalIconRegistry.java");
        assertTrue("Launcher 4.50 functional Dock registry must exist", Files.exists(registryPath));
        String registry = Files.readString(registryPath);

        assertTrue(registry.contains("com.miui.home.launcher.hotseats.HotSeatsListContentAdapter"));
        assertTrue(registry.contains("onBindViewHolder"));
        assertTrue("adapter viewType classification must use the centralized 4.50 policy",
                registry.contains("Launcher450DockFunctionalIconPolicy.isFunctionalViewType"));
        assertFalse("functional classification must not depend on localized descriptions",
                registry.contains("getContentDescription"));
    }

    @Test
    public void functionalOnlyModeGatesStaticDockGlassWithoutEnablingAllIcons() throws Exception {
        String staticHook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java"));
        String state = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java"));
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String config = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String registry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockGlassItemRegistry.java"));
        String compositor = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockGlassCompositor.java"));
        String ui = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(schema.contains("FUNCTIONAL_DOCK_ICON_GLASS"));
        assertTrue(schema.contains("liquid_functional_dock_icon_glass"));
        assertTrue(state.contains("isFunctionalDockIconEnabled"));
        assertTrue(state.contains("isAnyIconEnabled"));
        assertTrue(staticHook.contains("Launcher450DockFunctionalIconRegistry.isFunctional"));
        assertTrue(staticHook.contains("isAnyIconEnabled"));
        assertTrue("Dock registry must remain live when functional-only mode is the sole icon mode",
                registry.contains("GlassRuntimeState.isAnyIconEnabled()"));
        assertTrue("Dock compositor must accept the union-enabled icon style",
                config.contains("resolvedIconEnabled || functionalDockIconEnabled"));
        assertTrue("Dock compositor must not independently require the broad icon switch",
                !compositor.contains("GlassRuntimeState.isIconEnabled()"));
        assertTrue(ui.contains("仅 Dock 功能图标玻璃"));
        assertTrue(ui.contains("搜索、小爱"));
    }
}
