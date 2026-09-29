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
 * <p>Every edge gesture delivered by GestureStubView uses one shared OS4-style second-stage
 * Sidebar flow. Scene-specific HOME/App adaptation is intentionally avoided; Launcher itself owns
 * whether GestureStubView is touchable in the current scene.</p>
 */
final class Launcher450SideSlideHoldHook {
    private static final String TAG = "[DC][SideSlideHold450]";
    private static final String GESTURE_STUB = "com.miui.home.recents.GestureStubView";
    private static final String ARROW_VIEW = "com.miui.home.recents.GestureBackArrowView";
    private static final String GESTURES_BACK_CONTROLLER =
            "com.miui.home.recents.GesturesBackController";
    private static final String READY_STATE =
            "com.miui.home.recents.GestureBackArrowView$ReadyState";

    // Recovered from GestureStubView's predictive-back progress: abs(dx) / 180f, clamped to 1.
    // Used only as a visual-progress fallback before ArrowView publishes its converted offset.
    private static final float BACK_VISUAL_SATURATION_PX = 180f;
    // OS4 split_effect_renderer starts at gesture progress 0.8 and reaches its full split at 1.0.
    // Map OS3's observed 180px visual boundary to that same 0.8 point.
    private static final float OS4_SPLIT_START_PROGRESS = 0.8f;
    // OS4 GestureBackArrowView visual source geometry used by Security Center's launcher-origin
    // transform. Side authority is kept separate: Security Center derives left/right solely from
    // the first x argument, so x must come from the gesture edge rather than the Arrow view.
    private static final float OS4_SOURCE_WIDTH_DP = 24f;
    private static final float OS4_SOURCE_HEIGHT_DP = 53f;
    private static final float OS4_SOURCE_RADIUS_DP = 8f;
    // OS4 calculate_positions clamps projected placement to gesture progress 0.8. Constructor
    // field +0x1f0 is dp_to_px(30), the exact horizontal offset from the calculated endpoint to
    // the separated mini Sidebar / projected evoke center.
    private static final float OS4_PROJECTED_PROFILE_WIDTH_DP = 76f;
    private static final float OS4_PROJECTED_POSITION_PROGRESS = 0.8f;
    private static final float OS4_PROJECTED_CENTER_OFFSET_DP = 30f;
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
    private static volatile Method resetRenderPropertyMethod;
    private static volatile Method onBackCancelledMethod;
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
            Method resetRenderProperty = HookUtil.findMethodExact(
                    stubClass, "resetRenderProperty", new Class<?>[]{String.class});
            Method onBackCancelled = HookUtil.findMethodExact(
                    stubClass, "onBackCancelled", new Class<?>[0]);

            Object back = enumConstant(readyClass, "READY_STATE_BACK");
            if (back == null) {
                SideSlideHoldDiagnostics.log(TAG + " READY_STATE_BACK unavailable; fail closed");
                return false;
            }
            setReadyFinishMethod = setReadyFinish;
            convertOffsetMethod = convertOffset;
            readyStateBack = back;
            resetRenderPropertyMethod = resetRenderProperty;
            onBackCancelledMethod = onBackCancelled;

            HookUtil.hook(onTouchEvent, chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                MotionEvent event = args.length > 0 && args[0] instanceof MotionEvent
                        ? (MotionEvent) args[0] : null;
                GestureState state = stateFor(owner);
                int action = event != null ? event.getActionMasked() : -1;
                if (owner instanceof View && event != null) {
                    observeTouchBefore((View) owner, event, state);
                }
                if (owner instanceof View && action == MotionEvent.ACTION_UP) {
                    commitRelease((View) owner, state);
                } else if (owner instanceof View && action == MotionEvent.ACTION_CANCEL) {
                    cancelConfirmation((View) owner, state);
                }

                try {
                    // Launcher still owns whether GestureStubView is touchable in this scene;
                    // LiquidDock only replaces the second-stage behavior for delivered gestures.
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
                    if (state.policy.shouldConsumeVendorCompletion()
                            || state.confirmationVisible) {
                        // OS3's 1/3-screen quick-switch handler can promote the arrow to RECENT
                        // while the finger is still down. Once OS4-style Sidebar confirmation is
                        // active, keep BACK as the host state so that promotion cannot hide/reset
                        // the ArrowView underneath our mini Sidebar.
                        args[0] = readyStateBack;
                    } else if ("READY_STATE_RECENT".equals(readyName)) {
                        SideSlideHoldDiagnostics.log(TAG + " ReadyState entered RECENT");
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
                        // Once confirmation is committed by the dwell gate, pointer distance no
                        // longer owns visibility. The mini Sidebar stays latched in place; only
                        // observeTouchBefore() may begin the explicit edgeward retract after the
                        // finger crosses back over the mini Sidebar itself.
                        maybeEnterOs4Split(
                                state, arrow instanceof View ? (View) arrow : null);
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
                                stableArrowBackWidth(arrow),
                                state.os4GestureProgress,
                                state.splitActive,
                                state.splitStartedAtUptimeMs,
                                !Float.isNaN(state.hoverAnchorY)
                                        ? state.hoverAnchorY
                                        : state.lastRawY,
                                state.confirmationStartedAtUptimeMs,
                                state.releaseStartedAtUptimeMs,
                                state.releaseMiniCenterX,
                                state.interactiveMiniCenterX);
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
                    cancelNativeBackSession(chain.getThisObject());
                    SideSlideHoldDiagnostics.log(
                            TAG + " cancel native Back session; suppress vendor Back after Sidebar show acknowledgement");
                    return null;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            installed = true;
            SideSlideHoldDiagnostics.log(TAG
                    + " installed; unified authority=GestureStubView/ReadyState");
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

    private static void cancelNativeBackSession(Object owner) {
        Method method = onBackCancelledMethod;
        if (method == null || owner == null) return;
        try {
            method.invoke(owner);
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG
                    + " failed to cancel native Back session", error);
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
            state.downX = event.getRawX();
            state.downRawY = event.getRawY();
            state.leftEdge = state.downX < view.getResources().getDisplayMetrics().widthPixels / 2f;
            state.hoverAnchorX = Float.NaN;
            state.hoverAnchorY = Float.NaN;
            state.hoverAnchorLocalY = Float.NaN;
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
            state.releaseMiniCenterX = Float.NaN;
            state.interactiveMiniCenterX = Float.NaN;
            state.lockedMiniCenterX = Float.NaN;
            state.lockedMiniCenterScreenX = Float.NaN;
            state.confirmationFingerRawX = Float.NaN;
            state.retractingConfirmation = false;
            state.confirmationConsumedThisGesture = false;
            state.confirmationHapticFired = false;
            state.clearFrozenSourceGeometry();
            state.secondStageDistancePx =
                    SideSlideHoldFeatureConfig.secondStageDistancePx(ConfigReader.load());
            state.policy.onDown();
            state.activeGeneration = state.policy.generation();
            SideSlideHoldDiagnostics.log(TAG + " DOWN generation=" + state.activeGeneration
                    + " sideStub=" + state.sideStub
                    + " edge=" + (state.leftEdge ? "left" : "right")
                    + " secondStage=" + state.secondStageDistancePx + "px");
            return;
        }

        if (state.sideStub && action == MotionEvent.ACTION_MOVE) {
            float rawDx = Math.abs(event.getRawX() - state.downX);
            if (Float.isNaN(state.arrowOffsetX)) {
                // Fallback only until GestureBackArrowView.onActionMove supplies the vendor
                // converted progress. OS3's public predictive-back stream already defines
                // abs(dx)/180 as its normalized 0..1 visual progress.
                state.os4GestureProgress =
                        clamp01(rawDx / BACK_VISUAL_SATURATION_PX);
                maybeEnterOs4Split(state, state.arrow instanceof View ? (View) state.arrow : null);
            }

            if (state.confirmationVisible && state.releaseStartedAtUptimeMs <= 0L) {
                // Confirmation is a one-way latch until the user deliberately crosses back over
                // the mini Sidebar and continues toward the physical edge. Ordinary inward/outward
                // motion must neither move nor dismiss it.
                updateLatchedConfirmationDrag(view, event, state);
                return;
            }
        }

        if (!state.sideStub || action != MotionEvent.ACTION_MOVE) return;

        float dx = event.getRawX() - state.downX;
        boolean inward = state.leftEdge ? dx > 0f : dx < 0f;
        boolean secondStageReached =
                inward && Math.abs(dx) >= state.secondStageDistancePx;
        boolean entered = state.policy.onSecondStageProgress(secondStageReached);

        if (!secondStageReached) {
            state.hoverAnchorX = Float.NaN;
            state.hoverAnchorY = Float.NaN;
            state.hoverAnchorLocalY = Float.NaN;
            cancelDwell(view, state);
            return;
        }

        state.hoverAnchorX = event.getRawX();
        state.hoverAnchorY = event.getRawY();
        state.hoverAnchorLocalY = event.getY();
        if (!state.confirmationConsumedThisGesture
                && (entered || state.dwellRunnable == null)) {
            cancelDwell(view, state);
            int generation = state.policy.generation();
            state.scheduledGeneration = generation;
            Runnable runnable = () -> {
                if (state.scheduledGeneration != generation) return;
                if (!state.policy.requestArm(generation)) return;
                SideSlideHoldDiagnostics.log(TAG + " second-stage hold confirmed for "
                        + SideSlideHoldPolicy.HOLD_DWELL_MS + "ms"
                        + " at x=" + state.hoverAnchorX + " y=" + state.hoverAnchorY);
                prepareThenShowSidebar(view, state, generation);
            };
            state.dwellRunnable = runnable;
            view.postDelayed(runnable, SideSlideHoldPolicy.HOLD_DWELL_MS);
            SideSlideHoldDiagnostics.log(TAG
                    + " second-stage armed dx=" + dx
                    + " threshold=" + state.secondStageDistancePx + "px");
        }
    }

    private static boolean isPadSideStub(View view) {
        Configuration config = view.getResources().getConfiguration();
        if (config.smallestScreenWidthDp < 600) return false;
        DisplayMetrics dm = view.getResources().getDisplayMetrics();
        int width = view.getWidth();
        return width > 0 && dm.widthPixels > 0 && width < dm.widthPixels / 3;
    }

    private static void prepareThenShowSidebar(View owner, GestureState state, int generation) {
        Context context = owner.getContext();
        if (context == null) {
            state.policy.onArmResult(false, generation);
            return;
        }
        Intent prepare = new Intent(SidebarCommandContract.ACTION_PREPARE)
                .setPackage(SidebarCommandContract.SECURITY_CENTER_PACKAGE)
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
                    // This replaces, rather than overlays, OS3's bitmap Back animation. The
                    // confirmation commit is latched for the remainder of this pointer stream:
                    // exactly one haptic, one popup, and no distance-based disappearance.
                    state.confirmationConsumedThisGesture = true;
                    if (!state.confirmationHapticFired) {
                        owner.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        state.confirmationHapticFired = true;
                    }
                    state.confirmationVisible = true;
                    state.confirmationStartedAtUptimeMs = SystemClock.uptimeMillis();
                    state.releaseStartedAtUptimeMs = 0L;
                    state.splitActive = false;
                    state.splitStartedAtUptimeMs = 0L;
                    state.confirmationFingerRawX = state.lastRawX;
                    state.retractingConfirmation = false;
                    state.interactiveMiniCenterX = Float.NaN;
                    maybeEnterOs4Split(state, state.arrow instanceof View ? (View) state.arrow : null);
                    state.confirmationDrawLogged = false;
                    View arrow = resolveArrowView(owner, state);
                    if (arrow != null) {
                        state.arrow = arrow;
                        lockConfirmationGeometry(owner, state, arrow);
                        arrow.postInvalidateOnAnimation();
                    } else {
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
        state.confirmationDrawLogged = false;
        View arrow = state.arrow instanceof View ? (View) state.arrow : resolveArrowView(owner, state);
        if (arrow != null) {
            state.arrow = arrow;
            // Capture the exact visible spring position before release starts. Renderer and
            // Security Center now share this same source center until handoff completes.
            state.releaseMiniCenterX = !Float.isNaN(state.lockedMiniCenterX)
                    ? state.lockedMiniCenterX
                    : Launcher450Os4SidebarConfirmationRenderer.currentMiniSidebarCenterX(
                            arrow,
                            state.leftEdge,
                            state.arrowStartX,
                            stableArrowBackWidth(state.arrow),
                            state.os4GestureProgress,
                            state.splitActive,
                            state.splitStartedAtUptimeMs,
                            0L);
        } else {
            state.releaseMiniCenterX = Float.NaN;
        }
        state.releaseStartedAtUptimeMs = SystemClock.uptimeMillis();
        if (arrow != null) {
            arrow.postInvalidateOnAnimation();
        }
        snapshotSidebarSourceGeometry(
                owner, state, arrow, generation, "release-handoff");
        SideSlideHoldDiagnostics.log(TAG + " ACTION_UP -> commit Sidebar");
        showSidebar(owner, state, generation);
        forceVendorCleanupToBack(state);
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
        state.interactiveMiniCenterX = Float.NaN;
        state.lockedMiniCenterX = Float.NaN;
        state.lockedMiniCenterScreenX = Float.NaN;
        state.confirmationFingerRawX = Float.NaN;
        state.retractingConfirmation = false;
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
                .putExtra(SidebarCommandContract.EXTRA_GESTURE_Y, Math.round(state.lastRawY))
                .putExtra(SidebarCommandContract.EXTRA_GENERATION, generation);

        BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent ignoredIntent) {
                final boolean accepted =
                        getResultCode() == SidebarCommandContract.RESULT_ACCEPTED;
                SideSlideHoldDiagnostics.log(TAG
                        + " Sidebar release show result accepted=" + accepted);
                if (accepted) {
                    // Security Center has synchronously consumed the frozen mini-Sidebar geometry
                    // and scheduled its native transform. Stop drawing the Launcher copy on the
                    // next display frame, while keeping stock Back suppressed until the vendor
                    // animation-start callback completes the handoff state machine.
                    owner.postOnAnimation(() ->
                            hideConfirmationMiniForVendorHandoff(
                                    state, generation, "vendor show accepted"));
                } else {
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

    private static void hideConfirmationMiniForVendorHandoff(
            GestureState state,
            int generation,
            String reason) {
        if (state == null
                || !state.awaitingVendorHandoff
                || state.committedGeneration != generation) {
            return;
        }
        state.confirmationVisible = false;
        View arrow = state.arrow instanceof View ? (View) state.arrow : null;
        if (arrow != null) arrow.postInvalidateOnAnimation();
        SideSlideHoldDiagnostics.log(TAG
                + " confirmation mini released to vendor frame: " + reason);
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
        state.releaseMiniCenterX = Float.NaN;
        state.interactiveMiniCenterX = Float.NaN;
        state.lockedMiniCenterX = Float.NaN;
        state.lockedMiniCenterScreenX = Float.NaN;
        state.confirmationFingerRawX = Float.NaN;
        state.retractingConfirmation = false;
        state.confirmationDrawLogged = false;
        View arrow = state.arrow instanceof View ? (View) state.arrow : null;
        if (arrow != null) arrow.postInvalidateOnAnimation();
        restoreGestureStubWindow(state, reason);
        SideSlideHoldDiagnostics.log(TAG + " confirmation visual handoff: " + reason);
    }

    private static void restoreGestureStubWindow(GestureState state, String reason) {
        Method reset = resetRenderPropertyMethod;
        Object owner = state != null ? state.owner : null;
        if (reset == null || owner == null) return;
        try {
            reset.invoke(owner, "LiquidDockSidebarHandoff");
            SideSlideHoldDiagnostics.log(TAG
                    + " restored GestureStub window after Sidebar handoff reason=" + reason);
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(
                    TAG + " failed to restore GestureStub window after Sidebar handoff", error);
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
        float density = owner.getResources().getConfiguration().densityDpi > 0
                ? owner.getResources().getConfiguration().densityDpi / 160f
                : dm.density;
        int width = Math.max(1, Math.round(OS4_SOURCE_WIDTH_DP * density));
        int height = Math.max(1, Math.round(OS4_SOURCE_HEIGHT_DP * density));
        int radius = Math.max(1, Math.round(OS4_SOURCE_RADIUS_DP * density));
        int screenWidth = Math.max(width, dm.widthPixels);
        int screenHeight = Math.max(height, dm.heightPixels);

        float centerY = !Float.isNaN(state.hoverAnchorY)
                ? state.hoverAnchorY
                : (!Float.isNaN(state.lastRawY) ? state.lastRawY : state.downRawY);

        View arrow = state.arrow instanceof View ? (View) state.arrow : null;
        // Keep Launcher and Security Center on the same screen-space center. GestureBackArrowView
        // may move with its gesture window between the confirmation frame and ACTION_UP; using a
        // fresh getLocationOnScreen() at release produces the observed few-pixel horizontal jump.
        float projectedCenterScreenX = state.lockedMiniCenterScreenX;
        if (Float.isNaN(projectedCenterScreenX) && arrow != null) {
            float localCenter = !Float.isNaN(state.releaseMiniCenterX)
                    ? state.releaseMiniCenterX
                    : Launcher450Os4SidebarConfirmationRenderer.currentMiniSidebarCenterX(
                            arrow,
                            state.leftEdge,
                            state.arrowStartX,
                            stableArrowBackWidth(state.arrow),
                            state.os4GestureProgress,
                            state.splitActive,
                            state.splitStartedAtUptimeMs,
                            state.releaseStartedAtUptimeMs);
            if (!Float.isNaN(localCenter)) {
                int[] arrowLocation = new int[2];
                arrow.getLocationOnScreen(arrowLocation);
                projectedCenterScreenX = arrowLocation[0] + localCenter;
            }
        }
        if (Float.isNaN(projectedCenterScreenX)) {
            // Only a fallback for a missing ArrowView. The normal release path uses the exact
            // local center from the same renderer that drew the visible mini Sidebar.
            float projectedDistance = OS4_PROJECTED_PROFILE_WIDTH_DP
                    * density
                    * OS4_PROJECTED_POSITION_PROGRESS;
            float projectedOffset = OS4_PROJECTED_CENTER_OFFSET_DP * density;
            projectedCenterScreenX = state.leftEdge
                    ? projectedDistance + projectedOffset
                    : screenWidth - projectedDistance - projectedOffset;
        }

        int x = Math.round(projectedCenterScreenX - width * 0.5f);
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
        if (!state.awaitingVendorHandoff) {
            state.arrow = null;
            state.confirmationVisible = false;
            state.suppressStockAfterCommit = false;
            state.confirmationStartedAtUptimeMs = 0L;
            state.confirmationDrawLogged = false;
        }
        state.hoverAnchorX = Float.NaN;
        state.hoverAnchorY = Float.NaN;
        state.hoverAnchorLocalY = Float.NaN;
        if (!state.awaitingVendorHandoff) {
            state.clearFrozenSourceGeometry();
        }
        state.scheduledGeneration = Integer.MIN_VALUE;
    }

    private static float stableArrowBackWidth(Object arrow) {
        if (arrow == null) return Float.NaN;
        try {
            int width = HookUtil.getIntField(arrow, "mBackWidth");
            return width > 0 ? width : Float.NaN;
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " unable to read GestureBackArrowView#mBackWidth", error);
            return Float.NaN;
        }
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
        return clamp01(offset / BACK_VISUAL_SATURATION_PX);
    }

    private static void lockConfirmationGeometry(
            View owner, GestureState state, View arrow) {
        if (owner == null || state == null || arrow == null) return;
        float center = Launcher450Os4SidebarConfirmationRenderer.settledMiniSidebarCenterX(
                arrow,
                state.leftEdge,
                state.arrowStartX,
                stableArrowBackWidth(state.arrow),
                state.os4GestureProgress);
        if (Float.isNaN(center)) return;
        int[] location = new int[2];
        arrow.getLocationOnScreen(location);
        state.lockedMiniCenterX = center;
        state.lockedMiniCenterScreenX = location[0] + center;
        state.interactiveMiniCenterX = Float.NaN;
        SideSlideHoldDiagnostics.log(TAG
                + " confirmation mini locked localX=" + center
                + " screenX=" + state.lockedMiniCenterScreenX);
    }

    private static void updateLatchedConfirmationDrag(
            View owner, MotionEvent event, GestureState state) {
        if (owner == null || event == null || state == null || !state.confirmationVisible) return;

        View arrow = state.arrow instanceof View ? (View) state.arrow : resolveArrowView(owner, state);
        if (arrow != null && Float.isNaN(state.lockedMiniCenterScreenX)) {
            state.arrow = arrow;
            lockConfirmationGeometry(owner, state, arrow);
        }

        float currentRawX = event.getRawX();
        float previousRawX = state.confirmationFingerRawX;
        state.confirmationFingerRawX = currentRawX;

        if (Float.isNaN(state.lockedMiniCenterScreenX)
                || Float.isNaN(state.lockedMiniCenterX)
                || arrow == null) {
            // Even when the ArrowView cannot be resolved, keep the confirmation latched rather
            // than falling back to the old distance-based cancellation behavior.
            return;
        }

        DisplayMetrics dm = owner.getResources().getDisplayMetrics();
        float density = owner.getResources().getConfiguration().densityDpi > 0
                ? owner.getResources().getConfiguration().densityDpi / 160f
                : dm.density;
        float miniHalfWidth = OS4_SOURCE_WIDTH_DP * density * 0.5f;
        float outerBoundary = state.leftEdge
                ? state.lockedMiniCenterScreenX - miniHalfWidth
                : state.lockedMiniCenterScreenX + miniHalfWidth;

        // "Cross the Dock" means the finger must pass the whole miniature body, not merely its
        // center line. The previous center crossing made a small outward correction look like an
        // explicit retract and is exactly what the latest device log shows before disappearance.
        boolean crossedTowardEdge;
        if (state.leftEdge) {
            crossedTowardEdge = !Float.isNaN(previousRawX)
                    && previousRawX >= outerBoundary
                    && currentRawX < outerBoundary
                    && currentRawX < previousRawX;
        } else {
            crossedTowardEdge = !Float.isNaN(previousRawX)
                    && previousRawX <= outerBoundary
                    && currentRawX > outerBoundary
                    && currentRawX > previousRawX;
        }

        if (!state.retractingConfirmation && crossedTowardEdge) {
            state.retractingConfirmation = true;
            SideSlideHoldDiagnostics.log(TAG
                    + " confirmation retract started after crossing full mini Sidebar"
                    + " boundary=" + outerBoundary);
        }
        if (!state.retractingConfirmation) return;

        float screenWidth = Math.max(1f, dm.widthPixels);
        float edgeDistance = state.leftEdge
                ? Math.max(0f, currentRawX)
                : Math.max(0f, screenWidth - currentRawX);
        float lockedEdgeDistance = state.leftEdge
                ? Math.max(1f, state.lockedMiniCenterScreenX)
                : Math.max(1f, screenWidth - state.lockedMiniCenterScreenX);
        float retractProgress = clamp01(edgeDistance / lockedEdgeDistance);
        float outsideCenter = state.leftEdge
                ? -OS4_SOURCE_WIDTH_DP * density * 0.5f
                : arrow.getWidth() + OS4_SOURCE_WIDTH_DP * density * 0.5f;
        state.interactiveMiniCenterX = outsideCenter
                + (state.lockedMiniCenterX - outsideCenter) * retractProgress;
        arrow.postInvalidateOnAnimation();

        float dismissDistance = 4f * density;
        if (edgeDistance <= dismissDistance) {
            state.policy.onSecondStageProgress(false);
            cancelDwell(owner, state);
            cancelConfirmation(owner, state);
            SideSlideHoldDiagnostics.log(TAG + " confirmation retracted through screen edge");
        }
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
        int secondStageDistancePx =
                SideSlideHoldFeatureConfig.DEFAULT_SECOND_STAGE_DISTANCE_PX;
        boolean gestureActive;
        boolean sideStub;
        boolean leftEdge;
        boolean confirmationVisible;
        boolean suppressStockAfterCommit;
        boolean awaitingVendorHandoff;
        boolean confirmationDrawLogged;
        boolean arrowFallbackBindingLogged;
        boolean splitActive;
        boolean retractingConfirmation;
        boolean confirmationConsumedThisGesture;
        boolean confirmationHapticFired;
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
        float releaseMiniCenterX = Float.NaN;
        float interactiveMiniCenterX = Float.NaN;
        float lockedMiniCenterX = Float.NaN;
        float lockedMiniCenterScreenX = Float.NaN;
        float confirmationFingerRawX = Float.NaN;
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
