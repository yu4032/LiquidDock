package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hides Xiaomi Launcher's Dock entry for phone mirroring without changing the adapter geometry.
 *
 * <p>The vendor HotSeats layout is center-justified. Removing the mirror item from the adapter
 * therefore changes the total flex width and moves every remaining Dock icon. Keep the vendor
 * item (and its trailing divider) in the data/layout model, and hide only their bound content.
 */
final class DockMirrorShortcutHook {
    private static final String TAG = "[DC][DockMirror]";
    private static final String HOTSEATS_ADAPTER =
            "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter";
    private static final String HOTSEATS_VIEW_HOLDER = HOTSEATS_ADAPTER + "$ViewHolder";

    private static final Map<Object, Boolean> ADAPTERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private DockMirrorShortcutHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        installAdapterVisibilityHook(classLoader);
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
                            MainHook.log(TAG + " bound visibility apply failed: " + error);
                        }
                        return result;
                    });

            MainHook.log(TAG + " adapter visibility hook installed constructors="
                    + constructors.length);
        } catch (Throwable error) {
            MainHook.log(TAG + " adapter visibility hook unavailable: " + error);
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

        Object content = HookUtil.requireInvoke(viewHolder, "getContent");
        if (!(content instanceof View)) return;

        boolean hidden = DockMirrorShortcutVisibilityPolicy.shouldHideItem(
                VisualRuntimeState.isMirrorShortcutHidden(),
                position,
                itemCount,
                viewType,
                trailingViewType);
        ((View) content).setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
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
}
