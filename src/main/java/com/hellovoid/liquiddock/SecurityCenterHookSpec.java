package com.hellovoid.liquiddock;

/** Stable Security Center semantic anchors; renamed carrier classes are never listed here. */
final class SecurityCenterHookSpec {
    static final String BOOTSTRAP_SERVICE_CLASS =
            "com.miui.gamebooster.service.DockWindowManagerService";
    static final String TURBO_LAYOUT_CLASS =
            "com.miui.gamebooster.windowmanager.newbox.TurboLayout";

    static final String CONFIGURE_DOCK_METHOD = "V";
    static final String DOCK_READY_METHOD = "c0";
    static final String TOGGLE_ALL_APPS_METHOD = "d0";
    static final String FINAL_BACKGROUND_METHOD = "U";
    static final String REMOVE_TURBO_LAYOUT_METHOD = "d2";
    static final String REMOVE_TURBO_LAYOUT_WITHOUT_ANIMATION_METHOD = "f2";
    static final String SIDEBAR_TURBO_GETTER = "C";
    static final String DOCK_LAYOUT_GETTER = "getDockLayout";
    static final String APPS_LAYOUT_GETTER = "getAppsLayout";
    static final String BOX_VIEW_GETTER = "getBoxView";
    static final String GAME_BOX_GETTER = "getGameTurboLayout";
    static final String GAME_MATERIAL_GETTER = "getMainView";
    static final String VIDEO_ADAPTER_GETTER = "getVideoBoxViewAdapter";

    // Reflection cannot distinguish the real carrier's several identical private void() methods.
    // These are therefore validated member anchors on structurally discovered carrier classes,
    // never class-name mappings. Missing or shape-changed anchors fail the whole preflight closed.
    static final String GAME_MATERIAL_RESTORE_METHOD = "o";
    static final String VIDEO_MATERIAL_RESTORE_METHOD = "t";

    static final String ALL_APPS_PRESENT_FIELD = "q";
    static final String TRANSFORMING_FIELD = "s";

    static final String ALL_APPS_RADIUS_RESOURCE = "dp_24";
    static final String GAME_RADIUS_RESOURCE = "game_toolbox_background_radius";
    static final String VIDEO_CONTENT_RESOURCE = "main_content";

    private SecurityCenterHookSpec() {}
}
