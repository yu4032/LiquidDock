package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Requires standard AppWidgets to clear only the tagged RemoteViews content-root background. */
public class WidgetRemoteViewsRootBackgroundContractTest {
    private static final Path HELPER = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassVendorMaterialSuppressor.java");

    @Test
    public void standardWidgetTargetsTaggedDirectRemoteViewsRootInsteadOfViewIdLookup()
            throws Exception {
        String source = Files.readString(HELPER);

        assertTrue(source.contains("resolveRemoteViewsContent"));
        assertTrue(source.contains("host instanceof ViewGroup"));
        assertTrue(source.contains("getChildAt"));
        assertTrue(source.contains("child.getTag(android.R.id.widget_frame)"));
        assertTrue(source.contains("setBackground(null)"));

        assertFalse(source.contains("findViewById(android.R.id.widget_frame)"));
        assertFalse(source.contains("resolveRemoteViewsContent(child)"));
        assertFalse(source.contains("removeAllViews"));
        assertFalse(source.contains("setVisibility(View.GONE"));
    }
}
