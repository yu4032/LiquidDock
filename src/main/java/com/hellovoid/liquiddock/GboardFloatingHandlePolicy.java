package com.hellovoid.liquiddock;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Keeps Gboard's vendor drag implementation intact while optionally translating the terminal drag
 * release into cancellation. Gboard's drag listener preserves the moved position on cancellation,
 * but does not enter its editing/resize UI. The policy is bound only to the structurally resolved
 * floating-keyboard bottom frame.
 */
final class GboardFloatingHandlePolicy {
    private static final Object LOCK = new Object();
    private static final WeakHashMap<View, View.OnTouchListener> VENDOR_LISTENERS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, DragReleaseListener> BOUND = new WeakHashMap<>();
    private static boolean installed;

    private GboardFloatingHandlePolicy() {}

    static boolean install() {
        synchronized (LOCK) {
            if (installed) return true;
            try {
                Method setter = View.class.getDeclaredMethod(
                        "setOnTouchListener", View.OnTouchListener.class);
                HookUtil.hook(setter, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object owner = chain.getThisObject();
                    if (owner instanceof View && args.length == 1) {
                        View view = (View) owner;
                        View.OnTouchListener listener = args[0] instanceof View.OnTouchListener
                                ? (View.OnTouchListener) args[0] : null;
                        synchronized (LOCK) {
                            if (listener == null) {
                                VENDOR_LISTENERS.remove(view);
                                BOUND.remove(view);
                            } else if (!(listener instanceof DragReleaseListener)) {
                                VENDOR_LISTENERS.put(view, listener);
                                DragReleaseListener wrapper = BOUND.get(view);
                                if (wrapper != null) {
                                    wrapper.setDelegate(listener);
                                    args[0] = wrapper;
                                }
                            }
                        }
                    }
                    return chain.proceed(args);
                });
                installed = true;
                return true;
            } catch (Throwable error) {
                return false;
            }
        }
    }

    static void bind(View bottomFrame) {
        if (bottomFrame == null) return;
        DragReleaseListener wrapper;
        synchronized (LOCK) {
            View.OnTouchListener vendor = VENDOR_LISTENERS.get(bottomFrame);
            if (vendor == null) return;
            wrapper = BOUND.get(bottomFrame);
            if (wrapper == null) {
                wrapper = new DragReleaseListener(bottomFrame, vendor);
                BOUND.put(bottomFrame, wrapper);
            } else {
                wrapper.setDelegate(vendor);
            }
        }
        bottomFrame.setOnTouchListener(wrapper);
    }

    private static boolean autoResizeAfterHandleDragEnabled() {
        ConfigReader reader = ConfigReader.load();
        return reader.b(
                GboardGlassPreferences.AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY,
                GboardGlassPreferences.AUTO_RESIZE_AFTER_HANDLE_DRAG_DEFAULT);
    }

    private static final class DragReleaseListener implements View.OnTouchListener {
        private final int touchSlop;
        private View.OnTouchListener delegate;
        private int activePointerId = -1;
        private float downX;
        private float downY;
        private boolean dragged;

        DragReleaseListener(View view, View.OnTouchListener delegate) {
            this.delegate = delegate;
            touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        }

        void setDelegate(View.OnTouchListener value) {
            if (value != null && value != this) delegate = value;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            View.OnTouchListener current = delegate;
            if (current == null || event == null) return false;

            int action = event.getActionMasked();
            int actionIndex = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN) {
                activePointerId = event.getPointerId(actionIndex);
                downX = event.getX(actionIndex);
                downY = event.getY(actionIndex);
                dragged = false;
            } else if (action == MotionEvent.ACTION_MOVE && activePointerId >= 0) {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex >= 0) {
                    float dx = event.getX(pointerIndex) - downX;
                    float dy = event.getY(pointerIndex) - downY;
                    if ((dx * dx) + (dy * dy) > (float) touchSlop * touchSlop) {
                        dragged = true;
                    }
                }
            }

            boolean terminalActivePointer = (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_POINTER_UP)
                    && activePointerId >= 0
                    && event.getPointerId(actionIndex) == activePointerId;
            if (terminalActivePointer && dragged && !autoResizeAfterHandleDragEnabled()) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                try {
                    return current.onTouch(view, cancel);
                } finally {
                    cancel.recycle();
                    reset();
                }
            }

            boolean handled = current.onTouch(view, event);
            if (action == MotionEvent.ACTION_CANCEL || terminalActivePointer) reset();
            return handled;
        }

        private void reset() {
            activePointerId = -1;
            dragged = false;
        }
    }
}
