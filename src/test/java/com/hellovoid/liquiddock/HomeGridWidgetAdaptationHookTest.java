package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridWidgetAdaptationHookTest {
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

    @Test
    public void disabledOwnerNeverAdapts() {
        HomeGridWidgetAdaptationHook hook = new HomeGridWidgetAdaptationHook(false);
        assertFalse(hook.shouldAdapt(new FakeItemInfo(true, 4), 2, 1));
    }

    @Test
    public void enabledOwnerRequiresWidgetAndSupportedSpan() {
        HomeGridWidgetAdaptationHook hook = new HomeGridWidgetAdaptationHook(true);
        assertTrue(hook.shouldAdapt(new FakeItemInfo(true, 4), 2, 1));
        assertFalse(hook.shouldAdapt(new FakeItemInfo(true, 4), 3, 2));
        assertFalse(hook.shouldAdapt(new FakeItemInfo(false, 1), 2, 1));
    }
}
