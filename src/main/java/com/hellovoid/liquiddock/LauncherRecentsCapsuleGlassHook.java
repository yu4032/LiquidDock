package com.hellovoid.liquiddock;

import android.content.res.Resources;
import android.view.View;

import java.util.WeakHashMap;

/**
 * Adds native HyperOS backdrop blur to the two Pad Recents action capsules while leaving the
 * Launcher-owned drawable, geometry, Folme touch state, visibility and click listeners intact.
 *
 * Targets are the stable resource owners confirmed in OS3 Launcher 4.50.0.1204:
 * recent_clear_all_task_container_for_pad and world_container.
 */
final class LauncherRecentsCapsuleGlassHook {
    private static final String TAG = "[DC][RecentsCapsule]";
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final String RECENTS_DECORATIONS =
            "com.miui.home.recents.views.RecentsDecorations";
    private static final String CLEAR_ALL_CAPSULE = "recent_clear_all_task_container_for_pad";
    private static final String WORLD_CAPSULE = "world_container";

    private static final WeakHashMap<View, CapsuleBinding> bindings = new WeakHashMap<>();
    private static boolean installed;

    private LauncherRecentsCapsuleGlassHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            HookUtil.hookMethod(classLoader, RECENTS_DECORATIONS, "findAndSetupViews", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof View) {
                    bindResolvedCapsule((View) owner, CLEAR_ALL_CAPSULE, "clear-all");
                    bindResolvedCapsule((View) owner, WORLD_CAPSULE, "device-interconnect");
                }
                return result;
            });
            installed = true;
            MainHook.log(TAG + " stable RecentsDecorations hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " RecentsDecorations hook unavailable: " + error);
        }
    }

    private static void bindResolvedCapsule(View decorations, String resourceName, String label) {
        int id = resourceId(decorations, resourceName);
        if (id == 0) {
            MainHook.log(TAG + " resource unavailable name=" + resourceName);
            return;
        }
        View capsule = decorations.findViewById(id);
        if (capsule == null) {
            MainHook.log(TAG + " capsule unavailable name=" + resourceName);
            return;
        }
        bindCapsule(capsule, label);
    }

    private static int resourceId(View owner, String resourceName) {
        Resources resources = owner.getResources();
        if (resources == null) return 0;
        return resources.getIdentifier(resourceName, "id", LAUNCHER_PACKAGE);
    }

    private static void bindCapsule(View capsule, String label) {
        CapsuleBinding existing = bindings.get(capsule);
        if (existing != null) {
            existing.apply();
            return;
        }
        CapsuleBinding binding = new CapsuleBinding(capsule, label);
        bindings.put(capsule, binding);
        capsule.addOnAttachStateChangeListener(binding);
        if (capsule.isAttachedToWindow()) binding.apply();
    }

    private static final class CapsuleBinding implements View.OnAttachStateChangeListener {
        private final View capsule;
        private final String label;
        private boolean active;

        CapsuleBinding(View capsule, String label) {
            this.capsule = capsule;
            this.label = label;
        }

        void apply() {
            LiquidDockConfig config = LiquidDockConfig.load();
            if (!config.enabled || !config.glass.enabled || !GlassRuntimeState.isEnabled()) {
                clear();
                return;
            }
            int radiusPx = Math.max(1, Math.round(config.glass.blur));
            boolean applied = MiBlurBridge.applyPassWindowBlur(capsule, radiusPx);
            if (applied && !active) {
                MainHook.log(TAG + " glass active target=" + label + " blur=" + radiusPx);
            } else if (!applied && active) {
                MainHook.log(TAG + " glass lost target=" + label);
            }
            active = applied;
        }

        private void clear() {
            if (!active) return;
            MiBlurBridge.clearPassWindowBlur(capsule);
            active = false;
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            apply();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            clear();
        }
    }
}
