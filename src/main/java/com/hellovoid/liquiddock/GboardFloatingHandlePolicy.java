package com.hellovoid.liquiddock;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Keeps Gboard's vendor drag implementation intact while optionally translating the terminal drag
 * release into cancellation. Gboard's drag listener preserves the moved position on cancellation,
 * but does not enter its editing/resize UI. The policy is bound only to the structurally resolved
 * floating-keyboard bottom frame.
 */
final class GboardFloatingHandlePolicy {
    interface DragObserver {
        void onDragStarted();
        void onDragEnded();
    }

    private static final Object LOCK = new Object();
    private static final WeakHashMap<View, WeakReference<View.OnTouchListener>> VENDOR_LISTENERS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<DragReleaseListener>> BOUND =
            new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<DragObserver>> OBSERVERS =
            new WeakHashMap<>();
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
                                OBSERVERS.remove(view);
                            } else if (!(listener instanceof DragReleaseListener)) {
                                VENDOR_LISTENERS.put(view, new WeakReference<>(listener));
                                DragReleaseListener wrapper = dereference(BOUND.get(view));
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
                GboardDragDiagnostics.log("HANDLE_HOOK_OK");
                return true;
            } catch (Throwable error) {
                GboardDragDiagnostics.log("HANDLE_HOOK_FAIL " + error.getClass().getName(), error);
                return false;
            }
        }
    }

    static void bind(View bottomFrame) {
        if (bottomFrame == null) return;
        DragReleaseListener wrapper;
        synchronized (LOCK) {
            View.OnTouchListener vendor = dereference(VENDOR_LISTENERS.get(bottomFrame));
            if (vendor == null) {
                GboardDragDiagnostics.log("HANDLE_BIND_VENDOR_MISSING view="
                        + bottomFrame.getClass().getName());
                return;
            }
            wrapper = dereference(BOUND.get(bottomFrame));
            if (wrapper == null) {
                wrapper = new DragReleaseListener(bottomFrame, vendor);
                BOUND.put(bottomFrame, new WeakReference<>(wrapper));
            } else {
                wrapper.setDelegate(vendor);
            }
        }
        bottomFrame.setOnTouchListener(wrapper);
        GboardDragDiagnostics.log("HANDLE_BIND_OK view=" + bottomFrame.getClass().getName());
    }

    static void observe(View bottomFrame, DragObserver observer) {
        if (bottomFrame == null) return;
        synchronized (LOCK) {
            if (observer == null) OBSERVERS.remove(bottomFrame);
            else OBSERVERS.put(bottomFrame, new WeakReference<>(observer));
        }
        GboardDragDiagnostics.log("HANDLE_OBSERVER " + (observer != null ? "SET" : "CLEARED"));
    }

    static void clearObserver(View bottomFrame, DragObserver observer) {
        if (bottomFrame == null) return;
        synchronized (LOCK) {
            DragObserver current = dereference(OBSERVERS.get(bottomFrame));
            if (current == null || current == observer) OBSERVERS.remove(bottomFrame);
        }
        GboardDragDiagnostics.log("HANDLE_OBSERVER_CLEARED");
    }

    private static void notifyDragStarted(View view) {
        GboardDragDiagnostics.log("DRAG_START owner=" + view.getClass().getName());
        DragObserver observer;
        synchronized (LOCK) { observer = dereference(OBSERVERS.get(view)); }
        GboardDragDiagnostics.log("DRAG_START observer=" + (observer != null));
        if (observer != null) observer.onDragStarted();
    }

    private static void notifyDragEnded(View view) {
        GboardDragDiagnostics.log("DRAG_END owner=" + view.getClass().getName());
        DragObserver observer;
        synchronized (LOCK) { observer = dereference(OBSERVERS.get(view)); }
        GboardDragDiagnostics.log("DRAG_END observer=" + (observer != null));
        if (observer != null) observer.onDragEnded();
    }

    private static boolean autoResizeAfterHandleDragEnabled() {
        ConfigReader reader = ConfigReader.load();
        return reader.b(
                GboardGlassPreferences.AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY,
                GboardGlassPreferences.AUTO_RESIZE_AFTER_HANDLE_DRAG_DEFAULT);
    }

    static boolean shouldCancelTerminalRelease(
            boolean terminalActivePointer, boolean autoResizeEnabled) {
        return terminalActivePointer && !autoResizeEnabled;
    }

    private static <T> T dereference(WeakReference<T> reference) {
        return reference != null ? reference.get() : null;
    }

    private static final class DragReleaseListener implements View.OnTouchListener {
        private final View owner;
        private final int touchSlop;
        private View.OnTouchListener delegate;
        private int activePointerId = -1;
        private float downX;
        private float downY;
        private boolean dragged;

        DragReleaseListener(View view, View.OnTouchListener delegate) {
            owner = view;
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
                GboardDragDiagnostics.log("TOUCH_DOWN x=" + downX + " y=" + downY
                        + " slop=" + touchSlop);
            } else if (action == MotionEvent.ACTION_MOVE && activePointerId >= 0) {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex >= 0) {
                    float dx = event.getX(pointerIndex) - downX;
                    float dy = event.getY(pointerIndex) - downY;
                    if (!dragged && (dx * dx) + (dy * dy) > (float) touchSlop * touchSlop) {
                        dragged = true;
                        notifyDragStarted(owner);
                    }
                }
            }

            boolean terminalActivePointer = (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_POINTER_UP)
                    && activePointerId >= 0
                    && event.getPointerId(actionIndex) == activePointerId;
            if (shouldCancelTerminalRelease(
                    terminalActivePointer, autoResizeAfterHandleDragEnabled())) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                try {
                    return current.onTouch(view, cancel);
                } finally {
                    finishDragIfNeeded();
                    cancel.recycle();
                    reset();
                }
            }

            boolean handled = current.onTouch(view, event);
            if (action == MotionEvent.ACTION_CANCEL || terminalActivePointer) {
                finishDragIfNeeded();
                reset();
            }
            return handled;
        }

        private void finishDragIfNeeded() {
            if (dragged) notifyDragEnded(owner);
        }

        private void reset() {
            activePointerId = -1;
            dragged = false;
        }
    }
}
