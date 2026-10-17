package com.hellovoid.liquiddock;

import android.view.Window;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

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
    // One bounded diagnostic window per vendor Recents→HOME event.
    // Logging is disabled completely unless the existing GUI debug option is on.
    private static final AtomicInteger RECENTS_BLUR_TRACE_REMAINING = new AtomicInteger();
    // OEM Launcher 4.50 calls fastBlurWhenOpenOrCloseFolder(..., true)
    // on BOTH open and close. The Boolean parameter does not distinguish them.
    private static volatile boolean actualFolderOpening;
    private static volatile boolean recentsReturningToOpenFolder;

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

    static void traceRecentsShown() {
        if (!MainHook.debugLogging) return;
        RECENTS_BLUR_TRACE_REMAINING.set(0);
        MainHook.log(TAG + " Recents shown nativeFolderOpening=" + actualFolderOpening);
    }

    static void traceRecentsHide(boolean coveredByVendor, boolean activeExpandedSession) {
        if (!MainHook.debugLogging) return;
        RECENTS_BLUR_TRACE_REMAINING.set(24);
        MainHook.log(TAG + " Recents hide covered=" + coveredByVendor
                + " nativeFolderOpening=" + actualFolderOpening
                + " expandedGlassSession=" + activeExpandedSession
                + " returnGuard=" + recentsReturningToOpenFolder);
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
                boolean opening = Boolean.TRUE.equals(FOLDER_OPEN.get());
                boolean recentsReturn = actualFolderOpening && recentsReturningToOpenFolder;
                if (args.length == 3 && args[0] instanceof Number) {
                    float requested = ((Number) args[0]).floatValue();
                    float adjusted = requested;
                    if (opening || recentsReturn) {
                        adjusted = FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                                requested, opening, recentsReturn, OPEN_STRENGTH);
                        args[0] = adjusted;
                    }
                    if (MainHook.debugLogging && RECENTS_BLUR_TRACE_REMAINING
                            .getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                        MainHook.log(TAG + " Recents-exit native fastBlur requested="
                                + requested + " applied=" + adjusted
                                + " animated=" + args[2]
                                + " folderOpening=" + actualFolderOpening
                                + " openingScope=" + opening
                                + " returnScope=" + recentsReturn);
                    }
                    if (recentsReturn && requested > adjusted) {
                        MainHook.log(TAG + " Recents-return fastBlur target corrected "
                                + requested + " -> " + adjusted);
                    }
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
            MainHook.log(TAG + " native folder fastBlur owner installed");
        } catch (Throwable error) {
            // OEM signature differs on some Launcher builds. Never patch
            // global Window blur or SurfaceControl as a fallback.
            MainHook.log(TAG + " native folder fastBlur owner unavailable: " + error);
        }
    }
}
