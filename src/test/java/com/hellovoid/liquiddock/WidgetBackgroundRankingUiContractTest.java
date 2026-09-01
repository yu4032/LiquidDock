package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class WidgetBackgroundRankingUiContractTest {
    private static final Path PICKER = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentsPage.kt");
    private static final Path DETAIL = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentDetailActivity.kt");
    private static final Path DISCOVERY = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherWidgetComponentDiscovery.java");

    @Test public void discoveryCollectsRenderDepthAreaAndZMetadata() throws Exception {
        String source = Files.readString(DISCOVERY);
        assertTrue(source.contains("renderOrdinal"));
        assertTrue(source.contains("areaRatio"));
        assertTrue(source.contains("view.getZ()"));
        assertTrue(source.contains("getWidth"));
        assertTrue(source.contains("getHeight"));
    }

    @Test public void exactNodeUiSurfacesLikelyBackgroundsFirstWithDiagnostics() throws Exception {
        String picker = Files.readString(PICKER);
        String detail = Files.readString(DETAIL);

        assertTrue(picker.contains("WidgetComponentRanking.compare"));
        assertTrue(detail.contains("疑似底层背景"));
        assertTrue(detail.contains("WidgetComponentRanking.sorted"));
        assertTrue(detail.contains("WidgetComponentRanking.isLikelyBackground"));
        assertTrue(detail.contains("Render #"));
        assertTrue(detail.contains("Depth"));
        assertTrue(detail.contains("Area"));
        assertTrue(detail.contains("Z"));
    }
}
