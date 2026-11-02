package com.hellovoid.liquiddock;

/** Exact deep-interior evaluation shared by the specialized Workspace materials. */
final class LauncherGlassHwuiMotionBody {
    private static final String MAIN = "half4 main(float2 fragCoord) {";
    private static final String DISTANCE = "float edgeDist = -distMask;";

    static String apply(String source) {
        boolean motion = source.contains("vec3 motion27Grade(");
        if ((!motion && !source.contains("vec3 optical27Grade("))
                || !source.contains(MAIN)
                || source.indexOf(DISTANCE) != source.lastIndexOf(DISTANCE)) {
            throw new IllegalArgumentException("Workspace motion material contract changed");
        }
        source =
                source.replace(
                        MAIN,
                        """
                        // Beyond every optical edge band, refraction/dispersion, Fresnel reflection,
                        // OS4 light and contour coverage are exactly zero. Keep the original dome's
                        // holding shadow and motion face grade; sample the current backdrop normally.
                        vec3 workspaceMotionBody(vec2 screenUv, float depth,
                                vec2 halfSize, float minSize, float radius) {
                            float dome = clamp(u_liquidDome, 0.0, 2.0);
                            float refractionHeight = max(u_heightTransitionWidth * (1.0 + 0.55 * dome), 1.0);
                            refractionHeight = min(refractionHeight, minSize * 0.98);
                            float innerReach = max(min(halfSize.x, halfSize.y) - radius * 0.42,
                                    minSize * 0.22);
                            innerReach += refractionHeight * (1.0 + 0.25 * dome);
                            innerReach = min(innerReach, max(halfSize.x, halfSize.y) * 0.95);
                            float tDeep = clamp(depth / max(innerReach, 2.0), 0.0, 1.0);
                            float tShell = 1.0 - tDeep;
                            float meniscusBand = smoothstep(0.0, 0.12, tShell);
                            float hCap = pow(tShell, 0.38);
                            float edgeBulge = 0.10 * pow(tShell, 2.8);
                            float hDome = (hCap + edgeBulge) * meniscusBand;
                            float coreBlend = smoothstep(0.0, 0.38, tDeep);
                            float hSlab = mix(0.58 + 0.42 * coreBlend, 1.0,
                                    0.4 + 0.6 * (1.0 - dome));
                            float domeW = dome * (0.74 + 0.26 * smoothstep(0.12, 0.94, tShell));
                            float height = mix(hSlab, hDome, domeW);
                            float edgeRound = 1.0 - smoothstep(0.72, 1.0, tShell);
                            height = clamp(height * (0.84 + 0.16 * meniscusBand + 0.08 * edgeRound), 0.0, 1.0);
                            vec2 uv = backdropUv(screenUv, vec2(0.0), 0.0);
                            vec3 color = applyVibrancy(sampleBlurred(uv).rgb, u_vibrancy);
                            color *= u_brightness;
                            color = mix(color, color * u_glassColor.rgb, u_glassColor.a);
                            float avgDim = (u_glassSize.x + u_glassSize.y) * 0.5;
                            float innerShadow = 1.0 - smoothstep(0.0,
                                    avgDim * u_invariantShadowExtent, depth);
                            innerShadow = pow(innerShadow, 2.35) * 0.62 * (0.22 + height * 0.68);
                            innerShadow *= 1.0 - smoothstep(0.0, clamp(minSize * 0.05, 0.6, 3.5), depth) * 0.55;
                            color = mix(color, u_shadowColor.rgb * 0.25, innerShadow * u_shadowColor.a);
                            if (u_backlitStrength > 0.0) {
                                vec3 gain = mix(vec3(1.0),
                                        clamp(u_glassColor.rgb, vec3(0.55), vec3(1.40)),
                                        0.045 * u_backlitStrength);
                                color = motion27Grade(color, gain, vec3(0.008 * u_backlitStrength));
                            }
                            return mix(color, u_baseColor.rgb, u_baseColor.a);
                        }

                        """
                                + MAIN);
        if (!motion) source = source.replace("motion27Grade(", "optical27Grade(");
        return source.replace(
                DISTANCE,
                DISTANCE
                        + """

                            float bodyDome = clamp(u_liquidDome, 0.0, 2.0);
                            float bodyReach = min(max(u_heightTransitionWidth * (1.0 + 0.55 * bodyDome), 1.0),
                                    minDim * 0.98);
                            bodyReach = max(bodyReach, max(28.0, 4.0 * u_backlitWidthPx + 8.0)
                                    + abs((u_refractionInset - 20.0) * 0.25));
                            bodyReach = max(bodyReach, max(u_os4EdgeWidthPx, 18.0) + 8.0);
                            if (minDim >= 96.0 && edgeDist > bodyReach
                                    && u_showNormals == 0 && u_pressProgress == 0.0
                                    && u_causticIntensity <= 0.001) {
                                vec3 body = workspaceMotionBody(v_screenTexCoord, edgeDist, halfSz, minDim, crMask);
                                return half4(body * u_transmittance, u_transmittance);
                            }
                        """);
    }
}
