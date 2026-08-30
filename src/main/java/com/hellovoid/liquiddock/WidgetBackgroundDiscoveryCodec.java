package com.hellovoid.liquiddock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Compact fail-closed format shared by the injected Launcher and settings UI. */
public final class WidgetBackgroundDiscoveryCodec {
    private static final String VERSION = "v1";
    private static final String NULL = "~";

    private WidgetBackgroundDiscoveryCodec() {}

    public static String preferenceKey(WidgetBackgroundIdentity identity) {
        return "widget:" + field(identity.type) + ":" + field(identity.productId) + ":"
                + field(identity.appPackage) + ":" + identity.spanX + ":" + identity.spanY + ":"
                + identity.configSpanX + ":" + identity.configSpanY;
    }

    public static String encode(WidgetBackgroundDiscoverySnapshot snapshot) {
        WidgetBackgroundIdentity id = snapshot.identity();
        StringBuilder out = new StringBuilder();
        out.append(VERSION).append('|')
                .append(field(id.type)).append('|')
                .append(field(id.productId)).append('|')
                .append(field(id.appPackage)).append('|')
                .append(id.spanX).append('|').append(id.spanY).append('|')
                .append(id.configSpanX).append('|').append(id.configSpanY).append('|')
                .append(snapshot.lastSeenMillis());
        for (WidgetBackgroundDiscoveryTarget target : snapshot.targets()) {
            out.append('\n').append(target.kind().name()).append('|')
                    .append(field(target.name())).append('|').append(field(target.detail()));
        }
        return out.toString();
    }

    public static WidgetBackgroundDiscoverySnapshot decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            String[] lines = encoded.split("\\n");
            String[] header = lines[0].split("\\|", -1);
            if (header.length != 9 || !VERSION.equals(header[0])) return null;
            WidgetBackgroundIdentity identity = new WidgetBackgroundIdentity(
                    value(header[1]), value(header[2]), value(header[3]),
                    Integer.parseInt(header[4]), Integer.parseInt(header[5]),
                    Integer.parseInt(header[6]), Integer.parseInt(header[7]));
            long lastSeen = Long.parseLong(header[8]);
            List<WidgetBackgroundDiscoveryTarget> targets = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String[] row = lines[i].split("\\|", -1);
                if (row.length != 3) continue;
                String name = value(row[1]);
                if (name == null || name.isEmpty()) continue;
                try {
                    targets.add(new WidgetBackgroundDiscoveryTarget(
                            WidgetBackgroundUserRule.TargetKind.valueOf(row[0]),
                            name, value(row[2])));
                } catch (Throwable ignored) {}
            }
            return new WidgetBackgroundDiscoverySnapshot(identity, targets, lastSeen);
        } catch (Throwable ignored) {
            return null;
        }
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
