package com.hellovoid.liquiddock;

import android.app.Instrumentation;
import android.opengl.*;
import android.os.Bundle;

import com.hellovoid.prismal.*;

import java.nio.*;

/** Compares the original two-pass adapter with direct producer rendering on the device GPU. */
final class DockBackdropCheck {
    private static final int W = 1535, H = 257;
    private final FloatBuffer quad =
            ByteBuffer.allocateDirect(64)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(new float[] {-1, -1, 0, 0, 1, -1, 1, 0, -1, 1, 0, 1, 1, 1, 1, 1});
    private int originalProgram, fusedProgram, input, intermediate, intermediateFbo;
    private final float[] matrix = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    private int rotation;
    private boolean partial;
    private final double[] totalDelta = new double[3];
    private final long[] sampleCount = new long[3];
    private final int[] maximumDelta = new int[3];

    static void run(Instrumentation owner) {
        Bundle result = new Bundle();
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        try {
            require(EGL14.eglInitialize(display, new int[2], 0, new int[2], 0), "initialize");
            EGLConfig[] configs = new EGLConfig[1];
            require(
                    EGL14.eglChooseConfig(
                            display,
                            new int[] {
                                EGL14.EGL_RENDERABLE_TYPE,
                                4,
                                EGL14.EGL_SURFACE_TYPE,
                                EGL14.EGL_PBUFFER_BIT,
                                EGL14.EGL_RED_SIZE,
                                8,
                                EGL14.EGL_GREEN_SIZE,
                                8,
                                EGL14.EGL_BLUE_SIZE,
                                8,
                                EGL14.EGL_ALPHA_SIZE,
                                8,
                                EGL14.EGL_NONE
                            },
                            0,
                            configs,
                            0,
                            1,
                            new int[1],
                            0),
                    "config");
            EGLContext context =
                    EGL14.eglCreateContext(
                            display,
                            configs[0],
                            EGL14.EGL_NO_CONTEXT,
                            new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE},
                            0);
            EGLSurface surface =
                    EGL14.eglCreatePbufferSurface(
                            display,
                            configs[0],
                            new int[] {
                                EGL14.EGL_WIDTH, 1578, EGL14.EGL_HEIGHT, 328, EGL14.EGL_NONE
                            },
                            0);
            require(EGL14.eglMakeCurrent(display, surface, surface, context), "current");
            try {
                new DockBackdropCheck().check(result);
            } finally {
                EGL14.eglMakeCurrent(
                        display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(display, surface);
                EGL14.eglDestroyContext(display, context);
            }
            result.putString(
                    "result",
                    "PASS: direct backdrop matches source, blur and glass across"
                        + " rotations/crops/partial coverage");
            owner.finish(-1, result);
        } catch (Throwable error) {
            result.putString("failure", android.util.Log.getStackTraceString(error));
            owner.finish(1, result);
        } finally {
            EGL14.eglTerminate(display);
        }
    }

    private void check(Bundle result) {
        // Only the texture target changes for this synthetic source; all actual producer mapping
        // GLSL (rotation, signed crop compensation and mirrored guard) remains under test.
        String fragment =
                Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT
                        .replace("#extension GL_OES_EGL_image_external : require", "")
                        .replace("samplerExternalOES", "sampler2D");
        originalProgram = program(Miuix307PassBlurShaders.QUAD_VERTEX, fragment);
        fusedProgram = program(Miuix307PassBlurShaders.PRISMAL_SOURCE_VERTEX, fragment);
        input = texture();
        intermediate = texture();
        intermediateFbo = framebuffer(intermediate);
        ByteBuffer pixels = ByteBuffer.allocateDirect(W * H * 4);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                pixels.put((byte) (x * 255 / (W - 1)))
                        .put((byte) (y * 255 / (H - 1)))
                        .put((byte) (((x / 19 + y / 13) % 2) * 255))
                        .put((byte) 255);
            }
        pixels.flip();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, input);
        GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                W,
                H,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                pixels);
        PrismalParams params = PrismalParams.builder().build();
        try (PrismalRenderer old = new PrismalRenderer();
                PrismalRenderer fused = new PrismalRenderer()) {
            for (int r = 0; r < 4; r++)
                for (int crop = 0; crop < 3; crop++) {
                    rotation = r;
                    partial = crop == 2;
                    matrix[0] = crop == 0 ? 1 : -0.93f;
                    matrix[5] = crop == 0 ? 1 : 0.87f;
                    matrix[12] = crop == 0 ? 0 : 0.96f;
                    matrix[13] = crop == 0 ? 0 : 0.06f;
                    oldBackdrop(old, params);
                    fused.prepareBackdropRendered(W, H, W, H, params, () -> draw(fusedProgram));
                    compare(
                            old.preparedSourceTexture(),
                            fused.preparedSourceTexture(),
                            W,
                            H,
                            0,
                            "source");
                    compare(
                            old.preparedBlurTexture(),
                            fused.preparedBlurTexture(),
                            W / 2,
                            H / 2,
                            1,
                            "blur");
                    PrismalGeometry geometry =
                            new PrismalGeometry(W, H, W * .5f, H * .5f, W * .8f, H * .7f, 40);
                    old.beginGlassFrame();
                    old.drawGlass(geometry, params, PrismalHighlightProfile.ALL_ENABLED);
                    fused.beginGlassFrame();
                    fused.drawGlass(geometry, params, PrismalHighlightProfile.ALL_ENABLED);
                    compare(old.outputTexture(), fused.outputTexture(), W, H, 2, "glass");
                }
            for (int stage = 0; stage < 3; stage++) {
                result.putInt("maximumChannelDelta" + stage, maximumDelta[stage]);
                result.putDouble(
                        "meanChannelDelta" + stage, totalDelta[stage] / sampleCount[stage]);
            }
            // Alternate order to reduce thermal/clock bias. Each block includes GPU completion;
            // this is background preparation cost, not whole-Launcher frame timing.
            double oldNs = 0, fusedNs = 0;
            for (int block = 0; block < 8; block++) {
                if ((block & 1) == 0) {
                    oldNs += time(old, params, false);
                    fusedNs += time(fused, params, true);
                } else {
                    fusedNs += time(fused, params, true);
                    oldNs += time(old, params, false);
                }
            }
            result.putDouble("originalPrepareMs", oldNs / 8 / 40 / 1e6);
            result.putDouble("directPrepareMs", fusedNs / 8 / 40 / 1e6);
        }
        DockSurfaceCheck.check(intermediate, W, H, result);
        require(GLES20.glGetError() == GLES20.GL_NO_ERROR, "GL error");
    }

    private long time(PrismalRenderer renderer, PrismalParams params, boolean fused) {
        GLES20.glFinish();
        long start = System.nanoTime();
        for (int i = 0; i < 40; i++) {
            if (fused)
                renderer.prepareBackdropRendered(W, H, W, H, params, () -> draw(fusedProgram));
            else oldBackdrop(renderer, params);
        }
        GLES20.glFinish();
        return System.nanoTime() - start;
    }

    private void oldBackdrop(PrismalRenderer renderer, PrismalParams params) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, intermediateFbo);
        GLES20.glViewport(0, 0, W, H);
        draw(originalProgram);
        renderer.prepareBackdrop(intermediate, W, H, W, H, params);
    }

    private void draw(int program) {
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glUseProgram(program);
        int pos = GLES20.glGetAttribLocation(program, "aPosition"),
                uv = GLES20.glGetAttribLocation(program, "aUv");
        quad.position(0);
        GLES20.glEnableVertexAttribArray(pos);
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad);
        quad.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, input);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0);
        GLES20.glUniformMatrix4fv(
                GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, matrix, 0);
        GLES20.glUniform4f(
                GLES20.glGetUniformLocation(program, "uBackdropRect"), -.03f, .06f, 1.08f, .9f);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uConfigRot"), rotation);
        GLES20.glUniform4f(
                GLES20.glGetUniformLocation(program, "uValidDockRect"),
                partial ? .2f : 0,
                partial ? .1f : 0,
                1,
                partial ? .8f : 1);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(pos);
        GLES20.glDisableVertexAttribArray(uv);
    }

    private void compare(int a, int b, int w, int h, int stage, String label) {
        ByteBuffer pa = read(a, w, h), pb = read(b, w, h);
        int max = 0;
        long sum = 0;
        for (int i = 0; i < w * h * 4; i++) {
            int delta = Math.abs((pa.get(i) & 255) - (pb.get(i) & 255));
            max = Math.max(max, delta);
            sum += delta;
        }
        maximumDelta[stage] = Math.max(maximumDelta[stage], max);
        totalDelta[stage] += sum;
        sampleCount[stage] += (long) w * h * 4;
        // Removing the intermediate bilinear RGBA8 copy changes rounding by at most one
        // source level. The unchanged material can amplify that to two output levels.
        require(max <= (stage == 2 ? 2 : 1), label + " max channel difference=" + max);
        require(
                (double) sum / (w * h * 4) < .05,
                label + " mean channel difference=" + (double) sum / (w * h * 4));
    }

    private static ByteBuffer read(int texture, int w, int h) {
        int fbo = framebuffer(texture);
        ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4);
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
        GLES20.glDeleteFramebuffers(1, new int[] {fbo}, 0);
        return pixels;
    }

    private static int texture() {
        int[] id = new int[1];
        GLES20.glGenTextures(1, id, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0]);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                W,
                H,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null);
        return id[0];
    }

    private static int framebuffer(int texture) {
        int[] id = new int[1];
        GLES20.glGenFramebuffers(1, id, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, id[0]);
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                texture,
                0);
        require(
                GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
                        == GLES20.GL_FRAMEBUFFER_COMPLETE,
                "FBO");
        return id[0];
    }

    static int program(String vertex, String fragment) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vertex));
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fragment));
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        require(ok[0] != 0, GLES20.glGetProgramInfoLog(p));
        return p;
    }

    private static int shader(int type, String source) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, source);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        require(ok[0] != 0, GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
