package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Method;

/**
 * Re-anchors the Video Toolbox child transform to the Launcher mini-Sidebar source only while
 * Security Center is consuming showNewDockFromLauncher geometry.
 *
 * <p>OS4 animates the Video box as an independent child of TurboLayout. The vendor translation
 * curve is preserved; only its initial translation vector is remapped so scale=0 originates at the
 * Launcher-provided source center instead of the legacy internal dock anchor.</p>
 */
final class SecurityCenterVideoLauncherHandoffGeometry {
    private static final String TAG = "[DC][SidebarVideoOrigin]";
    private static final String TURBO_LAYOUT =
            "com.miui.gamebooster.windowmanager.newbox.TurboLayout";

    private static volatile boolean installed;
    private static volatile PendingSource pendingSource;
    private static volatile ActiveBox activeBox;

    private SecurityCenterVideoLauncherHandoffGeometry() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> turboClass = Class.forName(TURBO_LAYOUT, false, classLoader);
            Method launcherShowPrepare = resolveSixIntVoidMethod(turboClass);
            Method getBoxView = HookUtil.findMethodExact(
                    turboClass, "getBoxView", new Class<?>[0]);
            Method getVideoAdapter = HookUtil.findMethodExact(
                    turboClass, "getVideoBoxViewAdapter", new Class<?>[0]);
            Method setTranslationX = HookUtil.findMethodExact(
                    View.class, "setTranslationX", new Class<?>[]{float.class});
            Method setTranslationY = HookUtil.findMethodExact(
                    View.class, "setTranslationY", new Class<?>[]{float.class});
            Method setScaleX = HookUtil.findMethodExact(
                    View.class, "setScaleX", new Class<?>[]{float.class});

            HookUtil.hook(launcherShowPrepare, chain -> {
                Object owner = chain.getThisObject();
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                PendingSource source = pendingSource;
                if (source == null || owner == null || !turboClass.isInstance(owner)) {
                    return result;
                }
                try {
                    Object videoAdapter = getVideoAdapter.invoke(owner);
                    Object box = getBoxView.invoke(owner);
                    if (videoAdapter != null && box instanceof View) {
                        ActiveBox state = new ActiveBox(
                                (View) box, source.generation, source.centerX, source.centerY);
                        activeBox = state;
                        SideSlideHoldDiagnostics.log(TAG
                                + " armed generation=" + source.generation
                                + " source=" + source.centerX + "," + source.centerY);
                    }
                } catch (Throwable error) {
                    SideSlideHoldDiagnostics.log(TAG + " video box bind failed", error);
                }
                return result;
            });

            HookUtil.hook(setScaleX, chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                ActiveBox state = activeBox;
                if (state != null && owner == state.box && args.length == 1
                        && args[0] instanceof Float) {
                    state.lastScale = (Float) args[0];
                }
                return chain.proceed(args);
            });

            HookUtil.hook(setTranslationX, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                rewriteTranslationArgs(chain.getThisObject(), args, true);
                return chain.proceed(args);
            });
            HookUtil.hook(setTranslationY, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                ActiveBox state = activeBox;
                float vendor = args.length == 1 && args[0] instanceof Float
                        ? (Float) args[0] : 0.0f;
                rewriteTranslationArgs(chain.getThisObject(), args, false);
                Object result = chain.proceed(args);
                if (state != null && chain.getThisObject() == state.box) {
                    maybeFinish(state, vendor);
                }
                return result;
            });

            installed = true;
            SideSlideHoldDiagnostics.log(TAG + " structural video-origin hook installed");
            return true;
        } catch (Throwable error) {
            installed = false;
            SideSlideHoldDiagnostics.log(TAG + " install failed", error);
            return false;
        }
    }

    static void arm(int generation, int x, int y, int width, int height) {
        if (generation == Integer.MIN_VALUE || width <= 0 || height <= 0) {
            disarm("invalid source");
            return;
        }
        pendingSource = new PendingSource(
                generation,
                x + (width * 0.5f),
                y + (height * 0.5f));
        activeBox = null;
    }

    static void disarm(String reason) {
        PendingSource pending = pendingSource;
        ActiveBox active = activeBox;
        pendingSource = null;
        activeBox = null;
        if (pending != null || active != null) {
            SideSlideHoldDiagnostics.log(TAG + " disarmed reason=" + reason);
        }
    }

    private static void rewriteTranslationArgs(
            Object owner, Object[] args, boolean horizontal) {
        ActiveBox state = activeBox;
        if (state == null || owner != state.box || args.length != 1
                || !(args[0] instanceof Float)) {
            return;
        }

        float vendor = (Float) args[0];
        Float nativeInitial = horizontal ? state.nativeInitialX : state.nativeInitialY;
        if (nativeInitial == null && Math.abs(vendor) > 0.5f) {
            if (horizontal) state.nativeInitialX = vendor;
            else state.nativeInitialY = vendor;
            nativeInitial = vendor;
        }

        if (nativeInitial == null || Math.abs(nativeInitial) <= 0.5f) return;
        Float desiredInitial = horizontal ? state.desiredInitialX : state.desiredInitialY;
        if (desiredInitial == null) {
            desiredInitial = computeDesiredInitial(state, horizontal);
            if (horizontal) state.desiredInitialX = desiredInitial;
            else state.desiredInitialY = desiredInitial;
        }
        if (desiredInitial != null) {
            args[0] = desiredInitial * (vendor / nativeInitial);
        }
    }

    private static Float computeDesiredInitial(ActiveBox state, boolean horizontal) {
        View box = state.box;
        if (box.getWidth() <= 0 || box.getHeight() <= 0) return null;
        ViewParent parent = box.getParent();
        if (!(parent instanceof View)) return null;

        int[] parentLocation = new int[2];
        ((View) parent).getLocationOnScreen(parentLocation);
        float centerX = parentLocation[0] + box.getLeft() + (box.getWidth() * 0.5f);
        float centerY = parentLocation[1] + box.getTop() + (box.getHeight() * 0.5f);
        return horizontal ? state.sourceCenterX - centerX : state.sourceCenterY - centerY;
    }

    private static void maybeFinish(ActiveBox state, float vendorTranslationY) {
        if (state != activeBox) return;
        if (state.nativeInitialX == null || state.nativeInitialY == null) return;
        if (Math.abs(vendorTranslationY) > 0.5f || state.lastScale < 0.99f) return;
        state.box.post(() -> {
            if (activeBox == state
                    && Math.abs(state.box.getTranslationX()) < 1.5f
                    && Math.abs(state.box.getTranslationY()) < 1.5f
                    && state.box.getScaleX() > 0.98f) {
                pendingSource = null;
                activeBox = null;
                SideSlideHoldDiagnostics.log(TAG
                        + " completed generation=" + state.generation);
            }
        });
    }

    private static Method resolveSixIntVoidMethod(Class<?> type) {
        Method match = null;
        for (Method method : type.getMethods()) {
            if (method.isSynthetic() || method.getReturnType() != void.class) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 6) continue;
            boolean allInt = true;
            for (Class<?> param : params) {
                if (param != int.class) {
                    allInt = false;
                    break;
                }
            }
            if (!allInt) continue;
            if (match != null) {
                throw new IllegalStateException("ambiguous six-int TurboLayout method");
            }
            match = method;
        }
        if (match == null) {
            throw new IllegalStateException("six-int TurboLayout method unavailable");
        }
        match.setAccessible(true);
        return match;
    }

    private static final class PendingSource {
        final int generation;
        final float centerX;
        final float centerY;

        PendingSource(int generation, float centerX, float centerY) {
            this.generation = generation;
            this.centerX = centerX;
            this.centerY = centerY;
        }
    }

    private static final class ActiveBox {
        final View box;
        final int generation;
        final float sourceCenterX;
        final float sourceCenterY;
        volatile Float nativeInitialX;
        volatile Float nativeInitialY;
        volatile Float desiredInitialX;
        volatile Float desiredInitialY;
        volatile float lastScale = 1.0f;

        ActiveBox(View box, int generation, float sourceCenterX, float sourceCenterY) {
            this.box = box;
            this.generation = generation;
            this.sourceCenterX = sourceCenterX;
            this.sourceCenterY = sourceCenterY;
        }
    }
}
