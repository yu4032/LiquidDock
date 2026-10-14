package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Controls the OEM large-folder stroke at native Drawable initialization.
 *
 * Launcher 4.50 constructs/replaces FolderIcon4x4NormalBackgroundDrawable in
 * onFinishInflate, refreshFolderBackground and thumbnail paths. A draw-time
 * owner registry cannot cover a new instance's first frame. Instead, hook the
 * vendor constructor and initPaint before any folder views are inflated.
 */
final class LargeFolderNativeOutlineHook {
    private static final String NATIVE_BACKGROUND =
            "com.miui.home.launcher.folder.FolderIcon4x4NormalBackgroundDrawable";

    // Weak identities; no folder Views or Drawables retained by set values.
    private static final Set<Drawable> INSTANCES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile boolean showNativeOutline = true;
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static boolean installed;

    private LargeFolderNativeOutlineHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed || loader == null) return installed;
        try {
            Class<?> type = Class.forName(NATIVE_BACKGROUND, false, loader);
            SharedPreferences prefs = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
            String key = ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name();
            showNativeOutline = prefs.getBoolean(key,
                    ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.runtimeFallback());

            // Native initPaint() writes mStrokeColor back into mStrokePaint.
            // Enforce the preference at the source on every native repaint
            // initialization, including calls made from the constructor.
            HookUtil.hook(type.getDeclaredMethod("initPaint"), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof Drawable) {
                    rememberAndApply((Drawable) owner);
                }
                return result;
            });
            HookUtil.hook(type.getDeclaredConstructor(Context.class, boolean.class), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof Drawable) {
                    rememberAndApply((Drawable) owner);
                }
                return result;
            });
            listener = (changed, name) -> {
                if (name != null && !key.equals(name)) return;
                boolean next = changed.getBoolean(key,
                        ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.runtimeFallback());
                if (next == showNativeOutline) return;
                showNativeOutline = next;
                new Handler(Looper.getMainLooper()).post(
                        LargeFolderNativeOutlineHook::refreshLiveInstances);
            };
            preferences = prefs;
            prefs.registerOnSharedPreferenceChangeListener(listener);
            installed = true;
            return true;
        } catch (Throwable error) {
            Api101Bridge.log("[DC][FolderOutline] native stroke setup unavailable", error);
            return false;
        }
    }

    private static void rememberAndApply(Drawable drawable) {
        synchronized (INSTANCES) {
            INSTANCES.add(drawable);
        }
        applyStroke(drawable);
    }

    private static void refreshLiveInstances() {
        ArrayList<Drawable> snapshot;
        synchronized (INSTANCES) {
            snapshot = new ArrayList<>(INSTANCES);
        }
        for (Drawable drawable : snapshot) applyStroke(drawable);
    }

    private static void applyStroke(Drawable drawable) {
        try {
            Object nativePaint = HookUtil.getField(drawable, "mStrokePaint");
            if (!(nativePaint instanceof Paint)) return;
            Paint paint = (Paint) nativePaint;
            int originalColor = HookUtil.getIntField(drawable, "mStrokeColor");
            int targetColor = NativeFolderOutlinePolicy.strokeColor(
                    originalColor, showNativeOutline);
            if (paint.getColor() != targetColor) {
                paint.setColor(targetColor);
                drawable.invalidateSelf();
            }
        } catch (Throwable error) {
            Api101Bridge.log("[DC][FolderOutline] native stroke state unavailable", error);
        }
    }
}
