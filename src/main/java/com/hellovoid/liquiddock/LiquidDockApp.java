package com.hellovoid.liquiddock;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.hellovoid.liquiddock.config.ConfigMigration;

import java.util.Map;
import java.util.UUID;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Module-process service bridge used by the settings UI for API101 Remote Preferences. */
public final class LiquidDockApp extends Application
        implements XposedServiceHelper.OnServiceListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile XposedService service;
    private SharedPreferences localPreferences;
    private boolean reconciling;

    @Override
    public void onCreate() {
        super.onCreate();
        localPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Upgrade the app-local authority before XposedService can synchronously bind and seed
        // Remote Preferences. Otherwise a stale local store can overwrite the Launcher's freshly
        // migrated API101 store during reconciliation.
        ConfigMigration.migrate(this, localPreferences);
        ensureWidgetDiscoveryToken();
        localPreferences.registerOnSharedPreferenceChangeListener(this);
        XposedServiceHelper.registerListener(this);
    }

    private void ensureWidgetDiscoveryToken() {
        if (localPreferences.contains(WidgetComponentStore.DISCOVERY_TOKEN_KEY)) return;
        localPreferences.edit().putString(
                WidgetComponentStore.DISCOVERY_TOKEN_KEY,
                UUID.randomUUID().toString()).commit();
    }

    @Override
    public void onServiceBind(XposedService value) {
        service = value;
        try {
            reconcileOnBind();
        } catch (Throwable error) {
            Log.w("LiquidDock", "initial Remote Preferences reconciliation failed", error);
        }
    }

    @Override
    public void onServiceDied(XposedService value) {
        if (service == value) service = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (reconciling) return;
        try {
            syncKeyToRemote(key, sharedPreferences);
        } catch (Throwable error) {
            Log.w("LiquidDock", "Remote Preferences sync failed for " + key, error);
        }
    }

    private void reconcileOnBind() {
        SharedPreferences remote = remotePreferences(ConfigReader.REMOTE_GROUP);
        if (remote == null || localPreferences == null) return;
        Map<String, ?> localAll = localPreferences.getAll();
        Map<String, ?> remoteAll = remote.getAll();
        boolean hasLocalConfig = false;
        if (localAll != null) {
            for (String key : localAll.keySet()) {
                if (!WidgetComponentStore.DISCOVERY_TOKEN_KEY.equals(key)
                        && !WidgetComponentStore.DISCOVERY_REQUEST_KEY.equals(key)) {
                    hasLocalConfig = true;
                    break;
                }
            }
        }

        // Discovery token/request are transport metadata, not user config. Ignore them when
        // deciding whether an otherwise-empty local store should pull a legacy-migrated config.
        if (!hasLocalConfig && remoteAll != null && !remoteAll.isEmpty()) {
            String token = localPreferences.getString(WidgetComponentStore.DISCOVERY_TOKEN_KEY, null);
            String request = localPreferences.getString(WidgetComponentStore.DISCOVERY_REQUEST_KEY, null);
            reconciling = true;
            try {
                copyAll(remote, localPreferences);
                SharedPreferences.Editor metadata = localPreferences.edit();
                if (token != null && !token.isEmpty()) {
                    metadata.putString(WidgetComponentStore.DISCOVERY_TOKEN_KEY, token);
                }
                if (request != null && !request.isEmpty()) {
                    metadata.putString(WidgetComponentStore.DISCOVERY_REQUEST_KEY, request);
                }
                metadata.commit();
                ensureWidgetDiscoveryToken();
                Log.i("LiquidDock", "seeded local UI prefs from API101 Remote Preferences");
            } finally {
                reconciling = false;
            }
            // Publish retained/generated discovery metadata back down after pulling config.
            syncToRemote(localPreferences);
        } else if (localAll != null && !localAll.isEmpty()) {
            syncToRemote(localPreferences);
            Log.i("LiquidDock", "seeded API101 Remote Preferences from local UI prefs");
        }
    }

    public static XposedService service() { return service; }

    public static SharedPreferences remotePreferences(String group) {
        XposedService value = service;
        return value != null ? value.getRemotePreferences(group) : null;
    }

    /** Full-seed only — used on initial bind.  Incremental updates use syncKeyToRemote. */
    public static boolean syncToRemote(SharedPreferences local) {
        if (local == null) return false;
        SharedPreferences remote = remotePreferences(ConfigReader.REMOTE_GROUP);
        if (remote == null) return false;
        copyAll(local, remote);
        return true;
    }

    /** Incremental: sync a single key change to the Remote Preferences store. */
    private static void syncKeyToRemote(String key, SharedPreferences source) {
        SharedPreferences remote = remotePreferences(ConfigReader.REMOTE_GROUP);
        if (remote == null) return;
        SharedPreferences.Editor editor = remote.edit();
        if (editor == null) return;
        Map<String, ?> all = source.getAll();
        Object value = all.get(key);
        if (value == null) {
            editor.remove(key);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof java.util.Set) {
            @SuppressWarnings("unchecked")
            java.util.Set<String> strings = (java.util.Set<String>) value;
            editor.putStringSet(key, strings);
        }
        editor.apply();
    }

    private static void copyAll(SharedPreferences source, SharedPreferences destination) {
        SharedPreferences.Editor editor = destination.edit();
        if (editor == null) return;
        editor.clear();
        for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof java.util.Set) {
                @SuppressWarnings("unchecked")
                java.util.Set<String> strings = (java.util.Set<String>) value;
                editor.putStringSet(key, strings);
            }
        }
        editor.commit();
    }
}
