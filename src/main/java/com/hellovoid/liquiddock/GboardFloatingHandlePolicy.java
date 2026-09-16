package com.hellovoid.liquiddock;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Keeps Gboard's vendor drag implementation intact while applying user policy before terminal
 * resize/docking transitions. The policy is bound only to the structurally resolved floating
 * keyboard bottom frame and uses Gboard's semantic dock-hint tag rather than R8 names or IDs.
 */
final class GboardFloatingHandlePolicy {
    private static final Object LOCK = new Object();
    private static final String DOCK_HINT_TAG = ".floating_keyboard_dock_hint_v2";
    private static final WeakHashMap<View, WeakReference<View.OnTouchListener>> VENDOR_LISTENERS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<DragReleaseListener>> BOUND =
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
                BOUND.put(bottomFrame, new WeakReference<>(wrapper));
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

    private static boolean bottomDockingEnabled() {
        ConfigReader reader = ConfigReader.load();
        return reader.b(
                GboardGlassPreferences.BOTTOM_DOCKING_KEY,
                GboardGlassPreferences.BOTTOM_DOCKING_DEFAULT);
    }

    static boolean shouldCancelTerminalRelease(
            boolean terminalActivePointer, boolean autoResizeEnabled) {
        return terminalActivePointer && !autoResizeEnabled;
    }

    static boolean shouldSuppressDockMove(
            boolean bottomDockingEnabled, float pointerScreenY, int dockZoneTop) {
        return !bottomDockingEnabled
                && dockZoneTop != Integer.MAX_VALUE
                && pointerScreenY >= dockZoneTop;
    }

    private static <T> T dereference(WeakReference<T> reference) {
        return reference != null ? reference.get() : null;
    }

    private static final class DragReleaseListener implements View.OnTouchListener {
        private final int touchSlop;
        private View.OnTouchListener delegate;
        private WeakReference<View> dockZone = new WeakReference<>(null);
        private int activePointerId = -1;
        private float downX;
        private float downY;
        private boolean dragged;
        private boolean dockGestureSuppressed;

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
                dockGestureSuppressed = false;
            }

            boolean terminalActivePointer = (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_POINTER_UP)
                    && activePointerId >= 0
                    && event.getPointerId(actionIndex) == activePointerId;

            if (dockGestureSuppressed) {
                if (action == MotionEvent.ACTION_CANCEL || terminalActivePointer) reset();
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE && activePointerId >= 0) {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex >= 0) {
                    float dx = event.getX(pointerIndex) - downX;
                    float dy = event.getY(pointerIndex) - downY;
                    if ((dx * dx) + (dy * dy) > (float) touchSlop * touchSlop) {
                        dragged = true;
                    }

                    int dockZoneTop = resolveDockZoneTop(view);
                    float pointerScreenY = pointerScreenY(view, event, pointerIndex);
                    if (shouldSuppressDockMove(
                            bottomDockingEnabled(), pointerScreenY, dockZoneTop)) {
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        try {
                            current.onTouch(view, cancel);
                        } finally {
                            cancel.recycle();
                        }
                        dockGestureSuppressed = true;
                        return true;
                    }
                }
            }

            if (shouldCancelTerminalRelease(
                    terminalActivePointer, autoResizeAfterHandleDragEnabled())) {
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

        private float pointerScreenY(View view, MotionEvent event, int pointerIndex) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            return location[1] + event.getY(pointerIndex);
        }

        private int resolveDockZoneTop(View view) {
            View zone = dockZone.get();
            if (zone == null || zone.getRootView() != view.getRootView()) {
                View hint = findTaggedView(view.getRootView());
                if (hint == null) return Integer.MAX_VALUE;
                Object parent = hint.getParent();
                zone = parent instanceof View ? (View) parent : hint;
                dockZone = new WeakReference<>(zone);
            }
            if (zone.getHeight() <= 0) return Integer.MAX_VALUE;
            int[] location = new int[2];
            zone.getLocationOnScreen(location);
            return location[1];
        }

        private View findTaggedView(View root) {
            if (root == null) return null;
            Object tag = root.getTag();
            if (DOCK_HINT_TAG.equals(tag)) return root;
            if (!(root instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTaggedView(group.getChildAt(i));
                if (found != null) return found;
            }
            return null;
        }

        private void reset() {
            activePointerId = -1;
            dragged = false;
            dockGestureSuppressed = false;
        }
    }
}
