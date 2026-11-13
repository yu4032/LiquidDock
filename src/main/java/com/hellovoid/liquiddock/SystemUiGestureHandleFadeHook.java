package com.hellovoid.liquiddock;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
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
    private static final String HOME_HANDLE_VISIBILITY_CONTROLLER =
            "com.android.systemui.navigationbar.HomeHandleVisibilityController";
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static final GestureHandleAlphaState<Object> ALPHA =
            new GestureHandleAlphaState<>();
    private static final Set<Object> HOME_HANDLE_CONTROLLERS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Class<?>> HOOKED_HANDLE_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> HOOKED_TASK_LISTENER_CLASSES =
            ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> HOOKED_LAUNCHER_PROXY_LISTENER_CLASSES =
            ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> HOOKED_HANDLE_VIEW_CLASSES =
            ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> OWN_ALPHA_WRITE = new ThreadLocal<>();

    private static final Api102HookDomain DOMAIN =
            Api102HookDomain.forProcess("systemui.gesture.handle");

    private static final GestureHandleSystemUiSceneState SCENE =
            new GestureHandleSystemUiSceneState();

    private static boolean installed;

    private SystemUiGestureHandleFadeHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        try {
            DOMAIN.begin();
            Class<?> navigationBarClass = Class.forName(NAVIGATION_BAR, false, classLoader);

            Method onInit = HookUtil.findMethodExact(
                    navigationBarClass, "onInit", new Class<?>[0]);
            DOMAIN.hook(onInit, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                DOMAIN.runActiveSideEffect(() -> captureNavigationBar(chain.getThisObject()));
                return result;
            });

            Method onViewAttached = HookUtil.findMethodExact(
                    navigationBarClass, "onViewAttached", new Class<?>[0]);
            DOMAIN.hook(onViewAttached, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                DOMAIN.runActiveSideEffect(() -> captureNavigationBar(chain.getThisObject()));
                return result;
            });

            Method recentsAnimation = HookUtil.findMethodExact(
                    navigationBarClass,
                    "onRecentsAnimationStateChanged",
                    new Class<?>[]{boolean.class});
            DOMAIN.hook(recentsAnimation, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                boolean running = args.length > 0
                        && args[0] instanceof Boolean
                        && (Boolean) args[0];
                DOMAIN.runActiveSideEffect(
                        () -> reconcileHiddenState(SCENE.onRecentsAnimationChanged(running)));
                return result;
            });

            installSystemBarAttributesHook(navigationBarClass);
            installHomeHandleControllerHooks(classLoader);
            installTransitionsHook(classLoader);

            DOMAIN.commit();
            installed = true;
            GestureHandleRuntimeState.setListener(enabled ->
                    DOMAIN.runActiveSideEffect(SystemUiGestureHandleFadeHook::reconcileHiddenState));
        } catch (Throwable error) {
            // If a framework unhook fails, keep the owner logically installed and BLOCKED:
            // another install would otherwise create a second interception chain.
            if (DOMAIN.state() == Api102HookDomain.State.INSTALLING
                    || DOMAIN.state() == Api102HookDomain.State.BLOCKED) {
                installed = !DOMAIN.abort();
            } else if (DOMAIN.state() == Api102HookDomain.State.ACTIVE) {
                installed = !DOMAIN.stop();
            } else {
                installed = false;
            }
            Api101Bridge.log(TAG + " SystemUI-only hook unavailable", error);
        }
    }

    private static void captureNavigationBar(Object navigationBar) {
        if (navigationBar == null || !DOMAIN.isActive()) return;
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
            Object handleView = HookUtil.getField(handle, "mCurrentView");
            if (handleView != null) {
                ensureHandleViewAlphaHook(handleView.getClass());
            }
            if (!ALPHA.capture(handle, readAlpha(handle),
                    (owner, alpha) -> writeAlpha(owner, alpha, true))) {
                Api101Bridge.log(TAG + " new native handle alpha sync failed");
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

            Object visibilityController =
                    HookUtil.getField(navigationBar, "mHomeHandleVisibilityController");
            if (visibilityController != null) {
                HOME_HANDLE_CONTROLLERS.add(visibilityController);
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
            DOMAIN.hookDynamic(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                ActivityManager.RunningTaskInfo task =
                        args.length > 0 && args[0] instanceof ActivityManager.RunningTaskInfo
                                ? (ActivityManager.RunningTaskInfo) args[0]
                                : null;
                DOMAIN.runActiveSideEffect(() -> updateTopTask(task));
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
        boolean someInstalled = false;
        try {
            // Resolve BOTH signatures before installing either; vendor changes must not leave
            // half an overview listener without being recorded by its owner.
            Method shown = HookUtil.findMethodExact(
                    listenerClass, "onOverviewShown", new Class<?>[0]);
            Method connectionChanged = HookUtil.findMethodExact(
                    listenerClass, "onConnectionChanged", new Class<?>[]{boolean.class});
            DOMAIN.hookDynamic(shown, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                DOMAIN.runActiveSideEffect(
                        () -> reconcileHiddenState(SCENE.onOverviewShown()));
                return result;
            });
            someInstalled = true;
            DOMAIN.hookDynamic(connectionChanged, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                boolean connected = args.length > 0
                        && args[0] instanceof Boolean
                        && (Boolean) args[0];
                if (!connected) {
                    DOMAIN.runActiveSideEffect(
                            () -> reconcileHiddenState(SCENE.onLauncherProxyDisconnected()));
                }
                return result;
            });
            someInstalled = true;

        } catch (Throwable error) {
            // A successful first hook already owns the class. Do not erase the class marker
            // and retry a second registration when the next method is unavailable.
            if (!someInstalled) HOOKED_LAUNCHER_PROXY_LISTENER_CLASSES.remove(listenerClass);
            Api101Bridge.log(TAG + " native overview authority unavailable class="
                    + listenerClass.getName(), error);
        }
    }

    private static synchronized void ensureHandleAlphaHook(Class<?> handleClass) {
        if (handleClass == null) return;
        boolean firstInstall = HOOKED_HANDLE_CLASSES.add(handleClass);
        try {
            Method setAlpha = HookUtil.findMethodExact(
                    handleClass, "setAlpha", new Class<?>[]{float.class, boolean.class});
            io.github.libxposed.api.XposedInterface.Hooker hooker = chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (!Boolean.TRUE.equals(OWN_ALPHA_WRITE.get())
                        && args.length >= 2
                        && args[0] instanceof Number) {
                    DOMAIN.runActiveSideEffect(() -> args[0] =
                            ALPHA.onVendorAlpha(owner, ((Number) args[0]).floatValue()));
                }
                return chain.proceed(args);
            };
            if (firstInstall) {
                DOMAIN.hookDynamic(setAlpha, hooker);
            } else {
                // NavigationBar can be re-created without changing its HomeHandle class.
                // Refresh only the callback using API 102's atomic replacement, retaining
                // the original Hook id/priority and avoiding a duplicate interception chain.
                DOMAIN.replaceActive(setAlpha, hooker);
            }
        } catch (Throwable error) {
            // A failed replace still leaves the previous valid callback and handle installed.
            // Only an unsuccessful first installation may discard the class marker.
            if (firstInstall) HOOKED_HANDLE_CLASSES.remove(handleClass);
            Api101Bridge.log(TAG + " native alpha authority unavailable class="
                    + handleClass.getName(), error);
        }
    }

    private static synchronized void ensureHandleViewAlphaHook(Class<?> viewClass) {
        if (viewClass == null || !HOOKED_HANDLE_VIEW_CLASSES.add(viewClass)) return;
        try {
            Method setAlpha = HookUtil.findMethodExact(
                    viewClass, "setAlpha", new Class<?>[]{float.class});
            DOMAIN.hookDynamic(setAlpha, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (!Boolean.TRUE.equals(OWN_ALPHA_WRITE.get())
                        && DOMAIN.isActive() && GestureHandleRuntimeState.isEnabled()
                        && SCENE.shouldHide()
                        && args.length >= 1 && args[0] instanceof Number) {
                    args[0] = 0.0f;
                }
                return chain.proceed(args);
            });
        } catch (Throwable error) {
            HOOKED_HANDLE_VIEW_CLASSES.remove(viewClass);
            Api101Bridge.log(TAG + " handle view alpha hook unavailable class="
                    + viewClass.getName(), error);
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
        if (!DOMAIN.isActive()) return;
        setHiddenRequested(GestureHandleRuntimeState.isEnabled() && sceneShouldHide);
    }

    private static void setHiddenRequested(boolean hidden) {
        ALPHA.setHidden(hidden, (handle, target) -> writeAlpha(handle, target, target > 0f));
        syncHomeHandleControllers(hidden);
    }

    private static void installSystemBarAttributesHook(Class<?> targetClass) {
        Method method = findSystemBarAttributesMethod(targetClass);
        if (method == null) return;
        DOMAIN.hook(method, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            DOMAIN.runActiveSideEffect(() -> handleSystemBarAttributes(args));
            Object result = chain.proceed(args);
            if (HOME_HANDLE_VISIBILITY_CONTROLLER.equals(targetClass.getName())
                    && chain.getThisObject() != null) {
                HOME_HANDLE_CONTROLLERS.add(chain.getThisObject());
                DOMAIN.runActiveSideEffect(() -> {
                    if (GestureHandleRuntimeState.isEnabled() && SCENE.shouldHide()) {
                        HookUtil.tryInvoke(chain.getThisObject(), "setViewAlphaImmediate", 0.0f);
                    }
                });
            }
            return result;
        });
    }

    private static void installHomeHandleControllerHooks(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = Class.forName(
                    HOME_HANDLE_VISIBILITY_CONTROLLER, false, classLoader);
            installSystemBarAttributesHook(controllerClass);
            Method computeShouldHide = HookUtil.findMethodExact(
                    controllerClass, "computeShouldHide", new Class<?>[0]);
            DOMAIN.hook(computeShouldHide, chain -> {
                if (chain.getThisObject() != null) {
                    HOME_HANDLE_CONTROLLERS.add(chain.getThisObject());
                }
                if (DOMAIN.isActive() && GestureHandleRuntimeState.isEnabled()
                        && SCENE.shouldHide()) {
                    return Boolean.TRUE;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
        } catch (ClassNotFoundException ignored) {
            // Non-HyperOS4 SystemUI builds only use ButtonDispatcher + NavigationBar.
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " HomeHandleVisibilityController hook unavailable", error);
        }
    }

    private static void installTransitionsHook(ClassLoader classLoader) {
        try {
            Class<?> transitionsClass = Class.forName(
                    "com.android.wm.shell.transition.Transitions", false, classLoader);
            Method onTransitionReady = null;
            for (Method m : transitionsClass.getDeclaredMethods()) {
                if ("onTransitionReady".equals(m.getName()) && m.getParameterCount() == 4) {
                    m.setAccessible(true);
                    onTransitionReady = m;
                    break;
                }
            }
            if (onTransitionReady == null) return;
            final Handler mainHandler = new Handler(Looper.getMainLooper());
            Runnable postToHomeTrue = () -> {
                Runnable r = () -> DOMAIN.runActiveSideEffect(
                        () -> reconcileHiddenState(SCENE.onForegroundPackage(true)));
                if (Looper.myLooper() == Looper.getMainLooper()) r.run();
                else mainHandler.post(r);
            };
            DOMAIN.hook(onTransitionReady, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length >= 2 && args[1] != null) {
                    Boolean toHome = classifyTransitionToHome(args[1]);
                    if (toHome != null) {
                        Runnable r = () -> DOMAIN.runActiveSideEffect(
                                () -> reconcileHiddenState(SCENE.onForegroundPackage(toHome)));
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            r.run();
                        } else {
                            mainHandler.post(r);
                        }
                    }
                }
                return chain.proceed(args);
            });
            try {
                Class<?> recentsHandlerClass = Class.forName(
                        "com.android.wm.shell.recents.RecentsTransitionHandler", false, classLoader);
                for (Method m : recentsHandlerClass.getDeclaredMethods()) {
                    if ("startAnimation".equals(m.getName()) && m.getParameterCount() == 5) {
                        m.setAccessible(true);
                        DOMAIN.hook(m, chain -> {
                            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            if (Boolean.TRUE.equals(result)) {
                                postToHomeTrue.run();
                            }
                            return result;
                        });
                        break;
                    }
                }
                Class<?> controllerClass = Class.forName(
                        "com.android.wm.shell.recents.RecentsTransitionHandler$RecentsController",
                        false, classLoader);
                for (Method m : controllerClass.getDeclaredMethods()) {
                    if ("finishInner".equals(m.getName()) && m.getParameterCount() == 4
                            && m.getParameterTypes()[0] == boolean.class) {
                        m.setAccessible(true);
                        DOMAIN.hook(m, chain -> {
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            if (args.length >= 1 && args[0] instanceof Boolean) {
                                boolean toHome = (Boolean) args[0];
                                Runnable r = () -> DOMAIN.runActiveSideEffect(
                                        () -> reconcileHiddenState(SCENE.onForegroundPackage(toHome)));
                                if (Looper.myLooper() == Looper.getMainLooper()) r.run();
                                else mainHandler.post(r);
                            }
                            return chain.proceed(args);
                        });
                        break;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " WMShell Transitions hook unavailable", error);
        }
    }

    private static Boolean classifyTransitionToHome(Object transitionInfo) {
        HookUtil.InvocationResult<Object> changesRes =
                HookUtil.tryInvoke(transitionInfo, "getChanges");
        if (!changesRes.succeeded() || !(changesRes.value() instanceof List<?>)) return null;
        List<?> changes = (List<?>) changesRes.value();
        boolean homeOpening = false;
        boolean appOpening = false;
        boolean appClosing = false;
        for (Object change : changes) {
            if (change == null) continue;
            Object taskObj = HookUtil.tryInvoke(change, "getTaskInfo").value();
            if (!(taskObj instanceof ActivityManager.RunningTaskInfo)) continue;
            Object typeObj = HookUtil.tryInvoke(taskObj, "getActivityType").value();
            Object modeObj = HookUtil.tryInvoke(change, "getMode").value();
            if (!(typeObj instanceof Integer) || !(modeObj instanceof Integer)) continue;
            int activityType = (Integer) typeObj;
            int mode = (Integer) modeObj;
            boolean opening = (mode == 1 || mode == 3);
            boolean closing = (mode == 2 || mode == 4);
            if (activityType == 2 && opening) {
                homeOpening = true;
            } else if (activityType == 1) {
                if (opening) appOpening = true;
                if (closing) appClosing = true;
            }
        }
        if (homeOpening || (appClosing && !appOpening)) return Boolean.TRUE;
        if (appOpening) return Boolean.FALSE;
        return null;
    }

    private static Method findSystemBarAttributesMethod(Class<?> targetClass) {
        for (Method m : targetClass.getDeclaredMethods()) {
            if (!"onSystemBarAttributesChanged".equals(m.getName())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length >= 7 && params[0] == int.class && params[6] == String.class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static void handleSystemBarAttributes(Object[] args) {
        if (args == null || args.length < 7 || !(args[0] instanceof Integer)) return;
        if (((Integer) args[0]) != 0) return;
        Object pkgObj = args[6];
        if (!(pkgObj instanceof String)) return;
        String pkg = (String) pkgObj;
        if (pkg.isEmpty() || SYSTEMUI_PACKAGE.equals(pkg)) return;
        reconcileHiddenState(SCENE.onForegroundPackage(LAUNCHER_PACKAGE.equals(pkg)));
    }

    private static void syncHomeHandleControllers(boolean hidden) {
        Object[] controllers;
        synchronized (HOME_HANDLE_CONTROLLERS) {
            if (HOME_HANDLE_CONTROLLERS.isEmpty()) return;
            controllers = HOME_HANDLE_CONTROLLERS.toArray();
        }
        for (Object controller : controllers) {
            if (controller == null) continue;
            Runnable action = () -> {
                if (hidden) {
                    HookUtil.tryInvoke(controller, "setViewAlphaImmediate", 0.0f);
                } else {
                    HookUtil.tryInvoke(controller, "reEvaluate");
                }
            };
            if (Looper.myLooper() == Looper.getMainLooper()) {
                action.run();
            } else {
                Object handlerObj = HookUtil.getField(controller, "mainHandler");
                if (handlerObj instanceof Handler) {
                    ((Handler) handlerObj).post(action);
                } else {
                    new Handler(Looper.getMainLooper()).post(action);
                }
            }
        }
    }

    private static boolean writeAlpha(Object handle, float alpha, boolean animate) {
        if (handle == null) return false;
        OWN_ALPHA_WRITE.set(Boolean.TRUE);
        try {
            HookUtil.InvocationResult<Object> result =
                    HookUtil.tryInvoke(handle, "setAlpha", alpha, animate);
            if (!result.succeeded()) {
                Api101Bridge.log(TAG + " native alpha write failed: " + result.failure());
            }
            return result.succeeded();
        } finally {
            OWN_ALPHA_WRITE.remove();
        }
    }

    /**
     * Reversible gesture-owner teardown only. Not a process-wide hot-reload entry point:
     * HandleMenu, keyguard, vendor glass and other process owners still have live resources.
     *
     * <p>Disarm preference/posted callbacks, drain hook side effects, unhook, then restore the
     * last vendor-requested alpha. Failed native restores keep their weak ledger entries for
     * retry, and never report a completed teardown.</p>
     */
    static synchronized boolean stopForFutureReload() {
        if (!installed && DOMAIN.state() == Api102HookDomain.State.IDLE
                && ALPHA.trackedCount() == 0) return true;
        try {
            if (!GestureHandleRuntimeState.shutdownForFutureReload()) return false;
            if (!DOMAIN.stop()) return false;
            if (!ALPHA.restoreAndClear((handle, target) -> writeAlpha(handle, target, false))) {
                return false;
            }
            HOOKED_HANDLE_CLASSES.clear();
            HOOKED_HANDLE_VIEW_CLASSES.clear();
            HOOKED_TASK_LISTENER_CLASSES.clear();
            HOOKED_LAUNCHER_PROXY_LISTENER_CLASSES.clear();
            HOME_HANDLE_CONTROLLERS.clear();
            SCENE.resetForFutureReload();
            installed = false;
            return true;
        } catch (Throwable error) {
            try { Api101Bridge.log(TAG + " gesture owner teardown failed", error); }
            catch (Throwable ignored) {}
            return false;
        }
    }
}
