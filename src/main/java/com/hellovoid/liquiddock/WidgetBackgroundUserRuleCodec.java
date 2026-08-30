package com.hellovoid.liquiddock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Stable text format used by ordinary LiquidDock config for user-selected widget targets. */
public final class WidgetBackgroundUserRuleCodec {
    private static final String VERSION = "v1";
    private static final String NULL = "~";

    private WidgetBackgroundUserRuleCodec() {}

    public static String encode(List<WidgetBackgroundUserRule> rules) {
        if (rules == null || rules.isEmpty()) return "";
        List<String> rows = new ArrayList<>();
        for (WidgetBackgroundUserRule rule : rules) {
            if (rule == null) continue;
            WidgetBackgroundIdentity id = rule.identity();
            rows.add(String.join("|",
                    VERSION,
                    rule.targetKind().name(),
                    field(id.type),
                    field(id.productId),
                    field(id.appPackage),
                    Integer.toString(id.spanX),
                    Integer.toString(id.spanY),
                    Integer.toString(id.configSpanX),
                    Integer.toString(id.configSpanY),
                    field(rule.target())));
        }
        Collections.sort(rows);
        return String.join("\n", rows);
    }

    public static List<WidgetBackgroundUserRule> decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return List.of();
        List<WidgetBackgroundUserRule> result = new ArrayList<>();
        for (String row : encoded.split("\\n")) {
            if (row == null || row.isEmpty()) continue;
            try {
                String[] fields = row.split("\\|", -1);
                if (fields.length != 10 || !VERSION.equals(fields[0])) continue;
                WidgetBackgroundUserRule.TargetKind kind =
                        WidgetBackgroundUserRule.TargetKind.valueOf(fields[1]);
                WidgetBackgroundIdentity identity = new WidgetBackgroundIdentity(
                        value(fields[2]), value(fields[3]), value(fields[4]),
                        Integer.parseInt(fields[5]), Integer.parseInt(fields[6]),
                        Integer.parseInt(fields[7]), Integer.parseInt(fields[8]));
                String target = value(fields[9]);
                if (target == null || target.isEmpty()) continue;
                result.add(new WidgetBackgroundUserRule(identity, kind, target));
            } catch (Throwable ignored) {
                // A malformed user row must never broaden into a wildcard rule.
            }
        }
        return List.copyOf(result);
    }

    private static String field(String value) {
        if (value == null) return NULL;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(String encoded) {
        if (NULL.equals(encoded)) return null;
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
