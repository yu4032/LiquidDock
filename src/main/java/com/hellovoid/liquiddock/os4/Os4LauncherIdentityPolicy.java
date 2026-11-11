package com.hellovoid.liquiddock.os4;

/** AOT process eligibility, independent of Launcher release and Android SDK version. */
public final class Os4LauncherIdentityPolicy {
    public static final String TARGET_PACKAGE = "com.miui.home";

    private Os4LauncherIdentityPolicy() {}

    public static boolean matchesAotPublisher(String packageName, boolean hasCode, boolean arm64) {
        return TARGET_PACKAGE.equals(packageName)
                && !hasCode
                && arm64;
    }

    /**
     * The legacy Java backend targets a dex process. Any AOT Launcher must bypass it,
     * regardless of release name or Android SDK level.
     */
    public static boolean shouldBlockLegacyJavaBackend(boolean hasCode) {
        return !hasCode;
    }
}
