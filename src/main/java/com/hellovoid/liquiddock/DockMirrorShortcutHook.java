package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hides Xiaomi Launcher's Dock entry for phone mirroring without shifting the remaining icons.
 *
 * <p>The vendor HotSeats layout is center-justified. Merely removing/collapsing the mirror item
 * re-centers the remaining items, while merely hiding its pixels leaves a visible hole. This hook
 * therefore collapses the mirror footprint to zero and applies a half-footprint counter-shift to
 * the Dock content/background so the surviving icons stay anchored.
 */
final class DockMirrorShortcutHook {
    private static final String TAG = "[DC][DockMirror]";
    private static final String HOTSEATS_ADAPTER =
            "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter";
    private static final String HOTSEATS_VIEW_HOLDER = HOTSEATS_ADAPTER + "$ViewHolder";
    private static final String OFFSET_DECORATION =
            "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration";

    private static final Map<Object, Boolean> ADAPTERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, HiddenGeometry> HIDDEN_ITEMS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> HIDDEN_DECOR_WIDTHS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Float> APPLIED_X_OFFSETS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private DockMirrorShortcutHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        installAdapterVisibilityHook(classLoader);
        installDecorationCollapseHook(classLoader);
        installed = true;
    }

    private static void installAdapterVisibilityHook(ClassLoader classLoader) {
        try {
            Class<?> adapterType = Class.forName(HOTSEATS_ADAPTER, false, classLoader);
            Class<?> viewHolderType = Class.forName(HOTSEATS_VIEW_HOLDER, false, classLoader);

            Constructor<?>[] constructors = adapterType.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object adapter = chain.getThisObject();
                    if (adapter != null) ADAPTERS.put(adapter, Boolean.TRUE);
                    return result;
                });
            }

            HookUtil.hookMethod(
                    adapterType,
                    "onBindViewHolder",
                    new Class<?>[]{viewHolderType, int.class, List.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        try {
                            applyBoundItemVisibility(
                                    chain.getThisObject(), args[0], ((Number) args[1]).intValue());
                        } catch (Throwable error) {
                            // Fail open: a vendor contract mismatch must never hide unrelated items.
                            MainHook.log(TAG + " bound geometry apply failed: " + error);
                        }
                        return result;
                    });

            MainHook.log(TAG + " adapter geometry hook installed constructors="
                    + constructors.length);
        } catch (Throwable error) {
            MainHook.log(TAG + " adapter geometry hook unavailable: " + error);
        }
    }

    private static void installDecorationCollapseHook(ClassLoader classLoader) {
        try {
            Class<?> recyclerView = Class.forName(
                    "androidx.recyclerview.widget.RecyclerView", false, classLoader);
            Class<?> recyclerState = Class.forName(
                    "androidx.recyclerview.widget.RecyclerView$State", false, classLoader);
            HookUtil.hookMethod(
                    classLoader,
                    OFFSET_DECORATION,
                    "getItemOffsets",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (!(args[0] instanceof Rect)
                                || !(args[1] instanceof View)
                                || !(args[2] instanceof View)) {
                            return result;
                        }

                        View itemView = (View) args[1];
                        HiddenGeometry geometry = HIDDEN_ITEMS.get(itemView);
                        if (geometry == null) return result;

                        Rect out = (Rect) args[0];
                        HIDDEN_DECOR_WIDTHS.put(itemView, out.left + out.right);
                        out.left = 0;
                        out.right = 0;

                        View recycler = (View) args[2];
                        recycler.post(() -> applyCenteringCompensation(recycler));
                        return result;
                    },
                    Rect.class,
                    View.class,
                    recyclerView,
                    recyclerState);
            MainHook.log(TAG + " decoration collapse hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " decoration collapse hook unavailable: " + error);
        }
    }

    private static void applyBoundItemVisibility(
            Object adapter, Object viewHolder, int position) {
        if (adapter == null || viewHolder == null || position < 0) return;

        int itemCount = ((Number) HookUtil.requireInvoke(adapter, "getItemCount")).intValue();
        if (position >= itemCount) return;

        int viewType = ((Number) HookUtil.requireInvoke(
                adapter, "getItemViewType", position)).intValue();
        if (!DockMirrorShortcutVisibilityPolicy.needsVisibilityOwnership(viewType)) return;

        int trailingViewType = DockMirrorShortcutVisibilityPolicy.NO_VIEW_TYPE;
        if (position == itemCount - 2 && itemCount >= 2) {
            trailingViewType = ((Number) HookUtil.requireInvoke(
                    adapter, "getItemViewType", itemCount - 1)).intValue();
        }

        Object contentObject = HookUtil.requireInvoke(viewHolder, "getContent");
        if (!(contentObject instanceof View)) return;
        View content = (View) contentObject;
        ViewParent parent = content.getParent();
        if (!(parent instanceof View)) return;
        View itemView = (View) parent;

        boolean hidden = DockMirrorShortcutVisibilityPolicy.shouldHideItem(
                VisualRuntimeState.isMirrorShortcutHidden(),
                position,
                itemCount,
                viewType,
                trailingViewType);
        if (hidden) {
            collapseItem(content, itemView, position, itemCount, viewType);
        } else {
            restoreItem(content, itemView);
        }

        itemView.post(() -> {
            ViewParent recycler = itemView.getParent();
            if (recycler instanceof View) applyCenteringCompensation((View) recycler);
        });
    }

    private static void collapseItem(
            View content, View itemView, int position, int itemCount, int viewType) {
        ViewGroup.LayoutParams rawParams = itemView.getLayoutParams();
        if (rawParams == null) {
            content.setVisibility(View.INVISIBLE);
            return;
        }

        HiddenGeometry previous = HIDDEN_ITEMS.get(itemView);
        int originalWidth = rawParams.width;
        int originalLeftMargin = 0;
        int originalRightMargin = 0;
        if (rawParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) rawParams;
            originalLeftMargin = margins.leftMargin;
            originalRightMargin = margins.rightMargin;
        }

        if (previous != null) {
            if (originalWidth <= 0) originalWidth = previous.width;
            if (originalLeftMargin == 0 && originalRightMargin == 0) {
                originalLeftMargin = previous.leftMargin;
                originalRightMargin = previous.rightMargin;
            }
        }

        HIDDEN_ITEMS.put(itemView, new HiddenGeometry(
                Math.max(0, originalWidth),
                originalLeftMargin,
                originalRightMargin,
                position,
                itemCount,
                viewType));

        content.setVisibility(View.INVISIBLE);
        rawParams.width = 0;
        if (rawParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) rawParams;
            margins.leftMargin = 0;
            margins.rightMargin = 0;
        }
        HookUtil.tryInvoke(rawParams, "setMinWidth", 0);
        HookUtil.tryInvoke(rawParams, "setMaxWidth", 0);
        itemView.setLayoutParams(rawParams);
        itemView.requestLayout();
    }

    private static void restoreItem(View content, View itemView) {
        HiddenGeometry geometry = HIDDEN_ITEMS.remove(itemView);
        HIDDEN_DECOR_WIDTHS.remove(itemView);
        content.setVisibility(View.VISIBLE);
        if (geometry == null) return;

        ViewGroup.LayoutParams rawParams = itemView.getLayoutParams();
        if (rawParams == null) return;

        // Vendor bindView() runs before this callback and normally restores width/min/max.
        // Keep its refreshed width when available, otherwise fall back to the pre-collapse value.
        if (rawParams.width <= 0) rawParams.width = geometry.width;
        if (rawParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) rawParams;
            margins.leftMargin = geometry.leftMargin;
            margins.rightMargin = geometry.rightMargin;
        }
        if (rawParams.width > 0) {
            HookUtil.tryInvoke(rawParams, "setMinWidth", rawParams.width);
            HookUtil.tryInvoke(rawParams, "setMaxWidth", rawParams.width);
        }
        itemView.setLayoutParams(rawParams);
        itemView.requestLayout();
    }

    private static void applyCenteringCompensation(View recyclerView) {
        float hiddenFootprint = 0f;
        HiddenGeometry mirrorGeometry = null;

        ArrayList<Map.Entry<View, HiddenGeometry>> snapshot;
        synchronized (HIDDEN_ITEMS) {
            snapshot = new ArrayList<>(HIDDEN_ITEMS.entrySet());
        }
        for (Map.Entry<View, HiddenGeometry> entry : snapshot) {
            View itemView = entry.getKey();
            HiddenGeometry geometry = entry.getValue();
            if (itemView == null || geometry == null || itemView.getParent() != recyclerView) {
                continue;
            }
            hiddenFootprint += geometry.width + geometry.leftMargin + geometry.rightMargin;
            Integer decorWidth = HIDDEN_DECOR_WIDTHS.get(itemView);
            if (decorWidth != null) hiddenFootprint += decorWidth;
            if (geometry.viewType == DockMirrorShortcutVisibilityPolicy.MIRROR_VIEW_TYPE) {
                mirrorGeometry = geometry;
            }
        }

        float targetOffset = 0f;
        if (mirrorGeometry != null && hiddenFootprint > 0f) {
            boolean rtl = recyclerView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int direction = DockMirrorShortcutVisibilityPolicy.compensationDirection(
                    mirrorGeometry.position, mirrorGeometry.itemCount, rtl);
            targetOffset = direction * hiddenFootprint * 0.5f;
        }

        applyComposedTranslation(recyclerView, targetOffset);
        try {
            Object background = HookUtil.getField(recyclerView, "mContentBackground");
            if (background instanceof View) {
                applyComposedTranslation((View) background, targetOffset);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " background compensation unavailable: " + error);
        }
    }

    private static void applyComposedTranslation(View view, float targetOffset) {
        Float previousOffset = APPLIED_X_OFFSETS.get(view);
        float previous = previousOffset != null ? previousOffset : 0f;
        if (Math.abs(previous - targetOffset) < 0.01f) return;

        // Compose with any vendor/LiquidDock translation written since our last pass.
        float base = view.getTranslationX() - previous;
        view.setTranslationX(base + targetOffset);
        if (Math.abs(targetOffset) < 0.01f) APPLIED_X_OFFSETS.remove(view);
        else APPLIED_X_OFFSETS.put(view, targetOffset);
    }

    static void onRuntimeVisibilityChanged() {
        ArrayList<Object> snapshot;
        synchronized (ADAPTERS) {
            snapshot = new ArrayList<>(ADAPTERS.keySet());
        }
        int refreshed = 0;
        for (Object adapter : snapshot) {
            if (adapter == null) continue;
            HookUtil.InvocationResult<Object> refresh =
                    HookUtil.tryInvoke(adapter, "notifyDataSetChanged");
            if (refresh.succeeded()) {
                refreshed++;
            } else {
                MainHook.log(TAG + " adapter refresh failed: " + refresh.failure());
            }
        }
        MainHook.log(TAG + " hide=" + VisualRuntimeState.isMirrorShortcutHidden()
                + " refreshed=" + refreshed);
    }

    private static final class HiddenGeometry {
        final int width;
        final int leftMargin;
        final int rightMargin;
        final int position;
        final int itemCount;
        final int viewType;

        HiddenGeometry(
                int width,
                int leftMargin,
                int rightMargin,
                int position,
                int itemCount,
                int viewType) {
            this.width = width;
            this.leftMargin = leftMargin;
            this.rightMargin = rightMargin;
            this.position = position;
            this.itemCount = itemCount;
            this.viewType = viewType;
        }
    }
}
