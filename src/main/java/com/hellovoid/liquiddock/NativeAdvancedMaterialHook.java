package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalContinuousCorners;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Launcher-side native advanced material for graphics without an OEM material owner.
 * Widgets, folders, opened folders and the Dock keep their original Launcher material.
 * Native blur is applied to the exact source View; never render another Prismal sink.
 */
final class NativeAdvancedMaterialHook {
    private static final String TAG = "[DC][NativeMaterial]";
    private static final String SHORTCUT = "com.miui.home.launcher.ShortcutIcon";
    private static final String DECORATIONS = "com.miui.home.recents.views.RecentsDecorations";
    private static final String CLEAR_ALL = "recent_clear_all_task_container_for_pad";
    private static final String WORLD = "world_container";
    private static final Map<View, Owner> OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, View.OnAttachStateChangeListener> ICON_WATCHERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static boolean installed;
    private static boolean enabled;
    private static boolean iconEnabled;
    private static boolean functionalIconEnabled;
    private static boolean capsuleEnabled;
    private static boolean continuousCorners;
    private static int blurRadius;
    private static volatile float iconCornerRadiusDp;
    private static WeakReference<ViewGroup> recentsRoot = new WeakReference<>(null);
    private static SharedPreferences prefs;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;

    private NativeAdvancedMaterialHook() {}

    static synchronized void install(ClassLoader loader, LiquidDockConfig config) {
        if (installed || loader == null || config == null
                || !config.enabled || !config.glass.advancedMaterialEnabled) return;
        if (!MiBlurBridge.isPassWindowBlurAvailable()) {
            MainHook.log(TAG + " HyperOS background material APIs unavailable");
            return;
        }
        enabled = true;
        iconEnabled = config.glass.iconEnabled;
        functionalIconEnabled = config.glass.functionalDockIconEnabled;
        capsuleEnabled = config.glass.recentsCapsuleEnabled;
        continuousCorners = config.glass.continuousCorners;
        iconCornerRadiusDp = config.glass.iconStyle.cornerRadiusDp;
        NativeAdvancedMaterialStyle.update(ConfigReader.load());
        blurRadius = NativeAdvancedMaterialStyle.get().radiusPx;
        installPreferences();
        installIcons(loader);
        installRecents(loader);
        installed = true;
    }

    private static void installPreferences() {
        prefs = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
        if (prefs == null) return;
        listener = (changed, key) -> {
            if (key != null && !key.equals(ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name())
                    && !key.equals(ConfigSchema.Glass.ENABLED.name())
                    && !key.equals(ConfigSchema.Core.ENABLED.name())
                    && !key.equals(ConfigSchema.Glass.ICON_GLASS.name())
                    && !key.equals(ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name())
                    && !key.equals(ConfigSchema.Glass.RECENTS_CAPSULE_GLASS.name())
                    && !key.equals(ConfigSchema.Glass.ADVANCED_MATERIAL_BLUR.name())
                    && !key.equals(ConfigSchema.Glass.ICON_CORNER_RADIUS.name())
                    && !key.startsWith("native_advanced_material_")
                    && !key.equals(ConfigSchema.Glass.CONTINUOUS_CORNERS.name())) return;
            // Read all keys as one snapshot. Advanced material always loses to master-off;
            // it always wins over an imported contradictory liquid_glass=true.
            enabled = changed.getBoolean(ConfigSchema.Core.ENABLED.name(), true)
                    && changed.getBoolean(
                    ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name(), false);
            iconEnabled = changed.getBoolean(ConfigSchema.Glass.ICON_GLASS.name(), true);
            functionalIconEnabled = changed.getBoolean(
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(), false);
            capsuleEnabled = changed.getBoolean(
                    ConfigSchema.Glass.RECENTS_CAPSULE_GLASS.name(), true);
            NativeAdvancedMaterialStyle.update(ConfigReader.load());
            blurRadius = NativeAdvancedMaterialStyle.get().radiusPx;
            iconCornerRadiusDp = LiquidDockConfig.load().glass.iconStyle.cornerRadiusDp;
            View root = recentsRoot.get();
            if (root != null) root.post(NativeAdvancedMaterialHook::refreshOwners);
            else for (View view : new java.util.ArrayList<>(ICON_WATCHERS.keySet())) {
                if (view != null) view.post(() -> refresh(view, false));
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    private static void refreshOwners() {
        for (View view : new java.util.ArrayList<>(OWNERS.keySet())) {
            Owner state = OWNERS.get(view);
            if (state != null) {
                refresh(view, state.capsule);
                if (enabled) NativeAdvancedMaterialStyle.get().applyVendor(view);
            }
        }
        ViewGroup root = recentsRoot.get();
        if (root != null) bindRecents(root);
    }

    private static void installIcons(ClassLoader loader) {
        try {
            Class<?> type = Class.forName(SHORTCUT, false, loader);
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                HookUtil.hook(ctor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (chain.getThisObject() instanceof View) {
                        observeIcon((View) chain.getThisObject());
                    }
                    return result;
                });
            }
            MainHook.log(TAG + " native ShortcutIcon material hooks installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " native icon owner unavailable: " + error);
        }
    }

    private static void observeIcon(View view) {
        if (view == null || ICON_WATCHERS.containsKey(view)) return;
        View.OnAttachStateChangeListener watcher = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                v.post(() -> refresh(v, false));
            }
            @Override public void onViewDetachedFromWindow(View v) {
                release(v);
            }
        };
        ICON_WATCHERS.put(view, watcher);
        view.addOnAttachStateChangeListener(watcher);
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (l != ol || t != ot || r != or || b != ob) refresh(v, false);
        });
        if (view.isAttachedToWindow()) view.post(() -> refresh(view, false));
    }

    private static void installRecents(ClassLoader loader) {
        try {
            HookUtil.hookMethod(loader, DECORATIONS, "findAndSetupViews", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof ViewGroup) {
                    observeRecents((ViewGroup) chain.getThisObject());
                }
                return result;
            });
            MainHook.log(TAG + " native Recents capsules installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Recents action containers unavailable: " + error);
        }
    }

    private static void observeRecents(ViewGroup group) {
        ViewGroup existing = recentsRoot.get();
        if (existing == group) {
            bindRecents(group);
            return;
        }
        recentsRoot = new WeakReference<>(group);
        group.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                if (v instanceof ViewGroup) bindRecents((ViewGroup) v);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                if (recentsRoot.get() == v) recentsRoot = new WeakReference<>(null);
                if (v instanceof ViewGroup) {
                    release(findById((ViewGroup) v, CLEAR_ALL));
                    release(findById((ViewGroup) v, WORLD));
                }
            }
        });
        group.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (v instanceof ViewGroup) bindRecents((ViewGroup) v);
        });
        bindRecents(group);
    }

    private static View findById(ViewGroup root, String name) {
        Resources res = root.getResources();
        if (res == null) return null;
        int id = res.getIdentifier(name, "id", "com.miui.home");
        return id > 0 ? root.findViewById(id) : null;
    }

    private static void bindRecents(ViewGroup group) {
        if (!group.isAttachedToWindow()) return;
        View clear = findById(group, CLEAR_ALL);
        View world = findById(group, WORLD);
        if (clear != null) refresh(clear, true);
        if (world != null) refresh(world, true);
    }

    private static void refresh(View view, boolean capsule) {
        if (view == null) return;
        boolean permitted = enabled && view.isAttachedToWindow()
                && view.getWidth() > 0 && view.getHeight() > 0;
        if (!capsule) {
            LauncherGlassHierarchy.Domain domain = LauncherGlassHierarchy.classify(view);
            boolean dock = domain == LauncherGlassHierarchy.Domain.DOCK;
            permitted &= domain != LauncherGlassHierarchy.Domain.OTHER;
            permitted &= Launcher450DockFunctionalIconPolicy.shouldRender(
                    iconEnabled, functionalIconEnabled, dock,
                    Launcher450DockFunctionalIconRegistry.isFunctional(view));
        } else {
            permitted &= capsuleEnabled;
        }
        if (!permitted) {
            release(view);
            return;
        }
        Owner owner = OWNERS.get(view);
        if (owner != null) {
            // OEM can replace the outline during icon/theme updates.
            if (view.getOutlineProvider() != owner.outline) {
                owner.originalOutline = view.getOutlineProvider();
                view.setOutlineProvider(owner.outline);
            }
            // The system moves already-composited View blur with its native transform.
            // Avoid repeating expensive outline invalidation or contour generation.
            if (owner.radius != blurRadius) {
                owner.radius = blurRadius;
                MiBlurBridge.setPassWindowBlurRadius(view, blurRadius);
            }
            return;
        }
        // An OEM blur owner must not have its mode, blend colors or shadow overwritten.
        if (Boolean.TRUE.equals(MiBlurBridge.getPassWindowBlurEnabled(view))) return;
        if (view.getClipToOutline() && !capsule) {
            // Changing this outline would otherwise clip the icon label along with the graphic.
            return;
        }
        owner = new Owner(view, capsule);
        if (!MiBlurBridge.applyPassWindowBlur(
                view, blurRadius, NativeAdvancedMaterialStyle.get().blendColors())) return;
        OWNERS.put(view, owner);
        view.setOutlineProvider(owner.outline);
        view.invalidateOutline();
        NativeAdvancedMaterialStyle.get().applyVendor(view);
        if (capsule) view.setBackground(null);
    }

    private static void release(View view) {
        if (view == null) return;
        Owner owner = OWNERS.remove(view);
        if (owner == null) return;
        if (view.getOutlineProvider() == owner.outline) {
            view.setOutlineProvider(owner.originalOutline);
            view.invalidateOutline();
        }
        MiBlurBridge.clearPassWindowBlur(view);
        if (owner.capsule && view.getBackground() == null) {
            view.setBackground(owner.originalBackground);
        }
    }

    private static final class Owner {
        final boolean capsule;
        final Drawable originalBackground;
        ViewOutlineProvider originalOutline;
        final MaterialOutline outline;
        int radius;

        Owner(View view, boolean capsule) {
            this.capsule = capsule;
            originalOutline = view.getOutlineProvider();
            originalBackground = view.getBackground();
            outline = new MaterialOutline(capsule, continuousCorners);
            radius = blurRadius;
        }
    }

    /** Material outline, not a View content clip: icon label and click bounds stay intact. */
    private static final class MaterialOutline extends ViewOutlineProvider {
        private final boolean capsule, continuous;
        private final Rect bounds = new Rect();
        private final Path contour = new Path();
        private Rect cached = new Rect();
        private float cachedRadius = Float.NaN;

        MaterialOutline(boolean capsule, boolean continuous) {
            this.capsule = capsule;
            this.continuous = continuous;
        }

        void invalidateGeometry() {
            cached.setEmpty();
        }

        @Override public void getOutline(View view, Outline out) {
            float cornerRadius;
            if (capsule) {
                bounds.set(0, 0, view.getWidth(), view.getHeight());
                cornerRadius = Math.min(bounds.width(), bounds.height()) * .5f;
            } else {
                LauncherGlassIconGeometry.Bounds b = LauncherGlassIconGeometry.resolve(view);
                if (b == null) {
                    out.setEmpty();
                    return;
                }
                bounds.set(Math.round(b.left), Math.round(b.top),
                        Math.round(b.right), Math.round(b.bottom));
                float requested = iconCornerRadiusDp;
                float density = view.getResources().getDisplayMetrics().density;
                cornerRadius = requested > 0f ? requested * density :
                        Math.min(bounds.width(), bounds.height()) * .22f;
            }
            if (bounds.isEmpty()) {
                out.setEmpty();
                return;
            }
            cornerRadius = Math.max(0f,
                    Math.min(cornerRadius, Math.min(bounds.width(), bounds.height()) * .5f));
            if (!continuous || cornerRadius <= 1f) {
                out.setRoundRect(bounds, cornerRadius);
                return;
            }
            if (!bounds.equals(cached) || cachedRadius != cornerRadius) {
                contour.rewind();
                float[] points = PrismalContinuousCorners.contour(
                        bounds.width(), bounds.height(), cornerRadius);
                if (points.length < 8) {
                    out.setRoundRect(bounds, cornerRadius);
                    return;
                }
                contour.moveTo(bounds.left + points[0], bounds.top + points[1]);
                for (int i = 2; i < points.length; i += 2)
                    contour.lineTo(bounds.left + points[i], bounds.top + points[i + 1]);
                contour.close();
                cached.set(bounds);
                cachedRadius = cornerRadius;
            }
            out.setPath(contour);
        }
    }
}
