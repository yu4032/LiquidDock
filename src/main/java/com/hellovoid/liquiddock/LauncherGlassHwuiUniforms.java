package com.hellovoid.liquiddock;

import android.graphics.RuntimeShader;

import com.hellovoid.prismal.PrismalEdgeDecoration;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;

/** View-owned material bindings for its baseline, decorated and motion programs. */
final class LauncherGlassHwuiUniforms {
    private final Entry[] entries = {new Entry(), new Entry(), new Entry()};

    void clear() {
        for (Entry entry : entries) {
            entry.shader = null;
            entry.params = null;
            entry.profile = null;
            entry.decoration = null;
        }
    }

    void bind(
            RuntimeShader shader,
            PrismalParams params,
            PrismalHighlightProfile profile,
            PrismalEdgeDecoration decoration,
            float density,
            float minDimension,
            boolean motion,
            float[] invariants) {
        Entry selected = null;
        for (Entry entry : entries) {
            if (entry.shader == shader) {
                selected = entry;
                break;
            }
            if (selected == null && entry.shader == null) selected = entry;
        }
        if (selected == null)
            throw new IllegalStateException("Unexpected Workspace shader program");
        boolean fresh = selected.shader != shader;
        boolean opticsChanged = fresh || selected.params != params;
        boolean sizeChanged = fresh || Float.compare(selected.minDimension, minDimension) != 0;
        // Motion changes causticIntensity even on the shared undecorated program. Restore the
        // original value when motion ends, including a return with otherwise unchanged optics.
        if (opticsChanged || selected.motion != motion) {
            LauncherGlassSession.setHwuiProbeOpticalUniforms(shader, params, invariants);
            if (motion) LauncherGlassSession.setHwuiProbeMotionQuality(shader);
        }
        if (opticsChanged || sizeChanged) {
            LauncherGlassSession.setHwuiProbeSizeUniforms(shader, params, minDimension);
        }
        if (fresh || selected.profile != profile) {
            LauncherGlassSession.setHwuiProbeHighlightUniforms(shader, profile);
        }
        if (decoration != null
                && (fresh
                        || selected.decoration != decoration
                        || sizeChanged
                        || Float.compare(selected.density, density) != 0)) {
            LauncherGlassSession.setHwuiProbeDecorationUniforms(
                    shader, decoration, density, minDimension);
        }
        selected.shader = shader;
        selected.params = params;
        selected.profile = profile;
        selected.decoration = decoration;
        selected.minDimension = minDimension;
        selected.density = density;
        selected.motion = motion;
    }

    private static final class Entry {
        RuntimeShader shader;
        PrismalParams params;
        PrismalHighlightProfile profile;
        PrismalEdgeDecoration decoration;
        float minDimension, density;
        boolean motion;
    }
}
