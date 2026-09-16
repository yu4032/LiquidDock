package com.hellovoid.liquiddock;

import android.view.ViewGroup;

import java.lang.reflect.Method;

/** Hooks stable KeyboardHolder layout and structurally recognizes Gboard floating keyboard. */
final class GboardFloatingGlassHook {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static boolean installed;

    private GboardFloatingGlassHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) {
            GboardDragDiagnostics.log("HOOK_INSTALL_ALREADY");
            return true;
        }
        if (classLoader == null) {
            GboardDragDiagnostics.log("HOOK_INSTALL_SKIP loader=null");
            return false;
        }
        GboardFloatingHandlePolicy.install();
        try {
            GboardDragDiagnostics.log("HOOK_INSTALL_BEGIN anchor="
                    + GboardFloatingStructureResolver.KEYBOARD_HOLDER_CLASS);
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
            GboardDragDiagnostics.log("HOOK_INSTALL_OK class=" + keyboardHolderClass.getName()
                    + " method=onLayout");
            return true;
        } catch (Throwable error) {
            GboardDragDiagnostics.log("HOOK_INSTALL_FAIL cause=" + failureSummary(error), error);
            log("hook unavailable cause=" + failureSummary(error)
                    + "; stock floating background retained", error);
            return false;
        }
    }

    private static void handleKeyboardHolderLayout(ViewGroup keyboardHolder, ClassLoader classLoader) {
        GboardDragDiagnostics.log("HOLDER_LAYOUT size=" + keyboardHolder.getWidth()
                + "x" + keyboardHolder.getHeight()
                + " attached=" + keyboardHolder.isAttachedToWindow());
        GboardFloatingStructureResolver.Structure structure =
                GboardFloatingStructureResolver.resolveFromKeyboardHolder(keyboardHolder, classLoader);
        if (structure == null) {
            GboardDragDiagnostics.log("STRUCTURE_NULL");
            return;
        }

        boolean floating = GboardFloatingStructureResolver.isFloatingGeometry(structure);
        GboardDragDiagnostics.log("STRUCTURE_OK floating=" + floating
                + " keyboardArea=" + structure.keyboardArea.getWidth() + "x"
                + structure.keyboardArea.getHeight()
                + " bottom=" + structure.bottomFrame.getWidth() + "x"
                + structure.bottomFrame.getHeight());
        if (!floating) {
            GboardFloatingGlassCoordinator.onHidden(structure.keyboardArea);
            return;
        }

        GboardFloatingHandlePolicy.bind(structure.bottomFrame);

        ConfigReader liveReader = ConfigReader.load();
        LiquidDockConfig liveConfig = LiquidDockConfig.from(liveReader);
        GboardGlassPreferences.Appearance liveAppearance =
                GboardGlassPreferences.resolve(liveReader, liveConfig.glass);
        if (!liveConfig.enabled || !liveConfig.glass.enabled || !liveAppearance.enabled) {
            GboardDragDiagnostics.log("FEATURE_DISABLED module=" + liveConfig.enabled
                    + " glass=" + liveConfig.glass.enabled
                    + " gboard=" + liveAppearance.enabled);
            GboardFloatingGlassCoordinator.onHidden(structure.keyboardArea);
            return;
        }

        GboardDragDiagnostics.log("COORDINATOR_SHOW");
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
