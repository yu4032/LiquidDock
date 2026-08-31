package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contract for runtime-discovered, user-selectable widget component suppression. */
public class WidgetComponentSelectionContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path PICKER = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentsPage.kt");
    private static final Path DETAIL = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentDetailActivity.kt");
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
        assertTrue(receiverSource.contains("getSharedPreferences("));
        assertTrue(receiverSource.contains("WidgetComponentStore.CATALOG_PREFS"));
        assertTrue(receiverSource.contains("putStringSet(WidgetComponentStore.CATALOG_KEY"));

        String manifest = Files.readString(MANIFEST);
        assertTrue(manifest.contains("android:name=\".WidgetDiscoveryReceiver\""));
        assertTrue(manifest.contains("android:exported=\"true\""));
    }

    @Test public void discoveryOnlyRunsForExplicitOneShotRequest() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        assertTrue(store.contains("DISCOVERY_REQUEST_KEY = \"widget_discovery_request\""));
        assertTrue(store.contains("discoveryRequested()"));
        assertTrue(store.contains("acknowledgeDiscoveryRequest"));

        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));
        assertTrue(discovery.contains("if (!WidgetComponentStore.discoveryRequested()) return;"));
        assertTrue(discovery.contains("WidgetComponentStore.acknowledgeDiscoveryRequest"));

        String receiver = Files.readString(ROOT.resolve("WidgetDiscoveryReceiver.java"));
        assertTrue(receiver.contains("WidgetComponentStore.EXTRA_REQUEST_ACK"));
        assertTrue(receiver.contains("remove(WidgetComponentStore.DISCOVERY_REQUEST_KEY)"));

        String picker = Files.readString(PICKER);
        assertTrue(picker.contains("UUID.randomUUID().toString()"));
        assertTrue(picker.contains("WidgetComponentStore.DISCOVERY_REQUEST_KEY"));
        assertTrue(picker.contains("LiquidDockApp.syncToRemote(prefs)"));
        assertTrue(picker.contains("载入当前小组件"));
        assertFalse(picker.contains("重新扫描桌面"));
    }

    @Test public void discoveryBatchesDescriptorsInsteadOfBroadcastingEveryNode() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));
        String receiver = Files.readString(ROOT.resolve("WidgetDiscoveryReceiver.java"));

        assertTrue(store.contains("EXTRA_DESCRIPTORS = \"descriptors\""));
        assertTrue(store.contains("BATCH_MAX_ITEMS"));
        assertTrue(store.contains("publishBatch("));
        assertTrue(store.contains("putStringArrayListExtra(EXTRA_DESCRIPTORS"));

        assertTrue(discovery.contains("ArrayList<WidgetComponentStore.Descriptor>"));
        assertTrue(discovery.contains("WidgetComponentStore.publishBatch"));
        assertFalse(discovery.contains("WidgetComponentStore.publishRemoteViews("));
        int remotePublish = discovery.indexOf("WidgetComponentStore.publishBatch");
        int remoteAck = discovery.indexOf("WidgetComponentStore.acknowledgeDiscoveryRequest", remotePublish);
        assertTrue(remotePublish >= 0 && remoteAck > remotePublish);

        assertTrue(receiver.contains("getStringArrayListExtra(WidgetComponentStore.EXTRA_DESCRIPTORS)"));
        assertTrue(receiver.contains("for (String encoded : batch)"));
    }

    @Test public void remoteDiscoveryPublishesPropertyActionsWithExactHierarchyPath() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));

        assertTrue(store.contains("REMOTE_V2 = \"R2\""));
        assertTrue(store.contains("ACTION_CLEAR_BACKGROUND = \"background\""));
        assertTrue(store.contains("ACTION_CLEAR_IMAGE = \"image\""));
        assertTrue(store.contains("ACTION_HIDE_VIEW = \"hide\""));
        assertTrue(store.contains("hierarchyPath"));
        assertTrue(store.contains("componentType"));

        assertTrue(discovery.contains("view.getBackground() != null"));
        assertTrue(discovery.contains("instanceof ImageView"));
        assertTrue(discovery.contains("getDrawable() != null"));
        assertTrue(discovery.contains("hierarchyPath"));
        assertTrue(discovery.contains("ACTION_CLEAR_BACKGROUND"));
        assertTrue(discovery.contains("ACTION_CLEAR_IMAGE"));
        assertTrue(discovery.contains("ACTION_HIDE_VIEW"));
    }

    @Test public void oldRemoteSelectorsAreRejectedAndRuntimeUsesExactPropertyMutation() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        String executor = Files.readString(ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java"));

        assertTrue(store.contains("REMOTE.equals(parts[0])"));
        assertTrue(store.contains("return null;"));
        assertTrue(executor.contains("resolveExactRemoteView"));
        assertTrue(executor.contains("selector.hierarchyPath"));
        assertTrue(executor.contains("selector.className.equals"));
        assertTrue(executor.contains("selector.name.equals"));

        assertTrue(executor.contains("getBackground()"));
        assertTrue(executor.contains("setBackground(null)"));
        assertTrue(executor.contains("setBackground(item.originalBackground)"));
        assertTrue(executor.contains("getDrawable()"));
        assertTrue(executor.contains("setImageDrawable(null)"));
        assertTrue(executor.contains("setImageDrawable(item.originalImage)"));

        assertTrue(executor.contains("View.INVISIBLE"));
        assertFalse(executor.contains("View.GONE"));
    }

    @Test public void mamlDiscoveryRunsForEveryLoadedRootNotOnlyDiagnostics() throws Exception {
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));
        assertTrue(discovery.contains("scanMaml(View host, WidgetBackgroundIdentity identity, Object root)"));
        assertTrue(discovery.contains("readField(root, \"mElements\")"));

        String maml = Files.readString(ROOT.resolve("LauncherMamlBackgroundRuleExecutor.java"));
        assertTrue(maml.contains("LauncherWidgetComponentDiscovery.scanMaml(host, identity, root)"));
    }

    @Test public void composePaginatesWidgetThenComponentTypeThenExactNode() throws Exception {
        assertTrue(Files.exists(PICKER));
        assertTrue(Files.exists(DETAIL));
        String picker = Files.readString(PICKER);
        String detail = Files.readString(DETAIL);
        String manifest = Files.readString(MANIFEST);

        assertTrue(picker.contains("WidgetComponentDetailActivity"));
        assertTrue(picker.contains("已隐藏"));
        assertFalse(picker.contains("SwitchPreference("));

        assertTrue(detail.contains("selectedType"));
        assertTrue(detail.contains("组件类型"));
        assertTrue(detail.contains("背景层"));
        assertTrue(detail.contains("图像层"));
        assertTrue(detail.contains("文本"));
        assertTrue(detail.contains("容器"));
        assertTrue(detail.contains("其他"));
        assertTrue(detail.contains("高级整节点隐藏"));
        assertTrue(detail.contains("ArrowPreference("));
        assertTrue(detail.contains("SwitchPreference("));
        assertTrue(detail.contains("groupBy { it.componentType }"));
        assertTrue(manifest.contains("android:name=\".WidgetComponentDetailActivity\""));
    }
}
