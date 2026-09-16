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
            View.OnTouchListener vendor = dereference(VENDOR_LISTENERS.get(bottomFrame));
            if (vendor == null) return;
            wrapper = dereference(BOUND.get(bottomFrame));
            if (wrapper == null) {
                wrapper = new DragReleaseListener(bottomFrame, vendor);
                wrapper.setObserver(dereference(OBSERVERS.get(bottomFrame)));
                BOUND.put(bottomFrame, new WeakReference<>(wrapper));
            } else {
                wrapper.setDelegate(vendor);
            }
        }
        bottomFrame.setOnTouchListener(wrapper);
    }

    static void observe(View bottomFrame, DragObserver observer) {
        if (bottomFrame == null || observer == null) return;
        synchronized (LOCK) {
            OBSERVERS.put(bottomFrame, new WeakReference<>(observer));
            DragReleaseListener wrapper = dereference(BOUND.get(bottomFrame));
            if (wrapper != null) wrapper.setObserver(observer);
        }
    }

    static void clearObserver(View bottomFrame, DragObserver observer) {
        if (bottomFrame == null || observer == null) return;
        synchronized (LOCK) {
            DragObserver current = dereference(OBSERVERS.get(bottomFrame));
            if (current == observer) OBSERVERS.remove(bottomFrame);
            DragReleaseListener wrapper = dereference(BOUND.get(bottomFrame));
            if (wrapper != null) wrapper.clearObserver(observer);
        }
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
        private final float touchSlopSquared;
        private final GboardFloatingDragGestureState dragState =
                new GboardFloatingDragGestureState();
        private View.OnTouchListener delegate;
        private WeakReference<DragObserver> observerRef;
        private int activePointerId = -1;

        DragReleaseListener(View view, View.OnTouchListener delegate) {
            this.delegate = delegate;
            int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
            touchSlopSquared = (float) touchSlop * touchSlop;
        }

        void setDelegate(View.OnTouchListener value) {
            if (value != null && value != this) delegate = value;
        }

        void setObserver(DragObserver observer) {
            observerRef = observer != null ? new WeakReference<>(observer) : null;
        }

        void clearObserver(DragObserver observer) {
            DragObserver current = dereference(observerRef);
            if (current == observer) observerRef = null;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            View.OnTouchListener current = delegate;
            if (current == null || event == null) return false;

            int action = event.getActionMasked();
            int actionIndex = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN) {
                activePointerId = event.getPointerId(actionIndex);
                dragState.onDown(
                        activePointerId,
                        event.getX(actionIndex),
                        event.getY(actionIndex));
            } else if (action == MotionEvent.ACTION_MOVE && activePointerId >= 0) {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex >= 0) {
                    dispatch(dragState.onMove(
                            activePointerId,
                            event.getX(pointerIndex),
                            event.getY(pointerIndex),
                            touchSlopSquared));
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
                    cancel.recycle();
                    dispatch(dragState.onTerminal(activePointerId));
                    activePointerId = -1;
                }
            }

            boolean handled = current.onTouch(view, event);
            if (action == MotionEvent.ACTION_CANCEL) {
                dispatch(dragState.onCancel());
                activePointerId = -1;
            } else if (terminalActivePointer) {
                dispatch(dragState.onTerminal(activePointerId));
                activePointerId = -1;
            }
            return handled;
        }

        private void dispatch(GboardFloatingDragGestureState.Signal signal) {
            if (signal == null || signal == GboardFloatingDragGestureState.Signal.NONE) return;
            DragObserver observer = dereference(observerRef);
            if (observer == null) return;
            if (signal == GboardFloatingDragGestureState.Signal.STARTED) observer.onDragStarted();
            else if (signal == GboardFloatingDragGestureState.Signal.ENDED) observer.onDragEnded();
        }
    }
}
