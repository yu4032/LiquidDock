package com.hellovoid.liquiddock;

import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Folder-scoped ownership of the original Launcher Workspace presentation.
 *
 * In Launcher 4.50 the FolderCling Folme spring is distinct from the
 * mScreenContentHideAnimator, whose terminal callback makes Workspace
 * INVISIBLE. Intercept only that Launcher-specific side effect.
 */
final class LauncherNativeFolderWorkspaceFadeGate {
    private static final String TAG = "[DC][FolderWorkspace]";
    private static boolean installed;
    private static boolean folderSession;
    private static int skippedFade, skippedAlpha, skippedHide;

    private LauncherNativeFolderWorkspaceFadeGate() {}

    static void install(ClassLoader loader) {
        if (installed || loader == null) return;
        try {
            Class<?> launcher = Class.forName(
                    "com.miui.home.launcher.Launcher", false, loader);
            // Resolve each OEM method independently: one absent method must
            // not disable the others as it did in the old all-or-nothing hook.
            int installedCount = 0;
            installedCount += hook(launcher, "fadeInOrOutScreenContentWhenFolderAnimate",
                    new Class<?>[]{boolean.class}, chain -> {
                        if (!folderSession) return chain.proceed(
                                chain.getArgs().toArray(new Object[0]));
                        skippedFade++;
                        restoreWorkspace(chain.getThisObject());
                        return null;
                    });
            installedCount += hook(launcher, "setScreenContentAlpha",
                    new Class<?>[]{float.class}, chain -> {
                        if (!folderSession) return chain.proceed(
                                chain.getArgs().toArray(new Object[0]));
                        skippedAlpha++;
                        restoreWorkspace(chain.getThisObject());
                        return null;
                    });
            installedCount += hook(launcher, "onScreenContentHide",
                    new Class<?>[0], chain -> {
                        if (!folderSession) return chain.proceed(
                                chain.getArgs().toArray(new Object[0]));
                        skippedHide++;
                        restoreWorkspace(chain.getThisObject());
                        return null;
                    });
            installedCount += hook(launcher, "changeScreenContentOnOpen",
                    new Class<?>[]{float.class}, chain -> {
                        if (!folderSession) return chain.proceed(
                                chain.getArgs().toArray(new Object[0]));
                        skippedAlpha++;
                        return null;
                    });
            installedCount += hook(launcher, "changeScreenContentOnClose",
                    new Class<?>[]{float.class}, chain -> {
                        if (!folderSession) return chain.proceed(
                                chain.getArgs().toArray(new Object[0]));
                        skippedAlpha++;
                        return null;
                    });
            installed = installedCount > 0;
            MainHook.log(TAG + " native Workspace folder fade hooks=" + installedCount + "/5");
        } catch (Throwable error) {
            MainHook.log(TAG + " gate installation failure: " + error);
        }
    }

    private static int hook(Class<?> launcher, String name, Class<?>[] signature,
            XposedInterface.Hooker callback) {
        try {
            Method method = launcher.getDeclaredMethod(name, signature);
            HookUtil.hook(method, callback);
            MainHook.log(TAG + " installed " + name);
            return 1;
        } catch (Throwable error) {
            MainHook.log(TAG + " unable to hook " + name + ": " + error);
            return 0;
        }
    }

    static void onOpening(Object launcher) {
        if (!folderSession) {
            skippedFade = skippedAlpha = skippedHide = 0;
            folderSession = true;
            MainHook.log(TAG + " folder presentation ownership started");
        }
        if (launcher != null) restoreWorkspace(launcher);
    }

    static void onOpeningFailed() { folderSession = false; }

    static void onClosed() {
        if (folderSession) MainHook.log(TAG + " folder presentation complete skippedFade="
                + skippedFade + " skippedAlpha=" + skippedAlpha
                + " skippedHide=" + skippedHide);
        folderSession = false;
    }

    private static void restoreWorkspace(Object launcher) {
        View workspace = viewField(launcher, "mWorkspace");
        View search = viewField(launcher, "mSearchBar");
        if (workspace != null) {
            if (workspace.getVisibility() != View.VISIBLE) workspace.setVisibility(View.VISIBLE);
            if (workspace.getAlpha() != 1f) workspace.setAlpha(1f);
        }
        if (search != null) {
            if (search.getVisibility() != View.VISIBLE) search.setVisibility(View.VISIBLE);
            if (search.getAlpha() != 1f) search.setAlpha(1f);
        }
        View content = viewField(launcher, "mScreenContent");
        if (content != null) {
            if (content.getScaleX() != 1f) content.setScaleX(1f);
            if (content.getScaleY() != 1f) content.setScaleY(1f);
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
            MainHook.log(TAG + " unable to read " + name + ": " + error);
            return null;
        }
    }
}
