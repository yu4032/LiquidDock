package com.hellovoid.liquiddock;

import android.app.Instrumentation;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import com.hellovoid.prismal.*;

import java.time.Duration;

/** Native HWUI pixel comparison of the complete motion material and its interior fast path. */
final class WorkspaceMotionBodyCheck {
    private static final int WIDTH = 960, HEIGHT = 720;

    static void run(Instrumentation owner) {
        Bundle result = new Bundle();
        Throwable[] failure = new Throwable[1];
        owner.runOnMainSync(
                () -> {
                    try {
                        int maximum = 0;
                        long sum = 0, count = 0;
                        for (boolean motion : new boolean[] {true, false}) {
                            String source =
                                    PrismalRenderer.specializeWorkspaceHwuiSource(
                                            LauncherGlassHwuiProbeView.agslSource(true, motion));
                            RuntimeShader original = new RuntimeShader(source);
                            RuntimeShader optimized =
                                    new RuntimeShader(LauncherGlassHwuiMotionBody.apply(source));
                            Bitmap image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                            for (int y = 0; y < 64; y++)
                                for (int x = 0; x < 64; x++) {
                                    image.setPixel(
                                            x,
                                            y,
                                            Color.rgb(
                                                    (x * 7 + y * 3) % 256,
                                                    (x * 3 + y * 5) % 256,
                                                    (x * 11 + y * 7) % 256));
                                }
                            BitmapShader input =
                                    new BitmapShader(
                                            image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                            input.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                            for (RuntimeShader shader : new RuntimeShader[] {original, optimized}) {
                                shader.setInputShader("u_backgroundTexture", input);
                                shader.setInputShader("u_blurredTexture", input);
                                shader.setFloatUniform("uProbeRawScale", 64f / WIDTH, 64f / HEIGHT);
                                shader.setFloatUniform(
                                        "uProbeBlurScale", 64f / WIDTH, 64f / HEIGHT);
                                shader.setFloatUniform(
                                        "uProbeWallpaperTransform", .94f, .94f, .03f, .03f);
                                shader.setFloatUniform("u_resolution", WIDTH, HEIGHT);
                            }
                            PrismalHighlightProfile profile =
                                    PrismalHighlightProfile.ALL_ENABLED
                                            .withOs4EdgeReplacingLegacyEdge();
                            PrismalEdgeDecoration decoration =
                                    new PrismalEdgeDecoration(
                                            2, .65f, .6f, 14, 4, 0, 0, 0, .35f, .4f);
                            float[] invariants = new float[10];
                            try (Target target = new Target()) {
                                for (boolean continuous : new boolean[] {false, true}) {
                                    for (float size : new float[] {166, 300, 640}) {
                                        for (int variant = 0; variant < 4; variant++) {
                                            PrismalParams.Builder builder = PrismalParams.builder();
                                            builder.continuousCorners = continuous;
                                            builder.ior = 1.4f;
                                            builder.liquidDome = .7f;
                                            builder.shadowA = variant == 0 ? 0f : .14f;
                                            builder.baseColorA = variant == 1 ? .25f : 0f;
                                            builder.pressProgress = variant == 2 ? .7f : 0f;
                                            builder.sminSmoothingPx = 2f;
                                            builder.tintA = .2f;
                                            PrismalParams params = builder.build();
                                            float w = size, h = size > 300 ? 520f : size;
                                            float left = (WIDTH - w) / 2f, top = (HEIGHT - h) / 2f;
                                            for (RuntimeShader shader :
                                                    new RuntimeShader[] {original, optimized}) {
                                                shader.setFloatUniform("u_glassSize", w, h);
                                                shader.setFloatUniform(
                                                        "u_cornerRadii", 38f, 38f, 38f, 38f);
                                                shader.setFloatUniform(
                                                        "uProbeBounds",
                                                        left,
                                                        top,
                                                        left + w,
                                                        top + h);
                                                LauncherGlassSession.setHwuiProbeMaterialUniforms(
                                                        shader,
                                                        params,
                                                        profile,
                                                        Math.min(w, h),
                                                        invariants);
                                                LauncherGlassSession.setHwuiProbeDecorationUniforms(
                                                        shader, decoration, 2f, Math.min(w, h));
                                                LauncherGlassSession.setHwuiProbeMotionQuality(
                                                        shader);
                                                if (variant == 3)
                                                    shader.setIntUniform("u_showNormals", 1);
                                            }
                                            int[] a = target.pixels(original),
                                                    b = target.pixels(optimized);
                                            for (int i = 0; i < a.length; i++)
                                                for (int shift = 0; shift <= 24; shift += 8) {
                                                    int delta =
                                                            Math.abs(
                                                                    ((a[i] >>> shift) & 255)
                                                                            - ((b[i] >>> shift)
                                                                                    & 255));
                                                    maximum = Math.max(maximum, delta);
                                                    sum += delta;
                                                    count++;
                                                }
                                        }
                                    }
                                }
                            }
                            image.recycle();
                        }
                        result.putInt("maximumChannelDifference", maximum);
                        result.putDouble("meanChannelDifference", (double) sum / count);
                        if (maximum > 2 || (double) sum / count > .02)
                            throw new AssertionError("motion material changed: max=" + maximum);
                        result.putString(
                                "result",
                                "PASS: motion interior matches full material across shapes, sizes,"
                                        + " tint, shadow, press and normal debug");
                    } catch (Throwable error) {
                        failure[0] = error;
                    }
                });
        if (failure[0] != null)
            result.putString("failure", android.util.Log.getStackTraceString(failure[0]));
        owner.finish(failure[0] == null ? -1 : 1, result);
    }

    static final class Target implements AutoCloseable {
        final ImageReader reader =
                ImageReader.newInstance(
                        WIDTH,
                        HEIGHT,
                        PixelFormat.RGBA_8888,
                        3,
                        HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
                                | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        final HardwareRenderer renderer = new HardwareRenderer();
        final RenderNode node = new RenderNode("Workspace motion comparison");

        Target() {
            node.setPosition(0, 0, WIDTH, HEIGHT);
            renderer.setSurface(reader.getSurface());
            renderer.setContentRoot(node);
        }

        int[] pixels(RuntimeShader shader) throws Exception {
            RecordingCanvas canvas = node.beginRecording(WIDTH, HEIGHT);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            Paint paint = new Paint();
            paint.setShader(shader);
            canvas.drawRect(0, 0, WIDTH, HEIGHT, paint);
            node.endRecording();
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            try (Image frame = reader.acquireNextImage()) {
                if (frame == null) throw new AssertionError("HWUI did not present its image");
                try (SyncFence fence = frame.getFence()) {
                    if (!fence.await(Duration.ofSeconds(3)))
                        throw new AssertionError("HWUI fence timed out");
                }
                try (HardwareBuffer buffer = frame.getHardwareBuffer()) {
                    Bitmap hardware =
                            Bitmap.wrapHardwareBuffer(
                                    buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                    if (hardware == null) throw new AssertionError("HWUI image unavailable");
                    Bitmap cpu = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    int[] pixels = new int[WIDTH * HEIGHT];
                    cpu.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
                    cpu.recycle();
                    hardware.recycle();
                    return pixels;
                }
            }
        }

        @Override
        public void close() {
            renderer.destroy();
            reader.close();
            node.discardDisplayList();
        }
    }
}
