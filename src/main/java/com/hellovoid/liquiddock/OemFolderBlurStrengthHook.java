package com.hellovoid.liquiddock;

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
    // OEM fastBlur is normalized 0..1, not the Prismal source's pixel blur.
    // Populated from the dedicated persisted native background-blur setting.
    private static volatile float nativeFolderBlurRatio;
    private static final ThreadLocal<Boolean> FOLDER_OPEN = new ThreadLocal<>();
    // OEM Launcher 4.50 calls fastBlurWhenOpenOrCloseFolder(..., true)
    // on BOTH open and close. The Boolean parameter does not distinguish them.
    private static volatile boolean actualFolderOpening;
    private static volatile boolean recentsReturningToOpenFolder;

    static void onLiveConfigChanged(LiquidDockConfig.Glass config) {
        if (config == null) return;
        nativeFolderBlurRatio = FolderNativeBlurTargetPolicy.ratioFromPercent(
                config.openFolderNativeBlurPercent);
    }

    static void setFolderOpening(boolean opening) {
        actualFolderOpening = opening;
        if (!opening) recentsReturningToOpenFolder = false;
    }

    /** The semantic Recents return starts before OEM HomeState emits its
     * fastBlur(1.0) target. Only a real, still-open expanded folder may
     * retain its opening blur while the native return is in progress.
     */
    static void onRecentsReturnStarted(boolean expandedFolderOpen) {
        recentsReturningToOpenFolder = expandedFolderOpen && actualFolderOpening;
    }

    static void onRecentsReturnFinished() {
        recentsReturningToOpenFolder = false;
    }
    private static boolean installed;

    /** Final blur-target arbitration is owned by RecentsBackgroundBlurHook.
     * Both native folder opening and Recents-to-folder HOME return use this
     * one path, so the independent Recents percentage cannot overwrite it.
     */
    static float arbitrateTarget(float requested, boolean recentsScope, int recentsPercent) {
        boolean opening = Boolean.TRUE.equals(FOLDER_OPEN.get());
        boolean returning = actualFolderOpening && recentsReturningToOpenFolder;
        float resolved = FolderNativeBlurTargetPolicy.arbitrate(
                requested, recentsScope, recentsPercent,
                opening, returning, nativeFolderBlurRatio);
        if (returning && Float.compare(resolved, requested) != 0 && MainHook.debugLogging) {
            MainHook.log(TAG + " native fastBlur final target "
                    + requested + " -> " + resolved + " folderStrength="
                    + nativeFolderBlurRatio + " recentsScope=" + recentsScope);
        }
        return resolved;
    }

    private OemFolderBlurStrengthHook() {}

    /**
     * The native folder entry wrapper only marks synchronous opening scope.
     * Do not register a second fastBlur interceptor here: the shared Recents
     * hook is the single target writer, and installs independently of the
     * optional OEM wrapper signature.
     */
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
            MainHook.log(TAG + " native opening scope installed; shared fastBlur target arbiter");
        } catch (Throwable error) {
            // Even if this optional wrapper is absent, the shared fastBlur
            // target arbitration still protects a real Recents return.
            MainHook.log(TAG + " native opening scope unavailable: " + error);
        }
    }
}
