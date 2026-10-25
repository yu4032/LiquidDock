package com.hellovoid.prismal;

/**
 * Shared continuous-corner silhouette for glass and its CPU shadow/border paths.
 * Adapted from liquid_glass_easy, Copyright (c) 2025 Ahmed Gamil (MIT).
 * See docs/licenses/LiquidGlassEasy-MIT.txt. This is an Apple-like approximation.
 */
public final class PrismalContinuousCorners {
    private static final double REACH = .2893;
    private static final double EXP_RISE = .7198;
    private static final int STEPS = 32;

    private PrismalContinuousCorners() {}

    /** Clockwise x/y pairs, in view coordinates. Call only when geometry changes. */
    public static float[] contour(float width, float height, float radius) {
        if (!(width > 0) || !(height > 0)
                || !Float.isFinite(width) || !Float.isFinite(height)) return new float[0];
        double r = Float.isFinite(radius)
                ? Math.max(0, Math.min(radius, Math.min(width, height) * .5)) : 0;
        if (r <= .001) return new float[]{0, 0, width, 0, width, height, 0, height};
        double reachX = reach(width * .5, r), reachY = reach(height * .5, r);
        double rx = r + reachX, ry = r + reachY;
        double nx = 2 + EXP_RISE * reachX / (REACH * r);
        double ny = 2 + EXP_RISE * reachY / (REACH * r);
        float[] points = new float[4 * (STEPS + 1) * 2];
        int index = 0;
        for (int corner = 0; corner < 4; corner++) {
            for (int i = 0; i <= STEPS; i++) {
                double angle = i * Math.PI / (2 * STEPS);
                double s = i == 0 ? 0 : Math.sin(angle);
                double c = i == STEPS ? 0 : Math.cos(angle);
                double dx = rx * Math.pow((corner & 1) == 0 ? s : c, 2 / nx);
                double dy = ry * Math.pow((corner & 1) == 0 ? c : s, 2 / ny);
                boolean right = corner < 2, bottom = corner == 1 || corner == 2;
                points[index++] = (float) ((right ? width - rx : rx) + (right ? dx : -dx));
                points[index++] = (float) ((bottom ? height - ry : ry) + (bottom ? dy : -dy));
            }
        }
        return points;
    }

    private static double reach(double half, double r) {
        double t = Math.max(0, Math.min(1, (half - r) / r));
        return r * Math.min(REACH * Math.pow(t, .6), t);
    }

    // The zero set matches contour(): sum(pow(q / R, n)) == 1.
    // The field is an approximate distance; derivative AA and normals use its actual gradient.
    private static final String HELPERS = """
            uniform float u_continuousCorners;
            // Continuous corners adapted from Ahmed Gamil's liquid_glass_easy (MIT).
            vec2 continuousGlassReach(float r, vec2 b) {
                vec2 room = clamp((b - vec2(r)) / max(r, 1e-6), 0.0, 1.0);
                return r * min(0.2893 * pow(room, vec2(0.6)), room);
            }

            float sdContinuousGlass(vec2 p, vec2 b, float radius) {
                vec2 e = abs(p) - b;
                float box = min(max(e.x, e.y), 0.0) + length(max(e, vec2(0.0)));
                float r = clamp(radius, 0.0, min(b.x, b.y));
                if (r <= 0.001) return box;
                vec2 reach = continuousGlassReach(r, b);
                vec2 R = min(vec2(r) + reach, b);
                vec2 q = abs(p) - (b - R);
                if (min(q.x, q.y) <= 0.0) return box;
                vec2 n = vec2(2.0) + 0.7198 * reach / max(0.2893 * r, 1e-6);
                vec2 shares = pow(q / R, n);
                float total = shares.x + shares.y;
                if (total <= 1e-20) return box;
                vec2 weight = shares / total;
                float corner = dot(R, weight) * (pow(total, 1.0 / dot(n, weight)) - 1.0);
                return max(corner, box);
            }

            float sdRoundedRectRealistic(vec2 p, vec2 b, float r) {
                if (u_continuousCorners < 0.5) return sdLegacyRoundedRect(p, b, r);
                return sdContinuousGlass(p, b, r);
            }

            """;

    static String apply(String source) {
        String start = "float sdRoundedRectRealistic(";
        String end = "float circleMapRealistic(";
        int startIndex = source.indexOf(start), endIndex = source.indexOf(end, startIndex);
        if (startIndex < 0 || endIndex < startIndex) {
            throw new IllegalArgumentException("Continuous glass rounded-rect contract changed");
        }
        String legacyGeometry = source.substring(startIndex, endIndex)
                .replace("sdRoundedRectRealistic(", "sdLegacyRoundedRect(")
                .replace("gradSdRoundedRectRealistic(", "gradLegacyRoundedRect(");
        source = replaceRegion(source, start, end, legacyGeometry + HELPERS);
        int boxStart = source.indexOf("float sdRoundBox(");
        int boxEnd = source.indexOf("float getHeightFromDist(", boxStart);
        if (boxStart < 0 || boxEnd < boxStart) {
            throw new IllegalArgumentException("Continuous glass rounded-box contract changed");
        }
        String legacyBox = source.substring(boxStart, boxEnd)
                .replace("sdRoundBox(", "sdLegacyRoundBox(");
        source = replaceRegion(source, "float sdRoundBox(", "float getHeightFromDist(", legacyBox + """
                // Keep the adapter signature; continuous shoulders replace legacy smin rounding.
                float sdRoundBox(vec2 p, vec2 b, float r, float k) {
                    if (u_continuousCorners < 0.5) return sdLegacyRoundBox(p, b, r, k);
                    float radius = radiusAtCentered(vec2(p.x, -p.y), u_cornerRadii);
                    return sdContinuousGlass(p, b, radius);
                }

                """);
        // The old smoothing control now smooths the height normal, never the silhouette.
        source = replaceOnce(source, "vec2 s = vec2(1.0, 1.0);",
                "vec2 s = vec2(u_continuousCorners > 0.5 ? max(k, 1.0) : 1.0);");
        source = replaceOnce(source,
                "return vec2((hpx - hnx) * 0.5, (hpy - hny) * 0.5);",
                "return vec2(hpx - hnx, hpy - hny) * 0.5 / s;");
        return replaceOnce(source,
                "vec2 gradLens = gradSdRoundedRectRealistic(cKy, halfSz, gradRadius);",
                "vec2 gradLens = u_continuousCorners > 0.5 ? os4SdfGradient\n"
                        + "        : gradLegacyRoundedRect(cKy, halfSz, gradRadius);");
    }

    private static String replaceRegion(String source, String start, String end, String replacement) {
        int a = source.indexOf(start), b = source.indexOf(end, a);
        if (a < 0 || b < a || source.indexOf(start, a + start.length()) >= 0) {
            throw new IllegalArgumentException("Continuous glass shader contract changed: " + start);
        }
        return source.substring(0, a) + replacement + source.substring(b);
    }

    private static String replaceOnce(String source, String old, String replacement) {
        int index = source.indexOf(old);
        if (index < 0 || source.indexOf(old, index + old.length()) >= 0) {
            throw new IllegalArgumentException("Continuous glass shader contract changed: " + old);
        }
        return source.substring(0, index) + replacement + source.substring(index + old.length());
    }
}
