package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies one icon-size policy at Launcher ItemIcon's semantic icon-bind boundary.
 * Workspace and Dock ShortcutIcon instances share the same compound-drawable path;
 * FolderIcon1x1 keeps its parent hit target and resizes only the vendor mIconImageView.
 */
final class LauncherIconSizeHook {
    private static final String TAG = "[DC][IconSize]";
    private static final String ITEM_ICON = "com.miui.home.launcher.ItemIcon";
    private static final String SHORTCUT_ICON = "ShortcutIcon";
    private static final String SMALL_FOLDER = "FolderIcon1x1";

    private static final Map<Drawable, Rect> BASE_DRAWABLE_BOUNDS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, int[]> BASE_VIEW_SIZE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, View.OnAttachStateChangeListener> OWNER_ATTACH_LISTENERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, View.OnLayoutChangeListener> FOLDER_LAYOUT_LISTENERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile boolean enabled;
    private static volatile int percent = LauncherIconSizePolicy.DEFAULT_PERCENT;
    private static boolean installed;

    private LauncherIconSizeHook() {}

    static boolean install(ClassLoader classLoader, boolean iconSizeEnabled, int iconSizePercent) {
        enabled = iconSizeEnabled;
        percent = Math.max(LauncherIconSizePolicy.MIN_PERCENT,
                Math.min(LauncherIconSizePolicy.MAX_PERCENT, iconSizePercent));
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> itemIcon = Class.forName(ITEM_ICON, false, classLoader);
            Method bind = HookUtil.findMethodExact(itemIcon, "setIconImageView",
                    new Class<?>[]{Drawable.class, Bitmap.class});
            HookUtil.hook(bind, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner instanceof View) observeBoundIcon((View) owner);
                return result;
            });
            installed = true;
            MainHook.log(TAG + " ItemIcon.setIconImageView hook installed enabled="
                    + enabled + " percent=" + percent);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    static void applyBoundIcon(View owner) {
        observeBoundIcon(owner);
    }

    private static void observeBoundIcon(View owner) {
        if (owner == null || !isSupportedOwner(owner)) return;
        synchronized (OWNER_ATTACH_LISTENERS) {
            if (!OWNER_ATTACH_LISTENERS.containsKey(owner)) {
                View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View view) {
                        applyAttachedOwner(view);
                    }

                    @Override public void onViewDetachedFromWindow(View view) {
                        // A ShortcutIcon may later be rebound into a different Launcher domain.
                        // Re-evaluate only when it has a live parent again.
                    }
                };
                OWNER_ATTACH_LISTENERS.put(owner, listener);
                owner.addOnAttachStateChangeListener(listener);
            }
        }
        if (owner.isAttachedToWindow()) applyAttachedOwner(owner);
    }

    private static boolean isSupportedOwner(View owner) {
        String simpleName = owner.getClass().getSimpleName();
        String className = owner.getClass().getName();
        return SHORTCUT_ICON.equals(simpleName) || className.endsWith("." + SHORTCUT_ICON)
                || SMALL_FOLDER.equals(simpleName) || className.endsWith("." + SMALL_FOLDER);
    }

    private static void applyAttachedOwner(View owner) {
        String simpleName = owner.getClass().getSimpleName();
        String className = owner.getClass().getName();
        if (SHORTCUT_ICON.equals(simpleName) || className.endsWith("." + SHORTCUT_ICON)) {
            LauncherGlassHierarchy.Domain domain = LauncherGlassHierarchy.classify(owner);
            boolean inScope = domain == LauncherGlassHierarchy.Domain.WORKSPACE
                    || domain == LauncherGlassHierarchy.Domain.DOCK;
            applyShortcutIcon(owner, inScope && enabled);
            return;
        }
        if (SMALL_FOLDER.equals(simpleName) || className.endsWith("." + SMALL_FOLDER)) {
            applySmallFolder(owner);
        }
    }

    private static void applyShortcutIcon(View owner, boolean active) {
        if (!(owner instanceof TextView)) return;
        TextView text = (TextView) owner;
        Drawable drawable = topDrawable(text);
        if (drawable == null) return;

        Rect base = BASE_DRAWABLE_BOUNDS.get(drawable);
        if (base == null) {
            Rect current = drawable.getBounds();
            int width = current != null ? current.width() : 0;
            int height = current != null ? current.height() : 0;
            if (width <= 0) width = drawable.getIntrinsicWidth();
            if (height <= 0) height = drawable.getIntrinsicHeight();
            if (width <= 0 || height <= 0) return;
            base = new Rect(0, 0, width, height);
            BASE_DRAWABLE_BOUNDS.put(drawable, base);
        }

        int width = LauncherIconSizePolicy.scaledPx(base.width(), active, percent);
        int height = LauncherIconSizePolicy.scaledPx(base.height(), active, percent);
        Rect current = drawable.getBounds();
        if (current.width() == width && current.height() == height) return;
        drawable.setBounds(0, 0, width, height);
        text.requestLayout();
        text.invalidate();
    }

    private static Drawable topDrawable(TextView text) {
        Drawable[] absolute = text.getCompoundDrawables();
        Drawable top = absolute != null && absolute.length > 1 ? absolute[1] : null;
        if (top != null) return top;
        Drawable[] relative = text.getCompoundDrawablesRelative();
        return relative != null && relative.length > 1 ? relative[1] : null;
    }

    private static void applySmallFolder(View owner) {
        View material;
        try {
            Object value = HookUtil.getField(owner, "mIconImageView");
            if (!(value instanceof View)) return;
            material = (View) value;
        } catch (Throwable error) {
            MainHook.log(TAG + " FolderIcon1x1 material unavailable: " + error);
            return;
        }

        applySmallFolderMaterialSize(material);
        synchronized (FOLDER_LAYOUT_LISTENERS) {
            if (FOLDER_LAYOUT_LISTENERS.containsKey(material)) return;
            View.OnLayoutChangeListener listener = (view, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> applySmallFolderMaterialSize(view);
            FOLDER_LAYOUT_LISTENERS.put(material, listener);
            material.addOnLayoutChangeListener(listener);
        }
    }

    private static void applySmallFolderMaterialSize(View material) {
        if (material == null) return;
        int[] base = BASE_VIEW_SIZE.get(material);
        if (base == null) {
            int width = material.getWidth();
            int height = material.getHeight();
            ViewGroup.LayoutParams lp = material.getLayoutParams();
            if (width <= 0 && lp != null && lp.width > 0) width = lp.width;
            if (height <= 0 && lp != null && lp.height > 0) height = lp.height;
            if (width <= 0 || height <= 0) return;
            base = new int[]{width, height};
            BASE_VIEW_SIZE.put(material, base);
        }

        ViewGroup.LayoutParams lp = material.getLayoutParams();
        if (lp == null) return;
        boolean active = enabled && LauncherGlassHierarchy.isWorkspace(material);
        int targetWidth = LauncherIconSizePolicy.scaledPx(base[0], active, percent);
        int targetHeight = LauncherIconSizePolicy.scaledPx(base[1], active, percent);
        if (lp.width == targetWidth && lp.height == targetHeight) return;
        lp.width = targetWidth;
        lp.height = targetHeight;
        material.setLayoutParams(lp);
    }
}
