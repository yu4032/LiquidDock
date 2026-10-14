package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Controls only the OEM large-folder background stroke.
 * The native drawable keeps its fill, intrinsic geometry and identity.
 */
final class LargeFolderNativeOutlineHook {
    private static final String FOLDER_BASE =
            "com.miui.home.launcher.folder.FolderIcon2x2";
    private static final String NATIVE_BACKGROUND =
            "com.miui.home.launcher.folder.FolderIcon4x4NormalBackgroundDrawable";
    private static final ThreadLocal<Boolean> DRAWING_LARGE_FOLDER = new ThreadLocal<>();
    private static final Set<View> VISIBLE_FOLDERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile boolean showNativeOutline = true;
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static boolean installed;

    private LargeFolderNativeOutlineHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed || loader == null) return installed;
        try {
            Class<?> folder = Class.forName(FOLDER_BASE, false, loader);
            Class<?> nativeBackground = Class.forName(NATIVE_BACKGROUND, false, loader);
            java.lang.reflect.Method drawFolder = HookUtil.findMethodExact(
                    folder, "drawChild",
                    new Class<?>[]{Canvas.class, View.class, long.class});
            java.lang.reflect.Method drawBackground = HookUtil.findMethodExact(
                    nativeBackground, "draw", new Class<?>[]{Canvas.class});
            if (!drawFolder.getDeclaringClass().getName().startsWith("com.miui.home.")
                    || drawBackground.getDeclaringClass() != nativeBackground) return false;

            // Scope the native background draw to a folder's real Workspace render,
            // rather than mutating a shared drawable or a generic canvas globally.
            HookUtil.hook(drawFolder, chain -> {
                Object owner = chain.getThisObject();
                if (!(owner instanceof View)
                        || !LauncherGlassHierarchy.isWorkspace((View) owner)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                View view = (View) owner;
                synchronized (VISIBLE_FOLDERS) { VISIBLE_FOLDERS.add(view); }
                // FolderIcon2x2.drawChild is also the point where vendor code
                // may replace its background drawable during drag/rebind.
                View child = (View) chain.getArg(1);
                LauncherNativeFourFolderHook.reconcileTitleDrawingBeforeChild(view, child);
                Boolean previous = DRAWING_LARGE_FOLDER.get();
                DRAWING_LARGE_FOLDER.set(Boolean.TRUE);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    if (previous == null) DRAWING_LARGE_FOLDER.remove();
                    else DRAWING_LARGE_FOLDER.set(previous);
                }
            });
            HookUtil.hook(drawBackground, chain -> {
                if (!NativeFolderOutlinePolicy.shouldSuppress(
                        showNativeOutline, Boolean.TRUE.equals(DRAWING_LARGE_FOLDER.get()))) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                Object nativeStroke;
                try {
                    nativeStroke = HookUtil.getField(chain.getThisObject(), "mStrokePaint");
                } catch (Throwable ignored) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                if (!(nativeStroke instanceof Paint)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                Paint stroke = (Paint) nativeStroke;
                int originalAlpha = stroke.getAlpha();
                stroke.setAlpha(0);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    stroke.setAlpha(originalAlpha);
                }
            });
            installed = true;
            bindPreferences();
            return true;
        } catch (Throwable error) {
            Api101Bridge.log("[DC][FolderOutline] unavailable", error);
            return false;
        }
    }

    private static void bindPreferences() {
        SharedPreferences prefs = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
        preferences = prefs;
        showNativeOutline = prefs.getBoolean(
                ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name(),
                ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.runtimeFallback());
        listener = (changed, key) -> {
            if (key != null
                    && !ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name().equals(key)) return;
            boolean next = changed.getBoolean(
                    ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name(),
                    ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.runtimeFallback());
            if (next == showNativeOutline) return;
            showNativeOutline = next;
            new Handler(Looper.getMainLooper()).post(() -> {
                synchronized (VISIBLE_FOLDERS) {
                    for (View folder : new ArrayList<>(VISIBLE_FOLDERS)) {
                        if (folder.isAttachedToWindow()) folder.postInvalidateOnAnimation();
                    }
                }
            });
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }
}
