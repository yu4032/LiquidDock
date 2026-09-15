package com.hellovoid.liquiddock;

import android.view.View;
import android.widget.PopupWindow;

import java.lang.reflect.Method;

/** Hooks stable Android PopupWindow lifecycle and structurally recognizes Gboard floating keyboard. */
final class GboardFloatingGlassHook {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static boolean installed;

    private GboardFloatingGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (classLoader == null || runtimeConfig == null) return false;
        try {
            int showHooks = 0;
            for (Method method : PopupWindow.class.getDeclaredMethods()) {
                String name = method.getName();
                if (!"showAtLocation".equals(name) && !"showAsDropDown".equals(name)) continue;
                if (method.getReturnType() != Void.TYPE) continue;
                HookUtil.hook(method, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    Object owner = chain.getThisObject();
                    if (owner instanceof PopupWindow) {
                        handleShown((PopupWindow) owner, classLoader);
                    }
                    return result;
                });
                showHooks++;
            }
            if (showHooks == 0) throw new NoSuchMethodException("PopupWindow show lifecycle missing");

            Method dismiss = PopupWindow.class.getDeclaredMethod("dismiss");
            HookUtil.hook(dismiss, chain -> {
                Object owner = chain.getThisObject();
                if (owner instanceof PopupWindow) {
                    View content = ((PopupWindow) owner).getContentView();
                    if (content != null) GboardFloatingGlassCoordinator.onHidden(content);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            installed = true;
            log("hook installed via PopupWindow lifecycle showHooks=" + showHooks, null);
            return true;
        } catch (Throwable error) {
            log("hook unavailable cause=" + failureSummary(error)
                    + "; stock floating background retained", error);
            return false;
        }
    }

    private static void handleShown(PopupWindow popupWindow, ClassLoader classLoader) {
        if (popupWindow == null) return;
        ConfigReader liveReader = ConfigReader.load();
        LiquidDockConfig liveConfig = LiquidDockConfig.from(liveReader);
        GboardGlassPreferences.Appearance liveAppearance =
                GboardGlassPreferences.resolve(liveReader, liveConfig.glass);
        if (!liveConfig.enabled || !liveConfig.glass.enabled || !liveAppearance.enabled) return;

        View content = popupWindow.getContentView();
        if (content == null) return;
        GboardFloatingStructureResolver.Structure structure =
                GboardFloatingStructureResolver.resolve(content, classLoader);
        if (structure == null) return;
        GboardFloatingGlassCoordinator.onShown(content, structure, liveConfig.glass);
    }

    private static String failureSummary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        String summary = error.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            String causeMessage = cause.getMessage();
            summary += " <- " + cause.getClass().getName()
                    + (causeMessage == null || causeMessage.isEmpty()
                    ? "" : ": " + causeMessage);
        }
        return summary;
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
