package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.view.View;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SystemUI-only owner for fading the native gesture handle on HOME and RECENTS.
 *
 * <p>No Launcher classes are hooked and no Launcher broadcast/IPC is added. HOME comes from
 * NavigationBar's own TaskStackChangeListener. RECENTS comes from NavigationBar's native recents
 * animation callback plus its existing LauncherProxyListener.onOverviewShown callback. The actual
 * SystemUI home-handle object remains the visual owner and performs the alpha animation.</p>
 */
final class SystemUiGestureHandleFadeHook {
    private static final String TAG = "[DC][GestureHandle]";
    private static final String NAVIGATION_BAR =
            "com.android.systemui.navigationbar.views.NavigationBar";
    private static final String LAUNCHER_PACKAGE = "com.miui.home";

    private static final Object LOCK = new Object();
    private static final WeakHashMap<Object, Float> HOME_HANDLES = new WeakHashMap<>();
    private static final Set<Class<?>> HOOKED_HANDLE_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> HOOKED_TASK_LISTENER_CLASSES =
            ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> HOOKED_LAUNCHER_PROXY_LISTENER_CLASSES =
            ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> OWN_ALPHA_WRITE = new ThreadLocal<>();

    private static final GestureHandleSystemUiSceneState SCENE =
            new GestureHandleSystemUiSceneState();

    private static boolean installed;
    private static boolean hiddenRequested;

    private SystemUiGestureHandleFadeHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        try {
            Class<?> navigationBarClass = Class.forName(NAVIGATION_BAR, false, classLoader);

            Method onInit = HookUtil.findMethodExact(
                    navigationBarClass, "onInit", new Class<?>[0]);
            HookUtil.hook(onInit, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                captureNavigationBar(chain.getThisObject());
                return result;
            });

            Method onViewAttached = HookUtil.findMethodExact(
                    navigationBarClass, "onViewAttached", new Class<?>[0]);
            HookUtil.hook(onViewAttached, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                captureNavigationBar(chain.getThisObject());
                return result;
            });

            Method recentsAnimation = HookUtil.findMethodExact(
                    navigationBarClass,
                    "onRecentsAnimationStateChanged",
                    new Class<?>[]{boolean.class});
            HookUtil.hook(recentsAnimation, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                boolean running = args.length > 0
                        && args[0] instanceof Boolean
                        && (Boolean) args[0];
                reconcileHiddenState(SCENE.onRecentsAnimationChanged(running));
                return result;
            });

            installed = true;
            GestureHandleRuntimeState.setListener(enabled -> reconcileHiddenState());
        } catch (Throwable error) {
            installed = false;
            Api101Bridge.log(TAG + " SystemUI-only hook unavailable", error);
        }
    }

    private static void captureNavigationBar(Object navigationBar) {
        if (navigationBar == null) return;
        try {
            Object navBarView = HookUtil.getField(navigationBar, "mView");
            Context context = navBarView instanceof View
                    ? ((View) navBarView).getContext()
                    : null;

            HookUtil.InvocationResult<Object> handleResult =
                    HookUtil.tryInvoke(navBarView, "getHomeHandle");
            if (!handleResult.succeeded() || handleResult.value() == null) {
                Api101Bridge.log(TAG + " native home handle unavailable: "
                        + handleResult.failure());
                return;
            }

            Object handle = handleResult.value();
            ensureHandleAlphaHook(handle.getClass());
            synchronized (LOCK) {
                if (!HOME_HANDLES.containsKey(handle)) {
                    HOME_HANDLES.put(handle, readAlpha(handle));
                }
            }

            Object taskListener = HookUtil.getField(navigationBar, "mTaskStackListener");
            if (taskListener != null) {
                ensureTaskListenerHook(taskListener.getClass());
            }

            Object launcherProxyListener =
                    HookUtil.getField(navigationBar, "mLauncherProxyListener");
            if (launcherProxyListener != null) {
                ensureLauncherProxyListenerHook(launcherProxyListener.getClass());
            }

            if (context != null) refreshTopTask(context);
            reconcileHiddenState();
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " NavigationBar capture failed", error);
        }
    }

    private static void ensureTaskListenerHook(Class<?> listenerClass) {
        if (listenerClass == null || !HOOKED_TASK_LISTENER_CLASSES.add(listenerClass)) return;
        try {
            Method method = HookUtil.findMethodExact(
                    listenerClass,
                    "onTaskMovedToFront",
                    new Class<?>[]{ActivityManager.RunningTaskInfo.class});
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                ActivityManager.RunningTaskInfo task =
                        args.length > 0 && args[0] instanceof ActivityManager.RunningTaskInfo
                                ? (ActivityManager.RunningTaskInfo) args[0]
                                : null;
                updateTopTask(task);
                return result;
            });

        } catch (Throwable error) {
            HOOKED_TASK_LISTENER_CLASSES.remove(listenerClass);
            Api101Bridge.log(TAG + " native task-stack authority unavailable class="
                    + listenerClass.getName(), error);
        }
    }

    private static void ensureLauncherProxyListenerHook(Class<?> listenerClass) {
        if (listenerClass == null
                || !HOOKED_LAUNCHER_PROXY_LISTENER_CLASSES.add(listenerClass)) return;
        try {
            Method shown = HookUtil.findMethodExact(
                    listenerClass, "onOverviewShown", new Class<?>[0]);
            HookUtil.hook(shown, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                reconcileHiddenState(SCENE.onOverviewShown());
                return result;
            });

            Method connectionChanged = HookUtil.findMethodExact(
                    listenerClass, "onConnectionChanged", new Class<?>[]{boolean.class});
            HookUtil.hook(connectionChanged, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                boolean connected = args.length > 0
                        && args[0] instanceof Boolean
                        && (Boolean) args[0];
                if (!connected) {
                    reconcileHiddenState(SCENE.onLauncherProxyDisconnected());
                }
                return result;
            });

        } catch (Throwable error) {
            HOOKED_LAUNCHER_PROXY_LISTENER_CLASSES.remove(listenerClass);
            Api101Bridge.log(TAG + " native overview authority unavailable class="
                    + listenerClass.getName(), error);
        }
    }

    private static void ensureHandleAlphaHook(Class<?> handleClass) {
        if (handleClass == null || !HOOKED_HANDLE_CLASSES.add(handleClass)) return;
        try {
            Method setAlpha = HookUtil.findMethodExact(
                    handleClass, "setAlpha", new Class<?>[]{float.class, boolean.class});
            HookUtil.hook(setAlpha, chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (!Boolean.TRUE.equals(OWN_ALPHA_WRITE.get())
                        && args.length >= 2
                        && args[0] instanceof Number) {
                    synchronized (LOCK) {
                        if (HOME_HANDLES.containsKey(owner)) {
                            float vendorTarget = ((Number) args[0]).floatValue();
                            HOME_HANDLES.put(owner, vendorTarget);
                            if (hiddenRequested) {
                                // Preserve the configured fade already running toward zero.
                                // Letting the vendor write continue would cancel it and restart
                                // ButtonDispatcher's default-duration animation.
                                return null;
                            }
                        }
                    }
                }
                return chain.proceed(args);
            });

        } catch (Throwable error) {
            HOOKED_HANDLE_CLASSES.remove(handleClass);
            Api101Bridge.log(TAG + " native alpha authority unavailable class="
                    + handleClass.getName(), error);
        }
    }

    private static void refreshTopTask(Context context) {
        if (context == null) return;
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            List<ActivityManager.RunningTaskInfo> tasks =
                    manager == null ? null : manager.getRunningTasks(1);
            updateTopTask(tasks == null || tasks.isEmpty() ? null : tasks.get(0));
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " initial top-task query failed", error);
        }
    }

    private static void updateTopTask(ActivityManager.RunningTaskInfo task) {
        reconcileHiddenState(SCENE.onTaskMovedToFront(isHomeTask(task)));
    }

    private static boolean isHomeTask(ActivityManager.RunningTaskInfo task) {
        if (task == null) return false;
        ComponentName top = task.topActivity;
        if (top != null && LAUNCHER_PACKAGE.equals(top.getPackageName())) return true;
        ComponentName base = task.baseActivity;
        return base != null && LAUNCHER_PACKAGE.equals(base.getPackageName());
    }

    private static float readAlpha(Object handle) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(handle, "getAlpha");
        Object value = result.succeeded() ? result.value() : null;
        if (value instanceof Number) return ((Number) value).floatValue();
        return 1.0f;
    }

    private static void reconcileHiddenState() {
        reconcileHiddenState(SCENE.shouldHide());
    }

    private static void reconcileHiddenState(boolean sceneShouldHide) {
        setHiddenRequested(GestureHandleRuntimeState.isEnabled() && sceneShouldHide);
    }

    private static void setHiddenRequested(boolean hidden) {
        synchronized (LOCK) {
            if (hiddenRequested == hidden) return;
            hiddenRequested = hidden;
            for (Map.Entry<Object, Float> entry : HOME_HANDLES.entrySet()) {
                Object handle = entry.getKey();
                if (handle == null) continue;
                float target = hidden ? 0.0f : entry.getValue();
                writeAlpha(
                        handle,
                        target,
                        true,
                        hidden ? GestureHandleRuntimeState.fadeOutDurationMs() : -1);
            }
        }
    }

    private static void writeAlpha(
            Object handle, float alpha, boolean animate, int durationOverrideMs) {
        if (handle == null) return;
        OWN_ALPHA_WRITE.set(Boolean.TRUE);
        try {
            HookUtil.InvocationResult<Object> result = durationOverrideMs >= 0
                    ? HookUtil.tryInvoke(
                            handle, "setAlpha", alpha, animate, (long) durationOverrideMs)
                    : HookUtil.tryInvoke(handle, "setAlpha", alpha, animate);
            if (!result.succeeded() && durationOverrideMs >= 0) {
                result = HookUtil.tryInvoke(handle, "setAlpha", alpha, animate);
            }
            if (!result.succeeded()) {
                Api101Bridge.log(TAG + " native alpha write failed: " + result.failure());
            }
        } finally {
            OWN_ALPHA_WRITE.remove();
        }
    }
}
