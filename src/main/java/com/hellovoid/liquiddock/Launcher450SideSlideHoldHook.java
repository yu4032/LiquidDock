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
 * a Sidebar show command. No obfuscated Launcher member is read or hooked.</p>
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
                    MainHook.log(TAG + " suppress vendor Back after acknowledged Sidebar show");
                    return null;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            installed = true;
            MainHook.log(TAG + " installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable on target Launcher: " + error);
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
        if (action == MotionEvent.ACTION_DOWN) {
            cancelScheduled(view, state);
            state.sideStub = isPadSideStub(view);
            state.downX = event.getRawX();
            state.policy.onDown(event.getRawX(), event.getRawY(), eventTime);
            state.scheduledGeneration = Integer.MIN_VALUE;
            return;
        }
        if (!state.sideStub) return;
        if (action != MotionEvent.ACTION_MOVE) return;

        int beforeGeneration = state.policy.generation();
        state.policy.onMove(event.getRawX(), event.getRawY(), eventTime);
        int generation = state.policy.generation();

        boolean completedBack = Math.abs(event.getRawX() - state.downX)
                >= SideSlideHoldPolicy.BACK_COMPLETE_DISTANCE_PX;
        if (!completedBack) {
            cancelScheduled(view, state);
            state.scheduledGeneration = generation;
            return;
        }

        if (generation != beforeGeneration || state.scheduledGeneration != generation) {
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
            sendSidebarRequest(view, state, generation);
        };
        state.dwellRunnable = runnable;
        view.postDelayed(runnable, SideSlideHoldPolicy.HOLD_DWELL_MS);
    }

    private static void sendSidebarRequest(View view, GestureState state, int generation) {
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

        BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent ignoredIntent) {
                boolean shown = getResultCode() == SidebarCommandContract.RESULT_SHOWN;
                state.policy.onSidebarResult(shown, generation);
                if (shown && state.policy.shouldConsumeBack()) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    MainHook.log(TAG + " Sidebar acknowledged generation=" + generation);
                } else {
                    MainHook.log(TAG + " Sidebar unavailable generation=" + generation);
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
            MainHook.log(TAG + " command dispatch failed: " + error);
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
