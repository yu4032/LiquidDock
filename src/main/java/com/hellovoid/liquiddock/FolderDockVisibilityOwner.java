package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Hide the shared native HotSeats hierarchy during a folder session.
 *
 * Decompiled HotSeats#setAlpha only updates children, so other child animators
 * can make individual views visible while HotSeats.mAlpha remains zero.
 * INVISIBLE gates the entire subtree, independently of those alpha writers.
 * The native FolderState still owns all Dock window transactions.
 */
final class FolderDockVisibilityOwner {
    private static final String TAG = "[DC][FolderDockVisibility]";
    private static WeakReference<Object> launcherRef = new WeakReference<>(null);
    private static WeakReference<View> hotSeatsRef = new WeakReference<>(null);
    private static int previousVisibility = View.VISIBLE;
    private static boolean open;
    private static boolean awaitingNativeClose;
    private static boolean hookInstalled;
    private static int suppressedVisibilityWrites;

    private static final View.OnAttachStateChangeListener ATTACH =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    if (open && hotSeatsRef.get() == view) view.setVisibility(View.INVISIBLE);
                }
                @Override public void onViewDetachedFromWindow(View view) {}
            };

    private FolderDockVisibilityOwner() {}

    static void install(ClassLoader loader) {
        if (hookInstalled || loader == null) return;
        try {
            Class<?> hotSeats = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeats", false, loader);
            // HotSeats overrides this exact method and delegates to super.
            Method setter = hotSeats.getDeclaredMethod("setVisibility", int.class);
            setter.setAccessible(true);
            HookUtil.hook(setter, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (open && chain.getThisObject() == hotSeatsRef.get()
                        && args.length == 1 && args[0] instanceof Integer
                        && (Integer) args[0] != View.INVISIBLE) {
                    suppressedVisibilityWrites++;
                    return chain.proceed(new Object[]{View.INVISIBLE});
                }
                return chain.proceed(args);
            });
            hookInstalled = true;
            MainHook.log(TAG + " HotSeats.setVisibility subtree fence installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " HotSeats visibility fence unavailable: " + error);
        }
    }

    static void rememberLauncher(Object launcher) {
        if (launcher != null &&
                "com.miui.home.launcher.Launcher".equals(launcher.getClass().getName())) {
            launcherRef = new WeakReference<>(launcher);
        }
    }

    static void onFolderOpened() { onFolderOpened(null); }

    static void onFolderOpened(Object dispatcher) {
        Object launcher = launcherRef.get();
        if (launcher == null && dispatcher != null) {
            try {
                Class<?> application = Class.forName("com.miui.home.launcher.Application",
                        false, dispatcher.getClass().getClassLoader());
                Method method = application.getDeclaredMethod("getLauncher");
                method.setAccessible(true);
                launcher = method.invoke(null);
                rememberLauncher(launcher);
            } catch (Throwable error) {
                MainHook.log(TAG + " Launcher fallback unavailable: " + error);
            }
        }
        if (launcher == null) return;
        try {
            Field controllerField = launcher.getClass().getDeclaredField("mDockController");
            controllerField.setAccessible(true);
            Object controller = controllerField.get(launcher);
            if (controller == null) return;
            Field hotSeatsField = controller.getClass().getDeclaredField("mHotSeatsView");
            hotSeatsField.setAccessible(true);
            Object candidate = hotSeatsField.get(controller);
            if (!(candidate instanceof View)) return;
            View hotSeats = (View) candidate;
            if (open && hotSeatsRef.get() == hotSeats) {
                awaitingNativeClose = false;
                if (hotSeats.getVisibility() != View.INVISIBLE) {
                    hotSeats.setVisibility(View.INVISIBLE);
                }
                return;
            }
            restore();
            previousVisibility = hotSeats.getVisibility();
            hotSeatsRef = new WeakReference<>(hotSeats);
            open = true;
            awaitingNativeClose = false;
            suppressedVisibilityWrites = 0;
            hotSeats.addOnAttachStateChangeListener(ATTACH);
            hotSeats.setVisibility(View.INVISIBLE);
            MainHook.log(TAG + " HotSeats subtree hidden parent="
                    + (hotSeats.getParent() == null ? "none"
                    : hotSeats.getParent().getClass().getSimpleName())
                    + " id=" + Integer.toHexString(System.identityHashCode(hotSeats)));
        } catch (Throwable error) {
            MainHook.log(TAG + " HotSeats resolution failed: " + error);
        }
    }

    static void onNativeCloseStarted(boolean animated) {
        awaitingNativeClose = open && animated;
    }

    /** Semantic CloseFolder and FolderCling.close can precede the OEM spring. */
    static void onFolderClosed() {
        if (!awaitingNativeClose) restore();
    }

    static void onNativeBackgroundCloseFinished() { restore(); }

    private static void restore() {
        View hotSeats = hotSeatsRef.get();
        open = false;
        awaitingNativeClose = false;
        hotSeatsRef.clear();
        if (hotSeats == null) return;
        hotSeats.removeOnAttachStateChangeListener(ATTACH);
        hotSeats.setVisibility(previousVisibility);
        MainHook.log(TAG + " HotSeats subtree restored visibility="
                + previousVisibility + " preventedNativeVisibilityWrites="
                + suppressedVisibilityWrites);
        previousVisibility = View.VISIBLE;
        suppressedVisibilityWrites = 0;
    }
}
