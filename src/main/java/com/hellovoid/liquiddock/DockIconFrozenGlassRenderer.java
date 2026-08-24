package com.hellovoid.liquiddock;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.view.Surface;
import android.view.View;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;

/** One-shot frozen Dock icon glass renderer backed by the Dock renderer's already-consumed texture. */
final class DockIconFrozenGlassRenderer {
    private static final String TAG = "[DC][DockIconFrozenRender]";

    static final class FrozenIconSpec {
        final WeakReference<Miuix307PassBlurTextureView> ownerRef;
        final Object mappingGeneration;
        final LauncherGlassGeometry.Snapshot geometry;
        final PrismalParams prismalParams;
        final int sampleWidth;
        final int sampleHeight;
        final int bufferWidth;
        final int bufferHeight;

        FrozenIconSpec(
                Miuix307PassBlurTextureView owner,
                Object mappingGeneration,
                LauncherGlassGeometry.Snapshot geometry,
                PrismalParams prismalParams,
                int sampleWidth,
                int sampleHeight,
                int bufferWidth,
                int bufferHeight) {
            ownerRef = new WeakReference<>(owner);
            this.mappingGeneration = mappingGeneration;
            this.geometry = geometry;
            this.prismalParams = prismalParams;
            this.sampleWidth = sampleWidth;
            this.sampleHeight = sampleHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
        }
    }

    private DockIconFrozenGlassRenderer() {}

    static FrozenIconSpec capture(Miuix307PassBlurTextureView owner, View target) {
        if (owner == null || target == null || !owner.isAttachedToWindow()) return null;
        try {
            if (!getBoolean(owner, "hasConsumedFrame")) return null;
            Object mapping = HookUtil.getField(owner, "backdropSnapshot");
            if (mapping == null) return null;
            int sampleWidth = getInt(mapping, "sampleWidth");
            int sampleHeight = getInt(mapping, "sampleHeight");
            int visibleHeight = getInt(mapping, "visibleHeight");
            PrismalParams params = (PrismalParams) getFieldValue(mapping, "prismalParams");
            if (sampleWidth <= 0 || sampleHeight <= 0 || params == null) return null;

            float dockUvLeft = getFloat(mapping, "dockUvLeft");
            float dockUvBottom = getFloat(mapping, "dockUvBottom");
            float insetLeft = dockUvLeft * sampleWidth;
            float insetBottom = dockUvBottom * sampleHeight;
            float insetTop = Math.max(0f, sampleHeight - visibleHeight - insetBottom);

            Object compositorValue = HookUtil.getField(owner, "dockCompositor");
            if (!(compositorValue instanceof DockGlassCompositor)) return null;
            LauncherGlassGeometry.Snapshot geometry =
                    ((DockGlassCompositor) compositorValue).captureUiItem(
                            target, sampleWidth, sampleHeight,
                            insetLeft, insetTop, 1f, 1f);
            if (geometry == null || geometry.width <= 0f || geometry.height <= 0f) return null;
            int width = Math.max(1, (int) Math.ceil(geometry.width));
            int height = Math.max(1, (int) Math.ceil(geometry.height));
            return new FrozenIconSpec(
                    owner, mapping, geometry, params,
                    sampleWidth, sampleHeight, width, height);
        } catch (Throwable error) {
            MainHook.log(TAG + " capture unavailable: " + error);
            return null;
        }
    }

    static boolean render(FrozenIconSpec spec, Surface surface, Runnable ready, Runnable failed) {
        if (spec == null || surface == null || !surface.isValid()) return false;
        Miuix307PassBlurTextureView owner = spec.ownerRef.get();
        if (owner == null) return false;
        try {
            Object handlerValue = HookUtil.getField(owner, "renderHandler");
            if (!(handlerValue instanceof Handler)) return false;
            Handler renderHandler = (Handler) handlerValue;
            return renderHandler.post(() -> renderFrozenIconOnce(owner, spec, surface, ready, failed));
        } catch (Throwable error) {
            MainHook.log(TAG + " render queue unavailable: " + error);
            return false;
        }
    }

    private static void renderFrozenIconOnce(
            Miuix307PassBlurTextureView owner,
            FrozenIconSpec spec,
            Surface surface,
            Runnable ready,
            Runnable failed) {
        PrismalRenderer frozenProxyRenderer = null;
        EGLSurface frozenSurface = EGL14.EGL_NO_SURFACE;
        try {
            if (spec.ownerRef.get() != owner
                    || HookUtil.getField(owner, "backdropSnapshot") != spec.mappingGeneration
                    || !getBoolean(owner, "hasConsumedFrame")) {
                throw new IllegalStateException("frozen backdrop generation changed");
            }
            int rawTexture = HookUtil.getIntField(owner, "rawTexture");
            if (rawTexture == 0) throw new IllegalStateException("rawTexture unavailable");

            invokeExact(owner, "makeCurrent");
            EGLDisplay display = (EGLDisplay) HookUtil.getField(owner, "eglDisplay");
            EGLConfig config = (EGLConfig) HookUtil.getField(owner, "eglConfig");
            EGLContext context = (EGLContext) HookUtil.getField(owner, "eglContext");
            if (display == EGL14.EGL_NO_DISPLAY || config == null || context == EGL14.EGL_NO_CONTEXT) {
                throw new IllegalStateException("Dock EGL context unavailable");
            }

            frozenProxyRenderer = new PrismalRenderer();
            frozenProxyRenderer.prepareBackdrop(
                    rawTexture, spec.sampleWidth, spec.sampleHeight, spec.prismalParams);
            frozenProxyRenderer.beginGlassFrame();
            LauncherGlassGeometry.Snapshot item = spec.geometry;
            frozenProxyRenderer.drawGlass(
                    new PrismalGeometry(
                            spec.sampleWidth, spec.sampleHeight,
                            item.centerX, item.centerY,
                            item.width, item.height, item.cornerRadius),
                    spec.prismalParams);
            int glassTexture = frozenProxyRenderer.outputTexture();
            if (glassTexture == 0) throw new IllegalStateException("frozen glass texture unavailable");

            frozenSurface = EGL14.eglCreateWindowSurface(
                    display, config, surface, new int[]{EGL14.EGL_NONE}, 0);
            if (frozenSurface == null || frozenSurface == EGL14.EGL_NO_SURFACE) {
                throw new IllegalStateException("frozen EGL surface unavailable error=0x"
                        + Integer.toHexString(EGL14.eglGetError()));
            }
            if (!EGL14.eglMakeCurrent(display, frozenSurface, frozenSurface, context)) {
                throw new IllegalStateException("frozen eglMakeCurrent error=0x"
                        + Integer.toHexString(EGL14.eglGetError()));
            }

            GLES20.glViewport(0, 0, spec.bufferWidth, spec.bufferHeight);
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFuncSeparate(
                    GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                    GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            int compositeProgram = HookUtil.getIntField(owner, "compositeProgram");
            Object quadValue = HookUtil.getField(owner, "quadBuffer");
            if (!(quadValue instanceof FloatBuffer) || compositeProgram == 0) {
                throw new IllegalStateException("Dock composite pipeline unavailable");
            }
            FloatBuffer quad = (FloatBuffer) quadValue;
            bindQuad(compositeProgram, quad);
            GLES20.glUseProgram(compositeProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glassTexture);
            GLES20.glUniform1i(requireUniform(compositeProgram, "uTexture"), 0);

            float left = (item.centerX - item.width * 0.5f) / spec.sampleWidth;
            float bottom = 1f - (item.centerY + item.height * 0.5f) / spec.sampleHeight;
            float width = item.width / spec.sampleWidth;
            float height = item.height / spec.sampleHeight;
            GLES20.glUniform4f(
                    requireUniform(compositeProgram, "uCropRect"),
                    left, bottom, width, height);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            unbindQuad(compositeProgram);
            GLES20.glDisable(GLES20.GL_BLEND);

            if (!EGL14.eglSwapBuffers(display, frozenSurface)) {
                throw new IllegalStateException("frozen eglSwapBuffers error=0x"
                        + Integer.toHexString(EGL14.eglGetError()));
            }
            postMain(owner, ready);
        } catch (Throwable error) {
            MainHook.log(TAG + " one-shot render failed: " + error);
            postMain(owner, failed);
        } finally {
            try {
                EGLDisplay display = (EGLDisplay) HookUtil.getField(owner, "eglDisplay");
                if (display != null && display != EGL14.EGL_NO_DISPLAY
                        && frozenSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, frozenSurface);
                }
            } catch (Throwable ignored) {}
            try { invokeExact(owner, "makeCurrent"); } catch (Throwable ignored) {}
            if (frozenProxyRenderer != null) {
                try { frozenProxyRenderer.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void postMain(Miuix307PassBlurTextureView owner, Runnable action) {
        if (action == null) return;
        try {
            Object handler = HookUtil.getField(owner, "mainHandler");
            if (handler instanceof Handler && ((Handler) handler).post(action)) return;
        } catch (Throwable ignored) {}
        owner.post(action);
    }

    private static void bindQuad(int program, FloatBuffer quad) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position < 0 || uv < 0) throw new IllegalStateException("quad attribute unavailable");
        quad.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(
                position, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quad);
        quad.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(
                uv, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quad);
    }

    private static void unbindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position >= 0) GLES20.glDisableVertexAttribArray(position);
        if (uv >= 0) GLES20.glDisableVertexAttribArray(uv);
    }

    private static int requireUniform(int program, String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing uniform " + name);
        return location;
    }

    private static void invokeExact(Object target, String name) throws Exception {
        Method method = HookUtil.findMethodExact(target.getClass(), name, new Class<?>[0]);
        method.invoke(target);
    }

    private static Object getFieldValue(Object target, String name) throws Exception {
        Field field = HookUtil.findField(target.getClass(), name);
        return field.get(target);
    }

    private static int getInt(Object target, String name) throws Exception {
        Field field = HookUtil.findField(target.getClass(), name);
        return field.getInt(target);
    }

    private static float getFloat(Object target, String name) throws Exception {
        Field field = HookUtil.findField(target.getClass(), name);
        return field.getFloat(target);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        Field field = HookUtil.findField(target.getClass(), name);
        return field.getBoolean(target);
    }
}
