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
    private static final Path MANIFEST = Path.of("src/main/AndroidManifest.xml");

    @Test public void discoveryReportsUpstreamThroughExplicitAuthenticatedReceiver() throws Exception {
        Path store = ROOT.resolve("WidgetComponentStore.java");
        Path receiver = ROOT.resolve("WidgetDiscoveryReceiver.java");
        assertTrue(Files.exists(store));
        assertTrue(Files.exists(receiver));

        String storeSource = Files.readString(store);
        assertTrue(storeSource.contains("CATALOG_PREFS = \"widget_components\""));
        assertTrue(storeSource.contains("CATALOG_KEY = \"catalog\""));
        assertTrue(storeSource.contains("DISCOVERY_TOKEN_KEY = \"widget_discovery_token\""));
        assertTrue(storeSource.contains("context.sendBroadcast(intent)"));
        assertTrue(storeSource.contains("intent.setComponent(new ComponentName(MODULE_PACKAGE, RECEIVER_CLASS))"));

        String receiverSource = Files.readString(receiver);
        assertTrue(receiverSource.contains("WidgetComponentStore.EXTRA_DESCRIPTOR"));
        assertTrue(receiverSource.contains("WidgetComponentStore.EXTRA_TOKEN"));
        assertTrue(receiverSource.contains("MessageDigest.isEqual"));
        assertTrue(receiverSource.contains("getSharedPreferences(WidgetComponentStore.CATALOG_PREFS"));
        assertTrue(receiverSource.contains("putStringSet(WidgetComponentStore.CATALOG_KEY"));

        String manifest = Files.readString(MANIFEST);
        assertTrue(manifest.contains("android:name=\".WidgetDiscoveryReceiver\""));
        assertTrue(manifest.contains("android:exported=\"true\""));

        String app = Files.readString(ROOT.resolve("LiquidDockApp.java"));
        assertTrue(app.contains("WidgetComponentStore.DISCOVERY_TOKEN_KEY"));
        assertTrue(app.contains("UUID.randomUUID().toString()"));

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
        assertTrue(discovery.contains("scanMaml(View host, WidgetBackgroundIdentity identity, Object root)"));
        assertTrue(discovery.contains("readField(root, \"mElements\")"));

        String maml = Files.readString(ROOT.resolve("LauncherMamlBackgroundRuleExecutor.java"));
        assertTrue(maml.contains("LauncherWidgetComponentDiscovery.scanMaml(host, identity, root)"));
    }

    @Test public void composeExposesWidgetComponentPickerGroupedFromLocalDiscoveryCatalog() throws Exception {
        String source = Files.readString(KOTLIN);
        assertTrue(source.contains("WidgetComponents(R.string.page_widget_components)"));
        assertTrue(source.contains("WidgetComponentsPage"));
        assertTrue(source.contains("getSharedPreferences(WidgetComponentStore.CATALOG_PREFS, Context.MODE_PRIVATE)"));
        assertTrue(source.contains("WidgetComponentStore.CATALOG_KEY"));
        assertTrue(source.contains("WidgetComponentStore.SELECTION_KEY"));
        assertTrue(source.contains("显示全部内部元素"));
    }
}
