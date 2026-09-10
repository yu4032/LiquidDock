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
    private final String dockWindowManagerClass;
    private final String dockWindowTypeClass;
    private final String configureDockMethod;
    private final String dockReadyMethod;
    private final String gameToolboxPredicateMethod;
    private final String videoToolboxPredicateMethod;
    private final String globalDockPredicateMethod;
    private final String sidebarTurboGetter;
    private final String dockLayoutGetter;
    private final String appsLayoutGetter;
    private final String boxViewGetter;
    private final String gameToolboxViewClass;
    private final String gameToolboxMaterialGetter;
    private final String gameToolboxCornerRadiusResource;
    private final String toggleAllAppsMethod;
    private final String removeTurboLayoutMethod;
    private final String removeTurboLayoutWithoutAnimationMethod;
    private final String finalBackgroundMethod;
    private final String allAppsPresentField;
    private final String transformingField;
    private final String os4MaterialHelperClass;
    private final String os4MaterialResetMethod;
    private final String allAppsCornerRadiusResource;

    private SecurityCenterHookSpec(long versionCode,
            SecurityCenterVendorGeneration vendorGeneration,
            String turboLayoutClass,
            String sidebarWrapperClass,
            String dockWindowManagerClass,
            String dockWindowTypeClass,
            String configureDockMethod,
            String dockReadyMethod,
            String gameToolboxPredicateMethod,
            String videoToolboxPredicateMethod,
            String globalDockPredicateMethod,
            String sidebarTurboGetter,
            String dockLayoutGetter,
            String appsLayoutGetter,
            String boxViewGetter,
            String gameToolboxViewClass,
            String gameToolboxMaterialGetter,
            String gameToolboxCornerRadiusResource,
            String toggleAllAppsMethod,
            String removeTurboLayoutMethod,
            String removeTurboLayoutWithoutAnimationMethod,
            String finalBackgroundMethod,
            String allAppsPresentField,
            String transformingField,
            String os4MaterialHelperClass,
            String os4MaterialResetMethod,
            String allAppsCornerRadiusResource) {
        this.versionCode = versionCode;
        this.vendorGeneration = vendorGeneration;
        this.turboLayoutClass = turboLayoutClass;
        this.sidebarWrapperClass = sidebarWrapperClass;
        this.dockWindowManagerClass = dockWindowManagerClass;
        this.dockWindowTypeClass = dockWindowTypeClass;
        this.configureDockMethod = configureDockMethod;
        this.dockReadyMethod = dockReadyMethod;
        this.gameToolboxPredicateMethod = gameToolboxPredicateMethod;
        this.videoToolboxPredicateMethod = videoToolboxPredicateMethod;
        this.globalDockPredicateMethod = globalDockPredicateMethod;
        this.sidebarTurboGetter = sidebarTurboGetter;
        this.dockLayoutGetter = dockLayoutGetter;
        this.appsLayoutGetter = appsLayoutGetter;
        this.boxViewGetter = boxViewGetter;
        this.gameToolboxViewClass = gameToolboxViewClass;
        this.gameToolboxMaterialGetter = gameToolboxMaterialGetter;
        this.gameToolboxCornerRadiusResource = gameToolboxCornerRadiusResource;
        this.toggleAllAppsMethod = toggleAllAppsMethod;
        this.removeTurboLayoutMethod = removeTurboLayoutMethod;
        this.removeTurboLayoutWithoutAnimationMethod = removeTurboLayoutWithoutAnimationMethod;
        this.finalBackgroundMethod = finalBackgroundMethod;
        this.allAppsPresentField = allAppsPresentField;
        this.transformingField = transformingField;
        this.os4MaterialHelperClass = os4MaterialHelperClass;
        this.os4MaterialResetMethod = os4MaterialResetMethod;
        this.allAppsCornerRadiusResource = allAppsCornerRadiusResource;
    }

    private static SecurityCenterHookSpec os4SoftLightSpec(long versionCode) {
        return new SecurityCenterHookSpec(
                versionCode,
                SecurityCenterVendorGeneration.OS4_SOFT_LIGHT_CAPABLE,
                "com.miui.gamebooster.windowmanager.newbox.TurboLayout",
                "com.miui.dock.sidebar.p",
                "ob.e0",
                "ja.a",
                "V",
                "c0",
                "g",
                "k",
                "f",
                "C",
                "getDockLayout",
                "getAppsLayout",
                "getBoxView",
                "com.miui.gamebooster.windowmanager.newbox.x1",
                "getMainView",
                "game_toolbox_background_radius",
                "d0",
                "d2",
                "f2",
                "U",
                "q",
                "s",
                "gq.g",
                "l",
                "dp_24");
    }

    static SecurityCenterHookSpec forVersionCode(long versionCode) {
        if (versionCode == 40011320L) return OS4_40011320;
        if (versionCode == 40011355L) return OS4_40011355;
        return null;
    }

    long versionCode() { return versionCode; }
    SecurityCenterVendorGeneration vendorGeneration() { return vendorGeneration; }
    String turboLayoutClass() { return turboLayoutClass; }
    String sidebarWrapperClass() { return sidebarWrapperClass; }
    String dockWindowManagerClass() { return dockWindowManagerClass; }
    String dockWindowTypeClass() { return dockWindowTypeClass; }
    String configureDockMethod() { return configureDockMethod; }
    String dockReadyMethod() { return dockReadyMethod; }
    String gameToolboxPredicateMethod() { return gameToolboxPredicateMethod; }
    String videoToolboxPredicateMethod() { return videoToolboxPredicateMethod; }
    String globalDockPredicateMethod() { return globalDockPredicateMethod; }
    /** Compatibility alias retained for older exact-contract tests/callers. */
    String type4PredicateMethod() { return globalDockPredicateMethod; }
    String sidebarTurboGetter() { return sidebarTurboGetter; }
    String dockLayoutGetter() { return dockLayoutGetter; }
    String appsLayoutGetter() { return appsLayoutGetter; }
    String boxViewGetter() { return boxViewGetter; }
    String gameToolboxViewClass() { return gameToolboxViewClass; }
    String gameToolboxMaterialGetter() { return gameToolboxMaterialGetter; }
    String gameToolboxCornerRadiusResource() { return gameToolboxCornerRadiusResource; }
    String toggleAllAppsMethod() { return toggleAllAppsMethod; }
    String removeTurboLayoutMethod() { return removeTurboLayoutMethod; }
    String removeTurboLayoutWithoutAnimationMethod() { return removeTurboLayoutWithoutAnimationMethod; }
    String finalBackgroundMethod() { return finalBackgroundMethod; }
    String allAppsPresentField() { return allAppsPresentField; }
    String transformingField() { return transformingField; }
    String os4MaterialHelperClass() { return os4MaterialHelperClass; }
    String os4MaterialResetMethod() { return os4MaterialResetMethod; }
    String allAppsCornerRadiusResource() { return allAppsCornerRadiusResource; }
}
