package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * HyperOS Launcher 4.50 Pad compatibility for Back-drag + hold -> Security Center Sidebar.
 *
 * <p>The vendor Back state machine remains authoritative. This hook only observes the stable
 * {@code GestureStubView.onTouchEvent(MotionEvent)} boundary and suppresses the stable
 * {@code injectBackKeyEvent(boolean)} commit after Security Center has positively acknowledged
 * the hold preflight. No obfuscated Launcher member is read or hooked.</p>
 */
final class Launcher450SideSlideHoldHook {
    private static final String TAG = "[DC][SideSlideHold450]";
    private static final String GESTURE_STUB = "com.miui.home.recents.GestureStubView";
    private static final float SOURCE_SIZE_DP = 48f;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, GestureState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean installed;

    private Launcher450SideSlideHoldHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> stub = Class.forName(GESTURE_STUB, false, classLoader);
            Method onTouchEvent = HookUtil.findMethodExact(
                    stub, "onTouchEvent", new Class<?>[]{MotionEvent.class});
            Method injectBack = HookUtil.findMethodExact(
                    stub, "injectBackKeyEvent", new Class<?>[]{boolean.class});

            HookUtil.hook(onTouchEvent, chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                MotionEvent event = args.length > 0 && args[0] instanceof MotionEvent
                        ? (MotionEvent) args[0] : null;
                GestureState state = stateFor(owner);
                if (owner instanceof View && event != null) {
                    beforeTouch((View) owner, event, state);
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

            HookUtil.hook(injectBack, chain -> {
                GestureState state = stateFor(chain.getThisObject());
                if (state.policy.shouldConsumeBack()) {
                    SideSlideHoldDiagnostics.log(TAG + " suppress vendor Back after armed Sidebar release");
                    return null;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            installed = true;
            SideSlideHoldDiagnostics.log(TAG + " installed class=" + stub.getName());
            return true;
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " unavailable on target Launcher: " + error);
            return false;
        }
    }

    private static GestureState stateFor(Object owner) {
        synchronized (STATES) {
            GestureState state = STATES.get(owner);
            if (state == null) {
                state = new GestureState();
                STATES.put(owner, state);
            }
            return state;
        }
    }

    private static void beforeTouch(View view, MotionEvent event, GestureState state) {
        int action = event.getActionMasked();
        long eventTime = event.getEventTime();
        state.lastRawX = event.getRawX();
        state.lastRawY = event.getRawY();

        if (action == MotionEvent.ACTION_DOWN) {
            cancelScheduled(view, state);
            state.sideStub = isPadSideStub(view);
            state.downX = event.getRawX();
            state.policy.onDown(event.getRawX(), event.getRawY(), eventTime);
            state.scheduledGeneration = Integer.MIN_VALUE;
            DisplayMetrics dm = view.getResources().getDisplayMetrics();
            SideSlideHoldDiagnostics.log(TAG + " DOWN sideStub=" + state.sideStub
                    + " view=" + view.getWidth() + "x" + view.getHeight()
                    + " screen=" + dm.widthPixels + "x" + dm.heightPixels
                    + " sw=" + view.getResources().getConfiguration().smallestScreenWidthDp
                    + " raw=" + state.lastRawX + "," + state.lastRawY);
            return;
        }
        if (!state.sideStub) return;

        if (action == MotionEvent.ACTION_UP) {
            if (state.policy.shouldConsumeBack()) {
                SideSlideHoldDiagnostics.log(TAG + " UP armed=true -> dispatch show before vendor Back decision");
                dispatchShow(view, state);
            } else {
                SideSlideHoldDiagnostics.log(TAG + " UP armed=false -> stock Back remains authoritative");
            }
            return;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            SideSlideHoldDiagnostics.log(TAG + " CANCEL");
            return;
        }
        if (action != MotionEvent.ACTION_MOVE) return;

        int beforeGeneration = state.policy.generation();
        state.policy.onMove(event.getRawX(), event.getRawY(), eventTime);
        int generation = state.policy.generation();

        float distance = Math.abs(event.getRawX() - state.downX);
        boolean completedBack = distance >= SideSlideHoldPolicy.BACK_COMPLETE_DISTANCE_PX;
        if (!completedBack) {
            cancelScheduled(view, state);
            state.scheduledGeneration = generation;
            return;
        }

        if (generation != beforeGeneration || state.scheduledGeneration != generation) {
            SideSlideHoldDiagnostics.log(TAG + " completion reached distance=" + distance
                    + " generation=" + generation + " -> dwell "
                    + SideSlideHoldPolicy.HOLD_DWELL_MS + "ms");
            scheduleDwell(view, state, generation);
        }
    }

    private static boolean isPadSideStub(View view) {
        Configuration config = view.getResources().getConfiguration();
        if (config.smallestScreenWidthDp < 600) return false;
        DisplayMetrics dm = view.getResources().getDisplayMetrics();
        int width = view.getWidth();
        return width > 0 && dm.widthPixels > 0 && width < dm.widthPixels / 3;
    }

    private static void scheduleDwell(View view, GestureState state, int generation) {
        cancelScheduled(view, state);
        state.scheduledGeneration = generation;
        Runnable runnable = () -> {
            if (state.scheduledGeneration != generation) return;
            if (!state.policy.shouldRequestSidebar(SystemClock.uptimeMillis())) return;
            SideSlideHoldDiagnostics.log(TAG + " dwell elapsed generation=" + generation + " -> SC preflight");
            sendSidebarPrepare(view, state, generation);
        };
        state.dwellRunnable = runnable;
        view.postDelayed(runnable, SideSlideHoldPolicy.HOLD_DWELL_MS);
    }

    private static void sendSidebarPrepare(View view, GestureState state, int generation) {
        Context context = view.getContext();
        if (context == null) {
            state.policy.onSidebarResult(false, generation);
            return;
        }
        Intent intent = new Intent(SidebarCommandContract.ACTION_PREPARE)
                .setPackage(SidebarCommandContract.SECURITY_CENTER_PACKAGE);
        BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent ignoredIntent) {
                boolean ready = getResultCode() == SidebarCommandContract.RESULT_READY;
                state.policy.onSidebarResult(ready, generation);
                if (ready && state.policy.shouldConsumeBack()) {
                    // Diagnostic mapping for the recovered OS4 hold-commit CLICK feedback.
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    SideSlideHoldDiagnostics.log(TAG + " ARMED generation=" + generation
                            + " preflight=ready haptic=VIRTUAL_KEY");
                } else {
                    SideSlideHoldDiagnostics.log(TAG + " preflight unavailable generation=" + generation
                            + " result=" + getResultCode());
                }
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
            SideSlideHoldDiagnostics.log(TAG + " preflight dispatch failed: " + error);
        }
    }

    private static void dispatchShow(View view, GestureState state) {
        Context context = view.getContext();
        if (context == null) return;
        int[] geometry = sourceGeometry(view, state.lastRawX, state.lastRawY);
        Intent intent = new Intent(SidebarCommandContract.ACTION_SHOW)
                .setPackage(SidebarCommandContract.SECURITY_CENTER_PACKAGE)
                .putExtra(SidebarCommandContract.EXTRA_X, geometry[0])
                .putExtra(SidebarCommandContract.EXTRA_Y, geometry[1])
                .putExtra(SidebarCommandContract.EXTRA_WIDTH, geometry[2])
                .putExtra(SidebarCommandContract.EXTRA_HEIGHT, geometry[3])
                .putExtra(SidebarCommandContract.EXTRA_RADIUS, geometry[4]);
        try {
            context.sendBroadcast(intent);
            SideSlideHoldDiagnostics.log(TAG + " SHOW dispatched geometry="
                    + geometry[0] + "," + geometry[1] + " "
                    + geometry[2] + "x" + geometry[3] + " r=" + geometry[4]);
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " show dispatch failed after arm: " + error);
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
        if (owner instanceof View) cancelScheduled((View) owner, state);
        state.policy.onUpOrCancel();
        state.sideStub = false;
        state.scheduledGeneration = Integer.MIN_VALUE;
    }

    private static void cancelScheduled(View view, GestureState state) {
        Runnable runnable = state.dwellRunnable;
        if (runnable != null) view.removeCallbacks(runnable);
        state.dwellRunnable = null;
    }

    private static final class GestureState {
        final SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        Runnable dwellRunnable;
        int scheduledGeneration = Integer.MIN_VALUE;
        boolean sideStub;
        float downX;
        float lastRawX;
        float lastRawY;
    }
}
