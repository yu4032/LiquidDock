package com.hellovoid.liquiddock.config;

/** Immutable metadata for one persisted LiquidDock configuration setting. */
public final class ConfigKey<T> {
    public enum Type { BOOLEAN, INT, STRING }
    public enum StorageMode { DIRECT, DP_TENTHS }
    public enum ExportMode { ALWAYS, IF_PRESENT, NEVER }

    private final String name;
    private final Type type;
    private final T uiDefault;
    private final T runtimeFallback;
    private final T exportDefault;
    private final Integer minInt;
    private final Integer maxInt;
    private final StorageMode storageMode;
    private final ExportMode exportMode;
    // Fractional default is metadata, not a new persisted storage format.
    private final Integer defaultDpTenths;

    private ConfigKey(String name, Type type, T uiDefault, T runtimeFallback,
                      T exportDefault, Integer minInt, Integer maxInt,
                      StorageMode storageMode, ExportMode exportMode,
                      Integer defaultDpTenths) {
        this.name = name;
        this.type = type;
        this.uiDefault = uiDefault;
        this.runtimeFallback = runtimeFallback;
        this.exportDefault = exportDefault;
        this.minInt = minInt;
        this.maxInt = maxInt;
        this.storageMode = storageMode;
        this.exportMode = exportMode;
        this.defaultDpTenths = defaultDpTenths;
    }

    static <T> ConfigKey<T> register(
            ConfigSchema.RegistrationAuthority authority,
            String name,
            Type type,
            T uiDefault,
            T runtimeFallback,
            T exportDefault,
            Integer minInt,
            Integer maxInt,
            StorageMode storageMode,
            ExportMode exportMode) {
        if (authority == null) {
            throw new IllegalArgumentException("ConfigKey registration requires ConfigSchema authority");
        }
        return new ConfigKey<>(name, type, uiDefault, runtimeFallback, exportDefault,
                minInt, maxInt, storageMode, exportMode, null);
    }

    /** The UI and runtime share this tenth-dp default when no key was saved. */
    ConfigKey<T> withDefaultDpTenths(int tenths) {
        if (storageMode != StorageMode.DP_TENTHS) {
            throw new IllegalStateException("Fractional dp default requires DP_TENTHS");
        }
        if (tenths < minInt * 10 || tenths > maxInt * 10) {
            throw new IllegalArgumentException("Fractional dp default out of range");
        }
        return new ConfigKey<>(name, type, uiDefault, runtimeFallback,
                exportDefault, minInt, maxInt, storageMode, exportMode, tenths);
    }

    public float uiDefaultDp() {
        return defaultDpTenths == null ? ((Number) uiDefault).floatValue()
                : defaultDpTenths / 10f;
    }

    public float runtimeFallbackDp() {
        return defaultDpTenths == null ? ((Number) runtimeFallback).floatValue()
                : defaultDpTenths / 10f;
    }

    public String name() { return name; }
    public Type type() { return type; }
    public T uiDefault() { return uiDefault; }
    public T runtimeFallback() { return runtimeFallback; }
    public T exportDefault() { return exportDefault; }
    public Integer minInt() { return minInt; }
    public Integer maxInt() { return maxInt; }
    public StorageMode storageMode() { return storageMode; }
    public ExportMode exportMode() { return exportMode; }
}
