package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * Read-only SystemUI root probe for the app-caption Handle Menu.
 *
 * <p>This class deliberately performs no PassBlur mutation. It only records the full-screen
 * NotificationShadeWindowView and inspects immutable ViewRoot/SurfaceControl endpoint metadata so
 * a producer authority can be chosen from real device evidence instead of guessing.</p>
 */
final class SystemUiHandleMenuBackdropProbe {
    private static final String TAG = "[DC][SystemUiHandleMenuProbe]";
    private static final String SHADE_WINDOW =
            "com.android.systemui.shade.NotificationShadeWindowView";

    private static WeakReference<View> shadeRef = new WeakReference<>(null);
    private static boolean installed;

    private SystemUiHandleMenuBackdropProbe() {}

    static void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        try {
            Class<?> shadeWindow = Class.forName(SHADE_WINDOW, false, classLoader);
            HookUtil.hookMethod(
                    shadeWindow,
                    "onAttachedToWindow",
                    new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = chain.getThisObject();
                        if (owner instanceof View) {
                            View view = (View) owner;
                            shadeRef = new WeakReference<>(view);
                            view.addOnAttachStateChangeListener(
                                    new View.OnAttachStateChangeListener() {
                                        @Override
                                        public void onViewAttachedToWindow(View ignored) {}

                                        @Override
                                        public void onViewDetachedFromWindow(View detached) {
                                            View current = shadeRef.get();
                                            if (current == detached) {
                                                shadeRef = new WeakReference<>(null);
                                            }
                                            detached.removeOnAttachStateChangeListener(this);
                                            log("shade detached");
                                        }
                                    });
                            logEndpoint("shade-attached", view);
                        }
                        return result;
                    });
            installed = true;
            log("read-only shade probe installed");
        } catch (Throwable error) {
            log("read-only shade probe unavailable: " + error);
        }
    }

    static void logMenuSnapshot(View popupRoot, View target) {
        if (popupRoot == null || target == null) return;
        try {
            View popupViewRoot = popupRoot.getRootView();
            logEndpoint("popup", popupViewRoot);

            View shade = currentShade();
            if (shade == null) {
                log("shade snapshot unavailable");
                return;
            }
            logEndpoint("shade", shade);

            Rect shadeRect = new Rect();
            Rect targetRect = new Rect();
            boolean shadeVisible = shade.getGlobalVisibleRect(shadeRect);
            boolean targetVisible = target.getGlobalVisibleRect(targetRect);
            log("coverage shadeVisible=" + shadeVisible
                    + " targetVisible=" + targetVisible
                    + " shadeRect=" + shadeRect
                    + " targetRect=" + targetRect
                    + " contains=" + (shadeVisible && targetVisible
                    && shadeRect.contains(targetRect))
                    + " sameRoot=" + (shade.getRootView() == popupViewRoot));
        } catch (Throwable error) {
            log("snapshot failed: " + error);
        }
    }

    private static View currentShade() {
        View shade = shadeRef.get();
        if (shade == null || !shade.isAttachedToWindow()
                || shade.getWidth() <= 0 || shade.getHeight() <= 0) {
            return null;
        }
        return shade;
    }

    private static void logEndpoint(String label, View view) {
        if (view == null) {
            log(label + " view=<null>");
            return;
        }
        RootPassBlurEndpointBridge.Endpoint endpoint =
                RootPassBlurEndpointBridge.inspect(view);
        if (endpoint == null) {
            log(label + " endpoint=<unavailable>"
                    + " class=" + view.getClass().getName()
                    + " view=" + view.getWidth() + "x" + view.getHeight());
            return;
        }
        log(label
                + " class=" + view.getClass().getName()
                + " view=" + view.getWidth() + "x" + view.getHeight()
                + " surface=" + endpoint.surfaceWidth + "x" + endpoint.surfaceHeight
                + " buffer=" + endpoint.bufferWidth + "x" + endpoint.bufferHeight
                + " rotation=" + endpoint.rotation
                + " layerId=" + endpoint.rootLayerId
                + " surfaceSeq=" + endpoint.surfaceSequenceId
                + " viewRootId=" + endpoint.viewRootIdentity
                + " insets=" + endpoint.insetLeft + "," + endpoint.insetTop
                + "," + endpoint.insetRight + "," + endpoint.insetBottom
                + " valid=" + endpoint.isValid());
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
