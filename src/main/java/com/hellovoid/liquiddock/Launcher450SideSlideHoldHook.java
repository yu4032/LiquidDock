package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Launcher 4.50 Pad side-slide extension.
 *
 * <p>Apps use Launcher's native GestureBackArrowView ReadyState. HOME does not expose a
 * BACK/RECENT completion state on OS3, so HOME alone falls back to the already-existing OS3
 * predictive-back visual saturation distance (180 px) observed at GestureStubView. This fallback
 * is never used in app state and does not replace Launcher's app gesture authority.</p>
 */
final class Launcher450SideSlideHoldHook {
    private static final String TAG = "[DC][SideSlideHold450]";
    private static final String GESTURE_STUB = "com.miui.home.recents.GestureStubView";
    private static final String ARROW_VIEW = "com.miui.home.recents.GestureBackArrowView";
    private static final String READY_STATE =
            "com.miui.home.recents.GestureBackArrowView$ReadyState";
    private static final String LAUNCHER_APPLICATION = "com.miui.home.launcher.Application";
    private static final String LAUNCHER_CLASS = "com.miui.home.launcher.Launcher";
    private static final String LAUNCHER_STATE_CLASS = "com.miui.home.launcher.LauncherState";

    // Recovered from GestureStubView's predictive-back progress: abs(dx) / 180f, clamped to 1.
    // On HOME only, where OS3 never publishes READY_STATE_RECENT, this is used as a visual
    // saturation boundary rather than as a claim about vendor Back completion semantics.
    private static final float HOME_VISUAL_SATURATION_PX = 180f;
    // Hover means positional dwell, not merely elapsed time after crossing the boundary.
    private static final float HOME_HOVER_SLOP_DP = 12f;
    private static final float SOURCE_SIZE_DP = 48f;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, GestureState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile Method setReadyFinishMethod;
    private static volatile Object readyStateBack;
    private static volatile Method applicationGetLauncherMethod;
    private static volatile Method launcherIsInStateMethod;
    private static volatile Class<?> launcherStateClass;
    private static volatile Object launcherStateNormal;
    private static volatile boolean installed;

    private Launcher450SideSlideHoldHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> stubClass = Class.forName(GESTURE_STUB, false, classLoader);
            Class<?> arrowClass = Class.forName(ARROW_VIEW, false, classLoader);
            Class<?> readyClass = Class.forName(READY_STATE, false, classLoader);
            Class<?> applicationClass = Class.forName(LAUNCHER_APPLICATION, false, classLoader);
            Class<?> launcherClass = Class.forName(LAUNCHER_CLASS, false, classLoader);
            Class<?> stateClass = Class.forName(LAUNCHER_STATE_CLASS, false, classLoader);

            Method onTouchEvent = HookUtil.findMethodExact(
                    stubClass, "onTouchEvent", new Class<?>[]{MotionEvent.class});
            Method injectBack = HookUtil.findMethodExact(
                    stubClass, "injectBackKeyEvent", new Class<?>[]{boolean.class});
            Method setReadyFinish = HookUtil.findMethodExact(
                    arrowClass, "setReadyFinish", new Class<?>[]{readyClass});
            Method getLauncher = HookUtil.findMethodExact(
                    applicationClass, "getLauncher", new Class<?>[0]);
            Method isInState = HookUtil.findMethodExact(
                    launcherClass, "isInState", new Class<?>[]{stateClass});

            Object back = enumConstant(readyClass, "READY_STATE_BACK");
            if (back == null) {
                SideSlideHoldDiagnostics.log(TAG + " READY_STATE_BACK unavailable; fail closed");
                return false;
            }
            setReadyFinishMethod = setReadyFinish;
            readyStateBack = back;
            applicationGetLauncherMethod = getLauncher;
            launcherIsInStateMethod = isInState;
            launcherStateClass = stateClass;
            launcherStateNormal = null;

            HookUtil.hook(onTouchEvent, chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                MotionEvent event = args.length > 0 && args[0] instanceof MotionEvent
                        ? (MotionEvent) args[0] : null;
                GestureState state = stateFor(owner);
                if (owner instanceof View && event != null) {
                    observeTouchBefore((View) owner, event, state);
                }
                try {
                    return chain.proceed(args);
                } finally {
                    if (event != null) {
                        int action = event.getActionMasked();
                        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            finishGesture(owner, state);
                        }
                    }
                }
            });

            HookUtil.hook(setReadyFinish, chain -> {
                Object arrow = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object requested = args.length > 0 ? args[0] : null;
                GestureState state = stateForArrow(arrow);
                if (state != null) {
                    state.arrow = arrow;
                    String readyName = requested instanceof Enum<?>
                            ? ((Enum<?>) requested).name()
                            : String.valueOf(requested);
                    if (state.policy.shouldConsumeVendorCompletion()) {
                        args[0] = readyStateBack;
                    } else if (!state.desktopAtDown) {
                        boolean enteredRecent = state.policy.onReadyState(readyName);
                        if (enteredRecent) {
                            SideSlideHoldDiagnostics.log(TAG + " ReadyState entered RECENT");
                        }
                        if (enteredRecent && state.owner instanceof View) {
                            scheduleDwell((View) state.owner, state, "native RECENT");
                        } else if (!"READY_STATE_RECENT".equals(readyName)
                                && state.owner instanceof View) {
                            cancelDwell((View) state.owner, state);
                        }
                    }
                }
                return chain.proceed(args);
            });

            HookUtil.hook(injectBack, chain -> {
                GestureState state = stateFor(chain.getThisObject());
                if (state.policy.shouldConsumeVendorCompletion()) {
                    SideSlideHoldDiagnostics.log(
                            TAG + " suppress vendor Back after Sidebar show acknowledgement");
                    return null;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            installed = true;
            SideSlideHoldDiagnostics.log(TAG
                    + " installed; appAuthority=ReadyState homeAuthority=LauncherState.NORMAL");
            return true;
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " unavailable on target Launcher", error);
            return false;
        }
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        Object[] values = enumClass.getEnumConstants();
        if (values == null) return null;
        for (Object value : values) {
            if (value instanceof Enum<?> && name.equals(((Enum<?>) value).name())) {
                return value;
            }
        }
        return null;
    }

    private static GestureState stateFor(Object owner) {
        synchronized (STATES) {
            GestureState state = STATES.get(owner);
            if (state == null) {
                state = new GestureState(owner);
                STATES.put(owner, state);
            }
            return state;
        }
    }

    private static GestureState stateForArrow(Object arrow) {
        if (!(arrow instanceof View)) return null;
        ViewParent parent = ((View) arrow).getParent();
        if (parent == null) return null;
        synchronized (STATES) {
            return STATES.get(parent);
        }
    }

    private static void observeTouchBefore(View view, MotionEvent event, GestureState state) {
        state.lastRawX = event.getRawX();
        state.lastRawY = event.getRawY();
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            cancelDwell(view, state);
            state.sideStub = isPadSideStub(view);
            state.desktopAtDown = state.sideStub && isLauncherDesktop();
            state.downX = event.getRawX();
            state.leftEdge = state.downX < view.getResources().getDisplayMetrics().widthPixels / 2f;
            state.hoverAnchorX = Float.NaN;
            state.hoverAnchorY = Float.NaN;
            state.policy.onDown();
            SideSlideHoldDiagnostics.log(TAG + " DOWN sideStub=" + state.sideStub
                    + " desktop=" + state.desktopAtDown
                    + " edge=" + (state.leftEdge ? "left" : "right"));
            return;
        }

        if (!state.sideStub || !state.desktopAtDown || action != MotionEvent.ACTION_MOVE) return;

        float dx = event.getRawX() - state.downX;
        boolean inward = state.leftEdge ? dx > 0f : dx < 0f;
        boolean saturated = inward && Math.abs(dx) >= HOME_VISUAL_SATURATION_PX;
        boolean entered = state.policy.onDesktopProgress(saturated);
        if (!saturated) {
            state.hoverAnchorX = Float.NaN;
            state.hoverAnchorY = Float.NaN;
            cancelDwell(view, state);
            return;
        }

        float x = event.getRawX();
        float y = event.getRawY();
        float hoverSlop = HOME_HOVER_SLOP_DP * view.getResources().getDisplayMetrics().density;
        boolean anchorMissing = Float.isNaN(state.hoverAnchorX) || Float.isNaN(state.hoverAnchorY);
        boolean movedOutsideHover = !anchorMissing
                && (Math.abs(x - state.hoverAnchorX) > hoverSlop
                || Math.abs(y - state.hoverAnchorY) > hoverSlop);
        if (entered || anchorMissing || movedOutsideHover) {
            state.hoverAnchorX = x;
            state.hoverAnchorY = y;
            cancelDwell(view, state);
            int generation = state.policy.generation();
            state.scheduledGeneration = generation;
            Runnable runnable = () -> {
                if (state.scheduledGeneration != generation) return;
                if (!state.policy.requestSidebar(generation)) return;
                SideSlideHoldDiagnostics.log(TAG + " HOME hover confirmed for "
                        + SideSlideHoldPolicy.HOLD_DWELL_MS + "ms"
                        + " at x=" + state.hoverAnchorX + " y=" + state.hoverAnchorY);
                prepareThenShowSidebar(view, state, generation);
            };
            state.dwellRunnable = runnable;
            view.postDelayed(runnable, SideSlideHoldPolicy.HOLD_DWELL_MS);
            SideSlideHoldDiagnostics.log(TAG + " HOME hover armed dx=" + dx
                    + " reset=" + movedOutsideHover);
        }
    }

    private static boolean isLauncherDesktop() {
        Method getLauncher = applicationGetLauncherMethod;
        Method isInState = launcherIsInStateMethod;
        Class<?> stateClass = launcherStateClass;
        if (getLauncher == null || isInState == null || stateClass == null) return false;
        try {
            Object launcher = getLauncher.invoke(null);
            if (launcher == null) return false;

            Object normal = launcherStateNormal;
            if (normal == null) {
                // Do not touch LauncherState static fields during module/class-loader bootstrap.
                // Resolve NORMAL lazily only after Launcher already exists and Application.onCreate
                // has completed enough for vendor state construction to have a valid Context.
                normal = stateClass.getField("NORMAL").get(null);
                if (normal == null) return false;
                launcherStateNormal = normal;
                SideSlideHoldDiagnostics.log(TAG + " lazily resolved LauncherState.NORMAL");
            }

            boolean desktop = Boolean.TRUE.equals(isInState.invoke(launcher, normal));
            SideSlideHoldDiagnostics.log(TAG
                    + " HOME authority LauncherState.NORMAL=" + desktop);
            return desktop;
        } catch (Throwable error) {
            // Never let optional side-slide state resolution poison Launcher startup/runtime.
            SideSlideHoldDiagnostics.log(TAG + " HOME authority unavailable; fail open: " + error);
            return false;
        }
    }

    private static boolean isPadSideStub(View view) {
        Configuration config = view.getResources().getConfiguration();
        if (config.smallestScreenWidthDp < 600) return false;
        DisplayMetrics dm = view.getResources().getDisplayMetrics();
        int width = view.getWidth();
        return width > 0 && dm.widthPixels > 0 && width < dm.widthPixels / 3;
    }

    private static void scheduleDwell(View owner, GestureState state, String authority) {
        if (!state.sideStub) return;
        cancelDwell(owner, state);
        int generation = state.policy.generation();
        state.scheduledGeneration = generation;
        Runnable runnable = () -> {
            if (state.scheduledGeneration != generation) return;
            if (!state.policy.requestSidebar(generation)) return;
            SideSlideHoldDiagnostics.log(TAG + " " + authority + " stable for "
                    + SideSlideHoldPolicy.HOLD_DWELL_MS + "ms -> preflight Sidebar");
            prepareThenShowSidebar(owner, state, generation);
        };
        state.dwellRunnable = runnable;
        owner.postDelayed(runnable, SideSlideHoldPolicy.HOLD_DWELL_MS);
    }

    private static void prepareThenShowSidebar(View owner, GestureState state, int generation) {
        Context context = owner.getContext();
        if (context == null) {
            state.policy.onSidebarResult(false, generation);
            return;
        }
        Intent prepare = new Intent(SidebarCommandContract.ACTION_PREPARE)
                .setPackage(SidebarCommandContract.SECURITY_CENTER_PACKAGE);
        BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent ignoredIntent) {
                if (getResultCode() != SidebarCommandContract.RESULT_READY) {
                    state.policy.onSidebarResult(false, generation);
                    SideSlideHoldDiagnostics.log(TAG
                            + " Sidebar preflight unavailable/stale -> stock gesture");
                    return;
                }
                if (state.scheduledGeneration != generation) {
                    state.policy.onSidebarResult(false, generation);
                    return;
                }

                // OS4 Security Center confirms its 300 ms long-click with haptic, then widens
                // the Sidebar line. We preserve the same ordering: haptic first, vendor show next.
                owner.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                SideSlideHoldDiagnostics.log(TAG
                        + " Sidebar preflight ready -> haptic -> vendor show");
                showSidebar(owner, state, generation);
            }
        };
        try {
            context.sendOrderedBroadcast(
                    prepare,
                    null,
                    result,
                    MAIN,
                    SidebarCommandContract.RESULT_UNAVAILABLE,
                    null,
                    null);
        } catch (Throwable error) {
            state.policy.onSidebarResult(false, generation);
            SideSlideHoldDiagnostics.log(TAG + " Sidebar preflight failed", error);
        }
    }

    private static void showSidebar(View owner, GestureState state, int generation) {
        Context context = owner.getContext();
        if (context == null) {
            state.policy.onSidebarResult(false, generation);
            return;
        }
        int[] geometry = sourceGeometry(owner, state.lastRawX, state.lastRawY);
        Intent intent = new Intent(SidebarCommandContract.ACTION_SHOW)
                .setPackage(SidebarCommandContract.SECURITY_CENTER_PACKAGE)
                .putExtra(SidebarCommandContract.EXTRA_X, geometry[0])
                .putExtra(SidebarCommandContract.EXTRA_Y, geometry[1])
                .putExtra(SidebarCommandContract.EXTRA_WIDTH, geometry[2])
                .putExtra(SidebarCommandContract.EXTRA_HEIGHT, geometry[3])
                .putExtra(SidebarCommandContract.EXTRA_RADIUS, geometry[4]);

        BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent ignoredIntent) {
                boolean accepted =
                        getResultCode() == SidebarCommandContract.RESULT_ACCEPTED;
                state.policy.onSidebarResult(accepted, generation);
                if (!state.policy.shouldConsumeVendorCompletion()) {
                    SideSlideHoldDiagnostics.log(TAG
                            + " Sidebar show unavailable/stale -> stock gesture");
                    return;
                }
                if (!state.desktopAtDown) forceVendorCleanupToBack(state);
                SideSlideHoldDiagnostics.log(TAG
                        + " Sidebar accepted desktop=" + state.desktopAtDown);
            }
        };

        try {
            context.sendOrderedBroadcast(
                    intent,
                    null,
                    result,
                    MAIN,
                    SidebarCommandContract.RESULT_UNAVAILABLE,
                    null,
                    null);
        } catch (Throwable error) {
            state.policy.onSidebarResult(false, generation);
            SideSlideHoldDiagnostics.log(TAG + " Sidebar show request failed", error);
        }
    }

    private static void forceVendorCleanupToBack(GestureState state) {
        Method method = setReadyFinishMethod;
        Object arrow = state.arrow;
        Object back = readyStateBack;
        if (method == null || arrow == null || back == null) return;
        try {
            method.invoke(arrow, back);
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG
                    + " failed to reconcile vendor cleanup state to BACK", error);
        }
    }

    private static int[] sourceGeometry(View view, float rawX, float rawY) {
        DisplayMetrics dm = view.getResources().getDisplayMetrics();
        int size = Math.max(1, Math.round(SOURCE_SIZE_DP * dm.density));
        int screenWidth = Math.max(size, dm.widthPixels);
        int screenHeight = Math.max(size, dm.heightPixels);
        boolean left = rawX < screenWidth / 2f;
        int x = left ? 0 : screenWidth - size;
        int y = Math.round(rawY - size / 2f);
        y = Math.max(0, Math.min(y, screenHeight - size));
        return new int[]{x, y, size, size, size / 2};
    }

    private static void finishGesture(Object owner, GestureState state) {
        if (owner instanceof View) cancelDwell((View) owner, state);
        state.policy.onUpOrCancel();
        state.sideStub = false;
        state.desktopAtDown = false;
        state.arrow = null;
        state.hoverAnchorX = Float.NaN;
        state.hoverAnchorY = Float.NaN;
        state.scheduledGeneration = Integer.MIN_VALUE;
    }

    private static void cancelDwell(View owner, GestureState state) {
        Runnable runnable = state.dwellRunnable;
        if (runnable != null) owner.removeCallbacks(runnable);
        state.dwellRunnable = null;
        state.scheduledGeneration = Integer.MIN_VALUE;
    }

    private static final class GestureState {
        final Object owner;
        final SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        Runnable dwellRunnable;
        int scheduledGeneration = Integer.MIN_VALUE;
        boolean sideStub;
        boolean desktopAtDown;
        boolean leftEdge;
        Object arrow;
        float downX;
        float lastRawX;
        float lastRawY;
        float hoverAnchorX = Float.NaN;
        float hoverAnchorY = Float.NaN;

        GestureState(Object owner) {
            this.owner = owner;
        }
    }
}
