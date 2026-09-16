package com.hellovoid.liquiddock;

import android.view.ViewGroup;

import java.lang.reflect.Method;

/** Hooks stable KeyboardHolder layout and structurally recognizes Gboard floating keyboard. */
final class GboardFloatingGlassHook {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static boolean installed;

    private GboardFloatingGlassHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        GboardFloatingHandlePolicy.install();
        try {
            Class<?> keyboardHolderClass = Class.forName(
                    GboardFloatingStructureResolver.KEYBOARD_HOLDER_CLASS,
                    false,
                    classLoader);
            Method onLayout = keyboardHolderClass.getDeclaredMethod(
                    "onLayout",
                    Boolean.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE);
            HookUtil.hook(onLayout, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup) {
                    handleKeyboardHolderLayout((ViewGroup) owner, classLoader);
                }
                return result;
            });

            installed = true;
            return true;
        } catch (Throwable error) {
            log("hook unavailable cause=" + failureSummary(error)
                    + "; stock floating background retained", error);
            return false;
        }
    }

    private static void handleKeyboardHolderLayout(ViewGroup keyboardHolder, ClassLoader classLoader) {
        GboardFloatingStructureResolver.Structure structure =
                GboardFloatingStructureResolver.resolveFromKeyboardHolder(keyboardHolder, classLoader);
        if (structure == null) return;

        if (!GboardFloatingStructureResolver.isFloatingGeometry(structure)) {
            GboardFloatingGlassCoordinator.onHidden(structure.keyboardArea);
            return;
        }

        GboardFloatingHandlePolicy.bind(structure.bottomFrame);

        ConfigReader liveReader = ConfigReader.load();
        LiquidDockConfig liveConfig = LiquidDockConfig.from(liveReader);
        GboardGlassPreferences.Appearance liveAppearance =
                GboardGlassPreferences.resolve(liveReader, liveConfig.glass);
        if (!liveConfig.enabled || !liveConfig.glass.enabled || !liveAppearance.enabled) {
            GboardFloatingGlassCoordinator.onHidden(structure.keyboardArea);
            return;
        }

        GboardFloatingGlassCoordinator.onShown(
                structure.keyboardArea,
                structure,
                liveConfig.glass);
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
