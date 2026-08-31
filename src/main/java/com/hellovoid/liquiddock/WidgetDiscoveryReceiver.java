package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/** Receives authenticated widget descriptors from the hooked Launcher process. */
public final class WidgetDiscoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !WidgetComponentStore.ACTION_DISCOVER.equals(intent.getAction())) return;
        String encoded = intent.getStringExtra(WidgetComponentStore.EXTRA_DESCRIPTOR);
        String actualToken = intent.getStringExtra(WidgetComponentStore.EXTRA_TOKEN);
        if (WidgetComponentStore.parseCatalog(encoded) == null || actualToken == null) return;

        SharedPreferences config = PreferenceManager.getDefaultSharedPreferences(context);
        String expectedToken = config.getString(WidgetComponentStore.DISCOVERY_TOKEN_KEY, "");
        if (expectedToken == null || expectedToken.isEmpty() || !secureEquals(expectedToken, actualToken)) {
            return;
        }

        SharedPreferences catalog = context.getSharedPreferences(
                WidgetComponentStore.CATALOG_PREFS, Context.MODE_PRIVATE);
        Set<String> current = catalog.getStringSet(WidgetComponentStore.CATALOG_KEY, null);
        HashSet<String> updated = current == null ? new HashSet<>() : new HashSet<>(current);
        if (!updated.add(encoded)) return;
        catalog.edit().putStringSet(WidgetComponentStore.CATALOG_KEY, updated).apply();
    }

    private static boolean secureEquals(String expected, String actual) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
