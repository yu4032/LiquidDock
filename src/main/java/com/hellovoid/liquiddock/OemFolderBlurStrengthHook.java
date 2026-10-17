package com.hellovoid.liquiddock;

import android.view.Window;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Scale only the Launcher OEM global folder-opening blur, leaving its
 * original animator, root ownership and reverse-close restoration intact.
 *
 * Verified in the decompiled Launcher call site:
 * Launcher.lambda$openLauncherFolder$41 ->
 * BlurUtils.fastBlurWhenOpenOrCloseFolder(this, true).
 *
 * The private fastBlur overload is version-dependent. If its exact
 * signature is absent, leave stock OEM behavior untouched and log it.
 */
final class OemFolderBlurStrengthHook {
    private static final String TAG = "[FolderTrace] OEMBlur";
    // OEM fastBlur is normalized 0..1, not a radius in pixels.
    private static final float OPEN_STRENGTH = 0.22f;
    private static final ThreadLocal<Boolean> FOLDER_OPEN = new ThreadLocal<>();
    // OEM Launcher 4.50 calls fastBlurWhenOpenOrCloseFolder(..., true)
    // on BOTH open and close. The Boolean parameter does not distinguish them.
    private static volatile boolean actualFolderOpening;
    private static Method nativeFolderBlur;
    private static int nativeOpeningArg;

    static void setFolderOpening(boolean opening) {
        actualFolderOpening = opening;
    }
    private static boolean installed;

    private OemFolderBlurStrengthHook() {}

    static void install(ClassLoader loader) {
        if (installed || loader == null) return;
        try {
            Class<?> utils = Class.forName("com.miui.home.launcher.common.BlurUtils", false, loader);
            Class<?> launcher = Class.forName("com.miui.home.launcher.Launcher", false, loader);
            Method wrapper;
            int openingArg;
            try {
                wrapper = utils.getDeclaredMethod("fastBlurWhenOpenOrCloseFolder",
                        launcher, boolean.class);
                openingArg = 1;
            } catch (NoSuchMethodException reordered) {
                wrapper = utils.getDeclaredMethod("fastBlurWhenOpenOrCloseFolder",
                        boolean.class, launcher);
                openingArg = 0;
            }
            Method inner = utils.getDeclaredMethod("fastBlur",
                    float.class, Window.class, boolean.class);
            // Inner interception is installed before the outer scope owner.
            HookUtil.hook(inner, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (Boolean.TRUE.equals(FOLDER_OPEN.get()) && args.length == 3
                        && args[0] instanceof Number) {
                    float requested = ((Number) args[0]).floatValue();
                    float adjusted = Math.max(0f, Math.min(requested, OPEN_STRENGTH));
                    args[0] = adjusted;
                }
                return chain.proceed(args);
            });
            final int which = openingArg;
            HookUtil.hook(wrapper, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (!actualFolderOpening
                        || args.length <= which || !Boolean.TRUE.equals(args[which])) {
                    return chain.proceed(args);
                }
                FOLDER_OPEN.set(Boolean.TRUE);
                try {
                    return chain.proceed(args);
                } finally {
                    FOLDER_OPEN.remove();
                }
            });
            nativeFolderBlur = wrapper;
            nativeOpeningArg = which;
            installed = true;
            MainHook.log(TAG + " native folder blur entry installed");
        } catch (Throwable error) {
            // OEM signature differs on some Launcher builds. Never substitute
            // a global Window/Surface blur setter as an unsafe fallback.
            MainHook.log(TAG + " native folder blur entry unavailable: " + error);
        }
    }

    /** Restore the *same native folder-blur owner* after the OEM Recents
     * wallpaper/host return has settled. Recents can overwrite this value
     * without another FolderCling.open(), so the opening-only cap is not
     * replayed automatically. The original Launcher wrapper and fastBlur
     * animator stay authoritative; our existing opening cap still applies.
     */
    static void restoreAfterRecentsReturn() {
        Method method = nativeFolderBlur;
        if (!installed || method == null || !actualFolderOpening
                || !GlassRuntimeState.isEnabled()) return;
        if (!Modifier.isStatic(method.getModifiers())) {
            MainHook.log(TAG + " Recents folder blur restore skipped: non-static native owner");
            return;
        }
        try {
            ClassLoader loader = method.getDeclaringClass().getClassLoader();
            Class<?> application = Class.forName(
                    "com.miui.home.launcher.Application", false, loader);
            HookUtil.InvocationResult<Object> result =
                    HookUtil.tryInvokeStatic(application, "getLauncher");
            Object launcher = result.succeeded() ? result.value() : null;
            int index = nativeOpeningArg;
            if (launcher == null || !method.getParameterTypes()[1 - index].isInstance(launcher)) {
                MainHook.log(TAG + " Recents folder blur restore skipped: Launcher unavailable");
                return;
            }
            Object[] args = index == 1 ? new Object[]{launcher, true}
                    : new Object[]{true, launcher};
            method.invoke(null, args);
            MainHook.log(TAG + " restored native folder blur after Recents settle"
                    + " cap=" + OPEN_STRENGTH);
        } catch (Throwable error) {
            MainHook.log(TAG + " Recents folder blur restore failed: " + error);
        }
    }
}
