package com.hellovoid.liquiddock;

import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Suppress the OEM workspace/search-bar concealment while expanded folder
 * glass owns a frozen Launcher backdrop. The folder panel's own Folme spring
 * is left unchanged.
 *
 * Launcher 4.50 calls fadeInOrOutScreenContentWhenFolderAnimate(true), which
 * drives changeScreenContentOnOpen -> setScreenContentAlpha, then
 * onScreenContentHide (sets Workspace and search bar INVISIBLE).
 */
final class LauncherNativeFolderWorkspaceFadeGate {
    private static final String TAG = "[DC][FolderWorkspace]";
    private static boolean installed;

    private LauncherNativeFolderWorkspaceFadeGate() {}

    static void install(ClassLoader loader) {
        if (installed || loader == null) return;
        try {
            Class<?> launcher = Class.forName(
                    "com.miui.home.launcher.Launcher", false, loader);
            Method fade = launcher.getDeclaredMethod(
                    "fadeInOrOutScreenContentWhenFolderAnimate", boolean.class);
            Method hide = launcher.getDeclaredMethod("onScreenContentHide");
            Method opening = launcher.getDeclaredMethod("changeScreenContentOnOpen", float.class);
            Method closing = launcher.getDeclaredMethod("changeScreenContentOnClose", float.class);
            HookUtil.hook(fade, chain -> {
                if (!Miuix307ZeroCopyRenderer.isFolderFrozen()) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                // Skip only Workspace/toolbar hide/reveal animations. The
                // native FolderCling/Folme geometry still proceeds normally.
                cancelExistingFade(chain.getThisObject());
                ensureVisible(chain.getThisObject());
                return null;
            });
            HookUtil.hook(hide, chain -> {
                if (!Miuix307ZeroCopyRenderer.isFolderFrozen()) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                ensureVisible(chain.getThisObject());
                return null;
            });
            HookUtil.hook(opening, chain -> {
                if (!Miuix307ZeroCopyRenderer.isFolderFrozen()) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                return null;
            });
            HookUtil.hook(closing, chain -> {
                if (!Miuix307ZeroCopyRenderer.isFolderFrozen()) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                return null;
            });
            installed = true;
            MainHook.log(TAG + " native Workspace folder fade suppressed");
        } catch (Throwable error) {
            MainHook.log(TAG + " native Workspace folder fade unavailable: " + error);
        }
    }

    private static void ensureVisible(Object launcher) {
        View workspace = viewField(launcher, "mWorkspace");
        View search = viewField(launcher, "mSearchBar");
        View content = viewField(launcher, "mScreenContent");
        if (workspace != null) {
            workspace.setVisibility(View.VISIBLE);
            workspace.setAlpha(1f);
        }
        if (search != null) {
            search.setVisibility(View.VISIBLE);
            search.setAlpha(1f);
        }
        if (content != null) {
            content.setScaleX(1f);
            content.setScaleY(1f);
        }
    }

    private static View viewField(Object launcher, String name) {
        if (launcher == null) return null;
        try {
            Field field = launcher.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(launcher);
            return value instanceof View ? (View) value : null;
        } catch (Throwable error) {
            MainHook.log(TAG + " unable to resolve " + name + ": " + error);
            return null;
        }
    }

    private static void cancelExistingFade(Object launcher) {
        if (launcher == null) return;
        for (String name : new String[]{"mScreenContentHideAnimator", "mScreenContentShowAnimator"}) {
            try {
                Field field = launcher.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object animator = field.get(launcher);
                if (animator != null) {
                    Method cancel = animator.getClass().getMethod("cancel");
                    cancel.invoke(animator);
                }
            } catch (Throwable error) {
                MainHook.log(TAG + " unable to cancel " + name + ": " + error);
            }
        }
    }
}
