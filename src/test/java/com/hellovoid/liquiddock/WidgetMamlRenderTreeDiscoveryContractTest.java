package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** High-risk structure sentinel for third-party MAML layers absent from root.mElements. */
public class WidgetMamlRenderTreeDiscoveryContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path DETAIL = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentDetailActivity.kt");
    private static final Path PICKER = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentsPage.kt");

    @Test public void anonymousRenderTreeDiscoveryStaysExactAndSeparateFromLegacyNameLookup() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));
        String executor = Files.readString(ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java"));
        String picker = Files.readString(PICKER);
        String detail = Files.readString(DETAIL);

        assertTrue(store.contains("MAML_V2 = \"M2\""));
        assertTrue(store.contains("mamlRenderDescriptor"));
        assertTrue(discovery.contains("readField(root, \"mInnerGroup\")"));
        assertTrue(discovery.contains("scanMamlRenderChildren"));
        assertTrue(discovery.contains("readField(group, \"mElements\")"));
        assertTrue(discovery.contains("mamlRenderDescriptor"));

        assertTrue(executor.contains("resolveExactMamlElement"));
        assertTrue(executor.contains("selector.hierarchyPath"));
        assertTrue(executor.contains("selector.className.equals"));
        assertTrue(executor.contains("selector.name.equals"));
        assertFalse(executor.contains("resolveExactMamlElement(root, selector.hierarchyPath) == null\n                    ? HookUtil.invoke(root, \"findElement\", selector.name)"));

        assertTrue(picker.contains("descriptor.isMaml()"));
        assertTrue(detail.contains("(匿名元素)"));
        assertTrue(detail.contains("MAML ·"));
    }
}
