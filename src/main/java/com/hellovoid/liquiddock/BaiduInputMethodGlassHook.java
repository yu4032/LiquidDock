package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Hooks Baidu Input Method Xiaomi edition's semantic floating-keyboard lifecycle.
 *
 * <p>The primary authority is ImeService.showFloatKeyboardView(View), confirmed by the canonical
 * decompile. setInputView/onWindowShown are retained only for diagnostics and lifecycle recovery;
 * they no longer guess a floating keyboard from the entire IME tree.</p>
 */
final class BaiduInputMethodGlassHook {
    private static final String TAG = "[DC][BaiduInputMethodGlass]";
    private static final String IME_SERVICE_CLASS = "com.content.input_mi.ImeService";
    private static final WeakHashMap<Object, WeakReference<View>> INPUT_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<Object, WeakReference<View>> FLOAT_VIEWS = new WeakHashMap<>();
    private static boolean installed;

    private BaiduInputMethodGlassHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> serviceClass = Class.forName(IME_SERVICE_CLASS, false, classLoader);
            Method setInputView = serviceClass.getDeclaredMethod("setInputView", View.class);
            Method onWindowShown = serviceClass.getDeclaredMethod("onWindowShown");
            Method onWindowHidden = serviceClass.getDeclaredMethod("onWindowHidden");
            Method showFloatKeyboardView =
                    serviceClass.getDeclaredMethod("showFloatKeyboardView", View.class);

            HookUtil.hook(setInputView, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object service = chain.getThisObject();
                View inputView = viewArg(args);
                put(INPUT_VIEWS, service, inputView);
                log("setInputView " + BaiduInputMethodStructureResolver.describe(inputView), null);
                if (inputView != null) {
                    inputView.post(
                            () -> handleInputViewFallback(service, inputView, classLoader));
                }
                return result;
            });

            HookUtil.hook(showFloatKeyboardView, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object service = chain.getThisObject();
                View floatView = viewArg(args);
                View previous = current(FLOAT_VIEWS, service);
                if (previous != null && previous != floatView) {
                    BaiduInputMethodGlassCoordinator.onHidden(previous);
                }
                View inputView = current(INPUT_VIEWS, service);
                if (inputView != null && inputView != floatView) {
                    BaiduInputMethodGlassCoordinator.onHidden(inputView);
                }
                put(FLOAT_VIEWS, service, floatView);
                log("showFloatKeyboardView " + BaiduInputMethodStructureResolver.describe(floatView),
                        null);
                if (floatView != null) {
                    floatView.post(() -> handleFloatingView(floatView));
                } else if (inputView != null) {
                    inputView.post(
                            () -> handleInputViewFallback(service, inputView, classLoader));
                }
                return result;
            });

            HookUtil.hook(onWindowShown, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object service = chain.getThisObject();
                View floatView = current(FLOAT_VIEWS, service);
                if (floatView != null) {
                    log("onWindowShown resume semantic float view", null);
                    floatView.post(() -> handleFloatingView(floatView));
                } else {
                    View inputView = current(INPUT_VIEWS, service);
                    log("onWindowShown no semantic float view; probing stable input structure input="
                            + BaiduInputMethodStructureResolver.describe(inputView), null);
                    if (inputView != null) {
                        inputView.post(
                                () -> handleInputViewFallback(service, inputView, classLoader));
                    }
                }
                return result;
            });

            HookUtil.hook(onWindowHidden, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object service = chain.getThisObject();
                View floatView = current(FLOAT_VIEWS, service);
                if (floatView != null) BaiduInputMethodGlassCoordinator.onHidden(floatView);
                View inputView = current(INPUT_VIEWS, service);
                if (inputView != null && inputView != floatView) {
                    BaiduInputMethodGlassCoordinator.onHidden(inputView);
                }
                put(FLOAT_VIEWS, service, null);
                Object result = chain.proceed(args);
                log("onWindowHidden released semantic float view", null);
                return result;
            });

            installed = true;
            log("installed semantic hooks: setInputView/onWindowShown/onWindowHidden/"
                    + "showFloatKeyboardView", null);
            return true;
        } catch (Throwable error) {
            log("stable IME hook unavailable cause=" + failureSummary(error), error);
            return false;
        }
    }

    private static void handleFloatingView(View floatView) {
        if (floatView == null) return;
        BaiduInputMethodStructureResolver.Structure structure =
                BaiduInputMethodStructureResolver.resolveFromFloatView(floatView);
        handleResolved(floatView, structure, "semantic float view");
    }

    private static void handleInputViewFallback(
            Object service, View inputView, ClassLoader classLoader) {
        if (service == null || inputView == null || classLoader == null) return;
        if (current(FLOAT_VIEWS, service) != null) return;

        BaiduInputMethodStructureResolver.Structure structure =
                BaiduInputMethodStructureResolver.resolveFromInputView(inputView, classLoader);
        handleResolved(inputView, structure, "stable input-view fallback");
    }

    private static void handleResolved(
            View authority,
            BaiduInputMethodStructureResolver.Structure structure,
            String source) {
        if (authority == null) return;

        ConfigReader reader = ConfigReader.load();
        LiquidDockConfig config = LiquidDockConfig.from(reader);
        ThirdPartyGlassAppearance appearance =
                BaiduInputMethodGlassPreferences.resolve(reader, config.glass);
        if (!config.enabled || !config.glass.enabled || !appearance.enabled) {
            BaiduInputMethodGlassCoordinator.onHidden(authority);
            log(source + " glass disabled by config", null);
            return;
        }

        if (structure == null) {
            BaiduInputMethodGlassCoordinator.onHidden(authority);
            log(source + " unresolved: "
                    + BaiduInputMethodStructureResolver.describe(authority), null);
            return;
        }
        if (!BaiduInputMethodStructureResolver.isFloatingGeometry(structure)) {
            BaiduInputMethodGlassCoordinator.onHidden(authority);
            log(source + " rejected by compact geometry: "
                    + BaiduInputMethodStructureResolver.describe(structure), null);
            return;
        }

        log(source + " accepted: "
                + BaiduInputMethodStructureResolver.describe(structure), null);
        BaiduInputMethodGlassCoordinator.onShown(authority, structure, config.glass);
    }

    private static View viewArg(Object[] args) {
        return args != null && args.length > 0 && args[0] instanceof View
                ? (View) args[0] : null;
    }

    private static void put(
            WeakHashMap<Object, WeakReference<View>> map, Object service, View view) {
        if (service == null) return;
        synchronized (map) {
            if (view == null) map.remove(service);
            else map.put(service, new WeakReference<>(view));
        }
    }

    private static View current(
            WeakHashMap<Object, WeakReference<View>> map, Object service) {
        if (service == null) return null;
        synchronized (map) {
            WeakReference<View> reference = map.get(service);
            return reference != null ? reference.get() : null;
        }
    }

    private static String failureSummary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
