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
 * Launcher 4.50 Pad edge-gesture extension: native RECENT-ready + dwell -> Security Center Sidebar.
 *
 * <p>Launcher remains authoritative for distance, direction, speed and BACK/RECENT/NONE state.
 * LiquidDock observes only the stable semantic ReadyState boundary. If Security Center positively
 * accepts the Sidebar show request while the gesture is still active, the vendor completion state
 * is reconciled to BACK so Launcher performs its normal release cleanup; only the final Back
 * injection is suppressed.</p>
 */
final class Launcher450SideSlideHoldHook {
    private static final String TAG = "[DC][SideSlideHold450]";
    private static final String GESTURE_STUB = "com.miui.home.recents.GestureStubView";
    private static final String ARROW_VIEW = "com.miui.home.recents.GestureBackArrowView";
    private static final String READY_STATE =
            "com.miui.home.recents.GestureBackArrowView$ReadyState";
    private static final float SOURCE_SIZE_DP = 48f;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, GestureState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile Method setReadyFinishMethod;
    private static volatile Object readyStateBack;
    private static volatile boolean installed;

    private Launcher450SideSlideHoldHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> stubClass = Class.forName(GESTURE_STUB, false, classLoader);
            Class<?> arrowClass = Class.forName(ARROW_VIEW, false, classLoader);
            Class<?> readyClass = Class.forName(READY_STATE, false, classLoader);

            Method onTouchEvent = HookUtil.findMethodExact(
                    stubClass, "onTouchEvent", new Class<?>[]{MotionEvent.class});
            Method injectBack = HookUtil.findMethodExact(
                    stubClass, "injectBackKeyEvent", new Class<?>[]{boolean.class});
            Method setReadyFinish = HookUtil.findMethodExact(
                    arrowClass, "setReadyFinish", new Class<?>[]{readyClass});

            Object back = enumConstant(readyClass, "READY_STATE_BACK");
            if (back == null) {
                SideSlideHoldDiagnostics.log(TAG + " READY_STATE_BACK unavailable; fail closed");
                return false;
            }
            setReadyFinishMethod = setReadyFinish;
            readyStateBack = back;

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
                    } else {
                        boolean enteredRecent = state.policy.onReadyState(readyName);
                        if (enteredRecent && state.owner instanceof View) {
                            scheduleDwell((View) state.owner, state);
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
                    + " installed; authority=GestureBackArrowView.ReadyState");
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
            state.policy.onDown();
            SideSlideHoldDiagnostics.log(TAG + " DOWN sideStub=" + state.sideStub);
        }
    }

    private static boolean isPadSideStub(View view) {
        Configuration config = view.getResources().getConfiguration();
        if (config.smallestScreenWidthDp < 600) return false;
        DisplayMetrics dm = view.getResources().getDisplayMetrics();
        int width = view.getWidth();
        return width > 0 && dm.widthPixels > 0 && width < dm.widthPixels / 3;
    }

    private static void scheduleDwell(View owner, GestureState state) {
        if (!state.sideStub) return;
        cancelDwell(owner, state);
        int generation = state.policy.generation();
        state.scheduledGeneration = generation;
        Runnable runnable = () -> {
            if (state.scheduledGeneration != generation) return;
            if (!state.policy.requestSidebar(generation)) return;
            SideSlideHoldDiagnostics.log(TAG + " native RECENT stable for "
                    + SideSlideHoldPolicy.HOLD_DWELL_MS + "ms -> request Sidebar");
            requestSidebar(owner, state, generation);
        };
        state.dwellRunnable = runnable;
        owner.postDelayed(runnable, SideSlideHoldPolicy.HOLD_DWELL_MS);
    }

    private static void requestSidebar(View owner, GestureState state, int generation) {
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
                            + " Sidebar unavailable/stale -> vendor gesture remains authoritative");
                    return;
                }
                owner.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                forceVendorCleanupToBack(state);
                SideSlideHoldDiagnostics.log(TAG
                        + " Sidebar accepted -> arm vendor completion suppression");
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
            SideSlideHoldDiagnostics.log(TAG + " Sidebar request failed", error);
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
        state.arrow = null;
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
        Object arrow;
        float lastRawX;
        float lastRawY;

        GestureState(Object owner) {
            this.owner = owner;
        }
    }
}
