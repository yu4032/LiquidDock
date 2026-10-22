package com.hellovoid.liquiddock;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.SystemClock;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OS4 builds its rectangular grid correctly when the Launcher Activity is created in the target
 * orientation, but its hot-rotation path keeps the old coordinate domain. Recreate only the
 * Activity, in the same Launcher process, after the configuration has settled.
 */
final class Os4RotationActivityReloadHook {
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final long RECREATE_DELAY_MS = 300L;

    private static volatile boolean installed;
    private static volatile int lastOrientation = Configuration.ORIENTATION_UNDEFINED;
    private static volatile long lastReloadUptimeMs;
    private static final AtomicBoolean RECREATE_PENDING = new AtomicBoolean();

    private Os4RotationActivityReloadHook() {}

    static void install() {
        if (installed) return;
        synchronized (Os4RotationActivityReloadHook.class) {
            if (installed) return;
            HookUtil.hookMethod(
                    Activity.class,
                    "onConfigurationChanged",
                    new Class<?>[]{Configuration.class},
                    chain -> {
                        Object result = chain.proceed(
                                chain.getArgs().toArray(new Object[0]));
                        Object target = chain.getThisObject();
                        if (!(target instanceof Activity)) return result;
                        Activity activity = (Activity) target;
                        if (!LAUNCHER_PACKAGE.equals(activity.getPackageName())) return result;

                        Configuration configuration =
                                (Configuration) chain.getArg(0);
                        int current = configuration.orientation;
                        int previous = lastOrientation;
                        lastOrientation = current;
                        if (!isRectangularGridEnabled()) return result;
                        long now = SystemClock.uptimeMillis();
                        if (!Os4RotationActivityReloadPolicy.shouldReload(
                                true, previous, current, now, lastReloadUptimeMs)) {
                            return result;
                        }
                        if (!RECREATE_PENDING.compareAndSet(false, true)) return result;
                        lastReloadUptimeMs = now;
                        activity.getWindow().getDecorView().postDelayed(() -> {
                            try {
                                if (!activity.isFinishing() && !activity.isDestroyed()) {
                                    Api101Bridge.log("[DC][OS4Rotation] recreating Launcher Activity "
                                            + previous + " -> " + current);
                                    activity.recreate();
                                }
                            } catch (Throwable error) {
                                Api101Bridge.log(
                                        "[DC][OS4Rotation] Activity reload failed", error);
                            } finally {
                                RECREATE_PENDING.set(false);
                            }
                        }, RECREATE_DELAY_MS);
                        return result;
                    });
            installed = true;
        }
    }

    private static boolean isRectangularGridEnabled() {
        try {
            ConfigReader config = ConfigReader.load();
            int columns = config.i(
                    ConfigSchema.Grid.COLUMNS.name(),
                    ConfigSchema.Grid.COLUMNS.runtimeFallback());
            int rows = config.i(
                    ConfigSchema.Grid.ROWS.name(),
                    ConfigSchema.Grid.ROWS.runtimeFallback());
            return config.b(
                    ConfigSchema.Grid.ENABLED.name(),
                    ConfigSchema.Grid.ENABLED.runtimeFallback())
                    && columns > 0 && rows > 0 && columns != rows;
        } catch (Throwable error) {
            Api101Bridge.log("[DC][OS4Rotation] grid config unavailable", error);
            return false;
        }
    }
}
