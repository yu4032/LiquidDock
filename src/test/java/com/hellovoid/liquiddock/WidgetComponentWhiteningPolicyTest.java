package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** White foreground is an exact, property-scoped opt-in, not a widget-wide filter. */
public class WidgetComponentWhiteningPolicyTest {
    private static WidgetComponentStore.Descriptor remote(
            String provider, String action, String viewClass, String type) {
        return WidgetComponentStore.remoteDescriptor(
                provider, action, "time", viewClass, "0/1/2", type);
    }

    @Test public void onlyRemoteTextAndImageForegroundsAreWhitenable() {
        assertTrue(WidgetComponentWhiteningPolicy.supports(remote(
                "com.example/.Clock", WidgetComponentStore.ACTION_HIDE_VIEW,
                "android.widget.TextView", WidgetComponentStore.TYPE_TEXT)));
        assertTrue(WidgetComponentWhiteningPolicy.supports(remote(
                "com.example/.Clock", WidgetComponentStore.ACTION_CLEAR_IMAGE,
                "android.widget.ImageView", WidgetComponentStore.TYPE_IMAGE)));
        assertFalse(WidgetComponentWhiteningPolicy.supports(remote(
                "com.example/.Clock", WidgetComponentStore.ACTION_CLEAR_BACKGROUND,
                "android.widget.FrameLayout", WidgetComponentStore.TYPE_BACKGROUND)));
        assertFalse(WidgetComponentWhiteningPolicy.supports(remote(
                "com.example/.Clock", WidgetComponentStore.ACTION_HIDE_VIEW,
                "android.widget.LinearLayout", WidgetComponentStore.TYPE_CONTAINER)));
        assertFalse(WidgetComponentWhiteningPolicy.supports(remote(
                "com.example/.Clock", WidgetComponentStore.ACTION_HIDE_VIEW,
                "android.widget.ImageView", WidgetComponentStore.TYPE_IMAGE)));
    }

    @Test public void mamlRemainsFailClosedAndCannotTintWholeWidget() {
        WidgetComponentStore.Descriptor maml = WidgetComponentStore.mamlDescriptor(
                new WidgetBackgroundIdentity("maml", "clock", "com.example", 2, 2, 2, 2),
                "time", "com.miui.maml.elements.TextScreenElement");
        assertFalse(WidgetComponentWhiteningPolicy.supports(maml));
    }

    @Test public void conflictDetectionMatchesOneNodeEvenAcrossDifferentActions() {
        WidgetComponentStore.Descriptor image = remote("com.example/.Clock",
                WidgetComponentStore.ACTION_CLEAR_IMAGE, "android.widget.ImageView",
                WidgetComponentStore.TYPE_IMAGE);
        WidgetComponentStore.Descriptor hidden = remote("com.example/.Clock",
                WidgetComponentStore.ACTION_HIDE_VIEW, "android.widget.ImageView",
                WidgetComponentStore.TYPE_IMAGE);
        WidgetComponentStore.Descriptor other = remote("com.example/.Other",
                WidgetComponentStore.ACTION_HIDE_VIEW, "android.widget.ImageView",
                WidgetComponentStore.TYPE_IMAGE);
        assertTrue(WidgetComponentWhiteningPolicy.sameNode(image, hidden));
        assertFalse(WidgetComponentWhiteningPolicy.sameNode(image, other));
        assertFalse(WidgetComponentWhiteningPolicy.sameNode(null, image));
    }

    @Test public void selectionUsesExistingExactIdentityAndNeverMatchesOtherProviders() {
        WidgetComponentStore.Descriptor a = remote("com.example/.Clock",
                WidgetComponentStore.ACTION_HIDE_VIEW, "android.widget.TextView",
                WidgetComponentStore.TYPE_TEXT);
        WidgetComponentStore.Descriptor b = remote("com.example/.Other",
                WidgetComponentStore.ACTION_CLEAR_IMAGE, "android.widget.ImageView",
                WidgetComponentStore.TYPE_IMAGE);
        WidgetComponentStore.Descriptor c = remote("com.example/.Clock",
                WidgetComponentStore.ACTION_CLEAR_BACKGROUND, "android.widget.TextView",
                WidgetComponentStore.TYPE_BACKGROUND);
        List<WidgetComponentStore.Descriptor> selectors = WidgetComponentWhiteningPolicy.selectors(
                Set.of(a.selectorKey(), b.selectorKey(), c.selectorKey()),
                "com.example/.Clock");
        assertEquals(1, selectors.size());
        assertEquals(a.selectorKey(), selectors.get(0).selectorKey());
        assertTrue(WidgetComponentWhiteningPolicy.selectors(Set.of(), "com.example/.Clock").isEmpty());
    }
}
