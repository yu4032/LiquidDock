package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed contract for the centralized Launcher Widget classification boundary. */
public class WidgetClassifierTest {
    public static final class FakeItemInfo {
        public int itemType;
        private final boolean widget;

        FakeItemInfo(boolean widget, int itemType) {
            this.widget = widget;
            this.itemType = itemType;
        }

        public boolean isWidget() {
            return widget;
        }
    }

    public static final class FallbackOnlyItemInfo {
        public int itemType;

        FallbackOnlyItemInfo(int itemType) {
            this.itemType = itemType;
        }
    }

    @Test
    public void vendorIsWidgetTrueWins() {
        assertTrue(WidgetClassifier.isWidget(new FakeItemInfo(true, 0)));
    }

    @Test
    public void knownItemTypeFallbackIsAcceptedWhenVendorMethodIsFalse() {
        assertTrue(WidgetClassifier.isWidget(new FakeItemInfo(false, 4)));
    }

    @Test
    public void knownItemTypeFallbackIsAcceptedWhenVendorMethodIsUnavailable() {
        assertTrue(WidgetClassifier.isWidget(new FallbackOnlyItemInfo(19)));
    }

    @Test
    public void allCurrentFallbackTypesAreRecognized() {
        assertTrue(WidgetClassifier.isWidgetFallbackType(4));
        assertTrue(WidgetClassifier.isWidgetFallbackType(5));
        assertTrue(WidgetClassifier.isWidgetFallbackType(19));
    }

    @Test
    public void unrelatedTypesAreRejected() {
        assertFalse(WidgetClassifier.isWidget(new FakeItemInfo(false, 1)));
        assertFalse(WidgetClassifier.isWidgetFallbackType(0));
        assertFalse(WidgetClassifier.isWidgetFallbackType(1));
        assertFalse(WidgetClassifier.isWidgetFallbackType(6));
    }

    @Test
    public void nullItemInfoIsNotAWidget() {
        assertFalse(WidgetClassifier.isWidget(null));
    }
}
