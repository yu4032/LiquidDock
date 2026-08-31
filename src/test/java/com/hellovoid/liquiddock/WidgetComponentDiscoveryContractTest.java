package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Spike contract for read-only widget component discovery before any GUI or hide-rule work. */
public class WidgetComponentDiscoveryContractTest {
    private static final Path DISCOVERY = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherWidgetComponentDiscovery.java");
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherWidgetBackgroundController.java");
    private static final Path MAML = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java");

    @Test public void remoteViewsDiscoveryIsReadOnlyAndCapturesStableDescriptors() throws Exception {
        assertTrue(Files.exists(DISCOVERY));
        String source = Files.readString(DISCOVERY);
        assertTrue(source.contains("ViewGroup"));
        assertTrue(source.contains("getResourceEntryName"));
        assertTrue(source.contains("getClass().getName()"));
        assertTrue(source.contains("hierarchyPath"));
        assertTrue(source.contains("android.R.id.widget_frame"));
        assertFalse(source.contains("setVisibility("));
        assertFalse(source.contains("setBackground("));
        assertFalse(source.contains("setAlpha("));
    }

    @Test public void widgetClaimPublishesDiscoveryWithoutOwningHidePolicy() throws Exception {
        String controller = Files.readString(CONTROLLER);
        assertTrue(controller.contains("LauncherWidgetComponentDiscovery.scan(host)"));
    }

    @Test public void mamlDiscoveryReusesRealElementRegistryNamesAndTypes() throws Exception {
        String maml = Files.readString(MAML);
        assertTrue(maml.contains("LauncherWidgetComponentDiscovery.publishMaml"));
        assertTrue(maml.contains("mElements"));
        assertTrue(maml.contains("entry.getKey()"));
        assertTrue(maml.contains("element.getClass().getName()"));
    }
}
