package com.hellovoid.liquiddock;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Codec and atomic preference write for standalone widget-hidden-rule backups. */
final class WidgetHiddenRulesBackup {
    static final String FORMAT = "liquiddock-widget-hidden";
    static final int VERSION = 1;
    static final String KEY_FORMAT = "_format";
    static final String KEY_VERSION = "_version";
    static final String KEY_SELECTORS = "selectors";

    private WidgetHiddenRulesBackup() {}

    static Map<String, Object> exportValues(Set<String> selectors) {
        ArrayList<String> valid = new ArrayList<>();
        if (selectors != null) {
            for (String selector : selectors) {
                if (WidgetComponentStore.parseSelector(selector) != null) valid.add(selector);
            }
        }
        Collections.sort(valid);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_FORMAT, FORMAT);
        result.put(KEY_VERSION, VERSION);
        result.put(KEY_SELECTORS, valid);
        return result;
    }

    static Set<String> importValues(Map<String, Object> values) {
        if (values == null || !FORMAT.equals(values.get(KEY_FORMAT))) {
            throw new IllegalArgumentException("Not a LiquidDock widget hidden rules file");
        }
        Object version = values.get(KEY_VERSION);
        if (!(version instanceof Number) || ((Number) version).intValue() != VERSION) {
            throw new IllegalArgumentException("Unsupported widget hidden rules version");
        }
        Object selectorsValue = values.get(KEY_SELECTORS);
        if (!(selectorsValue instanceof Iterable<?>)) {
            throw new IllegalArgumentException("Missing widget hidden selectors");
        }

        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        for (Object item : (Iterable<?>) selectorsValue) {
            if (!(item instanceof String)) {
                throw new IllegalArgumentException("Widget hidden selector must be a string");
            }
            String selector = (String) item;
            if (WidgetComponentStore.parseSelector(selector) == null) {
                throw new IllegalArgumentException("Unsupported widget hidden selector");
            }
            selectors.add(selector);
        }
        return selectors;
    }

    static boolean replaceSelections(SharedPreferences preferences, Set<String> selectors) {
        if (preferences == null || selectors == null) return false;
        return preferences.edit()
                .putStringSet(WidgetComponentStore.SELECTION_KEY, new HashSet<>(selectors))
                .commit();
    }
}
