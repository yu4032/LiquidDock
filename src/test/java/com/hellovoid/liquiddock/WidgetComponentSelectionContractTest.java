package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class WidgetComponentSelectionContractTest {
    private static final Path ROOT = Path.of(
            "src", "main", "java", "com", "hellovoid", "liquiddock");
    private static final Path KOTLIN_ROOT = Path.of(
            "src", "main", "kotlin", "com", "hellovoid", "liquiddock");
    private static final Path PICKER = KOTLIN_ROOT.resolve("WidgetComponentsPage.kt");
    private static final Path DETAIL = KOTLIN_ROOT.resolve("WidgetComponentDetailActivity.kt");
    private static final Path MANIFEST = Path.of("src", "main", "AndroidManifest.xml");

    @Test public void selectionUsesExactRemotePathsAndSafeActions() throws Exception {
        String executor = Files.readString(ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java"));
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));

        assertTrue(store.contains("REMOTE_V2"));
        assertTrue(store.contains("hierarchyPath"));
        assertTrue(store.contains("ACTION_CLEAR_BACKGROUND"));
        assertTrue(store.contains("ACTION_CLEAR_IMAGE"));
        assertTrue(store.contains("ACTION_HIDE_VIEW"));

        assertTrue(executor.contains("resolveExactRemoteView"));
        assertTrue(executor.contains("selector.hierarchyPath"));
        assertTrue(executor.contains("selector.className.equals(target.getClass().getName())"));
        assertTrue(executor.contains("selector.name.equals(resource)"));
        assertTrue(executor.contains("setBackground(null)"));
        assertTrue(executor.contains("setImageDrawable(null)"));
        assertTrue(executor.contains("View.INVISIBLE"));
        assertFalse(executor.contains("View.GONE"));
    }

    @Test public void coarseRemoteSelectorIsRejected() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        assertTrue(store.contains("retired coarse selector"));
        assertTrue(store.contains("if (parts.length > 0 && REMOTE.equals(parts[0])) return null"));
    }

    @Test public void discoveryUsesExactPathsAndBatchedTransport() throws Exception {
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));

        assertTrue(discovery.contains("hierarchyPath + \"/\" + i"));
        assertTrue(discovery.contains("publishBatch"));
        assertTrue(store.contains("EXTRA_DESCRIPTORS"));
        assertTrue(store.contains("BATCH_MAX_ITEMS"));
    }

    @Test public void discoveryRequestIsOneShotButProcessSessionCanContinue() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        String receiver = Files.readString(ROOT.resolve("WidgetDiscoveryReceiver.java"));

        assertTrue(store.contains("discoverySessionLoaded"));
        assertTrue(store.contains("discoveryActive"));
        assertTrue(store.contains("acknowledgeDiscoveryRequest"));
        assertTrue(receiver.contains("EXTRA_REQUEST_ACK"));
        assertTrue(receiver.contains("DISCOVERY_REQUEST_KEY"));
    }

    @Test public void remoteDiscoveryCanRetrySameRootAfterProviderPopulation() throws Exception {
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));

        assertTrue(discovery.contains("remoteSnapshotSignature"));
        assertTrue(discovery.contains("Map<View, Integer> DUMPED_REMOTE_ROOTS"));
        assertFalse(discovery.contains("Set<View> DUMPED_REMOTE_ROOTS"));
    }

    @Test public void remoteRootCanExposePropertyActionsWithoutWholeNodeHide() throws Exception {
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));

        assertTrue(discovery.contains("scanNode(content, provider, \"0\", false"));
        assertTrue(discovery.contains("if (hasBackground)"));
        assertTrue(discovery.contains("if (hasImage)"));
        assertTrue(discovery.contains("if (allowWholeNodeHide)"));
    }

    @Test public void mamlSupportsStableNamesAndExactRenderPaths() throws Exception {
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        String executor = Files.readString(ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java"));

        assertTrue(discovery.contains("mamlDescriptor"));
        assertTrue(discovery.contains("mamlRenderDescriptor"));
        assertTrue(discovery.contains("mInnerGroup"));
        assertTrue(store.contains("MAML_V2"));
        assertTrue(executor.contains("resolveExactMamlElement"));
    }

    @Test public void exactMamlPathMustMatchNameAndClassBeforeHide() throws Exception {
        String executor = Files.readString(ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java"));

        assertTrue(executor.contains("selector.name.equals(targetName)"));
        assertTrue(executor.contains("selector.className.equals(target.getClass().getName())"));
    }

    @Test public void userClaimsRestoreOriginalProviderState() throws Exception {
        String executor = Files.readString(ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java"));

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
        assertTrue(picker.contains("SwitchPreference("));
        assertTrue(picker.contains("title = \"液态玻璃背景\""));
        assertTrue(picker.contains("WidgetGlassSelectionPolicy.SELECTION_KEY"));

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
