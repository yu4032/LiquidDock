package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.List;
import java.util.function.Consumer;

/**
 * Dedicated view tree for the FOUR-LARGE folder style.
 *
 * This is deliberately NOT a FolderIcon2x2_4 preview. The original Launcher
 * FolderIcon still owns folder open, item persistence, drag identity, and
 * workspace collision handling; this content view owns its own material,
 * 2x2 sizing, four native ShortcutInfo icons, and click hit regions.
 */
final class LauncherFourGridContentView extends FrameLayout {
    private static final int COUNT = 4;

    private final View background;
    private final ImageView[] tiles = new ImageView[COUNT];
    private final Object[] shortcuts = new Object[COUNT];
    private ViewGroup folder;
    private int boundCount = -1;

    LauncherFourGridContentView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);

        background = new View(context);
        GradientDrawable plate = new GradientDrawable();
        plate.setShape(GradientDrawable.RECTANGLE);
        plate.setCornerRadius(dp(24));
        plate.setColor(0x58939AA8);
        plate.setStroke(dp(1), 0x65FFFFFF);
        background.setBackground(plate);
        background.setClickable(false);
        addView(background);

        for (int i = 0; i < COUNT; i++) {
            ImageView tile = createNativeAnimTile(context);
            tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setVisibility(GONE);
            final int index = i;
            tile.setOnClickListener(clicked -> {
                Object shortcut = shortcuts[index];
                if (shortcut == null) return;
                Object launcher = field(folder, "mLauncher");
                HookUtil.InvocationResult<Object> launch =
                        HookUtil.tryInvoke(shortcut, "handleClick", launcher, clicked);
                if (!launch.succeeded()) {
                    Api101Bridge.log("[DC][FolderFourGrid] native shortcut click failed: "
                            + launch.failure());
                }
            });
            tile.setOnLongClickListener(clicked ->
                    folder != null && folder.performLongClick());
            tiles[i] = tile;
            addView(tile);
        }
    }

    void bind(ViewGroup nativeFolder, Object info, boolean refresh) {
        folder = nativeFolder;
        HookUtil.InvocationResult<Object> contentResult =
                HookUtil.tryInvoke(info, "getContents");
        Object nativeItems = contentResult.succeeded() ? contentResult.value() : null;
        if (!(nativeItems instanceof List)) {
            // Reference Launcher exposes mContents as its persistent data
            // field. Use that same native list when an OEM build inlines or
            // removes the getContents() accessor.
            nativeItems = field(info, "mContents");
        }
        if (!(nativeItems instanceof List)) {
            Api101Bridge.log("[DC][FolderFourGrid] folder shortcut list unavailable");
            return;
        }
        List<?> contents = (List<?>) nativeItems;
        if (!refresh && contents.size() == boundCount) {
            boolean same = true;
            for (int i = 0; i < COUNT; i++) {
                Object current = i < contents.size() ? contents.get(i) : null;
                if (shortcuts[i] != current) {
                    same = false;
                    break;
                }
            }
            if (same) return;
        }
        boundCount = contents.size();
        Object iconCache = field(nativeFolder, "mIconCache");
        if (iconCache == null) {
            try {
                Class<?> app = Class.forName(
                        "com.miui.home.launcher.Application", false,
                        nativeFolder.getClass().getClassLoader());
                HookUtil.InvocationResult<Object> instance =
                        HookUtil.tryInvokeStatic(app, "getInstance");
                if (instance.succeeded()) {
                    HookUtil.InvocationResult<Object> cache =
                            HookUtil.tryInvoke(instance.value(), "getIconCache");
                    if (cache.succeeded()) iconCache = cache.value();
                }
            } catch (Throwable ignored) {
                // Keep the last displayed icon if the vendor provider moved.
            }
        }

        for (int i = 0; i < COUNT; i++) {
            Object shortcut = i < contents.size() ? contents.get(i) : null;
            shortcuts[i] = shortcut;
            ImageView tile = tiles[i];
            if (shortcut == null) {
                tile.setImageDrawable(null);
                tile.setTag(null);
                tile.setVisibility(GONE);
                continue;
            }
            tile.setTag(shortcut);
            tile.setVisibility(VISIBLE);
            // FolderPreviewIconView itself is the vendor
            // LaunchAppAndBackHomeAnimTarget: keep BuddyInfo consistent with
            // the rendered icon for the app launch/return transition.
            if (tile.getClass().getName().endsWith(".FolderPreviewIconView")) {
                HookUtil.tryInvoke(tile, "setMBuddyInfo", shortcut);
            }
            // getIconDrawable() explicitly asserts NON-UI-THREAD in MIUI
            // Launcher. Its native getIconAsync() is the authoritative safe
            // path: cached drawables return immediately, otherwise Launcher
            // resolves them on its own executor and delivers on UI.
            final Object expected = shortcut;
            Drawable cached = field(shortcut, "mIconDrawable") instanceof Drawable
                    ? (Drawable) field(shortcut, "mIconDrawable") : null;
            tile.setImageDrawable(copyDrawable(cached));
            if (iconCache != null) {
                Consumer<Drawable> apply = drawable -> {
                    if (shortcuts[indexOf(tile)] != expected) return;
                    tile.post(() -> {
                        if (tile.getTag() == expected) {
                            tile.setImageDrawable(copyDrawable(drawable));
                        }
                    });
                };
                HookUtil.InvocationResult<Object> loading = HookUtil.tryInvoke(
                        shortcut, "getIconAsync",
                        getContext(), iconCache, (Drawable) null, apply);
                if (!loading.succeeded()) {
                    Api101Bridge.log("[DC][FolderFourGrid] native async icon unavailable "
                            + loading.failure());
                }
            }
            Object title = field(shortcut, "title");
            if (title instanceof CharSequence) {
                tile.setContentDescription((CharSequence) title);
            }
        }
    }

    /**
     * Reuse only the vendor's per-icon animation TARGET, not its 2x2 layout,
     * PreviewContainer or item placement. Its native interface supplies
     * location/radius/visibility to Quickstep animation code.
     */
    private static ImageView createNativeAnimTile(Context context) {
        try {
            Class<?> type = Class.forName(
                    "com.miui.home.launcher.folder.FolderPreviewIconView",
                    false, context.getClassLoader());
            Object view = type.getConstructor(Context.class).newInstance(context);
            if (view instanceof ImageView) return (ImageView) view;
        } catch (Throwable error) {
            Api101Bridge.log("[DC][FolderFourGrid] animation tile class unavailable: " + error);
        }
        return new ImageView(context);
    }

    View nativeAnimationTile(int index) {
        if (index < 0 || index >= COUNT) return null;
        ImageView tile = tiles[index];
        return tile.getClass().getName().endsWith(".FolderPreviewIconView")
                ? tile : null;
    }

    Object shortcutAt(int index) {
        return index >= 0 && index < COUNT ? shortcuts[index] : null;
    }

    int visibleTileCount() {
        int count = 0;
        for (int i = 0; i < COUNT; i++) {
            if (shortcuts[i] != null && tiles[i].getVisibility() == VISIBLE) count++;
        }
        return count;
    }

    View previewArea() {
        return background;
    }

    private Drawable copyDrawable(Drawable input) {
        if (input == null) return null;
        Drawable.ConstantState state = input.getConstantState();
        return state == null ? input : state.newDrawable(getResources()).mutate();
    }

    private int indexOf(ImageView tile) {
        for (int i = 0; i < COUNT; i++) {
            if (tiles[i] == tile) return i;
        }
        return -1;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);

        int title = titleReservation(height);
        int square = Math.max(0, Math.min(width, height - title));
        int inset = Math.max(dp(7), square / 14);
        int spacing = Math.max(dp(5), square / 22);
        int icon = Math.max(0, (square - 2 * inset - spacing) / 2);

        background.measure(exact(square), exact(square));
        for (ImageView tile : tiles) {
            tile.measure(exact(icon), exact(icon));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int square = Math.max(0, Math.min(width, height - titleReservation(height)));
        int ox = (width - square) / 2;
        background.layout(ox, 0, ox + square, square);

        int inset = Math.max(dp(7), square / 14);
        int spacing = Math.max(dp(5), square / 22);
        int icon = Math.max(0, (square - 2 * inset - spacing) / 2);
        for (int i = 0; i < COUNT; i++) {
            int x = ox + inset + (i % 2) * (icon + spacing);
            int y = inset + (i / 2) * (icon + spacing);
            tiles[i].layout(x, y, x + icon, y + icon);
        }
    }

    private int titleReservation(int height) {
        Object titleView = field(folder, "mTitleContainer");
        if (titleView instanceof View && ((View) titleView).getMeasuredHeight() > 0) {
            return ((View) titleView).getMeasuredHeight();
        }
        return Math.min(height / 6, dp(24));
    }

    private static int exact(int size) {
        return MeasureSpec.makeMeasureSpec(Math.max(0, size), MeasureSpec.EXACTLY);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private static Object field(Object target, String name) {
        if (target == null) return null;
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }
}
