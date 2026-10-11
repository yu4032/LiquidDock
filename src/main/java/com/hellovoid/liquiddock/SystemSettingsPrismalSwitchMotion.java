package com.hellovoid.liquiddock;

import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Native frame-clock approximation of GuiOnTouchPrismalToggle's PrismalSpringMotion.
 * Motion/checked events are observed after the vendor handlers run. We NEVER
 * call setChecked(), performClick(), dispatchTouchEvent() or cancel a MIUIX gesture.
 *
 * Reference geometry: 64x28dp track, 40x24dp thumb, 20dp travel, pressed
 * thumb scale 1.5; pressed motion stiffness ~986.96, damping ratio 0.6.
 */
final class SystemSettingsPrismalSwitchMotion {
    private static final WeakHashMap<CompoundButton, State> STATES = new WeakHashMap<>();
    private static final String TAG = "[DC][SettingsSwitch]";
    private static int traceCount;
    private SystemSettingsPrismalSwitchMotion() {}

    static final class State implements Choreographer.FrameCallback {
        private final WeakReference<CompoundButton> owner;
        float fraction;
        float fractionVelocity;
        float press;
        float pressVelocity;
        float pressTarget;
        float target;
        float velocityShape;
        boolean pointerDown;
        boolean dragging;
        float downX;
        float downFraction;
        long lastFrameNanos;
        boolean scheduled;

        State(CompoundButton button) {
            owner = new WeakReference<>(button);
            fraction = target = button.isChecked() ? 1f : 0f;
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            scheduled = false;
            CompoundButton button = owner.get();
            if (button == null || !button.isAttachedToWindow()) {
                lastFrameNanos = 0;
                return;
            }
            try {
                // Do not send state writes to vendor controls. Animation only.
                float delta = lastFrameNanos == 0
                        ? 1f / 120f
                        : clamp((frameTimeNanos - lastFrameNanos) * 1e-9f,
                                1f / 240f, 1f / 30f);
                lastFrameNanos = frameTimeNanos;
                // Two substeps cap spring overshoot on 60/120/165Hz screens.
                int steps = delta > 1f / 90f ? 2 : 1;
                for (int i = 0; i < steps; i++) {
                    float dt = delta / steps;
                    if (!pointerDown || !dragging) {
                        float error = target - fraction;
                        fractionVelocity += (error * 590f - fractionVelocity * 37.0f) * dt;
                        fraction += fractionVelocity * dt;
                        fraction = clamp(fraction, -0.10f, 1.10f);
                    }
                    float pressedError = pressTarget - press;
                    pressVelocity += (pressedError * 986.96f
                            - pressVelocity * 37.7f) * dt;
                    press = clamp(press + pressVelocity * dt, -0.08f, 1.08f);
                }
                // Retain spring velocity as a short-lived horizontal squeeze.
                velocityShape = clamp(fractionVelocity / 50f, -0.25f, 0.25f);
                if (Math.abs(target - fraction) < 0.001f &&
                        Math.abs(fractionVelocity) < 0.015f && !dragging) {
                    fraction = target;
                    fractionVelocity = 0f;
                }
                if (Math.abs(pressTarget - press) < 0.001f &&
                        Math.abs(pressVelocity) < 0.015f) {
                    press = pressTarget;
                    pressVelocity = 0f;
                }
                button.postInvalidateOnAnimation();
                if (Math.abs(target - fraction) >= 0.001f ||
                        Math.abs(fractionVelocity) >= 0.015f ||
                        Math.abs(pressTarget - press) >= 0.001f ||
                        Math.abs(pressVelocity) >= 0.015f || pointerDown) {
                    requestFrame();
                } else {
                    lastFrameNanos = 0;
                }
            } catch (Throwable error) {
                lastFrameNanos = 0;
                Api101Bridge.log(TAG + " Prismal motion frame aborted: "
                        + error.getClass().getSimpleName() + " " + error.getMessage());
            }
        }

        void requestFrame() {
            if (scheduled) return;
            scheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        void targetChanged(boolean checked) {
            float next = checked ? 1f : 0f;
            if (target != next) {
                target = next;
                if (!pointerDown) requestFrame();
            }
        }

        void handleTouch(CompoundButton button, MotionEvent event) {
            if (!button.isEnabled()) return;
            final int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    pointerDown = true;
                    dragging = false;
                    downX = event.getX();
                    downFraction = fraction;
                    fractionVelocity = 0f;
                    pressTarget = 1f;
                    requestFrame();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (pointerDown) {
                        float dx = event.getX() - downX;
                        if (Math.abs(dx) > 0.5f) dragging = true;
                        if (dragging) {
                            float dragWidth = Math.max(1f,
                                    button.getResources().getDisplayMetrics().density * 20f);
                            boolean rtl = button.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                            float sign = rtl ? -1f : 1f;
                            float next = clamp(downFraction + sign * dx / dragWidth, 0f, 1f);
                            fractionVelocity = clamp((next - fraction) * 8f, -18f, 18f);
                            fraction = next;
                            button.postInvalidateOnAnimation();
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    pointerDown = false;
                    dragging = false;
                    pressTarget = 0f;
                    targetChanged(button.isChecked()); // native authority, after its handler
                    requestFrame();
                    break;
                default:
                    break;
            }
        }
    }

    static State forView(CompoundButton button) {
        State s = STATES.get(button);
        if (s == null) {
            s = new State(button);
            STATES.put(button, s);
        }
        if (!s.pointerDown) s.targetChanged(button.isChecked());
        return s;
    }

    static void observeNativeTouch(CompoundButton button, MotionEvent event) {
        if (button == null || event == null || !button.isAttachedToWindow()) return;
        try {
            forView(button).handleTouch(button, event);
            if (traceCount++ < 8 && event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                Api101Bridge.log(TAG + " Prismal gesture observed="
                        + MotionEvent.actionToString(event.getActionMasked())
                        + " nativeChecked=" + button.isChecked());
            }
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " Prismal touch observation skipped: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }

    static void observeNativeChecked(CompoundButton button) {
        if (button == null || !button.isAttachedToWindow()) return;
        try {
            forView(button).targetChanged(button.isChecked());
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " Prismal checked observation skipped: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
        }
    }

    static float clamp(float x, float low, float high) {
        return Math.max(low, Math.min(high, x));
    }
}
