package com.hellovoid.liquiddock;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;

/** Launcher-process publisher for the settings page's live widget target inventory. */
final class WidgetBackgroundDiscoveryStore {
    private static final String SESSION = Process.myPid() + ":" + SystemClock.elapsedRealtime();

    private WidgetBackgroundDiscoveryStore() {}

    static void publish(Context context, WidgetBackgroundDiscoverySnapshot snapshot) {
        if (context == null || snapshot == null || snapshot.targets().isEmpty()) return;
        try {
            String key = WidgetBackgroundDiscoveryCodec.preferenceKey(snapshot.identity());
            Bundle extras = new Bundle();
            extras.putString(WidgetBackgroundDiscoveryProvider.EXTRA_SESSION, SESSION);
            extras.putString(WidgetBackgroundDiscoveryProvider.EXTRA_KEY, key);
            extras.putString(WidgetBackgroundDiscoveryProvider.EXTRA_VALUE,
                    WidgetBackgroundDiscoveryCodec.encode(snapshot));
            ContentResolver resolver = context.getContentResolver();
            Bundle result = resolver.call(WidgetBackgroundDiscoveryProvider.CONTENT_URI,
                    WidgetBackgroundDiscoveryProvider.METHOD_PUBLISH, null, extras);
            MainHook.log("[WidgetBgDiscovery] publish "
                    + (result != null && result.getBoolean(
                    WidgetBackgroundDiscoveryProvider.RESULT_OK, false) ? "ok" : "no-ack")
                    + " key=" + key + " targets=" + snapshot.targets().size());
        } catch (Throwable error) {
            MainHook.log("[WidgetBgDiscovery] publish failed transport=content-provider: " + error);
        }
    }
}
