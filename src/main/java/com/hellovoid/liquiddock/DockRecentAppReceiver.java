package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;

/** Receives the Launcher's current pre-filter Dock recommendation candidates. */
public final class DockRecentAppReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !DockRecentAppStore.ACTION_CANDIDATES.equals(intent.getAction())) return;

        SharedPreferences config = PreferenceManager.getDefaultSharedPreferences(context);
        String expected = config.getString(WidgetComponentStore.DISCOVERY_TOKEN_KEY, "");
        String actual = intent.getStringExtra(DockRecentAppStore.EXTRA_TOKEN);
        if (expected == null || expected.isEmpty() || actual == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        actual.getBytes(StandardCharsets.UTF_8))) return;

        ArrayList<String> values =
                intent.getStringArrayListExtra(DockRecentAppStore.EXTRA_PACKAGES);
        if (values == null) return;
        HashSet<String> cleaned = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && value.indexOf('\n') < 0
                    && value.indexOf('\r') < 0 && value.indexOf('\t') < 0) {
                cleaned.add(value);
            }
        }
        context.getSharedPreferences(DockRecentAppStore.CANDIDATE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(DockRecentAppStore.CANDIDATE_KEY, cleaned)
                .commit();

        config.edit().remove(DockRecentAppStore.DISCOVERY_REQUEST_KEY).commit();
        LiquidDockApp.syncToRemote(config);
    }
}
