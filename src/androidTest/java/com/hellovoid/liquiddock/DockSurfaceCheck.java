package com.hellovoid.liquiddock;

import android.opengl.GLES20;
import android.os.Bundle;

import com.hellovoid.prismal.*;

import java.nio.*;

/** Pixel and material-frame comparisons at the connected device's observed Dock geometry. */
final class DockSurfaceCheck {
    private static final int LW = 2498, LH = 1110, SW = 1578, SH = 328;
    private static int cropLeft = 460, cropBottom = 391;
    private static final FloatBuffer QUAD =
            ByteBuffer.allocateDirect(64)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(new float[] {-1, -1, 0, 0, 1, -1, 1, 0, -1, 1, 0, 1, 1, 1, 1, 1});
    private static int copyPosition, copyUv, copyTexture, copyCrop;
    private static float clearGuard;
    private static float[] previousBounds;

    static void check(int source, int physicalWidth, int physicalHeight, Bundle result) {
        previousBounds = null;
        int copy =
                DockBackdropCheck.program(
                        Miuix307PassBlurShaders.QUAD_VERTEX,
                        Miuix307PrismalCompositeShaders.FRAGMENT);
        copyPosition = GLES20.glGetAttribLocation(copy, "aPosition");
        copyUv = GLES20.glGetAttribLocation(copy, "aUv");
        copyTexture = GLES20.glGetUniformLocation(copy, "uTexture");
        copyCrop = GLES20.glGetUniformLocation(copy, "uCropRect");
        PrismalParams params = PrismalParams.builder().build();
        int maxDelta = 0;
        long sum = 0, count = 0;
        String worst = "";
        try (PrismalRenderer texture = new PrismalRenderer();
                PrismalRenderer compact = new PrismalRenderer()) {
            texture.prepareBackdrop(source, physicalWidth, physicalHeight, LW, LH, params);
            compact.prepareBackdrop(source, physicalWidth, physicalHeight, LW, LH, params);
            PrismalEdgeDecoration decorated =
                    new PrismalEdgeDecoration(2, .65f, .6f, 16, 4, 0, 0, 0, .35f, .4f);
            for (int parity = 0; parity < 4; parity++) {
                cropLeft = 460 + (parity & 1);
                cropBottom = 390 + ((parity >> 1) & 1);
                for (PrismalEdgeDecoration edge :
                        new PrismalEdgeDecoration[] {PrismalEdgeDecoration.NONE, decorated}) {
                    texture.setEdgeDecoration(edge, 1.5f);
                    compact.setEdgeDecoration(edge, 1.5f);
                    clearGuard =
                            2
                                    + Math.max(
                                            edge.extraGuardPx(1.5f, 244),
                                            edge.extraGuardPx(1.5f, 160));
                    for (int move : new int[] {0, -170, 180})
                        for (int coverage = 0; coverage < 3; coverage++) {
                            PrismalGeometry[] scene = scene(move);
                            int[] clip = clip(coverage);
                            frame(texture, params, scene, copy, false, clip);
                            ByteBuffer a = read();
                            frame(compact, params, scene, copy, true, clip);
                            ByteBuffer b = read();
                            for (int i = 0; i < SW * SH * 4; i++) {
                                int delta = Math.abs((a.get(i) & 255) - (b.get(i) & 255));
                                if (delta > maxDelta) {
                                    maxDelta = delta;
                                    worst =
                                            "edge="
                                                    + edge.hasShadow()
                                                    + " move="
                                                    + move
                                                    + " coverage="
                                                    + coverage
                                                    + " x="
                                                    + (i / 4 % SW)
                                                    + " y="
                                                    + (i / 4 / SW)
                                                    + " channel="
                                                    + (i % 4)
                                                    + " old="
                                                    + (a.get(i) & 255)
                                                    + " compact="
                                                    + (b.get(i) & 255);
                                }
                                sum += delta;
                                count++;
                            }
                        }
                }
            }
            cropLeft = 460;
            cropBottom = 391;
            frame(compact, params, scene(0), copy, true, clip(0));
            result.putInt("outputMaximumChannelDelta", maxDelta);
            result.putDouble("outputMeanChannelDelta", (double) sum / count);
            result.putString("outputWorst", worst);
            require(maxDelta <= 2, "compact crop max channel difference=" + maxDelta);
            require(
                    (double) sum / count < .05,
                    "compact crop mean difference=" + (double) sum / count);
            java.lang.reflect.Field output =
                    PrismalRenderer.class.getDeclaredField("outputTexture");
            output.setAccessible(true);
            require(
                    output.getInt(compact) == 0,
                    "compact frames allocated a full logical output texture");
            require(output.getInt(texture) != 0, "baseline did not allocate its output texture");
            java.lang.reflect.Field compactWidth =
                    PrismalRenderer.class.getDeclaredField("reducedOutputWidth");
            java.lang.reflect.Field compactHeight =
                    PrismalRenderer.class.getDeclaredField("reducedOutputHeight");
            compactWidth.setAccessible(true);
            compactHeight.setAccessible(true);
            require(compactWidth.getInt(compact) == SW + (cropLeft & 1), "compact width");
            require(
                    compactHeight.getInt(compact) == SH + (cropBottom & 1),
                    "compact height/derivative padding");
            result.putLong("originalOutputTextureBytes", (long) LW * LH * 4);
            result.putLong(
                    "compactOutputTextureBytes",
                    (long) compactWidth.getInt(compact) * compactHeight.getInt(compact) * 4);

            PrismalGeometry[] scene = scene(0);
            int[] full = clip(0);
            for (boolean shadow : new boolean[] {false, true}) {
                texture.setEdgeDecoration(shadow ? decorated : PrismalEdgeDecoration.NONE, 1.5f);
                compact.setEdgeDecoration(shadow ? decorated : PrismalEdgeDecoration.NONE, 1.5f);
                PrismalEdgeDecoration edge = shadow ? decorated : PrismalEdgeDecoration.NONE;
                clearGuard =
                        2 + Math.max(edge.extraGuardPx(1.5f, 244), edge.extraGuardPx(1.5f, 160));
                for (int i = 0; i < 8; i++) {
                    frame(texture, params, scene, copy, false, full);
                    frame(compact, params, scene, copy, true, full);
                }
                double oldNs = 0, directNs = 0;
                for (int block = 0; block < 8; block++) {
                    if ((block & 1) == 0) {
                        oldNs += time(texture, params, scene, copy, false, full);
                        directNs += time(compact, params, scene, copy, true, full);
                    } else {
                        directNs += time(compact, params, scene, copy, true, full);
                        oldNs += time(texture, params, scene, copy, false, full);
                    }
                }
                String name = shadow ? "Decorated" : "Baseline";
                result.putDouble("texture" + name + "FrameMs", oldNs / 8 / 30 / 1e6);
                result.putDouble("compact" + name + "FrameMs", directNs / 8 / 30 / 1e6);
            }
            // Compact -> full -> compact retains both targets' independent coordinates.
            frame(texture, params, scene, copy, true, full);
            frame(texture, params, scene, copy, false, full);
            ByteBuffer restored = read();
            frame(compact, params, scene, copy, true, full);
            ByteBuffer expected = read();
            for (int i = 0; i < SW * SH * 4; i++)
                require(
                        Math.abs((restored.get(i) & 255) - (expected.get(i) & 255)) <= 2,
                        "target switch changed crop");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        } finally {
            GLES20.glDeleteProgram(copy);
        }
    }

    private static PrismalGeometry[] scene(int move) {
        PrismalGeometry[] nodes = new PrismalGeometry[11];
        float cy = LH - cropBottom - SH * .5f;
        nodes[0] = new PrismalGeometry(LW, LH, cropLeft + SW * .5f + move, cy, 1494, 244, 90);
        for (int i = 1; i < nodes.length; i++)
            nodes[i] =
                    new PrismalGeometry(
                            LW, LH, cropLeft + 100 + (i - 1) * 150 + move, cy, 160, 160, 45);
        return nodes;
    }

    private static int[] clip(int mode) {
        return mode == 0
                ? new int[] {0, 0, SW, SH}
                : mode == 1 ? new int[] {120, 30, SW - 100, SH - 20} : new int[4];
    }

    private static void frame(
            PrismalRenderer renderer,
            PrismalParams p,
            PrismalGeometry[] scene,
            int copy,
            boolean compact,
            int[] clip) {
        float l = Float.POSITIVE_INFINITY, t = l, r = Float.NEGATIVE_INFINITY, b = r;
        for (PrismalGeometry geometry : scene) {
            l = Math.min(l, geometry.left() - clearGuard);
            t = Math.min(t, geometry.top() - clearGuard);
            r = Math.max(r, geometry.right() + clearGuard);
            b = Math.max(b, geometry.bottom() + clearGuard);
        }
        if (compact) renderer.beginGlassFrameCropped(SW, SH, cropLeft, cropBottom);
        else {
            float oldL = l, oldT = t, oldR = r, oldB = b;
            if (previousBounds != null) {
                oldL = Math.min(oldL, previousBounds[0]);
                oldT = Math.min(oldT, previousBounds[1]);
                oldR = Math.max(oldR, previousBounds[2]);
                oldB = Math.max(oldB, previousBounds[3]);
            } else previousBounds = new float[4];
            renderer.beginGlassFrameRegion(oldL, oldT, oldR, oldB);
            previousBounds[0] = l;
            previousBounds[1] = t;
            previousBounds[2] = r;
            previousBounds[3] = b;
        }
        for (PrismalGeometry geometry : scene)
            renderer.drawGlass(geometry, p, PrismalHighlightProfile.ALL_ENABLED);
        copy(renderer.outputTexture(), copy, clip, compact);
    }

    private static long time(
            PrismalRenderer renderer,
            PrismalParams p,
            PrismalGeometry[] scene,
            int copy,
            boolean compact,
            int[] clip) {
        GLES20.glFinish();
        long start = System.nanoTime();
        for (int i = 0; i < 30; i++) frame(renderer, p, scene, copy, compact, clip);
        GLES20.glFinish();
        return System.nanoTime() - start;
    }

    private static void copy(int texture, int program, int[] clip, boolean compact) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, SW, SH);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(clip[0], clip[1], clip[2] - clip[0], clip[3] - clip[1]);
        GLES20.glUseProgram(program);
        int pos = copyPosition, uv = copyUv;
        QUAD.position(0);
        GLES20.glEnableVertexAttribArray(pos);
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, QUAD);
        QUAD.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, QUAD);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(copyTexture, 0);
        if (compact)
            GLES20.glUniform4f(
                    copyCrop,
                    (cropLeft & 1) / (float) (SW + (cropLeft & 1)),
                    (cropBottom & 1) / (float) (SH + (cropBottom & 1)),
                    SW / (float) (SW + (cropLeft & 1)),
                    SH / (float) (SH + (cropBottom & 1)));
        else
            GLES20.glUniform4f(
                    copyCrop,
                    cropLeft / (float) LW,
                    cropBottom / (float) LH,
                    SW / (float) LW,
                    SH / (float) LH);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(pos);
        GLES20.glDisableVertexAttribArray(uv);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    private static ByteBuffer read() {
        ByteBuffer data = ByteBuffer.allocateDirect(SW * SH * 4);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glReadPixels(0, 0, SW, SH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
        return data;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
