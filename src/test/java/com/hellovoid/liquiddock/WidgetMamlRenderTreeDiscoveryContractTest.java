package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract for third-party MAML widgets whose visual layers are absent from root.mElements. */
public class WidgetMamlRenderTreeDiscoveryContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path DETAIL = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentDetailActivity.kt");
    private static final Path PICKER = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentsPage.kt");

    @Test public void discoveryWalksScreenElementRootInnerGroupInsteadOfOnlyNamedRegistry() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));

        assertTrue(store.contains("MAML_V2 = \"M2\""));
        assertTrue(store.contains("mamlRenderDescriptor"));
        assertTrue(discovery.contains("readField(root, \"mInnerGroup\")"));
        assertTrue(discovery.contains("scanMamlRenderChildren"));
        assertTrue(discovery.contains("readField(group, \"mElements\")"));
        assertTrue(discovery.contains("render/"));
        assertTrue(discovery.contains("mamlRenderDescriptor"));
    }

    @Test public void exactRenderPathSelectorFailsOpenWithoutNameMapFallback() throws Exception {
        String executor = Files.readString(ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java"));

        assertTrue(executor.contains("resolveExactMamlElement"));
        assertTrue(executor.contains("selector.hierarchyPath"));
        assertTrue(executor.contains("selector.className.equals"));
        assertTrue(executor.contains("selector.name.equals"));
        assertFalse(executor.contains("resolveExactMamlElement(root, selector.hierarchyPath) == null\n                    ? HookUtil.invoke(root, \"findElement\", selector.name)"));
    }

    @Test public void legacyAndExactMamlDescriptorsStayInOneWidgetAndAnonymousNodesAreReadable() throws Exception {
        String picker = Files.readString(PICKER);
        String detail = Files.readString(DETAIL);

        assertTrue(picker.contains("descriptor.isMaml()"));
        assertTrue(detail.contains("(匿名元素)"));
        assertTrue(detail.contains("MAML ·"));
    }
}
