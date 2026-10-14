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
        LargeFolderDebugTrace.log("install", "outline hook begin loader=" + (loader != null));
        try {
            Class<?> folder = Class.forName(FOLDER_BASE, false, loader);
            Class<?> nativeBackground = Class.forName(NATIVE_BACKGROUND, false, loader);
            java.lang.reflect.Method drawFolder = HookUtil.findMethodExact(
                    folder, "drawChild",
                    new Class<?>[]{Canvas.class, View.class, long.class});
            java.lang.reflect.Method drawBackground = HookUtil.findMethodExact(
                    nativeBackground, "draw", new Class<?>[]{Canvas.class});
            LargeFolderDebugTrace.log("install", "outline methods folder="
                    + drawFolder.toGenericString() + " background=" + drawBackground.toGenericString());
            if (!drawFolder.getDeclaringClass().getName().startsWith("com.miui.home.")
                    || drawBackground.getDeclaringClass() != nativeBackground) {
                LargeFolderDebugTrace.log("install", "outline methods rejected");
                return false;
            }

            // Scope the native background draw to a folder's real Workspace render,
            // rather than mutating a shared drawable or a generic canvas globally.
            HookUtil.hook(drawFolder, chain -> {
                Object owner = chain.getThisObject();
                if (!(owner instanceof View)
                        || !LauncherGlassHierarchy.isWorkspace((View) owner)) {
                    LargeFolderDebugTrace.log("draw-outside", "FolderIcon2x2.drawChild workspace="
                            + (owner instanceof View && LauncherGlassHierarchy.isWorkspace((View) owner))
                            + " owner=" + (owner == null ? "null" : owner.getClass().getName()));
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                View view = (View) owner;
                synchronized (VISIBLE_FOLDERS) { VISIBLE_FOLDERS.add(view); }
                // FolderIcon2x2.drawChild is also the point where vendor code
                // may replace its background drawable during drag/rebind.
                View child = (View) chain.getArg(1);
                LargeFolderDebugTrace.log("draw-inside", "drawChild owner="
                        + view.getClass().getSimpleName() + " child="
                        + (child == null ? "null" : child.getClass().getSimpleName()));
                LargeFolderDebugTrace.material("drawChild", view);
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
            // An ImageView may draw a cached native drawable without entering
            // FolderIcon2x2.drawChild. This temporary probe distinguishes that
            // path from a missing / suppressed Drawable.draw hook.
            try {
                Class<?> imageType = Class.forName(
                        "com.miui.home.launcher.folder.LauncherFolder2x2IconImageView",
                        false, loader);
                java.lang.reflect.Method imageDraw = HookUtil.findMethodExact(
                        imageType, "onDraw", new Class<?>[]{Canvas.class});
                if (imageDraw.getDeclaringClass().getName().startsWith("com.miui.home.")) {
                    HookUtil.hook(imageDraw, chain -> {
                        if (chain.getThisObject() instanceof View) {
                            View v = (View) chain.getThisObject();
                            LargeFolderDebugTrace.log("image-draw",
                                    v.getClass().getSimpleName() + "@"
                                    + Integer.toHexString(System.identityHashCode(v))
                                    + " duringFolderDraw="
                                    + Boolean.TRUE.equals(DRAWING_LARGE_FOLDER.get()));
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
                    LargeFolderDebugTrace.log("install", "native folder image.onDraw installed");
                } else {
                    LargeFolderDebugTrace.log("install",
                            "image.onDraw inherited framework: " + imageDraw.toGenericString());
                }
            } catch (Throwable error) {
                LargeFolderDebugTrace.log("install", "image.onDraw unavailable "
                        + LargeFolderDebugTrace.shortError(error));
            }
            HookUtil.hook(drawBackground, chain -> {
                boolean inside = Boolean.TRUE.equals(DRAWING_LARGE_FOLDER.get());
                String diagnosticChannel = showNativeOutline ? "outline-enabled" : "outline-disabled";
                LargeFolderDebugTrace.log(diagnosticChannel, "drawable.draw class="
                        + chain.getThisObject().getClass().getName()
                        + " duringFolderDraw=" + inside + " enabled=" + showNativeOutline);
                if (!NativeFolderOutlinePolicy.shouldSuppress(showNativeOutline, inside)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                Object nativeStroke;
                try {
                    nativeStroke = HookUtil.getField(chain.getThisObject(), "mStrokePaint");
                } catch (Throwable error) {
                    LargeFolderDebugTrace.log("outline-error", "stroke field missing "
                            + LargeFolderDebugTrace.shortError(error));
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                if (!(nativeStroke instanceof Paint)) {
                    LargeFolderDebugTrace.log("outline-error", "stroke field type="
                            + (nativeStroke == null ? "null" : nativeStroke.getClass().getName()));
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                Paint stroke = (Paint) nativeStroke;
                int originalAlpha = stroke.getAlpha();
                LargeFolderDebugTrace.log("outline-suppressed", "stroke Alpha "
                        + originalAlpha + " -> 0");
                stroke.setAlpha(0);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    stroke.setAlpha(originalAlpha);
                }
            });
            installed = true;
            LargeFolderDebugTrace.log("install", "outline draw hooks installed");
            bindPreferences();
            return true;
        } catch (Throwable error) {
            LargeFolderDebugTrace.log("install", "outline FAILED " + LargeFolderDebugTrace.shortError(error));
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
        LargeFolderDebugTrace.log("config", "initial showNativeOutline=" + showNativeOutline);
        listener = (changed, key) -> {
            if (key != null
                    && !ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name().equals(key)) return;
            boolean next = changed.getBoolean(
                    ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.name(),
                    ConfigSchema.Glass.LARGE_FOLDER_NATIVE_OUTLINE.runtimeFallback());
            LargeFolderDebugTrace.log("config", "preference changed key=" + key
                    + " next=" + next + " current=" + showNativeOutline);
            if (next == showNativeOutline) return;
            showNativeOutline = next;
            new Handler(Looper.getMainLooper()).post(() -> {
                synchronized (VISIBLE_FOLDERS) {
                    LargeFolderDebugTrace.log("config", "request redraw folderCount="
                            + VISIBLE_FOLDERS.size());
                    int reported = 0;
                    for (View folder : new ArrayList<>(VISIBLE_FOLDERS)) {
                        if (!folder.isAttachedToWindow()) continue;
                        if (reported++ < 8) {
                            LargeFolderDebugTrace.material("toggle/redraw-before", folder);
                            LargeFolderDebugTrace.snapshot("toggle/redraw-before", folder);
                        }
                        folder.postInvalidateOnAnimation();
                        if (reported <= 8) {
                            folder.postOnAnimation(() -> LargeFolderDebugTrace.material(
                                    "toggle/redraw-nextFrame", folder));
                        }
                    }
                }
            });
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }
}
