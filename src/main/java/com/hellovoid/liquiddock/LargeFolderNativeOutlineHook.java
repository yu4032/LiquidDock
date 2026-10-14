package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Suppress only the original Launcher large-folder stroke.
 *
 * A native background can be drawn independently of FolderIcon2x2.drawChild
 * (verified on Launcher 4.50). Associate the drawable with its owning
 * workspace folder instead of relying on a stack-scoped ThreadLocal.
 */
final class LargeFolderNativeOutlineHook {
    private static final String FOLDER_BASE =
            "com.miui.home.launcher.folder.FolderIcon2x2";
    private static final String NATIVE_BACKGROUND =
            "com.miui.home.launcher.folder.FolderIcon4x4NormalBackgroundDrawable";

    // Weak keys, no View/Drawable retained by map values.
    private static final Set<View> FOLDERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Drawable> BACKGROUNDS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static volatile boolean showNativeOutline = true;
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static boolean installed;

    private LargeFolderNativeOutlineHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed || loader == null) return installed;
        try {
            Class<?> folderType = Class.forName(FOLDER_BASE, false, loader);
            Class<?> drawableType = Class.forName(NATIVE_BACKGROUND, false, loader);
            java.lang.reflect.Method drawFolder = HookUtil.findMethodExact(
                    folderType, "drawChild",
                    new Class<?>[]{Canvas.class, View.class, long.class});
            java.lang.reflect.Method drawBackground = HookUtil.findMethodExact(
                    drawableType, "draw", new Class<?>[]{Canvas.class});
            if (!drawFolder.getDeclaringClass().getName().startsWith("com.miui.home.")
                    || drawBackground.getDeclaringClass() != drawableType) return false;

            HookUtil.hook(drawFolder, chain -> {
                Object owner = chain.getThisObject();
                if (!(owner instanceof View)
                        || !LauncherGlassHierarchy.isWorkspace((View) owner)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                View folder = (View) owner;
                synchronized (FOLDERS) { FOLDERS.add(folder); }
                // Refresh ownership if the OEM swapped its background while
                // rebinding; the map contains no strong owner reference.
                rememberBackground(folder, drawableType);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            HookUtil.hook(drawBackground, chain -> {
                Object candidate = chain.getThisObject();
                if (showNativeOutline || !(candidate instanceof Drawable)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                boolean belongsToFolder;
                synchronized (BACKGROUNDS) {
                    belongsToFolder = BACKGROUNDS.contains((Drawable) candidate);
                }
                if (!NativeFolderOutlinePolicy.shouldSuppress(
                        showNativeOutline, belongsToFolder)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                Object paint;
                try {
                    paint = HookUtil.getField(candidate, "mStrokePaint");
                } catch (Throwable ignored) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                if (!(paint instanceof Paint)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                Paint stroke = (Paint) paint;
                int alpha = stroke.getAlpha();
                stroke.setAlpha(0);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    stroke.setAlpha(alpha);
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

    private static void rememberBackground(View folder, Class<?> drawableType) {
        Object result = HookUtil.tryInvoke(folder, "getFolderBackground").value();
        if (!(result instanceof Drawable) || !drawableType.isInstance(result)) return;
        synchronized (BACKGROUNDS) {
            BACKGROUNDS.add((Drawable) result);
        }
    }

    private static void invalidateFolder(View folder) {
        if (!folder.isAttachedToWindow()) return;
        folder.postInvalidateOnAnimation();
        // The native ImageView can repaint its Drawable outside drawChild.
        try {
            Object value = HookUtil.getField(folder, "mImageView");
            if (value instanceof View) ((View) value).postInvalidateOnAnimation();
            if (value instanceof ImageView) {
                Drawable drawable = ((ImageView) value).getDrawable();
                if (drawable != null) drawable.invalidateSelf();
            }
        } catch (Throwable ignored) {
            // Some OEM builds rename mImageView; the parent invalidation remains.
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
                ArrayList<View> targets;
                synchronized (FOLDERS) {
                    targets = new ArrayList<>(FOLDERS);
                }
                for (View folder : targets) invalidateFolder(folder);
            });
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }
}
