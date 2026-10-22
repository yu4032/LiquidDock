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
            "RELEASE-8.01.02.7751-260904-10100945-R";
    public static final int TARGET_SDK = 36;

    private Os4LauncherIdentityPolicy() {}

    public static boolean matches(String packageName, String versionName,
                                  int sdkInt, boolean hasCode, boolean arm64) {
        return TARGET_PACKAGE.equals(packageName)
                && TARGET_RELEASE.equals(versionName)
                && sdkInt == TARGET_SDK
                && !hasCode
                && arm64;
    }
}
