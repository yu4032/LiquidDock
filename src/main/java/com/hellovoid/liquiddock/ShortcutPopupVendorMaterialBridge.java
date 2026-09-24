package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Claims only the visible MIUIX PopupView element material while leaving the popup hierarchy,
 * animation and parent background-only blur under vendor authority.
 *
 * <p>HyperOS PopupView.applyMaterialEffects() deliberately splits the material:
 * mMenuLayer owns background-only blur, while mContentView owns ViewBlur/ColorBlend/BloomStroke.
 * LiquidDock keeps the former as the real behind-menu substrate and removes only the latter so
 * the HWUI backdrop RenderEffect is the sole visible glass material.</p>
 */
final class ShortcutPopupVendorMaterialBridge {
    private static final String TAG = "[DC][ShortcutPopupMaterial]";

    private static final Method SET_MI_VIEW_BLUR_MODE;
    private static final Method SET_MI_BACKGROUND_BLUR_MODE;
    private static final Method CLEAR_MI_BACKGROUND_BLEND_COLOR;
    private static final Method SET_MI_BLOOM_STROKE;
    private static final boolean AVAILABLE;

    static {
        Method viewBlur = null;
        Method backgroundBlur = null;
        Method clearBlend = null;
        Method bloom = null;
        boolean available = false;
        try {
            viewBlur = HookUtil.findMethodExact(
                    View.class, "setMiViewBlurMode", new Class<?>[]{int.class});
            backgroundBlur = HookUtil.findMethodExact(
                    View.class, "setMiBackgroundBlurMode", new Class<?>[]{int.class});
            clearBlend = HookUtil.findMethodExact(
                    View.class, "clearMiBackgroundBlendColor", new Class<?>[0]);
            bloom = HookUtil.findMethodExact(
                    View.class, "setMiBloomStroke", new Class<?>[]{float[].class});
            available = true;
        } catch (Throwable ignored) {
            // Fail closed. A visible vendor material is better than a half-owned popup.
        }
        SET_MI_VIEW_BLUR_MODE = viewBlur;
        SET_MI_BACKGROUND_BLUR_MODE = backgroundBlur;
        CLEAR_MI_BACKGROUND_BLEND_COLOR = clearBlend;
        SET_MI_BLOOM_STROKE = bloom;
        AVAILABLE = available;
    }

    private ShortcutPopupVendorMaterialBridge() {}

    static Claim claim(View popupView, View contentView) {
        if (!AVAILABLE || popupView == null || contentView == null) return null;
        Method prepareHyperMaterial = null;
        try {
            // Do not query isMaterialEnabled() here. On the actual Launcher 4.50 build that
            // member is not publicly exposed even though JADX reconstructs it as public.
            // prepareHyperMaterial() is the vendor's own idempotent material gate: when called
            // on restore it internally decides whether to apply or clear advanced material.
            prepareHyperMaterial = HookUtil.findMethodExact(
                    popupView.getClass(), "prepareHyperMaterial", new Class<?>[0]);

            // Do not touch pass-window enable. PopupView uses it as the gate for the parent
            // mMenuLayer background-only blur that remains our real backdrop substrate.
            invoke(SET_MI_BACKGROUND_BLUR_MODE, contentView, 0);
            invoke(SET_MI_VIEW_BLUR_MODE, contentView, 0);
            invoke(CLEAR_MI_BACKGROUND_BLEND_COLOR, contentView);
            invoke(SET_MI_BLOOM_STROKE, contentView, (Object) new float[21]);
            contentView.invalidate();

            MainHook.log(TAG + " custom material claimed"
                    + " target=" + contentView.getClass().getName()
                    + " parent=" + (contentView.getParent() != null
                            ? contentView.getParent().getClass().getName() : "null"));
            return new Claim(popupView, prepareHyperMaterial);
        } catch (Throwable error) {
            if (prepareHyperMaterial != null && popupView.isAttachedToWindow()) {
                try {
                    prepareHyperMaterial.invoke(popupView);
                } catch (Throwable restoreError) {
                    MainHook.log(TAG + " failed-claim restore failed: " + root(restoreError));
                }
            }
            MainHook.log(TAG + " claim failed; vendor material retained: " + root(error));
            return null;
        }
    }

    static void restoreIfVisible(Claim claim) {
        if (claim == null || claim.restored) return;
        claim.restored = true;
        View popupView = claim.popupRef.get();
        if (popupView == null || !popupView.isAttachedToWindow()) return;
        try {
            claim.prepareHyperMaterial.invoke(popupView);
            MainHook.log(TAG + " vendor advanced material restored");
        } catch (Throwable error) {
            MainHook.log(TAG + " vendor material restore failed: " + root(error));
        }
    }

    static final class Claim {
        final WeakReference<View> popupRef;
        final Method prepareHyperMaterial;
        boolean restored;

        Claim(View popupView, Method prepareHyperMaterial) {
            this.popupRef = new WeakReference<>(popupView);
            this.prepareHyperMaterial = prepareHyperMaterial;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("vendor material invocation failed", cause);
        } catch (Throwable error) {
            throw new IllegalStateException("vendor material invocation failed", error);
        }
    }

    private static Throwable root(Throwable error) {
        if (error instanceof InvocationTargetException
                && ((InvocationTargetException) error).getCause() != null) {
            return ((InvocationTargetException) error).getCause();
        }
        return error;
    }
}
