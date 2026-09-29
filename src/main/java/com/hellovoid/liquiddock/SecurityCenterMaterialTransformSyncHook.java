package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Synchronously samples the real Security Center material carriers when Folme mutates their
 * stable View properties. This is observation only: LiquidDock never supplies animation values.
 */
final class SecurityCenterMaterialTransformSyncHook {
    private static final Object LOCK = new Object();
    private static final Map<View, WeakReference<SecurityCenterGlassSinkView>> SINKS =
            new WeakHashMap<>();
    private static boolean installed;

    private SecurityCenterMaterialTransformSyncHook() {}

    static void install() {
        synchronized (LOCK) {
            if (installed) return;
            installed = true;
        }
        hookFloatSetter("setScaleX");
        hookFloatSetter("setScaleY");
        hookFloatSetter("setTranslationX");
        hookFloatSetter("setTranslationY");
        hookFloatSetter("setAlpha");
        hookLayoutParams();
    }

    static void bind(View material, SecurityCenterGlassSinkView sink) {
        if (material == null || sink == null) return;
        install();
        synchronized (LOCK) {
            SINKS.put(material, new WeakReference<>(sink));
        }
    }

    static void unbind(View material, SecurityCenterGlassSinkView sink) {
        if (material == null) return;
        synchronized (LOCK) {
            WeakReference<SecurityCenterGlassSinkView> ref = SINKS.get(material);
            SecurityCenterGlassSinkView current = ref != null ? ref.get() : null;
            if (current == null || current == sink) SINKS.remove(material);
        }
    }

    private static void hookFloatSetter(String name) {
        try {
            Method method = HookUtil.findMethodExact(
                    View.class, name, new Class<?>[]{float.class});
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                notifyMutation(chain.getThisObject());
                return result;
            });
        } catch (Throwable error) {
            log("failed to hook View." + name, error);
        }
    }

    private static void hookLayoutParams() {
        try {
            Method method = HookUtil.findMethodExact(
                    View.class, "setLayoutParams",
                    new Class<?>[]{ViewGroup.LayoutParams.class});
            HookUtil.hook(method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                notifyMutation(chain.getThisObject());
                return result;
            });
        } catch (Throwable error) {
            log("failed to hook View.setLayoutParams", error);
        }
    }

    private static void notifyMutation(Object receiver) {
        if (!(receiver instanceof View)) return;
        SecurityCenterGlassSinkView sink;
        synchronized (LOCK) {
            WeakReference<SecurityCenterGlassSinkView> ref = SINKS.get((View) receiver);
            sink = ref != null ? ref.get() : null;
            if (sink == null && ref != null) SINKS.remove((View) receiver);
        }
        if (sink != null) sink.onNativeMaterialTransformMutated();
    }

    private static void log(String message, Throwable error) {
        try {
            Api101Bridge.log("[DC][SecurityCenterGlass] transform-sync " + message, error);
        } catch (Throwable ignored) {}
    }
}
