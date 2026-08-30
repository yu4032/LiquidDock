package com.hellovoid.liquiddock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Narrow, caller-verified Launcher -> module-app transport for discovery snapshots. */
public final class WidgetBackgroundDiscoveryProvider extends ContentProvider {
    static final String AUTHORITY = "com.hellovoid.liquiddock.widgetdiscovery";
    static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    static final String PREFS_NAME = "widget_discovery";
    static final String METHOD_PUBLISH = "publish";
    static final String EXTRA_SESSION = "session";
    static final String EXTRA_KEY = "key";
    static final String EXTRA_VALUE = "value";
    static final String RESULT_OK = "ok";
    private static final String SESSION_KEY = "__session";
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final int MAX_KEY_CHARS = 1024;
    private static final int MAX_VALUE_CHARS = 256 * 1024;
    private SharedPreferences preferences;

    @Override public boolean onCreate() {
        Context context = getContext();
        if (context == null) return false;
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        enforceCaller();
        if (!METHOD_PUBLISH.equals(method) || extras == null || preferences == null) {
            return super.call(method, arg, extras);
        }
        String session = extras.getString(EXTRA_SESSION, "");
        String key = extras.getString(EXTRA_KEY, "");
        String value = extras.getString(EXTRA_VALUE, "");
        if (session.isEmpty() || key.isEmpty() || key.length() > MAX_KEY_CHARS
                || value.isEmpty() || value.length() > MAX_VALUE_CHARS) {
            throw new IllegalArgumentException("invalid widget discovery payload");
        }
        synchronized (this) {
            String oldSession = preferences.getString(SESSION_KEY, null);
            SharedPreferences.Editor editor = preferences.edit();
            if (!session.equals(oldSession)) {
                editor.clear();
                editor.putString(SESSION_KEY, session);
            }
            editor.putString(key, value);
            if (!editor.commit()) throw new IllegalStateException("discovery persist failed");
        }
        Bundle result = new Bundle();
        result.putBoolean(RESULT_OK, true);
        return result;
    }

    private void enforceCaller() {
        Context context = getContext();
        if (context == null) throw new SecurityException("provider context unavailable");
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return;
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        if (packages != null) for (String packageName : packages) {
            if (LAUNCHER_PACKAGE.equals(packageName)) return;
        }
        throw new SecurityException("widget discovery caller rejected uid=" + uid);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("call() only");
    }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("call() only");
    }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        throw new UnsupportedOperationException("call() only");
    }
}
