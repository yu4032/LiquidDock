package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

/**
 * Bridges the decompiled HyperOS 4.50 DesktopWallpaperManager refresh transaction into Workspace
 * glass freshness.
 *
 * <p>Launcher itself treats {@code DesktopWallpaperManager.updateWallpaperInfo()} as the repeated
 * wallpaper-change transaction entry. Its background WallpaperInfoUpdateTask rereads MIUI
 * wallpaper metadata and then posts ColorModeRefreshTask to Workspace; that task reaches
 * {@code notifyWallpaperColorChanged()} only when Launcher is ready to notify wallpaper-derived
 * UI. LiquidDock mirrors those two concrete vendor boundaries instead of inferring wallpaper
 * identity from framework IDs or generic broadcasts.</p>
 */
final class LauncherWallpaperFreshnessHook {
    private static final String TAG = "[DC][WallpaperFreshness]";
    private static final String DESKTOP_MANAGER_CLASS =
            "com.miui.home.launcher.wallpaper.DesktopWallpaperManager";
    private static final String CALLBACK_CLASS =
            "com.miui.home.launcher.wallpaper.DesktopWallpaperManager$MiuiWallpaperManagerCallbackStub";

    private static volatile Handler mainHandler;
    private static boolean installed;

    private LauncherWallpaperFreshnessHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        installWallpaperUpdateTransaction(classLoader);
        installWallpaperNotifyCompletion(classLoader);
        // onDrawFrameEnd remains a separate Recents-return wallpaper-settle authority. The
        // decompiled DesktopWallpaperManager callback stub intentionally leaves this method empty,
        // so it is not used as Workspace wallpaper-content freshness authority.
        installDrawFrameEndForRecents(classLoader);
        installed = true;
        MainHook.log(TAG + " HyperOS DesktopWallpaperManager transaction hooks installed");
    }

    /**
     * Decompiled source fact:
     * MiuiWallpaperManagerCallbackStub.onWallpaperChanged(...) calls updateWallpaperInfo()
     * unconditionally. The legacy broadcast path does the same. updateWallpaperInfo() removes the
     * previously queued WallpaperInfoUpdateTask and enqueues the latest one, so every transaction
     * entry invalidates the previous wallpaper-content generation while duplicate rapid updates
     * naturally coalesce at the vendor task layer.
     */
    private static void installWallpaperUpdateTransaction(ClassLoader classLoader) {
        try {
            Class<?> manager = Class.forName(DESKTOP_MANAGER_CLASS, false, classLoader);
            Method method = manager.getDeclaredMethod("updateWallpaperInfo");
            HookUtil.hook(method, chain -> {
                dispatchToMain(() -> {
                    LauncherGlassSceneController.onWallpaperChangedForAll();
                    MainHook.log(TAG + " vendor updateWallpaperInfo -> content generation");
                });
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            MainHook.log(TAG + " DesktopWallpaperManager.updateWallpaperInfo installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " DesktopWallpaperManager.updateWallpaperInfo unavailable: " + error);
        }
    }

    /**
     * Decompiled source fact:
     * WallpaperInfoUpdateTask.run() rereads MIUI wallpaper colors/info, then calls
     * DesktopWallpaperManager.onDarkModeChange(); ColorModeRefreshTask runs on Workspace and, once
     * Launcher is not loading, calls notifyWallpaperColorChanged(). Hook after that method returns
     * so the vendor listener fan-out has completed before requesting the fresh PassBlur frame.
     *
     * A pure dark-mode refresh can also call notifyWallpaperColorChanged(), but without a preceding
     * updateWallpaperInfo() the wallpaper state machine has no pending generation and coalesces it.
     */
    private static void installWallpaperNotifyCompletion(ClassLoader classLoader) {
        try {
            Class<?> manager = Class.forName(DESKTOP_MANAGER_CLASS, false, classLoader);
            Method method = manager.getDeclaredMethod("notifyWallpaperColorChanged");
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                dispatchToMain(() -> {
                    LauncherGlassSceneController.onWallpaperCandidateForAll();
                    MainHook.log(TAG + " vendor notifyWallpaperColorChanged -> fresh candidate");
                });
                return result;
            });
            MainHook.log(TAG + " DesktopWallpaperManager.notifyWallpaperColorChanged installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " DesktopWallpaperManager.notifyWallpaperColorChanged unavailable: "
                    + error);
        }
    }

    private static void installDrawFrameEndForRecents(ClassLoader classLoader) {
        try {
            Class<?> callback = Class.forName(CALLBACK_CLASS, false, classLoader);
            Method method = callback.getDeclaredMethod("onDrawFrameEnd");
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                dispatchToMain(LauncherGlassRecentsHook::onSystemWallpaperDrawFrameEnd);
                return result;
            });
            MainHook.log(TAG + " onDrawFrameEnd installed for Recents settle only");
        } catch (Throwable error) {
            MainHook.log(TAG + " onDrawFrameEnd unavailable: " + error);
        }
    }

    private static Handler mainHandler() {
        Handler handler = mainHandler;
        if (handler != null) return handler;
        synchronized (LauncherWallpaperFreshnessHook.class) {
            handler = mainHandler;
            if (handler == null) {
                Looper main = Looper.getMainLooper();
                if (main == null) throw new IllegalStateException("Launcher main looper unavailable");
                handler = new Handler(main);
                mainHandler = handler;
            }
        }
        return handler;
    }

    private static void dispatchToMain(Runnable action) {
        if (action == null) return;
        Looper main = Looper.getMainLooper();
        if (main == null || Looper.myLooper() == main) {
            action.run();
            return;
        }
        mainHandler().post(action);
    }
}
