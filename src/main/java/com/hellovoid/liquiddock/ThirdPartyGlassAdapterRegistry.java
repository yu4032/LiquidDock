package com.hellovoid.liquiddock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Code-owned registry for supported third-party glass adapters. */
final class ThirdPartyGlassAdapterRegistry {
    interface Installer {
        boolean install(ClassLoader classLoader);
    }

    static final class Registration {
        final String profileId;
        final String packageName;
        final Installer installer;

        Registration(String profileId, String packageName, Installer installer) {
            ThirdPartyGlassProfiles.validateProfileId(profileId);
            if (packageName == null || packageName.isEmpty()) {
                throw new IllegalArgumentException("packageName is empty");
            }
            if (installer == null) throw new IllegalArgumentException("installer == null");
            this.profileId = profileId;
            this.packageName = packageName;
            this.installer = installer;
        }
    }

    private static final List<Registration> REGISTRATIONS = Collections.unmodifiableList(
            Arrays.asList(
                    new Registration(
                            "gboard.floating",
                            "com.google.android.inputmethod.latin",
                            ThirdPartyGlassAdapterRegistry::installGboard),
                    new Registration(
                            "miui.searchbox",
                            "com.android.quicksearchbox",
                            ThirdPartyGlassAdapterRegistry::installSearchbox)));

    private ThirdPartyGlassAdapterRegistry() {}

    static List<Registration> registrations() {
        return REGISTRATIONS;
    }

    static Registration find(String packageName) {
        if (packageName == null) return null;
        for (Registration registration : REGISTRATIONS) {
            if (registration.packageName.equals(packageName)) return registration;
        }
        return null;
    }

    static boolean handles(String packageName) {
        return find(packageName) != null;
    }

    static boolean install(String packageName, ClassLoader classLoader) {
        Registration registration = find(packageName);
        return registration != null && classLoader != null && registration.installer.install(classLoader);
    }

    private static boolean installGboard(ClassLoader classLoader) {
        if (!GboardPassBlurContinuousAuthority.install()) {
            Api101Bridge.log(
                    "[DC][GboardFloatingGlass] continuous PassBlur authority unavailable; fail closed");
            return false;
        }
        GboardFloatingGlassHook.install(classLoader);
        GboardHandwritingCapsuleGlassHook.install(classLoader);
        return true;
    }

    private static boolean installSearchbox(ClassLoader classLoader) {
        if (!MiuiSearchboxPassBlurContinuousAuthority.install()) {
            Api101Bridge.log(
                    "[DC][MiuiSearchboxGlass] continuous PassBlur authority unavailable; fail closed");
            return false;
        }
        return MiuiSearchboxGlassHook.install(classLoader);
    }
}
