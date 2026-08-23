package com.hellovoid.liquiddock;

import android.app.WallpaperColors;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.reflect.Method;

/**
 * Bridges the decompiled HyperOS 4.50 wallpaper lifecycle into Workspace glass freshness.
 *
 * <p>The Binder-side callbacks are marshalled to the Launcher main looper without delays. Each
 * vendor boundary is installed independently so a missing optional callback on another build does
 * not disable the remaining freshness signals.</p>
 */
final class LauncherWallpaperFreshnessHook {
    private static final String TAG = "[DC][WallpaperFreshness]";
    private static final String CALLBACK_CLASS =
            "com.miui.home.launcher.wallpaper.DesktopWallpaperManager$MiuiWallpaperManagerCallbackStub";
    private static final String WORKSPACE_CLASS = "com.miui.home.launcher.Workspace";

    private static volatile Handler mainHandler;
    private static boolean installed;

    private LauncherWallpaperFreshnessHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        installWallpaperChanged(classLoader);
        installCandidate(classLoader);
        installFirstFrameRendered(classLoader);
        installDrawFrameEnd(classLoader);
        installed = true;
        MainHook.log(TAG + " HyperOS wallpaper freshness hooks installed");
    }

    private static void installWallpaperChanged(ClassLoader classLoader) {
        try {
            Class<?> callback = Class.forName(CALLBACK_CLASS, false, classLoader);
            Method method = callback.getDeclaredMethod(
                    "onWallpaperChanged", WallpaperColors.class, String.class, int.class);
            HookUtil.hook(method, chain -> {
                // Queue the content-generation edge before the vendor callback starts its async
                // wallpaper-info/color propagation. The later Workspace candidate therefore cannot
                // overtake this generation change on the main queue.
                dispatchToMain(LauncherGlassSceneController::onWallpaperChangedForAll);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            MainHook.log(TAG + " onWallpaperChanged installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " onWallpaperChanged unavailable: " + error);
        }
    }

    private static void installCandidate(ClassLoader classLoader) {
        try {
            Class<?> workspace = Class.forName(WORKSPACE_CLASS, false, classLoader);
            Method method = workspace.getDeclaredMethod("onWallpaperColorChanged");
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof View) {
                    View workspaceView = (View) owner;
                    dispatchToMain(() ->
                            LauncherGlassSceneController.onWallpaperCandidate(workspaceView));
                }
                return result;
            });
            MainHook.log(TAG + " Workspace.onWallpaperColorChanged installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Workspace.onWallpaperColorChanged unavailable: " + error);
        }
    }

    private static void installFirstFrameRendered(ClassLoader classLoader) {
        try {
            Class<?> callback = Class.forName(CALLBACK_CLASS, false, classLoader);
            Method method = callback.getDeclaredMethod("onWallpaperFirstFrameRendered", int.class);
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                dispatchToMain(LauncherGlassSceneController::onWallpaperAuthoritativeForAll);
                return result;
            });
            MainHook.log(TAG + " onWallpaperFirstFrameRendered installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " onWallpaperFirstFrameRendered unavailable: " + error);
        }
    }

    private static void installDrawFrameEnd(ClassLoader classLoader) {
        try {
            Class<?> callback = Class.forName(CALLBACK_CLASS, false, classLoader);
            Method method = callback.getDeclaredMethod("onDrawFrameEnd");
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                dispatchToMain(LauncherGlassSceneController::onWallpaperAuthoritativeForAll);
                return result;
            });
            MainHook.log(TAG + " onDrawFrameEnd installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " onDrawFrameEnd unavailable: " + error);
        }
    }

    private static void dispatchToMain(Runnable action) {
        if (action == null) return;
        Looper main = Looper.getMainLooper();
        if (main == null || Looper.myLooper() == main) {
            action.run();
            return;
        }
        Handler handler = mainHandler;
        if (handler == null) {
            synchronized (LauncherWallpaperFreshnessHook.class) {
                handler = mainHandler;
                if (handler == null) {
                    handler = new Handler(main);
                    mainHandler = handler;
                }
            }
        }
        handler.post(action);
    }
}
