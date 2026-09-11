package com.hellovoid.liquiddock;

/** Stable Security Center semantic anchors. Obfuscated member names are never contracts here. */
final class SecurityCenterHookSpec {
    static final String BOOTSTRAP_SERVICE_CLASS =
            "com.miui.gamebooster.service.DockWindowManagerService";
    static final String TURBO_LAYOUT_CLASS =
            "com.miui.gamebooster.windowmanager.newbox.TurboLayout";

    // Public semantic getters verified across the managed JADX corpus.
    static final String DOCK_LAYOUT_GETTER = "getDockLayout";
    static final String APPS_LAYOUT_GETTER = "getAppsLayout";
    static final String BOX_VIEW_GETTER = "getBoxView";
    static final String GAME_BOX_GETTER = "getGameTurboLayout";
    static final String GAME_MATERIAL_GETTER = "getMainView";
    static final String VIDEO_ADAPTER_GETTER = "getVideoBoxViewAdapter";

    static final String ALL_APPS_RADIUS_RESOURCE = "dp_24";
    static final String GAME_RADIUS_RESOURCE = "game_toolbox_background_radius";
    static final String VIDEO_CONTENT_RESOURCE = "main_content";

    private SecurityCenterHookSpec() {}
}
