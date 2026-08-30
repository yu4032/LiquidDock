package com.hellovoid.liquiddock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Narrow Launcher-to-module IPC used only for live widget discovery snapshots. */
public final class WidgetBackgroundDiscoveryProvider extends ContentProvider {
    public static final String METHOD_PUBLISH = "publish";
    public static final String METHOD_RESET = "reset";
    public static final String EXTRA_KEY = "key";
    public static final String EXTRA_VALUE = "value";

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!isLauncherCaller()) throw new SecurityException("caller is not com.miui.home");
        SharedPreferences preferences = requireContext().getSharedPreferences(
                LiquidDockApp.WIDGET_DISCOVERY_GROUP, android.content.Context.MODE_PRIVATE);
        if (METHOD_RESET.equals(method)) {
            preferences.edit().clear().apply();
            return Bundle.EMPTY;
        }
        if (METHOD_PUBLISH.equals(method)) {
            String key = extras != null ? extras.getString(EXTRA_KEY) : null;
            String value = extras != null ? extras.getString(EXTRA_VALUE) : null;
            if (key == null || key.isEmpty() || value == null) {
                throw new IllegalArgumentException("missing discovery key/value");
            }
            preferences.edit().putString(key, value).apply();
            return Bundle.EMPTY;
        }
        throw new IllegalArgumentException("unsupported method: " + method);
    }

    private boolean isLauncherCaller() {
        String[] packages = requireContext().getPackageManager()
                .getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String packageName : packages) {
            if ("com.miui.home".equals(packageName)) return true;
        }
        return false;
    }

    private android.content.Context requireContext() {
        android.content.Context context = getContext();
        if (context == null) throw new IllegalStateException("provider context unavailable");
        return context;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
