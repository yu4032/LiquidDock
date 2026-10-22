package com.hellovoid.liquiddock.os4;

/**
 * Exact, fail-closed identity check for the independently redesigned OS4 backend.
 *
 * A version-name match alone does not validate an AOT binary: native entry points
 * also require an exact ELF digest and per-target verification before installation.
 * Keep this policy free of Android dependencies so it can be regression tested.
 */
public final class Os4LauncherIdentityPolicy {
    public static final String TARGET_PACKAGE = "com.miui.home";
    public static final String TARGET_RELEASE =
            "RELEASE-8.01.02.8583-260918-10161026-R";
    public static final int TARGET_SDK = 37;

    private Os4LauncherIdentityPolicy() {}

    public static boolean matches(String packageName, String versionName,
                                  int sdkInt, boolean hasCode, boolean arm64) {
        return TARGET_PACKAGE.equals(packageName)
                && TARGET_RELEASE.equals(versionName)
                && sdkInt == TARGET_SDK
                && !hasCode
                && arm64;
    }

    /**
     * The Launcher 4.50 Java backend targets the dex-based process. Any Android
     * 16+ AOT Launcher must bypass it, including unknown builds: unsupported AOT
     * code fails closed instead of running hooks against absent Java classes.
     */
    public static boolean shouldBlockLegacyJavaBackend(int sdkInt, boolean hasCode) {
        return sdkInt >= 36 && !hasCode;
    }
}
