package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WidgetComponentRankingTest {
    @Test public void discoveryMetadataRoundTripsWithoutChangingSelector() {
        WidgetComponentStore.Descriptor base = WidgetComponentStore.remoteDescriptor(
                "com.example/.Widget",
                WidgetComponentStore.ACTION_CLEAR_BACKGROUND,
                "background",
                "android.widget.FrameLayout",
                "0/0",
                WidgetComponentStore.TYPE_BACKGROUND);
        assertNotNull(base);
        String selector = base.selectorKey();

        WidgetComponentStore.Descriptor enriched = base.withDiscoveryMetadata(
                0, 1, 0.94f, 0.0f);
        WidgetComponentStore.Descriptor parsed =
                WidgetComponentStore.parseCatalog(enriched.encodeCatalog());

        assertNotNull(parsed);
        assertEquals(selector, parsed.selectorKey());
        assertEquals(0, parsed.renderOrdinal);
        assertEquals(1, parsed.depth);
        assertEquals(0.94f, parsed.areaRatio, 0.0001f);
        assertEquals(0.0f, parsed.effectiveZ, 0.0001f);
    }

    @Test public void likelyLargeEarlyBackgroundRanksBeforeTextAndSmallImages() {
        WidgetComponentStore.Descriptor background = WidgetComponentStore.remoteDescriptor(
                "com.example/.Widget",
                WidgetComponentStore.ACTION_CLEAR_BACKGROUND,
                "background",
                "android.widget.FrameLayout",
                "0/0",
                WidgetComponentStore.TYPE_BACKGROUND)
                .withDiscoveryMetadata(0, 1, 0.96f, 0.0f);
        WidgetComponentStore.Descriptor icon = WidgetComponentStore.remoteDescriptor(
                "com.example/.Widget",
                WidgetComponentStore.ACTION_CLEAR_IMAGE,
                "icon",
                "android.widget.ImageView",
                "0/1",
                WidgetComponentStore.TYPE_IMAGE)
                .withDiscoveryMetadata(2, 1, 0.08f, 0.0f);
        WidgetComponentStore.Descriptor text = WidgetComponentStore.remoteDescriptor(
                "com.example/.Widget",
                WidgetComponentStore.ACTION_HIDE_VIEW,
                "title",
                "android.widget.TextView",
                "0/2",
                WidgetComponentStore.TYPE_TEXT)
                .withDiscoveryMetadata(1, 1, 0.30f, 0.0f);

        List<WidgetComponentStore.Descriptor> sorted = WidgetComponentRanking.sorted(
                Arrays.asList(text, icon, background));

        assertEquals(background.selectorKey(), sorted.get(0).selectorKey());
        assertTrue(WidgetComponentRanking.isLikelyBackground(background));
        assertFalse(WidgetComponentRanking.isLikelyBackground(icon));
        assertFalse(WidgetComponentRanking.isLikelyBackground(text));
    }

    @Test public void legacyCatalogWithoutMetadataRemainsParseableAndNotAutoPromoted() {
        String legacy = "R2\tcom.example/.Widget\tbackground\tbg\tandroid.view.View\t0/0\t\tbackground";
        WidgetComponentStore.Descriptor descriptor = WidgetComponentStore.parseCatalog(legacy);

        assertNotNull(descriptor);
        assertEquals(-1, descriptor.renderOrdinal);
        assertEquals(-1, descriptor.depth);
        assertTrue(Float.isNaN(descriptor.areaRatio));
        assertTrue(Float.isNaN(descriptor.effectiveZ));
        assertFalse(WidgetComponentRanking.isLikelyBackground(descriptor));
    }
}
