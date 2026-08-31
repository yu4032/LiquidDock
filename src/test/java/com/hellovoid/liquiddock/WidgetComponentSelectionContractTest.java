package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contract for runtime-discovered, user-selectable widget component suppression. */
public class WidgetComponentSelectionContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

    @Test public void discoveryPublishesCatalogToIndependentRemotePreferencesGroup() throws Exception {
        Path store = ROOT.resolve("WidgetComponentStore.java");
        assertTrue(Files.exists(store));
        String source = Files.readString(store);
        assertTrue(source.contains("DISCOVERY_GROUP = \"widget_components\""));
        assertTrue(source.contains("CATALOG_KEY = \"catalog\""));
        assertTrue(source.contains("Api101Bridge.remotePreferences(DISCOVERY_GROUP)"));
        assertTrue(source.contains("putStringSet(CATALOG_KEY"));

        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));
        assertTrue(discovery.contains("WidgetComponentStore.publishRemoteViews"));
        assertTrue(discovery.contains("WidgetComponentStore.publishMaml"));
    }

    @Test public void selectedRulesLiveInNormalConfigAndAreReadableAtRuntime() throws Exception {
        Path store = ROOT.resolve("WidgetComponentStore.java");
        assertTrue(Files.exists(store));
        String source = Files.readString(store);
        assertTrue(source.contains("SELECTION_KEY = \"widget_hidden_components\""));

        String reader = Files.readString(ROOT.resolve("ConfigReader.java"));
        assertTrue(reader.contains("Set<String> stringSet("));
        assertTrue(reader.contains("prefs.get(key)"));
    }

    @Test public void runtimeExecutorRestoresRemoteViewsAndMamlWithoutGone() throws Exception {
        Path executor = ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java");
        assertTrue(Files.exists(executor));
        String source = Files.readString(executor);
        assertTrue(source.contains("View.INVISIBLE"));
        assertTrue(source.contains("getVisibility()"));
        assertTrue(source.contains("setVisibility(claim.originalVisibility)"));
        assertTrue(source.contains("HookUtil.invoke(target, \"show\", false)"));
        assertTrue(source.contains("originalShow"));
        assertFalse(source.contains("View.GONE"));

        String controller = Files.readString(ROOT.resolve("LauncherWidgetBackgroundController.java"));
        assertTrue(controller.contains("LauncherWidgetComponentSelectionExecutor.claim(host)"));
        assertTrue(controller.contains("LauncherWidgetComponentSelectionExecutor.claimLoadedMamlRoot(host, root)"));
        assertTrue(controller.contains("LauncherWidgetComponentSelectionExecutor.release(host)"));
    }

    @Test public void mamlDiscoveryRunsForEveryLoadedRootNotOnlyDiagnostics() throws Exception {
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));
        assertTrue(discovery.contains("scanMaml(WidgetBackgroundIdentity identity, Object root)"));
        assertTrue(discovery.contains("readField(root, \"mElements\")"));

        String maml = Files.readString(ROOT.resolve("LauncherMamlBackgroundRuleExecutor.java"));
        assertTrue(maml.contains("LauncherWidgetComponentDiscovery.scanMaml(identity, root)"));
    }

    @Test public void composeExposesWidgetComponentPickerGroupedFromDiscoveryCatalog() throws Exception {
        String source = Files.readString(KOTLIN);
        assertTrue(source.contains("WidgetComponents(R.string.page_widget_components)"));
        assertTrue(source.contains("WidgetComponentsPage"));
        assertTrue(source.contains("LiquidDockApp.remotePreferences(WidgetComponentStore.DISCOVERY_GROUP)"));
        assertTrue(source.contains("WidgetComponentStore.CATALOG_KEY"));
        assertTrue(source.contains("WidgetComponentStore.SELECTION_KEY"));
        assertTrue(source.contains("显示全部内部元素"));
    }
}
