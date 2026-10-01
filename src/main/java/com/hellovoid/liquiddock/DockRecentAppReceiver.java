package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;

/** Receives the Launcher's current pre-filter Dock recommendation candidates. */
public final class DockRecentAppReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "LiquidDockDockRecent";
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !DockRecentAppStore.ACTION_CANDIDATES.equals(intent.getAction())) return;

        SharedPreferences config = PreferenceManager.getDefaultSharedPreferences(context);
        String expected = config.getString(WidgetComponentStore.DISCOVERY_TOKEN_KEY, "");
        String actual = intent.getStringExtra(DockRecentAppStore.EXTRA_TOKEN);
        Log.i(LOG_TAG, "[DC][DockRecentBlacklist] receiver hit"
                + " expectedTokenPresent=" + (expected != null && !expected.isEmpty())
                + " actualTokenPresent=" + (actual != null && !actual.isEmpty()));
        if (expected == null || expected.isEmpty() || actual == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        actual.getBytes(StandardCharsets.UTF_8))) {
            Log.i(LOG_TAG, "[DC][DockRecentBlacklist] receiver tokenRejected");
            return;
        }

        ArrayList<String> values =
                intent.getStringArrayListExtra(DockRecentAppStore.EXTRA_PACKAGES);
        if (values == null) {
            Log.i(LOG_TAG, "[DC][DockRecentBlacklist] receiver packagesMissing");
            return;
        }
        Log.i(LOG_TAG, "[DC][DockRecentBlacklist] receiver rawPackages=" + values);
        HashSet<String> cleaned = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && value.indexOf('\n') < 0
                    && value.indexOf('\r') < 0 && value.indexOf('\t') < 0) {
                cleaned.add(value);
            }
        }
        boolean stored = context.getSharedPreferences(
                        DockRecentAppStore.CANDIDATE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(DockRecentAppStore.CANDIDATE_KEY, cleaned)
                .commit();
        Log.i(LOG_TAG, "[DC][DockRecentBlacklist] receiver stored=" + stored
                + " cleanedPackages=" + cleaned);

        boolean cleared = config.edit()
                .remove(DockRecentAppStore.DISCOVERY_REQUEST_KEY)
                .commit();
        boolean synced = LiquidDockApp.syncToRemote(config);
        Log.i(LOG_TAG, "[DC][DockRecentBlacklist] receiver requestCleared=" + cleared
                + " remoteSynced=" + synced);
    }
}
