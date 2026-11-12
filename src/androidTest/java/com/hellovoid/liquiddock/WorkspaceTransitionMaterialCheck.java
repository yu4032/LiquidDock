package com.hellovoid.liquiddock;

import android.app.Instrumentation;
import android.graphics.*;
import android.os.Bundle;

import com.hellovoid.prismal.*;

/** Check full-quality equivalence and alpha-preserving material interpolation in actual HWUI. */
final class WorkspaceTransitionMaterialCheck {
    static void run(Instrumentation owner) {
        Bundle result = new Bundle();
        Throwable[] failure = new Throwable[1];
        owner.runOnMainSync(
                () -> {
                    try {
                        int maximum = 0;
                        Bitmap image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                        for (int y = 0; y < 32; y++)
                            for (int x = 0; x < 32; x++) {
                                image.setPixel(
                                        x, y, Color.rgb(x * 8, y * 8, ((x / 4 + y / 4) % 2) * 220));
                            }
                        BitmapShader input =
                                new BitmapShader(
                                        image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                        PrismalParams.Builder builder = PrismalParams.builder();
                        builder.continuousCorners = true;
                        builder.transmittance = .5f;
                        PrismalParams params = builder.build();
                        PrismalEdgeDecoration edge =
                                new PrismalEdgeDecoration(2, .6f, .5f, 12, 3, 0, 0, 0, .4f, .3f);
                        float[] invariants = new float[10];
                        try (WorkspaceMotionBodyCheck.Target target =
                                new WorkspaceMotionBodyCheck.Target()) {
                            for (boolean decorated : new boolean[] {false, true}) {
                                String source =
                                        PrismalRenderer.specializeWorkspaceHwuiSource(
                                                LauncherGlassHwuiProbeView.agslSource(
                                                        decorated, false));
                                RuntimeShader original = new RuntimeShader(source);
                                RuntimeShader transition =
                                        new RuntimeShader(
                                                LauncherGlassHwuiTransitionShader.apply(
                                                        decorated
                                                                ? LauncherGlassHwuiMotionBody.apply(
                                                                        source)
                                                                : source,
                                                        decorated));
                                for (RuntimeShader shader :
                                        new RuntimeShader[] {original, transition}) {
                                    shader.setInputShader("u_backgroundTexture", input);
                                    shader.setInputShader("u_blurredTexture", input);
                                    shader.setFloatUniform("uProbeRawScale", 32f / 960, 32f / 720);
                                    shader.setFloatUniform("uProbeBlurScale", 32f / 960, 32f / 720);
                                    shader.setFloatUniform(
                                            "uProbeWallpaperTransform", 1f, 1f, 0f, 0f);
                                    shader.setFloatUniform("u_resolution", 960f, 720f);
                                    shader.setFloatUniform("u_glassSize", 640f, 520f);
                                    shader.setFloatUniform("u_cornerRadii", 38f, 38f, 38f, 38f);
                                    shader.setFloatUniform("uProbeBounds", 160f, 100f, 800f, 620f);
                                    LauncherGlassSession.setHwuiProbeMaterialUniforms(
                                            shader,
                                            params,
                                            PrismalHighlightProfile.ALL_ENABLED
                                                    .withOs4EdgeReplacingLegacyEdge(),
                                            520f,
                                            invariants);
                                    if (decorated)
                                        LauncherGlassSession.setHwuiProbeDecorationUniforms(
                                                shader, edge, 2f, 520f);
                                }
                                transition.setFloatUniform("u_workspaceDetail", 1f);
                                int[] a = target.pixels(original), b = target.pixels(transition);
                                for (int i = 0; i < a.length; i++)
                                    for (int shift = 0; shift <= 24; shift += 8)
                                        maximum =
                                                Math.max(
                                                        maximum,
                                                        Math.abs(
                                                                ((a[i] >>> shift) & 255)
                                                                        - ((b[i] >>> shift)
                                                                                & 255)));
                                for (float detail : new float[] {0f, .25f, .5f, .75f, 1f}) {
                                    transition.setFloatUniform("u_workspaceDetail", detail);
                                    int center = target.pixels(transition)[360 * 960 + 480];
                                    if (Math.abs(Color.alpha(center) - 128) > 1)
                                        throw new AssertionError(
                                                "crossfade changed body opacity: "
                                                        + detail
                                                        + " alpha="
                                                        + Color.alpha(center));
                                }
                                // Disable only the effects allowed to lose detail. Refraction,
                                // silhouette,
                                // light and tone must remain identical against a structured
                                // background.
                                transition.setFloatUniform("u_chromaticAberration", 0f);
                                transition.setFloatUniform("u_causticIntensity", 0f);
                                if (decorated) transition.setFloatUniform("u_backlitStrength", 0f);
                                transition.setFloatUniform("u_workspaceDetail", 1f);
                                int[] complete = target.pixels(transition);
                                transition.setFloatUniform("u_workspaceDetail", 0f);
                                int[] reduced = target.pixels(transition);
                                int opticalDifference = 0;
                                for (int i = 0; i < complete.length; i++)
                                    for (int shift = 0; shift <= 24; shift += 8) {
                                        opticalDifference =
                                                Math.max(
                                                        opticalDifference,
                                                        Math.abs(
                                                                ((complete[i] >>> shift) & 255)
                                                                        - ((reduced[i] >>> shift)
                                                                                & 255)));
                                    }
                                if (opticalDifference > 2)
                                    throw new AssertionError(
                                            "quality changed refraction/tone: decorated="
                                                    + decorated
                                                    + " difference="
                                                    + opticalDifference);
                            }
                        }
                        image.recycle();
                        result.putInt("fullQualityMaximumDifference", maximum);
                        if (maximum > 2)
                            throw new AssertionError("full material changed: " + maximum);
                        result.putString(
                                "result",
                                "PASS: complete material restored and interpolation preserves"
                                        + " opacity");
                    } catch (Throwable error) {
                        failure[0] = error;
                    }
                });
        if (failure[0] != null)
            result.putString("failure", android.util.Log.getStackTraceString(failure[0]));
        owner.finish(failure[0] == null ? -1 : 1, result);
    }
}
