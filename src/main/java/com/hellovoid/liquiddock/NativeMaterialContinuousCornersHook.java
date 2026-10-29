package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.hellovoid.prismal.PrismalContinuousCorners;
import com.hellovoid.liquiddock.config.ConfigSchema;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Applies the existing Prismal continuous silhouette to Launcher's OEM material outline.
 * The native blur mode, blend colors, shadow, clip flag and animation remain Launcher-owned.
 * Installs at OEM material initializers, not at View.draw() or per-frame texture capture.
 */
final class NativeMaterialContinuousCornersHook {
    private static final String TAG = "[DC][NativeCorners]";
    private static final String BLUR_UTILS = "com.miui.home.launcher.common.BlurUtilities";
    private static final String HOTSEATS =
            "com.miui.home.launcher.hotseats.";
    private static boolean installed;
    private static boolean continuousCorners;
    private static boolean advancedMaterial;

    private NativeMaterialContinuousCornersHook() {}

    static synchronized void install(ClassLoader loader, boolean enabled) {
        if (installed || loader == null) return;
        ConfigReader settings = ConfigReader.load();
        continuousCorners = settings.b(ConfigSchema.Glass.CONTINUOUS_CORNERS.name(),
                ConfigSchema.Glass.CONTINUOUS_CORNERS.runtimeFallback());
        advancedMaterial = settings.b(ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name(), false);
        if (!enabled) return; // This setting, like the Prismal shape, is restart-bound.
        int count = 0;
        try {
            Class<?> blur = Class.forName(BLUR_UTILS, false, loader);
            count += hookMaterialInitializer(blur, "setWidgetBlurIfSupported",
                    new Class<?>[]{View.class});
            count += hookMaterialInitializer(blur, "setFolderIconBlur",
                    new Class<?>[]{View.class, int.class, int.class, int.class, int.class,
                            int.class, int.class, int.class, int.class, int.class, int.class});
            try {
                Class<?> maml = Class.forName(
                        "com.miui.maml.component.MamlView", false, loader);
                count += hookMaterialInitializer(blur, "setMaMlBlurIfSupported",
                        new Class<?>[]{maml, boolean.class});
            } catch (Throwable error) {
                MainHook.log(TAG + " MAML material unavailable: " + error);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " Launcher BlurUtilities unavailable: " + error);
        }

        // Some Launcher generations share the compat radius setter with MiuiX; hook
        // the declaring Method once even if both class names resolve to it.
        Set<Method> hookedDockMethods = new HashSet<>();
        count += hookDockRadius(loader, HOTSEATS + "HotSeatsListContentMiuiXBlurBackground",
                hookedDockMethods);
        count += hookDockRadius(loader, HOTSEATS + "HotSeatsListContentBlurBackground2",
                hookedDockMethods);
        installed = count > 0;
        MainHook.log(TAG + " native material initializer hooks=" + count);
    }

    private static int hookMaterialInitializer(
            Class<?> owner, String name, Class<?>[] parameters) {
        try {
            Method method = HookUtil.findMethodExact(owner, name, parameters);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (args.length > 0 && args[0] instanceof View) {
                    // The MAML boolean means whether native blur is requested.
                    if (!"setMaMlBlurIfSupported".equals(name)
                            || (args.length > 1 && Boolean.TRUE.equals(args[1]))) {
                        apply((View) args[0]);
                    }
                }
                return result;
            });
            return 1;
        } catch (Throwable error) {
            MainHook.log(TAG + " initializer " + name + " unavailable: " + error);
            return 0;
        }
    }

    private static int hookDockRadius(
            ClassLoader loader, String className, Set<Method> hooked) {
        try {
            Class<?> owner = Class.forName(className, false, loader);
            Method radius = findMethod(owner, "setBackgroundRadius", float.class);
            if (radius == null || !hooked.add(radius)) return 0;
            HookUtil.hook(radius, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof View) {
                    apply((View) chain.getThisObject());
                }
                return result;
            });
            return 1;
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock radius hook unavailable " + className + ": " + error);
            return 0;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?> parameter) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameter);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // A native background may inherit the radius setter.
            }
        }
        return null;
    }

    static void applyTo(View material) {
        // May run in Searchbox/SystemUI where Launcher-only initializer hooks
        // were never installed. The corner preference is still process-global.
        continuousCorners = NativeMaterialMode.continuousCorners();
        apply(material);
    }

    private static void apply(View material) {
        if (material == null || MiuixGlassHook.isBoundTo(material)
                || LauncherGlassStaticNode.find(material) != null) return;
        if (advancedMaterial || NativeMaterialMode.isEnabled()) {
            NativeAdvancedMaterialStyle.get().applyVendor(material);
        }
        if (!continuousCorners) return;
        ViewOutlineProvider original = material.getOutlineProvider();
        if (original instanceof ContinuousOutline) return;
        if (original == null && material.getBackground() != null) {
            original = (view, outline) -> view.getBackground().getOutline(outline);
        }
        if (original == null) return;
        // No clipToOutline(true): widget text and icons must not be clipped by a new owner.
        // Existing OEM clipToOutline and MiUI blur flags are deliberately untouched.
        material.setOutlineProvider(new ContinuousOutline(original));
        material.invalidateOutline();
    }

    private static final class ContinuousOutline extends ViewOutlineProvider {
        private final ViewOutlineProvider original;
        private final Rect nativeBounds = new Rect();
        private final RectF bounds = new RectF();
        private final Path path = new Path();
        private float lastLeft = Float.NaN, lastTop, lastRight, lastBottom, lastRadius;

        ContinuousOutline(ViewOutlineProvider original) {
            this.original = original;
        }

        @Override
        public void getOutline(View view, Outline output) {
            original.getOutline(view, output);
            if (!output.getRect(nativeBounds) || nativeBounds.isEmpty()) return;
            float radius = output.getRadius();
            if (!(radius > 0.5f)) radius = nativeCornerRadius(view);
            if (!(radius > 0.5f) || !Float.isFinite(radius)) return;
            float r = Math.min(radius,
                    Math.min(nativeBounds.width(), nativeBounds.height()) * 0.5f);
            if (!(r > 0.5f)) return;
            float alpha = output.getAlpha();
            try {
                if (lastLeft != nativeBounds.left || lastTop != nativeBounds.top
                        || lastRight != nativeBounds.right || lastBottom != nativeBounds.bottom
                        || lastRadius != r) {
                    bounds.set(nativeBounds);
                    path.rewind();
                    float[] contour = PrismalContinuousCorners.contour(
                            bounds.width(), bounds.height(), r);
                    if (contour.length < 8) return;
                    path.moveTo(bounds.left + contour[0], bounds.top + contour[1]);
                    for (int i = 2; i < contour.length; i += 2) {
                        path.lineTo(bounds.left + contour[i], bounds.top + contour[i + 1]);
                    }
                    path.close();
                    lastLeft = bounds.left;
                    lastTop = bounds.top;
                    lastRight = bounds.right;
                    lastBottom = bounds.bottom;
                    lastRadius = r;
                }
                output.setPath(path);
                output.setAlpha(alpha);
            } catch (Throwable ignored) {
                // Preserve the exact OEM outline if this vendor build rejects the path.
                original.getOutline(view, output);
            }
        }

        private static float nativeCornerRadius(View view) {
            // The original radius is owned by Launcher; use its drawable/field only
            // if the outline provider exposes a rectangle instead of a round rect.
            if (view.getBackground() instanceof GradientDrawable) {
                float radius = ((GradientDrawable) view.getBackground()).getCornerRadius();
                if (radius > 0f) return radius;
            }
            return MiuixFolderGlassHook.readMaterialRadius(view);
        }
    }
}
