package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;

/** Uses HyperOS's semantic Recents dispatcher instead of guessing state from a mounted View. */
final class LauncherGlassRecentsHook {
    private static final String TAG = "[DC][GlassScene]";
    private static final String RECENTS_DISPATCHER =
            "com.miui.home.recents.RecentsServiceDispatcher";
    // Live Launcher 4.50 logs show wallpaper scale can still be returning to 1.0 for ~500 ms
    // after onRecentViewHide. Keep one extra frame-budget margin before accepting a fresh scene.
    private static final long RECENTS_WALLPAPER_SETTLE_MS = 600L;
    private static boolean installed;
    private static Handler mainHandler;
    private static long recentsReturnToken;

    private LauncherGlassRecentsHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed || config == null || !config.enabled || !config.glass.enabled) return;
        mainHandler = new Handler(Looper.getMainLooper());
        try {
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewShow", chain -> {
                // Cancel any delayed HOME release before covering the scene again. Clearing the
                // settle flag while COVERED cannot schedule a capture.
                recentsReturnToken++;
                LauncherGlassSceneController.setRecentsCoveredForAll(true);
                LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewHide", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));

                boolean workstationMode = MainHook.isWorkstationMode();
                boolean recentsCovered = LauncherGlassSceneController.isRecentsCoveredByVendor();
                if (!recentsCovered) {
                    MainHook.log(TAG + " ignoring non-covered Recents hide");
                    return result;
                }

                boolean rolloverAccepted = true;
                if (WorkstationRecentsRecoveryPolicy.shouldRequestRollover(
                        workstationMode, recentsCovered)) {
                    // Workstation can reuse an apparently-valid Launcher Surface while retiring the
                    // old PassBlur BufferQueue producer. Endpoint rollover acceptance is required
                    // before HOME may uncover, but does not itself make the scene fresh or visible.
                    rolloverAccepted = LauncherGlassSessionRegistry.prepareWorkstationRecentsReturn();
                }
                WorkstationRecentsRecoveryPolicy.Decision recovery =
                        WorkstationRecentsRecoveryPolicy.onRecentsReturn(
                                workstationMode, rolloverAccepted);
                if (!recovery.allowUncover) {
                    LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
                    MainHook.log(TAG
                            + " Workstation Recents producer rollover rejected; HOME remains covered");
                    return result;
                }

                // onRecentViewHide precedes MiuiWallpaperSurfaceAnimation.IDLE on the pure
                // HOME -> Recents -> HOME path. Arm the capture barrier before uncovering HOME,
                // then release it after the observed wallpaper scale-to-1.0 tail has settled.
                long token = ++recentsReturnToken;
                LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(true);
                LauncherGlassSceneController.setRecentsCoveredForAll(false);
                Handler handler = mainHandler;
                if (handler != null) {
                    handler.postDelayed(() -> {
                        if (token != recentsReturnToken) return;
                        LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
                        MainHook.log(TAG + " Recents wallpaper settle released token=" + token);
                    }, RECENTS_WALLPAPER_SETTLE_MS);
                } else {
                    LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
                }
                return result;
            });
            installed = true;
            MainHook.log(TAG + " semantic Recents dispatcher hooks installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " semantic Recents dispatcher unavailable: " + error);
        }
    }
}
