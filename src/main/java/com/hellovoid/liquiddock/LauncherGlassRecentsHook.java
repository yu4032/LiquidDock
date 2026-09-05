package com.hellovoid.liquiddock;

import android.os.Handler;
import android.os.Looper;

/** Uses HyperOS's semantic Recents dispatcher instead of guessing state from a mounted View. */
final class LauncherGlassRecentsHook {
    private static final String TAG = "[DC][GlassScene]";
    private static final String RECENTS_DISPATCHER =
            "com.miui.home.recents.RecentsServiceDispatcher";
    // Live Launcher 4.50 logs show wallpaper scale can still be returning to 1.0 for ~500 ms
    // after onRecentViewHide. This is an existing wallpaper-content barrier, not producer recovery.
    private static final long RECENTS_WALLPAPER_SETTLE_MS = 600L;
    private static final WorkstationRecentsRecoveryPolicy RECOVERY_POLICY =
            new WorkstationRecentsRecoveryPolicy();
    private static boolean installed;
    private static Handler mainHandler;
    private static long recentsReturnToken;

    private LauncherGlassRecentsHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed || config == null || !config.enabled || !config.glass.enabled) return;
        mainHandler = new Handler(Looper.getMainLooper());
        try {
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewShow", chain -> {
                // A new authoritative covered episode cancels any previous delayed HOME release.
                recentsReturnToken++;
                RECOVERY_POLICY.onRecentViewShow();
                LauncherGlassSceneController.setRecentsCoveredForAll(true);
                LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewHide", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));

                boolean workstationMode = MainHook.isWorkstationMode();
                boolean recentsCovered = LauncherGlassSceneController.isRecentsCoveredByVendor();
                WorkstationRecentsRecoveryPolicy.Decision decision =
                        RECOVERY_POLICY.onRecentViewHide(workstationMode, recentsCovered);
                if (!decision.authoritative) {
                    MainHook.log(TAG + " ignoring non-authoritative Recents hide"
                            + " covered=" + recentsCovered
                            + " phase=" + RECOVERY_POLICY.phase()
                            + " episode=" + RECOVERY_POLICY.activeEpisode());
                    return result;
                }

                // Keep the pre-existing HyperOS wallpaper-scale barrier anchored to the real vendor
                // hide callback. Workstation producer recovery does not add or extend a fixed delay.
                armRecentsWallpaperSettle();

                if (!decision.requestRollover) {
                    if (decision.allowUncover) {
                        LauncherGlassSceneController.setRecentsCoveredForAll(false);
                    }
                    return result;
                }

                LauncherGlassSessionRegistry.prepareWorkstationRecentsReturn(
                        decision.episode,
                        terminal -> dispatchRecoveryTerminal(decision.episode, terminal));
                return result;
            });
            installed = true;
            MainHook.log(TAG + " semantic Recents dispatcher hooks installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " semantic Recents dispatcher unavailable: " + error);
        }
    }

    private static void dispatchRecoveryTerminal(
            long episode, LauncherGlassProducerRecoveryState.Result result) {
        Handler handler = mainHandler;
        if (handler != null && Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> dispatchRecoveryTerminal(episode, result));
            return;
        }

        WorkstationRecentsRecoveryPolicy.TerminalDecision terminal =
                RECOVERY_POLICY.onRecoveryTerminal(episode, result);
        if (!terminal.matched) {
            MainHook.log(TAG + " ignoring stale Workstation recovery terminal"
                    + " episode=" + episode + " result=" + result
                    + " active=" + RECOVERY_POLICY.activeEpisode()
                    + " phase=" + RECOVERY_POLICY.phase());
            return;
        }
        if (!terminal.allowUncover) {
            // Failure owns no future retry side effect. Keep Recents authoritative/covered until a
            // real subsequent onRecentViewShow establishes a new episode.
            recentsReturnToken++;
            LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
            MainHook.log(TAG + " Workstation producer recovery " + result
                    + "; HOME remains fail-closed episode=" + episode);
            return;
        }

        // Producer terminal success authorizes only the coverage transition. Scene visibility still
        // belongs to LauncherGlassSceneController's matching scene-generation/fresh-OES barrier.
        LauncherGlassSceneController.setRecentsCoveredForAll(false);
        MainHook.log(TAG + " Workstation producer recovery terminal ACCEPTED"
                + " episode=" + episode + "; scene freshness still required");
    }

    private static void armRecentsWallpaperSettle() {
        long token = ++recentsReturnToken;
        LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(true);
        Handler handler = mainHandler;
        if (handler == null) {
            LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
            return;
        }
        handler.postDelayed(() -> {
            if (token != recentsReturnToken) return;
            LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
            MainHook.log(TAG + " Recents wallpaper settle released token=" + token);
        }, RECENTS_WALLPAPER_SETTLE_MS);
    }
}
