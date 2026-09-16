package com.hellovoid.liquiddock.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Restricted JSON codec for hidden third-party glass profiles.
 *
 * <p>Profile ids may be future code-owned adapter ids, but fields are strictly allowlisted here.
 * Class names, method names and arbitrary hook declarations never round-trip through this codec.</p>
 */
final class ThirdPartyGlassConfigCodec {
    private static final String PREFIX = "third_party_glass.";
    private static final Pattern PROFILE_ID = Pattern.compile("[a-z0-9_.-]+");

    private static final Set<String> BOOLEAN_FIELDS = Set.of(
            "enabled",
            "fresh_on_resume",
            "highlight_sky_haze",
            "highlight_specular",
            "highlight_lit_rim",
            "highlight_opposite_rim",
            "highlight_corner_rim",
            "highlight_face_sheen",
            "highlight_plain",
            "highlight_caustics",
            "highlight_press_glow");
    private static final Set<String> INT_FIELDS = Set.of(
            "tint_r", "tint_g", "tint_b", "tint_alpha",
            "capture_scale_percent", "render_fps");
    private static final Set<String> FLOAT_FIELDS = Set.of("blur", "corner_radius_dp");

    private ThirdPartyGlassConfigCodec() {}

    static void exportInto(Map<String, ?> preferences, LinkedHashMap<String, Object> out) {
        if (preferences == null || out == null) return;
        for (Map.Entry<String, ?> entry : preferences.entrySet()) {
            ParsedKey parsed = parse(entry.getKey());
            if (parsed == null) continue;
            Object normalized = normalize(parsed.field, entry.getValue());
            if (normalized != null) out.put(entry.getKey(), normalized);
        }
    }

    static void importInto(Map<String, ?> values, LinkedHashMap<String, Object> out) {
        if (values == null || out == null) return;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            ParsedKey parsed = parse(entry.getKey());
            if (parsed == null) continue;
            Object normalized = normalize(parsed.field, entry.getValue());
            if (normalized != null) out.put(entry.getKey(), normalized);
        }
    }

    private static ParsedKey parse(String key) {
        if (key == null || !key.startsWith(PREFIX)) return null;
        String remainder = key.substring(PREFIX.length());
        int split = remainder.lastIndexOf('.');
        if (split <= 0 || split >= remainder.length() - 1) return null;
        String profileId = remainder.substring(0, split);
        String field = remainder.substring(split + 1);
        if (!PROFILE_ID.matcher(profileId).matches() || !isAllowedField(field)) return null;
        return new ParsedKey(profileId, field);
    }

    private static boolean isAllowedField(String field) {
        return BOOLEAN_FIELDS.contains(field) || INT_FIELDS.contains(field) || FLOAT_FIELDS.contains(field);
    }

    private static Object normalize(String field, Object value) {
        if (BOOLEAN_FIELDS.contains(field)) {
            if (value instanceof Boolean) return value;
            if (value instanceof String) {
                String text = ((String) value).trim();
                if ("true".equalsIgnoreCase(text)) return Boolean.TRUE;
                if ("false".equalsIgnoreCase(text)) return Boolean.FALSE;
            }
            return null;
        }
        if (INT_FIELDS.contains(field)) {
            if (!(value instanceof Number)) return null;
            int raw = ((Number) value).intValue();
            switch (field) {
                case "tint_r":
                case "tint_g":
                case "tint_b":
                case "tint_alpha":
                    return clamp(raw, 0, 255);
                case "capture_scale_percent":
                    return clamp(raw,
                            PassBlurQualityKeys.CAPTURE_SCALE_MIN,
                            PassBlurQualityKeys.CAPTURE_SCALE_MAX);
                case "render_fps":
                    return clamp(raw,
                            PassBlurQualityKeys.RENDER_FPS_MIN,
                            PassBlurQualityKeys.RENDER_FPS_MAX);
                default:
                    return null;
            }
        }
        if (FLOAT_FIELDS.contains(field)) {
            if (!(value instanceof Number)) return null;
            double raw = ((Number) value).doubleValue();
            if (!Double.isFinite(raw)) return null;
            if ("blur".equals(field)) return (float) Math.max(0.0d, Math.min(60.0d, raw));
            if ("corner_radius_dp".equals(field)) {
                return (float) Math.max(-1.0d, Math.min(400.0d, raw));
            }
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ParsedKey {
        final String profileId;
        final String field;

        ParsedKey(String profileId, String field) {
            this.profileId = profileId;
            this.field = field;
        }
    }
}
