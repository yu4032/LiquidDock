package com.hellovoid.liquiddock;

import android.content.SharedPreferences;

/** Launcher-process publisher for the settings page's live widget target inventory. */
final class WidgetBackgroundDiscoveryStore {
    static final String REMOTE_GROUP = "widget_discovery";
    private static boolean sessionInitialized;

    private WidgetBackgroundDiscoveryStore() {}

    static void publish(WidgetBackgroundDiscoverySnapshot snapshot) {
        if (snapshot == null) return;
        try {
            SharedPreferences remote = Api101Bridge.remotePreferences(REMOTE_GROUP);
            synchronized (WidgetBackgroundDiscoveryStore.class) {
                if (!sessionInitialized) {
                    remote.edit().clear().apply();
                    sessionInitialized = true;
                }
            }
            remote.edit().putString(
                    WidgetBackgroundDiscoveryCodec.preferenceKey(snapshot.identity()),
                    WidgetBackgroundDiscoveryCodec.encode(snapshot)).apply();
        } catch (Throwable error) {
            MainHook.log("[WidgetBgDiscovery] publish failed: " + error);
        }
    }
}
