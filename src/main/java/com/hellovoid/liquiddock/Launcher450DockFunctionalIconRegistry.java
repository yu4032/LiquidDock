package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Launcher 4.50 Dock functional-entry authority.
 *
 * <p>HotSeatsListContentAdapter assigns dedicated view types to vendor/system actions. Those
 * view types are a stronger authority than localized labels, positions, content descriptions or
 * nullable ItemInfo tags. RecyclerView holders can be recycled, so every bind replaces the mark.</p>
 */
final class Launcher450DockFunctionalIconRegistry {
    private static final String TAG = "[DC][FunctionalDockGlass]";
    private static final String ADAPTER =
            "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter";
    private static final String VIEW_HOLDER =
            "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter$ViewHolder";

    private static final Map<View, Boolean> FUNCTIONAL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private Launcher450DockFunctionalIconRegistry() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> adapter = Class.forName(ADAPTER, false, classLoader);
            Class<?> holder = Class.forName(VIEW_HOLDER, false, classLoader);
            Method bind = HookUtil.findMethodExact(adapter, "onBindViewHolder",
                    new Class<?>[]{holder, int.class, List.class});
            HookUtil.hook(bind, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (args.length == 0 || args[0] == null) return result;
                Object viewHolder = args[0];
                HookUtil.InvocationResult<Object> typeResult =
                        HookUtil.tryInvoke(viewHolder, "getItemViewType");
                HookUtil.InvocationResult<Object> contentResult =
                        HookUtil.tryInvoke(viewHolder, "getContent");
                Object type = typeResult.succeeded() ? typeResult.value() : null;
                Object content = contentResult.succeeded() ? contentResult.value() : null;
                if (type instanceof Number && content instanceof View) {
                    View icon = (View) content;
                    boolean functional = Launcher450DockFunctionalIconPolicy.isFunctionalViewType(
                            ((Number) type).intValue());
                    if (functional) FUNCTIONAL.put(icon, Boolean.TRUE);
                    else FUNCTIONAL.remove(icon);
                    // A holder can change role after recycling. Reconcile every bind so stale
                    // functional-only ownership is dropped as soon as vendor semantics change.
                    MiuixLauncherStaticGlassHook.onDockIconAdapterBound(icon);
                }
                return result;
            });
            installed = true;
            MainHook.log(TAG + " adapter authority installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable on target Launcher: " + error);
            return false;
        }
    }

    static boolean isFunctional(View host) {
        return host != null && Boolean.TRUE.equals(FUNCTIONAL.get(host));
    }
}
