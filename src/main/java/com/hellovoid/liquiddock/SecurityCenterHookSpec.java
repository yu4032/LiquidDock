package com.hellovoid.liquiddock;

/** Immutable compatibility contract for supported Security Center builds. */
final class SecurityCenterHookSpec {
    static final String BOOTSTRAP_SERVICE_CLASS =
            "com.miui.gamebooster.service.DockWindowManagerService";

    private static final SecurityCenterHookSpec OS4_40011320 = os4SoftLightSpec(40011320L);
    private static final SecurityCenterHookSpec OS4_40011355 = os4SoftLightSpec(40011355L);

    private final long versionCode;
    private final SecurityCenterVendorGeneration vendorGeneration;
    private final String turboLayoutClass;
    private final String sidebarWrapperClass;
    private final String dockWindowTypeClass;
    private final String prepareDockMethod;
    private final String type4PredicateMethod;
    private final String dockLayoutGetter;
    private final String appsLayoutGetter;
    private final String toggleAllAppsMethod;
    private final String finalBackgroundMethod;
    private final String allAppsPresentField;
    private final String transformingField;
    private final String os4MaterialHelperClass;
    private final String os4MaterialResetMethod;

    private SecurityCenterHookSpec(long versionCode,
            SecurityCenterVendorGeneration vendorGeneration,
            String turboLayoutClass,
            String sidebarWrapperClass,
            String dockWindowTypeClass,
            String prepareDockMethod,
            String type4PredicateMethod,
            String dockLayoutGetter,
            String appsLayoutGetter,
            String toggleAllAppsMethod,
            String finalBackgroundMethod,
            String allAppsPresentField,
            String transformingField,
            String os4MaterialHelperClass,
            String os4MaterialResetMethod) {
        this.versionCode = versionCode;
        this.vendorGeneration = vendorGeneration;
        this.turboLayoutClass = turboLayoutClass;
        this.sidebarWrapperClass = sidebarWrapperClass;
        this.dockWindowTypeClass = dockWindowTypeClass;
        this.prepareDockMethod = prepareDockMethod;
        this.type4PredicateMethod = type4PredicateMethod;
        this.dockLayoutGetter = dockLayoutGetter;
        this.appsLayoutGetter = appsLayoutGetter;
        this.toggleAllAppsMethod = toggleAllAppsMethod;
        this.finalBackgroundMethod = finalBackgroundMethod;
        this.allAppsPresentField = allAppsPresentField;
        this.transformingField = transformingField;
        this.os4MaterialHelperClass = os4MaterialHelperClass;
        this.os4MaterialResetMethod = os4MaterialResetMethod;
    }

    private static SecurityCenterHookSpec os4SoftLightSpec(long versionCode) {
        return new SecurityCenterHookSpec(
                versionCode,
                SecurityCenterVendorGeneration.OS4_SOFT_LIGHT_CAPABLE,
                "com.miui.gamebooster.windowmanager.newbox.TurboLayout",
                "com.miui.dock.sidebar.p",
                "ja.a",
                "M",
                "f",
                "getDockLayout",
                "getAppsLayout",
                "d0",
                "U",
                "f17941q",
                "f17943s",
                "gq.g",
                "l");
    }

    static SecurityCenterHookSpec forVersionCode(long versionCode) {
        if (versionCode == 40011320L) return OS4_40011320;
        if (versionCode == 40011355L) return OS4_40011355;
        return null;
    }

    long versionCode() {
        return versionCode;
    }

    SecurityCenterVendorGeneration vendorGeneration() {
        return vendorGeneration;
    }

    String turboLayoutClass() {
        return turboLayoutClass;
    }

    String sidebarWrapperClass() {
        return sidebarWrapperClass;
    }

    String dockWindowTypeClass() {
        return dockWindowTypeClass;
    }

    String prepareDockMethod() {
        return prepareDockMethod;
    }

    String type4PredicateMethod() {
        return type4PredicateMethod;
    }

    String dockLayoutGetter() {
        return dockLayoutGetter;
    }

    String appsLayoutGetter() {
        return appsLayoutGetter;
    }

    String toggleAllAppsMethod() {
        return toggleAllAppsMethod;
    }

    String finalBackgroundMethod() {
        return finalBackgroundMethod;
    }

    String allAppsPresentField() {
        return allAppsPresentField;
    }

    String transformingField() {
        return transformingField;
    }

    String os4MaterialHelperClass() {
        return os4MaterialHelperClass;
    }

    String os4MaterialResetMethod() {
        return os4MaterialResetMethod;
    }
}
