package com.hellovoid.liquiddock;

import android.app.WallpaperColors;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

/**
 * Bridges the decompiled HyperOS 4.50 wallpaper-service transaction into Workspace glass
 * freshness without relying on framework wallpaper IDs, generic broadcasts, or fixed timing.
 *
 * <p>The target Launcher registers
 * {@code DesktopWallpaperManager.MiuiWallpaperManagerCallbackStub} directly with
 * {@code MiuiWallpaperManager.registerWallpaperChangeListener(..., 1)}. The callback is the raw
 * cross-process wallpaper-change boundary. Launcher then schedules
 * {@code WallpaperInfoUpdateTask}; in Laptop mode that task calls
 * {@code Utilities.updateCurrentWallpaperBitmap("laptop")} before it returns. LiquidDock records
 * content generation at the Binder callback and requests a fresh PassBlur frame only after the
 * matching background task has completed, so the vendor wallpaper cache is already current.</p>
 */
final class LauncherWallpaperFreshnessHook {
    private static final String TAG = "[DC][WallpaperFreshness]";
    private static final String CALLBACK_CLASS =
            "com.miui.home.launcher.wallpaper.DesktopWallpaperManager$MiuiWallpaperManagerCallbackStub";
    private static final String WALLPAPER_INFO_TASK_CLASS =
            "com.miui.home.launcher.wallpaper.DesktopWallpaperManager$WallpaperInfoUpdateTask";

    private static final LauncherWallpaperTransactionState TRANSACTION =
            new LauncherWallpaperTransactionState();

    private static volatile Handler mainHandler;
    private static boolean callbackHookInstalled;
    private static boolean taskHookInstalled;
    private static boolean drawFrameEndHookInstalled;

    private LauncherWallpaperFreshnessHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (classLoader == null) return;
        if (!callbackHookInstalled) {
            callbackHookInstalled = installWallpaperChangedCallback(classLoader);
        }
        if (!taskHookInstalled) {
            taskHookInstalled = installWallpaperInfoTaskCompletion(classLoader);
        }
        if (!drawFrameEndHookInstalled) {
            drawFrameEndHookInstalled = installDrawFrameEndForRecents(classLoader);
        }

        if (callbackHookInstalled && taskHookInstalled) {
            MainHook.log(TAG + " raw callback + wallpaper-cache completion hooks installed");
        } else {
            MainHook.log(TAG + " ERROR: wallpaper freshness authority incomplete callback="
                    + callbackHookInstalled + " task=" + taskHookInstalled);
        }
    }

    /**
     * Raw vendor authority. This method is entered from the MIUI wallpaper Binder callback before
     * DesktopWallpaperManager.updateWallpaperInfo() is invoked, so it cannot be lost if ART/JIT
     * later inlines the manager's same-class helper call.
     */
    private static boolean installWallpaperChangedCallback(ClassLoader classLoader) {
        try {
            Class<?> callback = Class.forName(CALLBACK_CLASS, false, classLoader);
            Method method = callback.getDeclaredMethod(
                    "onWallpaperChanged", WallpaperColors.class, String.class, int.class);
            HookUtil.hook(method, chain -> {
                long serial = TRANSACTION.onWallpaperChanged();
                dispatchToMain(() -> {
                    LauncherGlassSceneController.onWallpaperChangedForAll();
                    MainHook.log(TAG + " binder onWallpaperChanged -> content generation serial="
                            + serial);
                });
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            MainHook.log(TAG + " MiuiWallpaperManagerCallbackStub.onWallpaperChanged installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " onWallpaperChanged unavailable: " + error);
            return false;
        }
    }

    /**
     * Cache-ready authority. The decompiled task refreshes wallpaper metadata and, in Laptop mode,
     * executes Utilities.updateCurrentWallpaperBitmap("laptop") before calling onDarkModeChange().
     * A task that was already running when a newer Binder callback arrived is stale and must not
     * authorize the newer wallpaper generation.
     */
    private static boolean installWallpaperInfoTaskCompletion(ClassLoader classLoader) {
        try {
            Class<?> task = Class.forName(WALLPAPER_INFO_TASK_CLASS, false, classLoader);
            Method method = task.getDeclaredMethod("run");
            HookUtil.hook(method, chain -> {
                long serial = TRANSACTION.onTaskStarted();
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (TRANSACTION.shouldPublishTaskCompletion(serial)) {
                    dispatchToMain(() -> {
                        LauncherGlassSceneController.onWallpaperCandidateForAll();
                        MainHook.log(TAG + " WallpaperInfoUpdateTask complete -> fresh candidate"
                                + " serial=" + serial);
                    });
                } else if (serial > 0L) {
                    MainHook.log(TAG + " stale WallpaperInfoUpdateTask completion ignored serial="
                            + serial + " latest=" + TRANSACTION.latestChangeSerial());
                }
                return result;
            });
            MainHook.log(TAG + " WallpaperInfoUpdateTask.run installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " WallpaperInfoUpdateTask.run unavailable: " + error);
            return false;
        }
    }

    private static boolean installDrawFrameEndForRecents(ClassLoader classLoader) {
        try {
            Class<?> callback = Class.forName(CALLBACK_CLASS, false, classLoader);
            Method method = callback.getDeclaredMethod("onDrawFrameEnd");
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                dispatchToMain(LauncherGlassRecentsHook::onSystemWallpaperDrawFrameEnd);
                return result;
            });
            MainHook.log(TAG + " onDrawFrameEnd installed for Recents settle only");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " onDrawFrameEnd unavailable: " + error);
            return false;
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
