package com.hellovoid.liquiddock.config;

/** Independent opt-in for the SecurityCenter/Game Turbo sidebar glass domain. */
public final class SidebarGlassConfig {
    public static final ConfigKey<Boolean> ENABLED = new ConfigKey<>(
            "sidebar_liquid_glass",
            ConfigKey.Type.BOOLEAN,
            false,
            false,
            false,
            null,
            null,
            ConfigKey.StorageMode.DIRECT,
            ConfigKey.ExportMode.ALWAYS);

    private SidebarGlassConfig() {}
}
