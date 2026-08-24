package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Surface;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * One animation-lifetime GPU buffer for a Dock icon glass material.
 *
 * The Dock renderer fills this buffer exactly once from its already-consumed backdrop. Afterwards
 * FloatingIcon updates only mutate the buffer layer's SurfaceControl transaction; no Prismal draw,
 * PassBlur request, or EGL swap belongs to the animation hot path.
 */
final class DockIconFrozenGlassLayer {
    private static final String TAG = "[DC][DockIconFrozen]";
    private static final String SURFACE_CONTROL_UTILS =
            "com.android.systemui.shared.recents.system.SurfaceControlUtils";
    private static final String SURFACE_COMPAT =
            "com.android.systemui.shared.recents.system.SurfaceCompat";
    private static final String TRANSACTION_COMPAT =
            "com.android.systemui.shared.recents.system.TransactionCompat";

    private final Object parentCompat;
    private final Object layerCompat;
    private final Object surfaceCompat;
    private final Surface surface;
    private final Class<?> transactionCompatClass;
    private final Constructor<?> transactionCompatConstructor;
    private final Method getTransaction;
    private final Method setMatrix;
    private final Method setWindowCrop;
    private final Method setRelativeLayer;
    private final Method setAlpha;
    private final Method show;
    private final Method hide;
    private final Method remove;
    private final Method apply;
    private final int sourceWidth;
    private final int sourceHeight;
    private final Rect crop;
    private final Matrix transform = new Matrix();

    private boolean ready;
    private boolean failed;
    private boolean released;
    private RectF latestRect;
    private Object latestOwner;

    static DockIconFrozenGlassLayer tryCreate(Object owner, View sessionAnchor, View target) {
        if (owner == null || sessionAnchor == null || target == null) return null;
        DockIconFrozenGlassRenderer.FrozenIconSpec spec =
                Miuix307ZeroCopyRenderer.captureFrozenIconSpec(target);
        if (spec == null || spec.bufferWidth <= 0 || spec.bufferHeight <= 0) return null;
        try {
            ClassLoader loader = owner.getClass().getClassLoader();
            Class<?> utils = Class.forName(SURFACE_CONTROL_UTILS, false, loader);
            View root = sessionAnchor.getRootView();
            Object parentCompat = HookUtil.invokeStatic(utils, "getSurfaceControlCompat", root);
            if (!isValidCompat(parentCompat)) return null;
            Object layerCompat = HookUtil.invokeStatic(
                    utils, "getBufferLayer",
                    "LiquidDock Frozen Dock Icon Glass",
                    spec.bufferWidth, spec.bufferHeight, parentCompat);
            if (!isValidCompat(layerCompat)) return null;

            Class<?> surfaceCompatClass = Class.forName(SURFACE_COMPAT, false, loader);
            Constructor<?> surfaceCtor = surfaceCompatClass.getDeclaredConstructor(
                    layerCompat.getClass());
            surfaceCtor.setAccessible(true);
            Object surfaceCompat = surfaceCtor.newInstance(layerCompat);
            Object surfaceValue = HookUtil.getField(surfaceCompat, "mSurface");
            if (!(surfaceValue instanceof Surface) || !((Surface) surfaceValue).isValid()) {
                HookUtil.invoke(surfaceCompat, "release");
                return null;
            }

            DockIconFrozenGlassLayer layer = new DockIconFrozenGlassLayer(
                    loader, parentCompat, layerCompat, surfaceCompat, (Surface) surfaceValue,
                    spec.bufferWidth, spec.bufferHeight);
            if (!Miuix307ZeroCopyRenderer.renderFrozenIcon(
                    spec, layer.surface, layer::onReady, layer::onFailed)) {
                layer.release();
                return null;
            }
            return layer;
        } catch (Throwable error) {
            MainHook.log(TAG + " create failed: " + error);
            return null;
        }
    }

    private DockIconFrozenGlassLayer(
            ClassLoader loader,
            Object parentCompat,
            Object layerCompat,
            Object surfaceCompat,
            Surface surface,
            int sourceWidth,
            int sourceHeight) throws Exception {
        this.parentCompat = parentCompat;
        this.layerCompat = layerCompat;
        this.surfaceCompat = surfaceCompat;
        this.surface = surface;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.crop = new Rect(0, 0, sourceWidth, sourceHeight);

        transactionCompatClass = Class.forName(TRANSACTION_COMPAT, false, loader);
        transactionCompatConstructor = transactionCompatClass.getDeclaredConstructor();
        transactionCompatConstructor.setAccessible(true);
        Class<?> compatClass = layerCompat.getClass();
        getTransaction = transactionCompatClass.getMethod("getTransaction");
        setMatrix = transactionCompatClass.getMethod(
                "setMatrix", compatClass, Matrix.class);
        setWindowCrop = transactionCompatClass.getMethod(
                "setWindowCrop", compatClass, Rect.class);
        setRelativeLayer = transactionCompatClass.getMethod(
                "setRelativeLayer", compatClass, compatClass, int.class);
        setAlpha = transactionCompatClass.getMethod("setAlpha", compatClass, float.class);
        show = transactionCompatClass.getMethod("show", compatClass);
        hide = transactionCompatClass.getMethod("hide", compatClass);
        remove = transactionCompatClass.getMethod("remove", compatClass);
        apply = transactionCompatClass.getMethod("apply");
    }

    boolean isFailed() {
        return failed || released;
    }

    void update(Object owner, RectF rect, Object vendorTransaction) {
        if (released || rect == null || rect.width() <= 0f || rect.height() <= 0f) return;
        latestOwner = owner;
        latestRect = new RectF(rect);
        if (!ready) return;
        if (vendorTransaction != null && transactionCompatClass.isInstance(vendorTransaction)) {
            applyTransform(vendorTransaction, owner, latestRect, true);
        } else {
            applyStandalone(owner, latestRect, true);
        }
    }

    void holdHidden() {
        if (released || !ready) return;
        try {
            Object transaction = transactionCompatConstructor.newInstance();
            hide.invoke(transaction, layerCompat);
            apply.invoke(transaction);
        } catch (Throwable error) {
            MainHook.log(TAG + " hide failed: " + error);
        }
    }

    private void onReady() {
        if (released) return;
        ready = true;
        try {
            Object transaction = transactionCompatConstructor.newInstance();
            getTransaction.invoke(transaction);
        } catch (Throwable error) {
            onFailed();
            return;
        }
        RectF rect = latestRect;
        if (rect != null) applyStandalone(latestOwner, rect, true);
    }

    private void onFailed() {
        if (released) return;
        failed = true;
        release();
    }

    private void applyStandalone(Object owner, RectF rect, boolean visible) {
        if (released || rect == null) return;
        try {
            Object transaction = transactionCompatConstructor.newInstance();
            applyTransform(transaction, owner, rect, visible);
            apply.invoke(transaction);
        } catch (Throwable error) {
            failed = true;
            MainHook.log(TAG + " standalone transaction failed: " + error);
        }
    }

    private void applyTransform(
            Object transaction, Object owner, RectF rect, boolean visible) {
        try {
            float sx = rect.width() / Math.max(1f, sourceWidth);
            float sy = rect.height() / Math.max(1f, sourceHeight);
            transform.reset();
            transform.setScale(sx, sy);
            transform.postTranslate(rect.left, rect.top);
            setMatrix.invoke(transaction, layerCompat, transform);
            setWindowCrop.invoke(transaction, layerCompat, crop);
            setAlpha.invoke(transaction, layerCompat, 1f);

            Object relative = null;
            if (owner != null) {
                try { relative = HookUtil.getField(owner, "mFloatingIconSurfaceControl"); }
                catch (Throwable ignored) {}
            }
            if (!isValidCompat(relative)) relative = parentCompat;
            if (isValidCompat(relative)) {
                setRelativeLayer.invoke(transaction, layerCompat, relative,
                        relative == parentCompat ? 2 : -1);
            }
            if (visible) show.invoke(transaction, layerCompat);
        } catch (Throwable error) {
            failed = true;
            MainHook.log(TAG + " transform failed: " + error);
        }
    }

    void release() {
        if (released) return;
        released = true;
        ready = false;
        latestRect = null;
        latestOwner = null;
        try {
            Object transaction = transactionCompatConstructor.newInstance();
            remove.invoke(transaction, layerCompat);
            apply.invoke(transaction);
        } catch (Throwable error) {
            MainHook.log(TAG + " remove failed: " + error);
        }
        try { HookUtil.invoke(surfaceCompat, "release"); } catch (Throwable ignored) {}
    }

    private static boolean isValidCompat(Object compat) {
        if (compat == null) return false;
        Object valid = HookUtil.invoke(compat, "isValid");
        return valid instanceof Boolean && (Boolean) valid;
    }
}
