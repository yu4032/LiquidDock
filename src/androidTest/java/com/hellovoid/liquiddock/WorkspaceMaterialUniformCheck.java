package com.hellovoid.liquiddock;

import android.app.Instrumentation;
import android.graphics.RuntimeShader;
import android.os.Bundle;

import com.hellovoid.prismal.*;

import java.util.*;

/** Compare every real Skia uniform with the original complete upload after state transitions. */
final class WorkspaceMaterialUniformCheck {
    static void run(Instrumentation owner) {
        Bundle result = new Bundle();
        Throwable[] failure = new Throwable[1];
        owner.runOnMainSync(
                () -> {
                    try {
                        String source = LauncherGlassHwuiProbeView.agslSource(true, false);
                        CountingShader original = new CountingShader(source),
                                cached = new CountingShader(source);
                        LauncherGlassHwuiUniforms uniforms = new LauncherGlassHwuiUniforms();
                        PrismalParams.Builder initial = PrismalParams.builder();
                        initial.causticIntensity = .7f;
                        PrismalParams params = initial.build();
                        PrismalHighlightProfile profile =
                                PrismalHighlightProfile.ALL_ENABLED
                                        .withOs4EdgeReplacingLegacyEdge();
                        PrismalEdgeDecoration edge =
                                new PrismalEdgeDecoration(2, .6f, .5f, 12, 3, 0, 0, 0, .4f, .3f);
                        float[] invariants = new float[10];
                        for (int i = 0; i < 30; i++) {
                            if (i == 25) {
                                PrismalParams.Builder changed = PrismalParams.builder();
                                changed.ior = 1.57f;
                                changed.causticIntensity = .9f;
                                params = changed.build();
                                profile =
                                        new PrismalHighlightProfile(
                                                        false, true, true, true, true, true, true,
                                                        true, true)
                                                .withOs4EdgeReplacingLegacyEdge();
                            }
                            float size = i < 20 ? 166f : 600f;
                            boolean motion = i < 28;
                            LauncherGlassSession.setHwuiProbeMaterialUniforms(
                                    original, params, profile, size, invariants);
                            LauncherGlassSession.setHwuiProbeDecorationUniforms(
                                    original, edge, 2f, size);
                            if (motion) LauncherGlassSession.setHwuiProbeMotionQuality(original);
                            uniforms.bind(
                                    cached, params, profile, edge, 2f, size, motion, invariants);
                            require(
                                    original.values.equals(cached.values),
                                    "uniform mismatch at node " + i);
                        }
                        require(
                                cached.writes < original.writes / 4,
                                "unchanged material uploads were repeated");
                        int firstWrites = cached.writes;
                        uniforms.clear();
                        uniforms.bind(cached, params, profile, edge, 2f, 600f, false, invariants);
                        require(cached.writes > firstWrites, "clear retained shader state");
                        require(
                                original.values.equals(cached.values),
                                "clear changed material values");
                        CountingShader replacement = new CountingShader(source);
                        uniforms.bind(
                                replacement, params, profile, edge, 2f, 600f, false, invariants);
                        require(
                                original.values.equals(replacement.values),
                                "replacement program inherited another program's state");
                        result.putInt("fullUploads", original.writes);
                        result.putInt("cachedUploads", firstWrites);
                        result.putString(
                                "result",
                                "PASS: identical uniform values across nodes, sizes, motion exit"
                                    + " and clear");
                    } catch (Throwable error) {
                        failure[0] = error;
                    }
                });
        if (failure[0] != null)
            result.putString("failure", android.util.Log.getStackTraceString(failure[0]));
        owner.finish(failure[0] == null ? -1 : 1, result);
    }

    private static final class CountingShader extends RuntimeShader {
        final Map<String, String> values = new HashMap<>();
        int writes;

        CountingShader(String source) {
            super(source);
        }

        private void record(String name, float... values) {
            this.values.put(name, Arrays.toString(values));
            writes++;
        }

        @Override
        public void setFloatUniform(String n, float a) {
            record(n, a);
            super.setFloatUniform(n, a);
        }

        @Override
        public void setFloatUniform(String n, float a, float b) {
            record(n, a, b);
            super.setFloatUniform(n, a, b);
        }

        @Override
        public void setFloatUniform(String n, float a, float b, float c) {
            record(n, a, b, c);
            super.setFloatUniform(n, a, b, c);
        }

        @Override
        public void setFloatUniform(String n, float a, float b, float c, float d) {
            record(n, a, b, c, d);
            super.setFloatUniform(n, a, b, c, d);
        }

        @Override
        public void setIntUniform(String n, int value) {
            record(n, (float) value);
            super.setIntUniform(n, value);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
