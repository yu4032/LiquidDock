package com.hellovoid.liquiddock;

import android.view.Window;
import java.lang.reflect.Method;

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
                    MainHook.diag(TAG + " native folder open blur=" + requested
                            + " adjusted=" + adjusted);
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
            installed = true;
            MainHook.diag(TAG + " installed exact OEM folder opening blur scale="
                    + OPEN_STRENGTH);
        } catch (Throwable error) {
            // OEM signature differs on some Launcher builds. Do not guess or
            // change other blur callsites as a fallback.
            MainHook.diag(TAG + " unavailable; stock folder blur retained: " + error);
        }
    }
}
