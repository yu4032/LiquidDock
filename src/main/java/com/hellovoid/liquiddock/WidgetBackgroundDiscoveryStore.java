package com.hellovoid.liquiddock;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/** Launcher-process publisher for the settings page's live widget target inventory. */
final class WidgetBackgroundDiscoveryStore {
    private static final Uri PROVIDER_URI = Uri.parse(
            "content://com.hellovoid.liquiddock.widget-discovery");
    private static boolean sessionInitialized;

    private WidgetBackgroundDiscoveryStore() {}

    static void publish(Context context, WidgetBackgroundDiscoverySnapshot snapshot) {
        if (context == null || snapshot == null) return;
        try {
            ContentResolver resolver = context.getContentResolver();
            synchronized (WidgetBackgroundDiscoveryStore.class) {
                if (!sessionInitialized) {
                    resolver.call(PROVIDER_URI,
                            WidgetBackgroundDiscoveryProvider.METHOD_RESET, null, null);
                    sessionInitialized = true;
                }
            }
            Bundle extras = new Bundle();
            extras.putString(WidgetBackgroundDiscoveryProvider.EXTRA_KEY,
                    WidgetBackgroundDiscoveryCodec.preferenceKey(snapshot.identity()));
            extras.putString(WidgetBackgroundDiscoveryProvider.EXTRA_VALUE,
                    WidgetBackgroundDiscoveryCodec.encode(snapshot));
            resolver.call(PROVIDER_URI,
                    WidgetBackgroundDiscoveryProvider.METHOD_PUBLISH, null, extras);
        } catch (Throwable error) {
            MainHook.log("[WidgetBgDiscovery] publish failed: " + error);
        }
    }
}
