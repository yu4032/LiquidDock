package com.hellovoid.liquiddock;

import android.view.MotionEvent;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Keeps Gboard's vendor drag implementation intact while applying user policy around the
 * structurally resolved floating-keyboard bottom-frame listener. Bottom docking is disabled by
 * masking only Gboard's semantic dock-icon lookup while that vendor listener is synchronously
 * executing; no coordinate threshold or R8/private app symbol is used.
 */
final class GboardFloatingHandlePolicy {
    private static final Object LOCK = new Object();
    private static final String DOCK_ICON_TAG = ".icon.floating_keyboard_dock_hint_v2";
    private static final ThreadLocal<Integer> DOCK_HIT_MASK_DEPTH =
            ThreadLocal.withInitial(() -> 0);
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

                Method findViewById = View.class.getDeclaredMethod("findViewById", int.class);
                HookUtil.hook(findViewById, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    if (DOCK_HIT_MASK_DEPTH.get() <= 0 || !(result instanceof View)) {
                        return result;
                    }
                    View found = (View) result;
                    return shouldMaskDockHitResult(false, found.getTag()) ? null : result;
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
                wrapper = new DragReleaseListener(vendor);
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

    static boolean shouldMaskDockHitResult(boolean bottomDockingEnabled, Object viewTag) {
        return !bottomDockingEnabled && DOCK_ICON_TAG.equals(viewTag);
    }

    private static <T> T dereference(WeakReference<T> reference) {
        return reference != null ? reference.get() : null;
    }

    private static boolean dispatchVendor(
            View.OnTouchListener listener, View view, MotionEvent event) {
        if (bottomDockingEnabled()) {
            return listener.onTouch(view, event);
        }
        int previousDepth = DOCK_HIT_MASK_DEPTH.get();
        DOCK_HIT_MASK_DEPTH.set(previousDepth + 1);
        try {
            return listener.onTouch(view, event);
        } finally {
            if (previousDepth == 0) {
                DOCK_HIT_MASK_DEPTH.remove();
            } else {
                DOCK_HIT_MASK_DEPTH.set(previousDepth);
            }
        }
    }

    private static final class DragReleaseListener implements View.OnTouchListener {
        private View.OnTouchListener delegate;
        private int activePointerId = -1;

        DragReleaseListener(View.OnTouchListener delegate) {
            this.delegate = delegate;
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
                    return dispatchVendor(current, view, cancel);
                } finally {
                    cancel.recycle();
                    reset();
                }
            }

            boolean handled = dispatchVendor(current, view, event);
            if (action == MotionEvent.ACTION_CANCEL || terminalActivePointer) reset();
            return handled;
        }

        private void reset() {
            activePointerId = -1;
        }
    }
}
