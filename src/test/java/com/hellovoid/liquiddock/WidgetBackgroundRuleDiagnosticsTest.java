package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class WidgetBackgroundRuleDiagnosticsTest {
    @Test
    public void diagnosticKeyEmitsOnlyOnce() {
        List<String> messages = new ArrayList<>();
        OneShotDiagnostic diagnostic = new OneShotDiagnostic(messages::add);

        diagnostic.emit("widget_rules_load_failed", "first");
        diagnostic.emit("widget_rules_load_failed", "second");

        assertEquals(List.of("first"), messages);
    }
}
