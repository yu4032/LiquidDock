package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/** Launcher 4.50 hooks for the dedicated WidgetTypeAnimTarget animation path. */
final class LauncherWidgetTransitionHook {
    private static final String TAG = "[DC][WidgetTransition]";
    private static final String LAUNCHER_WIDGET_VIEW =
            "com.miui.home.launcher.LauncherWidgetView";
    private static final String MAML_WIDGET_VIEW =
            "com.miui.home.launcher.maml.MaMlWidgetView";

    private static final String[] CLOSING_WIDGET_LOOKUP_OWNERS = new String[]{
            "com.miui.home.recents.anim.WindowAnimParamsProvider",
            "com.miui.home.recents.GestureModeApp",
            "com.miui.home.recents.NavStubView"
    };

    private static boolean installed;

    private LauncherWidgetTransitionHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        boolean any = false;
        any |= installWidgetVisibilityHook(classLoader, LAUNCHER_WIDGET_VIEW);
        any |= installWidgetVisibilityHook(classLoader, MAML_WIDGET_VIEW);
        for (String owner : CLOSING_WIDGET_LOOKUP_OWNERS) {
            any |= installClosingWidgetLookupHooks(classLoader, owner);
        }
        installed = any;
        if (any) MainHook.log(TAG + " Launcher 4.50 widget transition hooks installed");
    }

    private static boolean installWidgetVisibilityHook(ClassLoader classLoader, String className) {
        try {
            HookUtil.hookMethod(classLoader, className, "setAnimTargetVisibility", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object owner = chain.getThisObject();
                View material = GlassRuntimeState.isWidgetEnabled() && owner != null
                        ? resolveAnimTargetContainer(owner) : null;
                int visibility = args.length > 0 && args[0] instanceof Number
                        ? ((Number) args[0]).intValue() : View.VISIBLE;

                if (material != null && visibility != View.VISIBLE) {
                    // Launch-away and return-to-widget both hide the native widget. The return
                    // path is already marked by findClosingWidgetView(), so the coordinator can
                    // distinguish it without treating VISIBLE as a HOME semantic event.
                    LauncherWidgetTransitionCoordinator.onAnimTargetWillHide(material);
                }

                Object result = chain.proceed(args);
                if (material != null && visibility == View.VISIBLE) {
                    // VISIBLE alone is not a HOME signal. It is only used to release suppression
                    // owned by launch-away; a marked return remains gated on the fresh HOME scene.
                    LauncherWidgetTransitionCoordinator.onAnimTargetVisible(material);
                }
                return result;
            }, int.class);
            MainHook.log(TAG + " widget visibility hook installed class=" + className);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " widget visibility hook unavailable class=" + className
                    + ": " + error);
            return false;
        }
    }

    private static boolean installClosingWidgetLookupHooks(
            ClassLoader classLoader, String className) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            int count = 0;
            for (Method method : type.getDeclaredMethods()) {
                if (!"findClosingWidgetView".equals(method.getName())) continue;
                HookUtil.hook(method, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (GlassRuntimeState.isWidgetEnabled() && result != null) {
                        View material = resolveAnimTargetContainer(result);
                        if (material != null) {
                            LauncherGlassStaticNode node = LauncherGlassStaticNode.find(material);
                            if (node != null
                                    && node.kind() == LauncherGlassDragState.Kind.WIDGET) {
                                LauncherWidgetTransitionCoordinator.markWidgetReturnTarget(material);
                            }
                        }
                    }
                    return result;
                });
                count++;
            }
            if (count > 0) {
                MainHook.log(TAG + " findClosingWidgetView hooks installed class="
                        + className + " count=" + count);
                return true;
            }
        } catch (ClassNotFoundException missing) {
            MainHook.log(TAG + " optional closing-widget owner absent class=" + className);
        } catch (Throwable error) {
            MainHook.log(TAG + " closing-widget lookup hook unavailable class=" + className
                    + ": " + error);
        }
        return false;
    }

    private static View resolveAnimTargetContainer(Object target) {
        HookUtil.InvocationResult<Object> containerResult =
                HookUtil.tryInvoke(target, "getAnimTargetContainerView");
        Object value = containerResult.succeeded() ? containerResult.value() : null;
        return value instanceof View ? (View) value : null;
    }
}
