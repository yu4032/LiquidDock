package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies workstation-only geometry to the visible laptop Dock container.
 *
 * Container and HotSeats attachment own the binding lifecycle; divider holders provide a
 * supplementary anchor. Background shells in a separate hierarchy receive the same user
 * translation, while descendants inherit the container transform exactly once.
 */
final class WorkstationDockGeometryHook {
    private static final String LINE_HOLDER =
            "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter$LineViewHolder";

    // Weak key alone is insufficient if the value strongly owns the key. Keep both sides weak;
    // the View itself owns the listeners for exactly as long as it is attached.
    private static final WeakHashMap<View, WeakReference<Binding>> bindings = new WeakHashMap<>();
    private static volatile int verticalOffsetPx;
    private static volatile boolean dockEnabled;
    private static volatile boolean taskbarEnabled;
    private static boolean unresolvedChainLogged;

    static void applyLiveConfig(LiquidDockConfig.Workstation config) {
        if (config == null) return;
        WorkstationNativeTaskbarHook.applyLiveConfig(config);
        float scale = config.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int nextVertical = Math.round(config.dockVerticalOffset * scale);
        boolean nextTaskbar = config.dockEnabled && config.taskbarMode;
        if (verticalOffsetPx == nextVertical && dockEnabled == config.dockEnabled
                && taskbarEnabled == nextTaskbar) return;
        verticalOffsetPx = nextVertical;
        dockEnabled = config.dockEnabled;
        taskbarEnabled = nextTaskbar;
        refreshInstalledGeometry();
        Miuix307ZeroCopyRenderer.setWorkstationTaskbarMode(
                MainHook.isWorkstationMode() && taskbarEnabled);
    }

    private static void refreshInstalledGeometry() {
        ArrayList<Binding> snapshot = new ArrayList<>();
        synchronized (bindings) {
            for (WeakReference<Binding> ref : bindings.values()) {
                Binding binding = ref != null ? ref.get() : null;
                if (binding != null) snapshot.add(binding);
            }
        }
        for (Binding binding : snapshot) {
            binding.apply(MainHook.isWorkstationMode() && dockEnabled);
        }
    }

    private WorkstationDockGeometryHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig.Workstation config) {
        applyLiveConfig(config);
        // Bind the actual container lifecycle; divider holders may be cached or absent.
        for (String name : new String[]{
                "com.miui.home.launcher.laptop.dockbar.DockContainerView",
                "com.miui.home.launcher.dock.DockContainerView"}) {
            try {
                HookUtil.hookMethod(classLoader, name, "onAttachedToWindow", chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object owner = chain.getThisObject();
                    if (owner instanceof View) bindGeometryView((View) owner, true);
                    return result;
                });
            } catch (Throwable error) {
                MainHook.log("[DC] Dock container attach hook unavailable " + name + ": " + error);
            }
        }

        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.hotseats.HotSeats",
                    "onAttachedToWindow", chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = chain.getThisObject();
                        if (owner instanceof View) bindFromAnchor((View) owner);
                        HookUtil.InvocationResult<Object> background =
                                HookUtil.tryInvoke(owner, "getHotSeatsBackground");
                        if (background.succeeded() && background.value() instanceof View) {
                            bindBackground((View) background.value());
                        }
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] workstation native background attach hook unavailable: " + error);
        }

        try {
            HookUtil.hookMethod(classLoader, LINE_HOLDER, "bindView", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                HookUtil.InvocationResult<Object> contentResult =
                        HookUtil.tryInvoke(chain.getThisObject(), "getContent");
                if (contentResult.succeeded() && contentResult.value() instanceof View) {
                    bindFromAnchor((View) contentResult.value());
                }
                return result;
            });
            MainHook.log("[DC] workstation visible Dock geometry hook installed");
        } catch (Throwable error) {
            MainHook.log("[DC] workstation visible Dock geometry hook unavailable: " + error);
        }
    }

    static void onWorkstationModeChanged(boolean enabled) {
        MiuixLauncherStaticGlassHook.onWorkstationModeChanged();
        WorkstationNativeTaskbarHook.refresh();
        // Workstation and the normal HotSeats background can overlap during the hierarchy handoff.
        // Release the remembered native foreground ring before changing visible Dock geometry.
        DockStrokeRenderer.onWorkstationModeChanged(enabled);

        ArrayList<Binding> snapshot = new ArrayList<>();
        synchronized (bindings) {
            Iterator<Map.Entry<View, WeakReference<Binding>>> iterator = bindings.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<View, WeakReference<Binding>> entry = iterator.next();
                Binding binding = entry.getValue() != null ? entry.getValue().get() : null;
                View container = entry.getKey();
                if (binding == null || container == null) {
                    iterator.remove();
                } else {
                    snapshot.add(binding);
                }
            }
        }
        for (Binding binding : snapshot) binding.apply(enabled && dockEnabled);
        Miuix307ZeroCopyRenderer.setWorkstationTaskbarMode(enabled && taskbarEnabled);
        WorkstationDockCustomizationHook.onWorkstationModeChanged(enabled);
    }

    private static void bindFromAnchor(View anchor) {
        View container = resolveDockContainer(anchor);
        if (container == null) {
            logUnresolvedChain(anchor);
            return;
        }

        bindGeometryView(container, true);
    }

    static void bindBackground(View background) {
        if (background == null) return;
        WorkstationNativeTaskbarHook.bind(background);
        bindGeometryView(background, false);
    }

    private static void bindGeometryView(View container, boolean isDockContainer) {
        Binding binding;
        synchronized (bindings) {
            WeakReference<Binding> bindingRef = bindings.get(container);
            binding = bindingRef != null ? bindingRef.get() : null;
            if (binding == null) {
                binding = new Binding(container, isDockContainer);
                bindings.put(container, new WeakReference<>(binding));
                container.addOnLayoutChangeListener(binding);
                container.addOnAttachStateChangeListener(binding);
                binding.installPreDraw(container);
                MainHook.log("[DC] workstation Dock container resolved class="
                        + container.getClass().getName());
            }
        }
        final Binding bound = binding;
        container.post(() -> bound.apply(MainHook.isWorkstationMode() && dockEnabled));
    }

    private static boolean hasTranslatedAncestor(View view) {
        ViewParent parent = view.getParent();
        synchronized (bindings) {
            while (parent instanceof View) {
                WeakReference<Binding> reference = bindings.get((View) parent);
                Binding binding = reference == null ? null : reference.get();
                if (binding != null && binding.isDockContainer) return true;
                parent = parent.getParent();
            }
        }
        return false;
    }

    private static View resolveDockContainer(View anchor) {
        View current = anchor;
        for (int depth = 0; current != null && depth < 16; depth++) {
            String className = current.getClass().getName();
            if (className.contains("DockContainer")) return current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static void logUnresolvedChain(View anchor) {
        if (unresolvedChainLogged) return;
        unresolvedChainLogged = true;
        StringBuilder chain = new StringBuilder();
        View current = anchor;
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (chain.length() > 0) chain.append(" <- ");
            chain.append(current.getClass().getName());
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        MainHook.log("[DC] workstation DockContainer not found from divider chain: " + chain);
    }

    private static final class Binding implements View.OnLayoutChangeListener,
            View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private final WeakReference<View> containerRef;
        private final boolean isDockContainer;

        private final WorkstationDockTranslationState translationState = new WorkstationDockTranslationState();
        private ViewTreeObserver observer;

        void installPreDraw(View container) {
            if (observer != null || !container.isAttachedToWindow()) return;
            observer = container.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
        }

        @Override public boolean onPreDraw() {
            applyTranslation(MainHook.isWorkstationMode() && dockEnabled);
            return true;
        }

        private void applyTranslation(boolean enabled) {
            View container = containerRef.get();
            if (container == null) return;
            float current = container.getTranslationY();
            // Some launcher versions parent the material under the icon container; others
            // compose it in a separate shell. Apply the user offset exactly once in either tree.
            int offset = !isDockContainer && hasTranslatedAncestor(container) ? 0 : verticalOffsetPx;
            float target = translationState.apply(current, enabled, offset);
            if (Math.abs(target - current) > .001f) container.setTranslationY(target);
        }

        Binding(View container, boolean isDockContainer) {
            this.isDockContainer = isDockContainer;
            containerRef = new WeakReference<>(container);
        }

        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
            apply(MainHook.isWorkstationMode() && dockEnabled);
        }

        @Override
        public void onViewAttachedToWindow(View v) { installPreDraw(v); }

        @Override
        public void onViewDetachedFromWindow(View v) {
            applyTranslation(false);
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
            try { v.removeOnLayoutChangeListener(this); } catch (Throwable ignored) {}
            try { v.removeOnAttachStateChangeListener(this); } catch (Throwable ignored) {}
            synchronized (bindings) {
                WeakReference<Binding> current = bindings.get(v);
                if (current != null && current.get() == this) bindings.remove(v);
            }
            containerRef.clear();
        }

        void apply(boolean workstation) {
            // Only user-requested vertical translation belongs here. The Launcher
            // retains the Dock's native width, horizontal placement, and fly motion.
            applyTranslation(workstation);
        }
    }
}
