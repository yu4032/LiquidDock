package com.hellovoid.liquiddock;

import android.content.ComponentName;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridges native Launcher folder-open and app launch/return animation
 * contracts to the actual visible tile views of the independent four-grid
 * layout. No animation snapshots or replacement bitmap generation.
 */
final class LauncherFourGridAnimationHook {
    private static final String TAG = "[DC][FolderFourAnim]";
    private static boolean installed;

    private LauncherFourGridAnimationHook() {}

    static boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) return false;
        int hooks = 0;
        try {
            Class<?> base = Class.forName(
                    "com.miui.home.launcher.folder.FolderIcon2x2", false, loader);
            // The native return-animation contract expects an actual
            // LaunchAppAndBackHomeAnimTarget. Each four-grid tile is a
            // FolderPreviewIconView implementing that Launcher interface.
            hooks += hookIfPresent(base, "getPreviewByComponentName",
                    new Class<?>[]{ComponentName.class, int.class, long.class},
                    chain -> {
                        Object owner = chain.getThisObject();
                        LauncherFourGridContentView custom = customFor(owner);
                        if (custom != null) {
                            View tile = findAnimationTile(custom,
                                    (ComponentName) chain.getArg(0),
                                    (Integer) chain.getArg(1),
                                    (Long) chain.getArg(2));
                            if (tile != null) return tile;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            hooks += hookIfPresent(base, "getPreviewItems", new Class<?>[0],
                    chain -> {
                        LauncherFourGridContentView custom = customFor(chain.getThisObject());
                        if (custom != null) {
                            List<View> tiles = new ArrayList<>(4);
                            for (int i = 0; i < 4; i++) {
                                View tile = custom.nativeAnimationTile(i);
                                if (tile != null && custom.shortcutAt(i) != null) tiles.add(tile);
                            }
                            if (!tiles.isEmpty()) return tiles;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            hooks += hookIfPresent(base, "getPreviewIconView", new Class<?>[0],
                    chain -> {
                        LauncherFourGridContentView custom = customFor(chain.getThisObject());
                        if (custom != null && custom.nativeAnimationTile(0) != null) {
                            return custom.nativeAnimationTile(0);
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            hooks += hookIfPresent(base, "getPreviewIconHeight", new Class<?>[0],
                    chain -> {
                        LauncherFourGridContentView custom = customFor(chain.getThisObject());
                        if (custom != null && custom.nativeAnimationTile(0) != null) {
                            int measured = custom.nativeAnimationTile(0).getHeight();
                            if (measured > 0) return (float) measured;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            // Folder opening/closing and workspace transitions change the
            // native preview alpha through showPreview(). Mirror that on our
            // independent view; otherwise a second static copy remains
            // visible over the animated native folder.
            hooks += hookIfPresent(base, "showPreview",
                    new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        LauncherFourGridContentView custom = customFor(chain.getThisObject());
                        if (custom != null) {
                            boolean show = (Boolean) chain.getArg(0);
                            HookUtil.InvocationResult<Object> nativeAlpha =
                                    HookUtil.tryInvoke(chain.getThisObject(),
                                            "getFolderPreviewAlpha", show);
                            float alpha = nativeAlpha.succeeded()
                                    && nativeAlpha.value() instanceof Number
                                    ? ((Number) nativeAlpha.value()).floatValue()
                                    : (show ? 1f : 0f);
                            custom.setAlpha(alpha);
                        }
                        return result;
                    });
            hooks += hookIfPresent(base, "getPreviewCount", new Class<?>[0],
                    chain -> {
                        LauncherFourGridContentView custom = customFor(chain.getThisObject());
                        if (custom != null) return custom.visibleTileCount();
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });

            for (String cls : new String[]{
                    "com.miui.home.launcher.folder.FolderIcon2x2",
                    "com.miui.home.launcher.folder.FolderIcon2x2_4",
                    "com.miui.home.launcher.folder.FolderIcon1x1"}) {
                try {
                    Class<?> folderType = Class.forName(cls, false, loader);
                    hooks += hookIfPresent(folderType, "getPreviewPosition",
                            new Class<?>[]{Rect.class}, chain -> {
                                Object owner = chain.getThisObject();
                                LauncherFourGridContentView custom = customFor(owner);
                                if (custom != null && chain.getArg(0) instanceof Rect) {
                                    Float scale = updateNativeFolderOpenAnchor(
                                            (View) owner, custom, (Rect) chain.getArg(0));
                                    if (scale != null) return scale;
                                }
                                return chain.proceed(chain.getArgs().toArray(new Object[0]));
                            });
                } catch (Throwable ignored) {
                    // 1x1 legacy or 2x2 subclass missing on some builds.
                }
            }
            installed = hooks > 0;
            Api101Bridge.log(TAG + " native animation bridges=" + hooks);
            return installed;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " install failed", error);
            return false;
        }
    }

    private static int hookIfPresent(Class<?> owner, String name, Class<?>[] signature,
            io.github.libxposed.api.XposedInterface.Hooker callback) {
        try {
            HookUtil.hook(owner.getDeclaredMethod(name, signature), callback);
            return 1;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " optional " + owner.getSimpleName()
                    + "#" + name + " unavailable: " + error);
            return 0;
        }
    }

    private static LauncherFourGridContentView customFor(Object host) {
        if (!(host instanceof View)) return null;
        View root = (View) host;
        Object info = root.getTag();
        if (!LauncherFourGridLayoutHook.isFourGridInfo(info)
                || !LauncherLargeFolderPickerHook.isMarked(root.getContext(), info)) {
            return null;
        }
        return LauncherFourGridLayoutHook.currentContent(root);
    }

    private static View findAnimationTile(LauncherFourGridContentView custom,
            ComponentName component, int user, long shortcutId) {
        if (component == null) return null;
        View fallback = null;
        for (int i = 0; i < 4; i++) {
            Object info = custom.shortcutAt(i);
            View tile = custom.nativeAnimationTile(i);
            if (info == null || tile == null) continue;
            HookUtil.InvocationResult<Object> cname =
                    HookUtil.tryInvoke(info, "getComponentName");
            HookUtil.InvocationResult<Object> uid =
                    HookUtil.tryInvoke(info, "getUserId");
            if (!cname.succeeded() || !(cname.value() instanceof ComponentName)
                    || !uid.succeeded() || !(uid.value() instanceof Integer)) continue;
            ComponentName actual = (ComponentName) cname.value();
            if (!actual.getPackageName().equals(component.getPackageName())
                    || ((Integer) uid.value()) != user) continue;
            if (actual.equals(component)
                    && id(info) == shortcutId) return tile;
            if (fallback == null) fallback = tile;
        }
        return fallback;
    }

    private static long id(Object info) {
        try { return HookUtil.getLongField(info, "id"); }
        catch (Throwable ignored) { return -1; }
    }

    private static Float updateNativeFolderOpenAnchor(
            View folder, LauncherFourGridContentView custom, Rect out) {
        View preview = custom.previewArea();
        if (preview.getWidth() <= 0 || preview.getHeight() <= 0) return null;
        Object launcher = field(folder, "mLauncher");
        HookUtil.InvocationResult<Object> dragLayer =
                launcher == null ? null : HookUtil.tryInvoke(launcher, "getDragLayer");
        if (dragLayer == null || !dragLayer.succeeded()
                || !(dragLayer.value() instanceof View)) return null;
        View drag = (View) dragLayer.value();
        int[] previewXY = new int[2];
        int[] dragXY = new int[2];
        preview.getLocationOnScreen(previewXY);
        drag.getLocationOnScreen(dragXY);
        int left = previewXY[0] - dragXY[0];
        int top = previewXY[1] - dragXY[1];

        // Folder.open() interprets the Rect in DragLayer coordinates and
        // multiplies the native preview size by the returned scale.
        float scaleX = Math.abs(preview.getScaleX());
        float scaleY = Math.abs(preview.getScaleY());
        float scale = Math.max(0.001f, Math.min(scaleX, scaleY));
        int w = Math.max(1, Math.round(preview.getWidth() * scale));
        int h = Math.max(1, Math.round(preview.getHeight() * scale));
        out.set(left, top, left + w, top + h);
        return scale;
    }

    private static Object field(Object object, String name) {
        try { return HookUtil.getField(object, name); }
        catch (Throwable ignored) { return null; }
    }
}
