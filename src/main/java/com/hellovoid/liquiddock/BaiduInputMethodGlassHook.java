package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Hooks only stable Baidu InputMethodService lifecycle and delegates runtime structure discovery. */
final class BaiduInputMethodGlassHook {
    private static final String TAG = "[DC][BaiduInputMethodGlass]";
    private static final WeakHashMap<Object, WeakReference<View>> INPUT_VIEWS = new WeakHashMap<>();
    private static boolean installed;

    private BaiduInputMethodGlassHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> serviceClass = Class.forName(
                    BaiduInputMethodStructureResolver.IME_SERVICE_CLASS,
                    false,
                    classLoader);
            Method setInputView = serviceClass.getDeclaredMethod("setInputView", View.class);
            Method onWindowShown = serviceClass.getDeclaredMethod("onWindowShown");

            HookUtil.hook(setInputView, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object service = chain.getThisObject();
                View inputView = args.length > 0 && args[0] instanceof View ? (View) args[0] : null;
                synchronized (INPUT_VIEWS) {
                    if (inputView != null) INPUT_VIEWS.put(service, new WeakReference<>(inputView));
                    else INPUT_VIEWS.remove(service);
                }
                if (inputView != null) inputView.post(() -> handle(service, inputView, classLoader));
                return result;
            });

            HookUtil.hook(onWindowShown, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object service = chain.getThisObject();
                View inputView = currentInputView(service);
                if (inputView != null) inputView.post(() -> handle(service, inputView, classLoader));
                return result;
            });

            installed = true;
            return true;
        } catch (Throwable error) {
            log("stable IME hook unavailable cause=" + failureSummary(error), error);
            return false;
        }
    }

    private static void handle(Object service, View inputView, ClassLoader classLoader) {
        if (service == null || inputView == null) return;
        BaiduInputMethodStructureResolver.Structure structure =
                BaiduInputMethodStructureResolver.resolve(inputView, classLoader);
        if (structure == null || !BaiduInputMethodStructureResolver.isFloatingGeometry(structure)) {
            BaiduInputMethodGlassCoordinator.onHidden(inputView);
            return;
        }

        ConfigReader reader = ConfigReader.load();
        LiquidDockConfig config = LiquidDockConfig.from(reader);
        if (!config.enabled || !config.glass.enabled) {
            BaiduInputMethodGlassCoordinator.onHidden(inputView);
            return;
        }
        BaiduInputMethodGlassCoordinator.onShown(inputView, structure, config.glass);
    }

    private static View currentInputView(Object service) {
        synchronized (INPUT_VIEWS) {
            WeakReference<View> reference = INPUT_VIEWS.get(service);
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
