package com.hellovoid.liquiddock;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.reflect.Method;

/**
 * Bridges system and HyperOS wallpaper lifecycle boundaries into Workspace glass freshness.
 *
 * <p>The public system wallpaper identity/change signals are the stable content authority. HyperOS
 * callbacks remain useful early/compositor boundaries, but a missing vendor callback can no longer
 * leave Workspace glass permanently bound to an old cached backdrop.</p>
 */
final class LauncherWallpaperFreshnessHook {
    private static final String TAG = "[DC][WallpaperFreshness]";
    private static final String CALLBACK_CLASS =
            "com.miui.home.launcher.wallpaper.DesktopWallpaperManager$MiuiWallpaperManagerCallbackStub";
    private static final String WORKSPACE_CLASS = "com.miui.home.launcher.Workspace";
    private static final WallpaperChangeIdentityState CHANGE_IDENTITY =
            new WallpaperChangeIdentityState();

    private static volatile Handler mainHandler;
    private static Context applicationContext;
    private static WallpaperManager wallpaperManager;
    private static BroadcastReceiver wallpaperReceiver;
    private static WallpaperManager.OnColorsChangedListener colorsListener;
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

    /**
     * Called once Launcher.setupViews has a real Context. This system-level authority is deliberately
     * separate from the optional vendor callback bridge installed above.
     */
    static synchronized void attachContext(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;

        if (applicationContext != app || wallpaperManager == null) {
            applicationContext = app;
            try {
                wallpaperManager = WallpaperManager.getInstance(app);
                CHANGE_IDENTITY.initialize(readSystemWallpaperId(wallpaperManager));
            } catch (Throwable error) {
                wallpaperManager = null;
                MainHook.log(TAG + " WallpaperManager authority unavailable: " + error);
            }
        }

        if (wallpaperReceiver == null) {
            try {
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override public void onReceive(Context receiverContext, Intent intent) {
                        if (intent == null
                                || !Intent.ACTION_WALLPAPER_CHANGED.equals(intent.getAction())) {
                            return;
                        }
                        dispatchToMain(() ->
                                dispatchWallpaperBoundary("system-broadcast", true));
                    }
                };
                app.registerReceiver(
                        receiver,
                        new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
                        Context.RECEIVER_NOT_EXPORTED);
                wallpaperReceiver = receiver;
                MainHook.log(TAG + " system wallpaper-change authority registered");
            } catch (Throwable error) {
                MainHook.log(TAG + " system wallpaper-change authority unavailable: " + error);
            }
        }

        if (colorsListener == null && wallpaperManager != null) {
            try {
                WallpaperManager.OnColorsChangedListener listener = (colors, which) -> {
                    if ((which & WallpaperManager.FLAG_SYSTEM) == 0) return;
                    dispatchToMain(() -> dispatchWallpaperBoundary("system-colors", true));
                };
                wallpaperManager.addOnColorsChangedListener(listener, mainHandler());
                colorsListener = listener;
                MainHook.log(TAG + " system wallpaper-colors authority registered");
            } catch (Throwable error) {
                MainHook.log(TAG + " system wallpaper-colors authority unavailable: " + error);
            }
        }
    }

    private static void installWallpaperChanged(ClassLoader classLoader) {
        try {
            Class<?> callback = Class.forName(CALLBACK_CLASS, false, classLoader);
            Method method = callback.getDeclaredMethod(
                    "onWallpaperChanged", WallpaperColors.class, String.class, int.class);
            HookUtil.hook(method, chain -> {
                // The vendor callback can arrive before WallpaperManager publishes the new ID.
                // Identity coalescing therefore treats it as an early boundary; the later public
                // broadcast/colors callback remains sufficient even when this hook never fires.
                dispatchToMain(() -> dispatchWallpaperBoundary("vendor-callback", false));
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
                dispatchToMain(() -> {
                    LauncherGlassRecentsHook.onSystemWallpaperDrawFrameEnd();
                    LauncherGlassSceneController.onWallpaperAuthoritativeForAll();
                });
                return result;
            });
            MainHook.log(TAG + " onDrawFrameEnd installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " onDrawFrameEnd unavailable: " + error);
        }
    }

    private static void dispatchWallpaperBoundary(String authority, boolean candidateReady) {
        WallpaperManager manager = wallpaperManager;
        int wallpaperId = readSystemWallpaperId(manager);
        boolean advanced = CHANGE_IDENTITY.shouldAdvance(wallpaperId);
        if (advanced) {
            LauncherGlassSceneController.onWallpaperChangedForAll();
        }
        if (candidateReady) {
            LauncherGlassSceneController.onWallpaperCandidateForAll();
        }
        MainHook.log(TAG + " boundary=" + authority
                + " wallpaperId=" + wallpaperId
                + " advanced=" + advanced
                + " candidate=" + candidateReady);
    }

    private static int readSystemWallpaperId(WallpaperManager manager) {
        if (manager == null) return -1;
        try {
            return manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        } catch (Throwable error) {
            MainHook.log(TAG + " system wallpaper ID unavailable: " + error);
            return -1;
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
