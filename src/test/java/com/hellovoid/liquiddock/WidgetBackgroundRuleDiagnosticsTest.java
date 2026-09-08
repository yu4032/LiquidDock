package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class WidgetBackgroundRuleDiagnosticsTest {
    @Test
    public void diagnosticKeyEmitsOnlyOnce() {
        List<String> messages = new ArrayList<>();
        OneShotDiagnostic diagnostic = new OneShotDiagnostic(messages::add);

        diagnostic.emit("widget_rules_load_failed", "first");
        diagnostic.emit("widget_rules_load_failed", "second");

        assertEquals(List.of("first"), messages);
    }

    @Test
    public void missingBundledResourceIsDistinguishable() {
        ClassLoader loader = new ClassLoader(null) {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                return null;
            }
        };

        WidgetBackgroundRuleEngine.LoadResult result =
                WidgetBackgroundRuleEngine.loadBundled(loader);

        assertEquals(WidgetBackgroundRuleEngine.LoadStatus.MISSING_RESOURCE, result.status());
        assertNotNull(result.engine());
        assertNull(result.engine().match(new WidgetBackgroundIdentity(
                "maml", "missing", "pkg", 1, 1, 2, 2)));
    }

    @Test
    public void malformedBundledResourceIsDistinguishable() {
        ClassLoader loader = new ClassLoader(null) {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                return new ByteArrayInputStream(
                        "<broken".getBytes(StandardCharsets.UTF_8));
            }
        };

        WidgetBackgroundRuleEngine.LoadResult result =
                WidgetBackgroundRuleEngine.loadBundled(loader);

        assertEquals(WidgetBackgroundRuleEngine.LoadStatus.PARSE_FAILED, result.status());
        assertNotNull(result.engine());
        assertNull(result.engine().match(new WidgetBackgroundIdentity(
                "maml", "broken", "pkg", 1, 1, 2, 2)));
    }
}
