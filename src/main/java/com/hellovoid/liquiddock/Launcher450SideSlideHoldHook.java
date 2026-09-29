package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
    private static final String GESTURES_BACK_CONTROLLER =
            "com.miui.home.recents.GesturesBackController";
    private static final String READY_STATE =
            "com.miui.home.recents.GestureBackArrowView$ReadyState";
    private static final String LAUNCHER_APPLICATION = "com.miui.home.launcher.Application";
    private static final String LAUNCHER_CLASS = "com.miui.home.launcher.Launcher";
    private static final String LAUNCHER_STATE_CLASS = "com.miui.home.launcher.LauncherState";

    // Recovered from GestureStubView's predictive-back progress: abs(dx) / 180f, clamped to 1.
    // On HOME only, where OS3 never publishes READY_STATE_RECENT, this is used as a visual
    // saturation boundary rather than as a claim about vendor Back completion semantics.
    private static final float HOME_VISUAL_SATURATION_PX = 180f;
    // OS4 split_effect_renderer starts at gesture progress 0.8 and reaches its full split at 1.0.
    // Map OS3's observed 180px visual boundary to that same 0.8 point.
    private static final float OS4_SPLIT_START_PROGRESS = 0.8f;
    // OS4 GestureBackArrowView visual source geometry used by Security Center's launcher-origin
    // transform. Side authority is kept separate: Security Center derives left/right solely from
    // the first x argument, so x must come from the gesture edge rather than the Arrow view.
    private static final float OS4_SOURCE_WIDTH_DP = 24f;
    private static final float OS4_SOURCE_HEIGHT_DP = 53f;
    private static final float OS4_SOURCE_RADIUS_DP = 8f;
    // GestureBackArrowView::on_swipe_stop creates a 100 ms ValueAnimator before its listener
    // completes the release transition. Do not tear down the Launcher visual on SC start earlier
    // than that native release window.
    private static final long OS4_RELEASE_DURATION_MS = 100L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, GestureState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile Method setReadyFinishMethod;
    private static volatile Method convertOffsetMethod;
    private static volatile Object readyStateBack;
    private static volatile Method applicationGetLauncherMethod;
    private static volatile Method launcherIsInStateMethod;
    private static volatile Method launcherGetWorkspaceMethod;
    private static volatile Method workspaceFinishCurrentGestureMethod;
    private static volatile Class<?> launcherStateClass;
    private static volatile Object launcherStateNormal;
    private static volatile boolean handoffReceiverRegistered;
    private static volatile boolean installed;

    private Launcher450SideSlideHoldHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> stubClass = Class.forName(GESTURE_STUB, false, classLoader);
            Class<?> arrowClass = Class.forName(ARROW_VIEW, false, classLoader);
            Class<?> gesturesBackControllerClass =
                    Class.forName(GESTURES_BACK_CONTROLLER, false, classLoader);
            Class<?> readyClass = Class.forName(READY_STATE, false, classLoader);
            Class<?> applicationClass = Class.forName(LAUNCHER_APPLICATION, false, classLoader);
            Class<?> launcherClass = Class.forName(LAUNCHER_CLASS, false, classLoader);
            Class<?> workspaceClass = Class.forName("com.miui.home.launcher.Workspace", false, classLoader);
            Class<?> stateClass = Class.forName(LAUNCHER_STATE_CLASS, false, classLoader);

            Method onTouchEvent = HookUtil.findMethodExact(
                    stubClass, "onTouchEvent", new Class<?>[]{MotionEvent.class});
            Method injectBack = HookUtil.findMethodExact(
                    stubClass, "injectBackKeyEvent", new Class<?>[]{boolean.class});
            Method setReadyFinish = HookUtil.findMethodExact(
                    arrowClass, "setReadyFinish", new Class<?>[]{readyClass});
            Method convertOffset = HookUtil.findMethodExact(
                    gesturesBackControllerClass, "convertOffset", new Class<?>[]{float.class});
            Method onArrowDraw = HookUtil.findMethodExact(
                    arrowClass, "onDraw", new Class<?>[]{Canvas.class});
            Method onArrowActionDown = HookUtil.findMethodExact(
                    arrowClass, "onActionDown",
                    new Class<?>[]{float.class, float.class, float.class});
            Method onArrowActionMove = HookUtil.findMethodExact(
                    arrowClass, "onActionMove", new Class<?>[]{float.class});
            Method getLauncher = HookUtil.findMethodExact(
                    applicationClass, "getLauncher", new Class<?>[0]);
            Method isInState = HookUtil.findMethodExact(
                    launcherClass, "isInState", new Class<?>[]{stateClass});
            Method getWorkspace = HookUtil.findMethodExact(
                    launcherClass, "getWorkspace", new Class<?>[0]);
            Method finishCurrentGesture = HookUtil.findMethodExact(
                    workspaceClass, "finishCurrentGesture", new Class<?>[0]);

            Object back = enumConstant(readyClass, "READY_STATE_BACK");
            if (back == null) {
                SideSlideHoldDiagnostics.log(TAG + " READY_STATE_BACK unavailable; fail closed");
                return false;
            }
            setReadyFinishMethod = setReadyFinish;
            convertOffsetMethod = convertOffset;
            readyStateBack = back;
            applicationGetLauncherMethod = getLauncher;
            launcherIsInStateMethod = isInState;
            launcherGetWorkspaceMethod = getWorkspace;
            workspaceFinishCurrentGestureMethod = finishCurrentGesture;
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

                int action = event != null ? event.getActionMasked() : -1;
                if (owner instanceof View && action == MotionEvent.ACTION_UP) {
                    commitRelease((View) owner, state);
                } else if (owner instanceof View && action == MotionEvent.ACTION_CANCEL) {
                    cancelConfirmation((View) owner, state);
                }

                try {
                    // Always preserve GestureStubView's own state machine. HOME paging is cancelled
                    // explicitly when the edge gesture reaches the reserved side-slide region.
                    return chain.proceed(args);
                } finally {
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        finishGesture(owner, state);
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
                            cancelConfirmation((View) state.owner, state);
                        }
                    }
                }
                return chain.proceed(args);
            });

            HookUtil.hook(onArrowActionDown, chain -> {
                Object arrow = chain.getThisObject();
                GestureState state = stateForArrow(arrow);
                if (state != null) {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (args.length >= 3
                            && args[0] instanceof Float
                            && args[1] instanceof Float
                            && args[2] instanceof Float) {
                        state.arrowLocalCenterY = (Float) args[0];
                        state.arrowStartX = (Float) args[1];
                        state.arrowExpectedHeight = (Float) args[2];
                        state.arrow = arrow;
                        // Keep these values only for Launcher-local rendering. On this OS3 build
                        // the ArrowView local Y can be negative in landscape and is not a stable
                        // screen-space handoff coordinate.
                    }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            HookUtil.hook(onArrowActionMove, chain -> {
                Object arrow = chain.getThisObject();
                GestureState state = stateForArrow(arrow);
                if (state != null) {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (args.length > 0 && args[0] instanceof Float) {
                        float offset = Math.abs((Float) args[0]);
                        state.arrowOffsetX = offset;
                        state.os4GestureProgress = os4ProgressFromOffset(offset);
                        maybeEnterOs4Split(state, arrow instanceof View ? (View) arrow : null);
                        if (state.confirmationVisible && arrow instanceof View) {
                            ((View) arrow).postInvalidateOnAnimation();
                        }
                    }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            HookUtil.hook(onArrowDraw, chain -> {
                Object arrow = chain.getThisObject();
                GestureState state = stateForArrow(arrow);
                if (state != null && arrow instanceof View) {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Canvas canvas = args.length > 0 && args[0] instanceof Canvas
                            ? (Canvas) args[0] : null;
                    if (state.confirmationVisible && canvas != null) {
                        state.arrow = arrow;
                        if (!state.confirmationDrawLogged) {
                            state.confirmationDrawLogged = true;
                            SideSlideHoldDiagnostics.log(TAG
                                    + " OS4 confirmation renderer first frame"
                                    + " view=" + ((View) arrow).getWidth()
                                    + "x" + ((View) arrow).getHeight()
                                    + " edge=" + (state.leftEdge ? "left" : "right"));
                        }
                        Launcher450Os4SidebarConfirmationRenderer.draw(
                                canvas,
                                (View) arrow,
                                state.leftEdge,
                                state.arrowLocalCenterY,
                                state.arrowStartX,
                                state.arrowExpectedHeight,
                                state.os4GestureProgress,
                                state.splitActive,
                                state.splitStartedAtUptimeMs,
                                !Float.isNaN(state.hoverAnchorY)
                                        ? state.hoverAnchorY
                                        : state.lastRawY,
                                state.confirmationStartedAtUptimeMs,
                                state.releaseStartedAtUptimeMs);
                        return null;
                    }
                    if (state.suppressStockAfterCommit) {
                        // ACTION_UP has committed Sidebar ownership. Keep OS3's bitmap Back
                        // animation out of the display list until the next gesture begins.
                        return null;
                    }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
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
        View view = (View) arrow;

        // OS3 Launcher does not guarantee GestureBackArrowView is a direct child of the
        // GestureStubView that owns the touch state. Walk the whole parent chain first.
        ViewParent parent = view.getParent();
        synchronized (STATES) {
            while (parent != null) {
                GestureState mapped = STATES.get(parent);
                if (mapped != null) {
                    mapped.arrow = arrow;
                    return mapped;
                }
                parent = parent.getParent();
            }

            // Once a sibling-window arrow has been bound, keep that identity authoritative even
            // after ACTION_UP marks the pointer stream inactive. The OS4 confirmation remains
            // visible until the real Sidebar animation-start callback takes ownership.
            for (GestureState candidate : STATES.values()) {
                if (candidate != null && candidate.arrow == arrow) {
                    return candidate;
                }
            }

            // The back arrow may live in a sibling gesture window. In that case bind it to the
            // unique currently active edge gesture. Only one side can own a pointer stream.
            GestureState active = null;
            for (GestureState candidate : STATES.values()) {
                if (candidate == null || !candidate.gestureActive || !candidate.sideStub) continue;
                if (active != null && active != candidate) {
                    return null;
                }
                active = candidate;
            }
            if (active != null) {
                active.arrow = arrow;
                if (!active.arrowFallbackBindingLogged) {
                    active.arrowFallbackBindingLogged = true;
                    SideSlideHoldDiagnostics.log(TAG
                            + " bound GestureBackArrowView to active gesture via window fallback");
                }
            }
            return active;
        }
    }

    private static void observeTouchBefore(View view, MotionEvent event, GestureState state) {
        state.lastRawX = event.getRawX();
        state.lastRawY = event.getRawY();
        state.lastLocalY = event.getY();
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            cancelDwell(view, state);
            if (state.awaitingVendorHandoff) {
                finishConfirmationVisual(state, state.committedGeneration, "next gesture");
            }
            ensureVendorHandoffReceiver(view.getContext());
            state.gestureActive = true;
            state.sideStub = isPadSideStub(view);
            state.desktopAtDown = state.sideStub && isLauncherDesktop();
            state.downX = event.getRawX();
            state.downRawY = event.getRawY();
            state.leftEdge = state.downX < view.getResources().getDisplayMetrics().widthPixels / 2f;
            state.hoverAnchorX = Float.NaN;
            state.hoverAnchorY = Float.NaN;
            state.hoverAnchorLocalY = Float.NaN;
            state.workspaceCancelled = false;
            state.suppressStockAfterCommit = false;
            state.awaitingVendorHandoff = false;
            state.committedGeneration = Integer.MIN_VALUE;
            state.confirmationStartedAtUptimeMs = 0L;
            state.releaseStartedAtUptimeMs = 0L;
            state.confirmationDrawLogged = false;
            state.arrowFallbackBindingLogged = false;
            state.arrowLocalCenterY = Float.NaN;
            state.arrowStartX = Float.NaN;
            state.arrowExpectedHeight = Float.NaN;
            state.arrowOffsetX = Float.NaN;
            state.os4GestureProgress = 0f;
            state.splitActive = false;
            state.splitStartedAtUptimeMs = 0L;
            state.clearFrozenSourceGeometry();
            state.policy.onDown();
            state.activeGeneration = state.policy.generation();
            SideSlideHoldDiagnostics.log(TAG + " DOWN generation=" + state.activeGeneration
                    + " sideStub=" + state.sideStub
                    + " desktop=" + state.desktopAtDown
                    + " edge=" + (state.leftEdge ? "left" : "right"));
            return;
        }

        if (state.sideStub && action == MotionEvent.ACTION_MOVE) {
            float rawDx = Math.abs(event.getRawX() - state.downX);
            if (Float.isNaN(state.arrowOffsetX)) {
                // Fallback only until GestureBackArrowView.onActionMove supplies the vendor
                // converted progress. OS3's public predictive-back stream already defines
                // abs(dx)/180 as its normalized 0..1 visual progress.
                state.os4GestureProgress =
                        clamp01(rawDx / HOME_VISUAL_SATURATION_PX);
                maybeEnterOs4Split(state, state.arrow instanceof View ? (View) state.arrow : null);
            }
        }

        if (!state.sideStub || !state.desktopAtDown || action != MotionEvent.ACTION_MOVE) return;

        float dx = event.getRawX() - state.downX;
        boolean inward = state.leftEdge ? dx > 0f : dx < 0f;
        boolean saturated = inward && Math.abs(dx) >= HOME_VISUAL_SATURATION_PX;
        boolean entered = state.policy.onDesktopProgress(saturated);
        if (saturated && !state.workspaceCancelled) {
            cancelWorkspacePaging(view, state);
        }
        if (!saturated) {
            state.hoverAnchorX = Float.NaN;
            state.hoverAnchorY = Float.NaN;
            state.hoverAnchorLocalY = Float.NaN;
            cancelDwell(view, state);
            cancelConfirmation(view, state);
            return;
        }

        // OS4 hold authority is continuous once the gesture enters the SideSlideHold region.
        // Keep following the finger for handoff geometry, but do not restart the 300 ms timer for
        // ordinary motion inside that region. The old 12dp positional-reset rule made activation
        // depend on an unnaturally motionless finger and caused the observed probabilistic misses.
        state.hoverAnchorX = event.getRawX();
        state.hoverAnchorY = event.getRawY();
        state.hoverAnchorLocalY = event.getY();
        if (entered || state.dwellRunnable == null) {
            cancelDwell(view, state);
            int generation = state.policy.generation();
            state.scheduledGeneration = generation;
            Runnable runnable = () -> {
                if (state.scheduledGeneration != generation) return;
                if (!state.policy.requestArm(generation)) return;
                SideSlideHoldDiagnostics.log(TAG + " HOME hold confirmed for "
                        + SideSlideHoldPolicy.HOLD_DWELL_MS + "ms"
                        + " at x=" + state.hoverAnchorX + " y=" + state.hoverAnchorY);
                prepareThenShowSidebar(view, state, generation);
            };
            state.dwellRunnable = runnable;
            view.postDelayed(runnable, SideSlideHoldPolicy.HOLD_DWELL_MS);
            SideSlideHoldDiagnostics.log(TAG + " HOME hold armed dx=" + dx);
        }
    }

    private static void cancelWorkspacePaging(View source, GestureState state) {
        Method getLauncher = applicationGetLauncherMethod;
        Method getWorkspace = launcherGetWorkspaceMethod;
        Method finishCurrentGesture = workspaceFinishCurrentGestureMethod;
        if (getLauncher == null || getWorkspace == null || finishCurrentGesture == null) return;
        try {
            Object launcher = getLauncher.invoke(null);
            if (launcher == null) return;
            Object workspace = getWorkspace.invoke(launcher);
            if (!(workspace instanceof View)) return;

            MotionEvent cancel = MotionEvent.obtain(
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    MotionEvent.ACTION_CANCEL,
                    source.getX(),
                    source.getY(),
                    0);
            try {
                ((View) workspace).dispatchTouchEvent(cancel);
            } finally {
                cancel.recycle();
            }
            finishCurrentGesture.invoke(workspace);
            state.workspaceCancelled = true;
            SideSlideHoldDiagnostics.log(TAG
                    + " HOME Workspace paging cancelled for edge side-slide");
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG
                    + " HOME Workspace cancel failed; preserve stock paging", error);
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
            if (!state.policy.requestArm(generation)) return;
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
            state.policy.onArmResult(false, generation);
            return;
        }
        Intent prepare = new Intent(SidebarCommandContract.ACTION_PREPARE)
                .setPackage(SidebarCommandContract.SECURITY_CENTER_PACKAGE)
                .putExtra(SidebarCommandContract.EXTRA_DESKTOP, state.desktopAtDown)
                .putExtra(SidebarCommandContract.EXTRA_GESTURE_Y, Math.round(state.lastRawY))
                .putExtra(SidebarCommandContract.EXTRA_GENERATION, generation);
        BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent ignoredIntent) {
                final boolean ready =
                        getResultCode() == SidebarCommandContract.RESULT_READY;
                // GestureStubView is owned by FsGestureSecondaryThread on this Launcher build.
                // Ordered-broadcast results arrive on MAIN because of the cross-process bridge,
                // so every gesture-state/UI mutation must be posted back through the View itself.
                owner.post(() -> {
                    if (!ready) {
                        state.policy.onArmResult(false, generation);
                        SideSlideHoldDiagnostics.log(TAG
                                + " Sidebar preflight unavailable/stale -> stock gesture");
                        return;
                    }
                    if (state.scheduledGeneration != generation) {
                        state.policy.onArmResult(false, generation);
                        return;
                    }

                    if (!state.policy.onArmResult(true, generation)) {
                        return;
                    }

                    // OS4 confirmation is rendered by the existing Launcher GestureBackArrowView.
                    // This replaces, rather than overlays, OS3's bitmap Back animation.
                    owner.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    state.confirmationVisible = true;
                    state.confirmationStartedAtUptimeMs = SystemClock.uptimeMillis();
                    state.releaseStartedAtUptimeMs = 0L;
                    state.splitActive = false;
                    state.splitStartedAtUptimeMs = 0L;
                    maybeEnterOs4Split(state, state.arrow instanceof View ? (View) state.arrow : null);
                    state.confirmationDrawLogged = false;
                    View arrow = resolveArrowView(owner, state);
                    if (arrow != null) {
                        state.arrow = arrow;
                        snapshotSidebarSourceGeometry(
                                owner, state, arrow, generation, "confirmation-arm");
                        arrow.postInvalidateOnAnimation();
                    } else {
                        snapshotSidebarSourceGeometry(
                                owner, state, null, generation, "confirmation-arm-fallback");
                        // GestureBackArrowView is a sibling window on this Launcher build.
                        // Invalidate the gesture root so its next traversal reaches onDraw and lets
                        // stateForArrow bind the active arrow through the existing window fallback.
                        View root = owner.getRootView();
                        if (root != null) root.postInvalidateOnAnimation();
                        SideSlideHoldDiagnostics.log(TAG
                                + " confirmation armed; ArrowView unresolved, invalidate root");
                    }
                    SideSlideHoldDiagnostics.log(TAG
                            + " Sidebar preflight ready -> Launcher haptic + OS4 confirmation"
                            + " thread=" + Thread.currentThread().getName()
                            + "; wait ACTION_UP");
                });
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
            state.policy.onArmResult(false, generation);
            SideSlideHoldDiagnostics.log(TAG + " Sidebar preflight failed", error);
        }
    }

    private static void commitRelease(View owner, GestureState state) {
        int generation = state.policy.generation();
        if (!state.policy.commitRelease(generation)) {
            return;
        }
        cancelDwell(owner, state);
        // Keep the OS4 confirmation body alive through ACTION_UP. The real vendor Sidebar owns
        // the next frame only after ISidebarAnimCallback reports its animation start.
        state.confirmationVisible = true;
        state.suppressStockAfterCommit = true;
        state.awaitingVendorHandoff = true;
        state.committedGeneration = generation;
        state.releaseStartedAtUptimeMs = SystemClock.uptimeMillis();
        state.confirmationDrawLogged = false;
        View arrow = state.arrow instanceof View ? (View) state.arrow : resolveArrowView(owner, state);
        if (arrow != null) {
            state.arrow = arrow;
            arrow.postInvalidateOnAnimation();
        }
        SideSlideHoldDiagnostics.log(TAG + " ACTION_UP -> commit Sidebar");
        showSidebar(owner, state, generation);
        if (!state.desktopAtDown) {
            forceVendorCleanupToBack(state);
        }
    }

    private static void cancelConfirmation(View owner, GestureState state) {
        if (!state.confirmationVisible) return;
        state.confirmationVisible = false;
        state.suppressStockAfterCommit = false;
        state.confirmationStartedAtUptimeMs = 0L;
        state.releaseStartedAtUptimeMs = 0L;
        state.splitActive = false;
        state.splitStartedAtUptimeMs = 0L;
        state.awaitingVendorHandoff = false;
        state.committedGeneration = Integer.MIN_VALUE;
        View arrow = state.arrow instanceof View ? (View) state.arrow : null;
        if (arrow != null) arrow.postInvalidateOnAnimation();
        SideSlideHoldDiagnostics.log(TAG + " confirmation cancelled");
    }

    private static void showSidebar(
            View owner,
            GestureState state,
            int generation) {
        Context context = owner.getContext();
        if (context == null) {
            state.policy.onArmResult(false, generation);
            return;
        }
        int[] geometry = state.hasFrozenSourceGeometry(generation)
                ? state.frozenSourceGeometry()
                : sourceGeometry(owner, state);
        Intent intent = new Intent(SidebarCommandContract.ACTION_SHOW)
                .setPackage(SidebarCommandContract.SECURITY_CENTER_PACKAGE)
                .putExtra(SidebarCommandContract.EXTRA_X, geometry[0])
                .putExtra(SidebarCommandContract.EXTRA_Y, geometry[1])
                .putExtra(SidebarCommandContract.EXTRA_WIDTH, geometry[2])
                .putExtra(SidebarCommandContract.EXTRA_HEIGHT, geometry[3])
                .putExtra(SidebarCommandContract.EXTRA_RADIUS, geometry[4])
                .putExtra(SidebarCommandContract.EXTRA_DESKTOP, state.desktopAtDown)
                .putExtra(SidebarCommandContract.EXTRA_GESTURE_Y, Math.round(state.lastRawY))
                .putExtra(SidebarCommandContract.EXTRA_GENERATION, generation);

        final boolean requestWasDesktop = state.desktopAtDown;
        BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent ignoredIntent) {
                final boolean accepted =
                        getResultCode() == SidebarCommandContract.RESULT_ACCEPTED;
                SideSlideHoldDiagnostics.log(TAG
                        + " Sidebar release show result accepted=" + accepted
                        + " desktop=" + requestWasDesktop);
                if (!accepted) {
                    owner.post(() -> finishConfirmationVisual(
                            state, generation, "vendor show rejected"));
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
            SideSlideHoldDiagnostics.log(TAG + " Sidebar show request failed", error);
        }
    }

    private static View resolveArrowView(View owner, GestureState state) {
        if (state.arrow instanceof View) return (View) state.arrow;
        View local = findArrowView(owner);
        if (local != null) return local;
        View root = owner.getRootView();
        return root != null && root != owner ? findArrowView(root) : null;
    }

    private static View findArrowView(View candidate) {
        if (candidate == null) return null;
        if (ARROW_VIEW.equals(candidate.getClass().getName())) {
            return candidate;
        }
        if (!(candidate instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) candidate;
        for (int i = 0; i < group.getChildCount(); i++) {
            View match = findArrowView(group.getChildAt(i));
            if (match != null) return match;
        }
        return null;
    }

    private static void ensureVendorHandoffReceiver(Context context) {
        if (handoffReceiverRegistered || context == null) return;
        synchronized (Launcher450SideSlideHoldHook.class) {
            if (handoffReceiverRegistered) return;
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ignored, Intent intent) {
                    if (intent == null
                            || !SidebarCommandContract.ACTION_VENDOR_ANIM_STARTED
                            .equals(intent.getAction())) {
                        return;
                    }
                    int generation = intent.getIntExtra(
                            SidebarCommandContract.EXTRA_GENERATION, Integer.MIN_VALUE);
                    GestureState match = null;
                    synchronized (STATES) {
                        for (GestureState candidate : STATES.values()) {
                            if (candidate == null
                                    || !candidate.awaitingVendorHandoff
                                    || candidate.committedGeneration != generation) {
                                continue;
                            }
                            if (match != null && match != candidate) {
                                SideSlideHoldDiagnostics.log(TAG
                                        + " vendor handoff generation ambiguous=" + generation);
                                return;
                            }
                            match = candidate;
                        }
                    }
                    if (match == null) {
                        SideSlideHoldDiagnostics.log(TAG
                                + " vendor handoff has no matching gesture generation="
                                + generation);
                        return;
                    }
                    GestureState target = match;
                    View owner = target.owner instanceof View ? (View) target.owner : null;
                    Runnable finish = () -> finishConfirmationVisual(
                            target, generation, "vendor animation started");
                    long releaseStarted = target.releaseStartedAtUptimeMs;
                    long elapsed = releaseStarted > 0L
                            ? Math.max(0L, SystemClock.uptimeMillis() - releaseStarted)
                            : OS4_RELEASE_DURATION_MS;
                    // OS4 SidebarPopAnimListener::on_anim_started hands ownership away at
                    // the actual Sidebar animation-start callback. Keep only the native 100 ms
                    // GestureBackArrowView release floor so an unusually early callback cannot
                    // truncate the release frame; do not add an artificial 680 ms hold.
                    long remaining = Math.max(0L, OS4_RELEASE_DURATION_MS - elapsed);
                    if (owner != null) {
                        if (remaining > 0L) owner.postDelayed(finish, remaining);
                        else owner.post(finish);
                    } else if (remaining > 0L) {
                        MAIN.postDelayed(finish, remaining);
                    } else {
                        finish.run();
                    }
                }
            };
            try {
                app.registerReceiver(
                        receiver,
                        new IntentFilter(SidebarCommandContract.ACTION_VENDOR_ANIM_STARTED),
                        Context.RECEIVER_EXPORTED);
                handoffReceiverRegistered = true;
                SideSlideHoldDiagnostics.log(TAG + " vendor animation handoff receiver registered");
            } catch (Throwable error) {
                SideSlideHoldDiagnostics.log(TAG
                        + " vendor animation handoff receiver registration failed", error);
            }
        }
    }

    private static void finishConfirmationVisual(
            GestureState state,
            int generation,
            String reason) {
        if (state == null) return;
        if (state.awaitingVendorHandoff
                && state.committedGeneration != Integer.MIN_VALUE
                && generation != Integer.MIN_VALUE
                && state.committedGeneration != generation) {
            return;
        }
        state.confirmationVisible = false;
        state.suppressStockAfterCommit = false;
        state.awaitingVendorHandoff = false;
        state.committedGeneration = Integer.MIN_VALUE;
        state.confirmationStartedAtUptimeMs = 0L;
        state.releaseStartedAtUptimeMs = 0L;
        state.splitActive = false;
        state.splitStartedAtUptimeMs = 0L;
        state.confirmationDrawLogged = false;
        View arrow = state.arrow instanceof View ? (View) state.arrow : null;
        if (arrow != null) arrow.postInvalidateOnAnimation();
        SideSlideHoldDiagnostics.log(TAG + " confirmation visual handoff: " + reason);
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

    private static void snapshotSidebarSourceGeometry(
            View owner,
            GestureState state,
            View arrow,
            int generation,
            String reason) {
        if (state == null || owner == null) return;
        if (arrow != null) state.arrow = arrow;
        int[] geometry = sourceGeometry(owner, state);
        state.frozenSourceX = geometry[0];
        state.frozenSourceY = geometry[1];
        state.frozenSourceWidth = geometry[2];
        state.frozenSourceHeight = geometry[3];
        state.frozenSourceRadius = geometry[4];
        state.frozenSourceGeneration = generation;
        SideSlideHoldDiagnostics.log(TAG
                + " froze Sidebar source geometry generation=" + generation
                + " reason=" + reason
                + " geometry=" + geometry[0] + "," + geometry[1]
                + " " + geometry[2] + "x" + geometry[3]
                + " r=" + geometry[4]);
    }

    private static int[] sourceGeometry(View owner, GestureState state) {
        DisplayMetrics dm = owner.getResources().getDisplayMetrics();
        int width = Math.max(1, Math.round(OS4_SOURCE_WIDTH_DP * dm.density));
        int height = Math.max(1, Math.round(OS4_SOURCE_HEIGHT_DP * dm.density));
        int radius = Math.max(1, Math.round(OS4_SOURCE_RADIUS_DP * dm.density));
        int screenWidth = Math.max(width, dm.widthPixels);
        int screenHeight = Math.max(height, dm.heightPixels);

        int x;
        float centerY;

        // The OS3 ArrowView's local Y is not screen-authoritative on this landscape build
        // (observed negative values while the visible gesture is mid-screen). Freeze the actual
        // gesture confirmation point instead. Security Center derives left/right from x, so the
        // source rect stays attached to the physical display edge.
        x = state.leftEdge ? 0 : screenWidth - width;
        centerY = !Float.isNaN(state.hoverAnchorY)
                ? state.hoverAnchorY
                : (!Float.isNaN(state.lastRawY) ? state.lastRawY : state.downRawY);

        x = Math.max(0, Math.min(x, screenWidth - width));
        int y = Math.round(centerY - (height / 2f));
        y = Math.max(0, Math.min(y, screenHeight - height));

        SideSlideHoldDiagnostics.log(TAG
                + " Sidebar source geometry side=" + (state.leftEdge ? "left" : "right")
                + " securitySideX=" + x
                + " centerY=" + centerY
                + " y=" + y
                + " source=" + width + "x" + height
                + " r=" + radius);
        return new int[]{x, y, width, height, radius};
    }

    private static void finishGesture(Object owner, GestureState state) {
        if (owner instanceof View) cancelDwell((View) owner, state);
        state.policy.onFinish();
        state.gestureActive = false;
        state.sideStub = false;
        state.desktopAtDown = false;
        if (!state.awaitingVendorHandoff) {
            state.arrow = null;
            state.confirmationVisible = false;
            state.suppressStockAfterCommit = false;
            state.confirmationStartedAtUptimeMs = 0L;
            state.confirmationDrawLogged = false;
        }
        state.workspaceCancelled = false;
        state.hoverAnchorX = Float.NaN;
        state.hoverAnchorY = Float.NaN;
        state.hoverAnchorLocalY = Float.NaN;
        if (!state.awaitingVendorHandoff) {
            state.clearFrozenSourceGeometry();
        }
        state.scheduledGeneration = Integer.MIN_VALUE;
    }

    private static float os4ProgressFromOffset(float offset) {
        Method method = convertOffsetMethod;
        if (method != null) {
            try {
                Object converted = method.invoke(null, offset);
                if (converted instanceof Number) {
                    return clamp01(((Number) converted).floatValue() / 20f);
                }
            } catch (Throwable error) {
                SideSlideHoldDiagnostics.log(
                        TAG + " convertOffset unavailable for SideSlide progress; use fallback",
                        error);
            }
        }
        return clamp01(offset / HOME_VISUAL_SATURATION_PX);
    }

    private static void maybeEnterOs4Split(GestureState state, View arrow) {
        if (state == null
                || !state.confirmationVisible
                || state.splitActive
                || state.releaseStartedAtUptimeMs > 0L
                || state.os4GestureProgress < OS4_SPLIT_START_PROGRESS) {
            return;
        }
        state.splitActive = true;
        state.splitStartedAtUptimeMs = SystemClock.uptimeMillis();
        SideSlideHoldDiagnostics.log(TAG
                + " OS4 split entered progress=" + state.os4GestureProgress
                + " generation=" + state.activeGeneration);
        if (arrow != null) arrow.postInvalidateOnAnimation();
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(value, 1f));
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
        boolean gestureActive;
        boolean sideStub;
        boolean desktopAtDown;
        boolean leftEdge;
        boolean confirmationVisible;
        boolean suppressStockAfterCommit;
        boolean awaitingVendorHandoff;
        boolean workspaceCancelled;
        boolean confirmationDrawLogged;
        boolean arrowFallbackBindingLogged;
        boolean splitActive;
        int activeGeneration = Integer.MIN_VALUE;
        int committedGeneration = Integer.MIN_VALUE;
        long confirmationStartedAtUptimeMs;
        long releaseStartedAtUptimeMs;
        long splitStartedAtUptimeMs;
        Object arrow;
        float downX;
        float downRawY;
        float lastRawX;
        float lastRawY;
        float lastLocalY;
        float hoverAnchorX = Float.NaN;
        float hoverAnchorY = Float.NaN;
        float hoverAnchorLocalY = Float.NaN;
        float arrowLocalCenterY = Float.NaN;
        float arrowStartX = Float.NaN;
        float arrowExpectedHeight = Float.NaN;
        float arrowOffsetX = Float.NaN;
        float os4GestureProgress;
        int frozenSourceX = Integer.MIN_VALUE;
        int frozenSourceY = Integer.MIN_VALUE;
        int frozenSourceWidth = Integer.MIN_VALUE;
        int frozenSourceHeight = Integer.MIN_VALUE;
        int frozenSourceRadius = Integer.MIN_VALUE;
        int frozenSourceGeneration = Integer.MIN_VALUE;

        boolean hasFrozenSourceGeometry(int generation) {
            return frozenSourceGeneration == generation
                    && frozenSourceX != Integer.MIN_VALUE
                    && frozenSourceY != Integer.MIN_VALUE
                    && frozenSourceWidth > 0
                    && frozenSourceHeight > 0
                    && frozenSourceRadius >= 0;
        }

        int[] frozenSourceGeometry() {
            return new int[]{
                    frozenSourceX,
                    frozenSourceY,
                    frozenSourceWidth,
                    frozenSourceHeight,
                    frozenSourceRadius
            };
        }

        void clearFrozenSourceGeometry() {
            frozenSourceX = Integer.MIN_VALUE;
            frozenSourceY = Integer.MIN_VALUE;
            frozenSourceWidth = Integer.MIN_VALUE;
            frozenSourceHeight = Integer.MIN_VALUE;
            frozenSourceRadius = Integer.MIN_VALUE;
            frozenSourceGeneration = Integer.MIN_VALUE;
        }

        GestureState(Object owner) {
            this.owner = owner;
        }
    }
}
