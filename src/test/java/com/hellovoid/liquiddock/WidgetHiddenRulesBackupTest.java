package com.hellovoid.liquiddock;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WidgetHiddenRulesBackupTest {
    private static final String R2 =
            "R2\tcom.android.calendar/.widget.MonthWidgetProviderNew\tbackground\twidget_frame\t"
                    + "com.miui.miuiwidget.views.MIUIWidgetFrameLayout\t0/0";
    private static final String M =
            "M\tproduct-a\tbackground\tcom.miui.maml.elements.RectangleScreenElement";
    private static final String M2 =
            "M2\tproduct-b\t\tcom.miui.maml.elements.RectangleScreenElement\trender/0/2";

    @Test public void backupRoundTripsCurrentSelectorVersionsInStableOrder() throws Exception {
        Class<?> codec = codecClass();
        Method export = codec.getDeclaredMethod("exportValues", Set.class);
        Method importValues = codec.getDeclaredMethod("importValues", Map.class);
        export.setAccessible(true);
        importValues.setAccessible(true);

        @SuppressWarnings("unchecked") Map<String, Object> encoded = (Map<String, Object>)
                export.invoke(null, Set.of(M2, R2, M));

        assertEquals("liquiddock-widget-hidden", encoded.get("_format"));
        assertEquals(1, encoded.get("_version"));
        assertEquals(List.of(M, M2, R2), encoded.get("selectors"));

        @SuppressWarnings("unchecked") Set<String> decoded = (Set<String>) importValues.invoke(null, encoded);
        assertEquals(Set.of(R2, M, M2), decoded);
    }

    @Test public void importRejectsUnknownSelectorInsteadOfPartiallyAcceptingFile() throws Exception {
        Class<?> codec = codecClass();
        Method importValues = codec.getDeclaredMethod("importValues", Map.class);
        importValues.setAccessible(true);
        Map<String, Object> encoded = new HashMap<>();
        encoded.put("_format", "liquiddock-widget-hidden");
        encoded.put("_version", 1);
        encoded.put("selectors", List.of(R2, "unknown-selector"));

        try {
            importValues.invoke(null, encoded);
            fail("invalid selector should reject the whole backup");
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test public void applyReplacesExistingHiddenRulesRatherThanMerging() throws Exception {
        Class<?> codec = codecClass();
        Method replace = codec.getDeclaredMethod(
                "replaceSelections", SharedPreferences.class, Set.class);
        replace.setAccessible(true);
        TestSharedPreferences prefs = new TestSharedPreferences(Map.of(
                WidgetComponentStore.SELECTION_KEY, Set.of("old-rule")));

        assertEquals(Boolean.TRUE, replace.invoke(null, prefs, Set.of(R2, M2)));
        assertEquals(Set.of(R2, M2),
                prefs.getStringSet(WidgetComponentStore.SELECTION_KEY, Set.of()));
        assertEquals(1, prefs.commitCount());
    }

    private static Class<?> codecClass() {
        try {
            return Class.forName("com.hellovoid.liquiddock.WidgetHiddenRulesBackup");
        } catch (ClassNotFoundException missing) {
            fail("WidgetHiddenRulesBackup production codec is missing");
            throw new AssertionError(missing);
        }
    }
}
