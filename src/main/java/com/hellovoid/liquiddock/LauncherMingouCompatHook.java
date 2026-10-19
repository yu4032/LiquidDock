package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import com.hellovoid.liquiddock.config.ConfigSchema;

/**
 * Opt-in recovery for custom Launcher builds that recalculate FolderInfo spans
 * from itemType during load, losing LiquidDock's persisted real-2x2 footprint.
 *
 * A disabled toggle is a strict no-op. When enabled, only folders explicitly
 * marked by LiquidDock AND stored with a 2x2 database footprint are repaired.
 * No package, version-name or version-code detection is involved.
 */
final class LauncherMingouCompatHook {
    private static final String TAG = "[DC][MingouCompat]";
    private static final String KEY = ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT.name();
    private static boolean installed;
    private static volatile boolean enabled;
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;

    private LauncherMingouCompatHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed || loader == null) return installed;
        try {
            Class<?> folderInfo = Class.forName(
                    "com.miui.home.launcher.FolderInfo", false, loader);
            java.lang.reflect.Method load = HookUtil.findMethodExact(
                    folderInfo, "load",
                    new Class<?>[]{Context.class, Cursor.class});

            preferences = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
            enabled = preferences.getBoolean(KEY,
                    ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT.runtimeFallback());

            HookUtil.hook(load, chain -> {
                // The hook can stay installed, but the feature never reads or
                // changes a folder while the user switch is off.
                if (!enabled) return chain.proceed(chain.getArgs().toArray(new Object[0]));

                int dbSpanX = -1;
                int dbSpanY = -1;
                try {
                    Cursor cursor = (Cursor) chain.getArg(1);
                    if (cursor != null) {
                        int spanXColumn = cursor.getColumnIndex("spanX");
                        int spanYColumn = cursor.getColumnIndex("spanY");
                        if (spanXColumn >= 0 && spanYColumn >= 0) {
                            dbSpanX = cursor.getInt(spanXColumn);
                            dbSpanY = cursor.getInt(spanYColumn);
                        }
                    }
                } catch (Throwable ignored) {
                    // Never infer a 2x2 footprint without persisted evidence.
                }

                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (!enabled || dbSpanX != 2 || dbSpanY != 2) return result;

                try {
                    Object info = chain.getThisObject();
                    Object contextArg = chain.getArg(0);
                    if (info == null || !(contextArg instanceof Context)) return result;
                    Context context = (Context) contextArg;

                    int type = HookUtil.getIntField(info, "itemType");
                    if (!LargeFolderFootprintPolicy.supportsType(type)) return result;
                    boolean marked = LauncherLargeFolderPickerHook.isMarked(context, info);
                    int spanX = HookUtil.getIntField(info, "spanX");
                    int spanY = HookUtil.getIntField(info, "spanY");
                    if (!LargeFolderFootprintPolicy.shouldRestoreLoadedTwoByTwo(
                            enabled, type, marked, dbSpanX, dbSpanY, spanX, spanY)) {
                        return result;
                    }

                    HookUtil.setIntField(info, "spanX", 2);
                    HookUtil.setIntField(info, "spanY", 2);
                    Api101Bridge.log(TAG + " restored span " + spanX + "x" + spanY
                            + " -> 2x2, itemType=" + type);
                } catch (Throwable error) {
                    // The original vendor load already succeeded: fail closed.
                    Api101Bridge.log(TAG + " restore failed", error);
                }
                return result;
            });

            listener = (changed, key) -> {
                if (key != null && !KEY.equals(key)) return;
                enabled = changed.getBoolean(KEY,
                        ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT.runtimeFallback());
            };
            preferences.registerOnSharedPreferenceChangeListener(listener);
            installed = true;
            return true;
        } catch (Throwable error) {
            if (preferences != null && listener != null) {
                try {
                    preferences.unregisterOnSharedPreferenceChangeListener(listener);
                } catch (Throwable ignored) {
                    // Optional cleanup only.
                }
            }
            listener = null;
            preferences = null;
            enabled = false;
            Api101Bridge.log(TAG + " unavailable", error);
            return false;
        }
    }
}
