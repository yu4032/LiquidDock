package com.hellovoid.liquiddock;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Synchronous per-page sidecar for LayoutTransformRuleGridChanged.
 *
 * <p>Records are namespaced by runtime grid size + screenId + itemId. Moving an item to another
 * screen invalidates all of its old per-orientation records so the destination page gets a fresh
 * first-time mapping on the next rotation.</p>
 */
final class HomeGridRotationPositionMemory {
    static final class Identity {
        final long itemId;
        final Long screenId;

        Identity(long itemId, Long screenId) {
            this.itemId = itemId;
            this.screenId = screenId;
        }
    }

    static final class Position {
        final int x;
        final int y;
        final int spanX;
        final int spanY;

        Position(int x, int y, int spanX, int spanY) {
            this.x = x;
            this.y = y;
            this.spanX = spanX;
            this.spanY = spanY;
        }
    }

    private static final String PREFS = "liquiddock_grid_rotation_positions";
    private static final String FAVORITES_CLASS =
            "com.miui.home.launcher.LauncherSettings$Favorites";
    private static final long NO_SCREEN = Long.MIN_VALUE;
    private static final long GC_INTERVAL_MS = 60L * 60L * 1000L;

    private static final AtomicBoolean GC_RUNNING = new AtomicBoolean();
    private static final ExecutorService GC_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "LiquidDockGridMemoryGc");
                thread.setDaemon(true);
                return thread;
            });

    private static volatile SharedPreferences preferences;
    private static volatile Context applicationContext;
    private static volatile long lastGcElapsed;

    private HomeGridRotationPositionMemory() {}

    static Identity identity(Object data) {
        if (data instanceof Number) {
            return new Identity(((Number) data).longValue(), null);
        }
        if (data instanceof View) {
            Object tag = ((View) data).getTag();
            if (tag != null) {
                try {
                    long id = HookUtil.getLongField(tag, "id");
                    long screenId = HookUtil.getLongField(tag, "screenId");
                    return id >= 0 ? new Identity(id, screenId) : null;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    static Map<Long, Position> load(
            int columns, int rows, long screenId, Iterable<Long> ids) {
        SharedPreferences prefs = prefs();
        LinkedHashMap<Long, Position> result = new LinkedHashMap<>();
        if (prefs == null || ids == null) return result;
        for (Long id : ids) {
            if (id == null) continue;
            String encoded = prefs.getString(
                    HomeGridRotationMemoryKey.layout(columns, rows, screenId, id), null);
            Position position = decode(encoded);
            if (position != null) result.put(id, position);
        }
        return result;
    }

    static void save(
            int columns,
            int rows,
            long screenId,
            Map<Long, Position> positions) {
        SharedPreferences prefs = prefs();
        if (prefs == null || positions == null || positions.isEmpty()) return;

        Map<String, ?> existing = prefs.getAll();
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<Long, Position> entry : positions.entrySet()) {
            Long id = entry.getKey();
            Position position = entry.getValue();
            if (id == null || position == null) continue;

            String ownerKey = HomeGridRotationMemoryKey.owner(id);
            long previousScreen = prefs.getLong(ownerKey, NO_SCREEN);
            if (previousScreen != NO_SCREEN && previousScreen != screenId) {
                String suffix = HomeGridRotationMemoryKey.itemSuffix(id);
                for (String existingKey : existing.keySet()) {
                    if (existingKey != null
                            && existingKey.startsWith("layout:")
                            && existingKey.endsWith(suffix)) {
                        editor.remove(existingKey);
                    }
                }
            }

            editor.putLong(ownerKey, screenId);
            editor.putString(
                    HomeGridRotationMemoryKey.layout(columns, rows, screenId, id),
                    encode(position));
        }
        editor.apply();
        scheduleGarbageCollect();
    }

    static void scheduleGarbageCollect() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastGcElapsed < GC_INTERVAL_MS) return;
        if (!GC_RUNNING.compareAndSet(false, true)) return;

        GC_EXECUTOR.execute(() -> {
            try {
                garbageCollectNow();
                lastGcElapsed = SystemClock.elapsedRealtime();
            } catch (Throwable error) {
                MainHook.log("[DC][HomeGridRotation] sidecar GC skipped: " + error);
            } finally {
                GC_RUNNING.set(false);
            }
        });
    }

    private static void garbageCollectNow() throws Exception {
        Context context = appContext();
        SharedPreferences prefs = prefs();
        if (context == null || prefs == null) return;

        Uri favoritesUri = favoritesUri(context);
        if (favoritesUri == null) return;

        Set<Long> liveIds = queryLiveItemIds(context.getContentResolver(), favoritesUri);
        if (liveIds == null) return;

        Map<String, ?> all = prefs.getAll();
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (String key : all.keySet()) {
            Long itemId = HomeGridRotationMemoryKey.itemIdFromStoredKey(key);
            if (itemId == null || liveIds.contains(itemId)) continue;
            editor.remove(key);
            changed = true;
        }

        if (changed) editor.apply();
    }

    private static Set<Long> queryLiveItemIds(
            ContentResolver resolver, Uri favoritesUri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(
                    favoritesUri,
                    new String[]{"_id"},
                    null,
                    null,
                    null);
            if (cursor == null) return null;

            HashSet<Long> ids = new HashSet<>();
            int idColumn = cursor.getColumnIndex("_id");
            if (idColumn < 0) return null;
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(idColumn));
            }
            return ids;
        } catch (Throwable error) {
            MainHook.log("[DC][HomeGridRotation] Favorites GC query unavailable: " + error);
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static Uri favoritesUri(Context context) {
        try {
            Class<?> favorites = Class.forName(
                    FAVORITES_CLASS, false, context.getClassLoader());
            Field field = favorites.getDeclaredField("CONTENT_URI");
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof Uri ? (Uri) value : null;
        } catch (Throwable error) {
            MainHook.log("[DC][HomeGridRotation] Favorites URI unavailable: " + error);
            return null;
        }
    }

    private static SharedPreferences prefs() {
        SharedPreferences current = preferences;
        if (current != null) return current;
        Context context = appContext();
        if (context == null) return null;
        current = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences = current;
        return current;
    }

    private static Context appContext() {
        Context current = applicationContext;
        if (current != null) return current;

        HookUtil.InvocationResult<Object> applicationResult =
                HookUtil.tryInvokeActivityThreadCurrentApplication();
        if (!applicationResult.succeeded()
                || !(applicationResult.value() instanceof Context)) {
            return null;
        }
        current = ((Context) applicationResult.value()).getApplicationContext();
        if (current == null) current = (Context) applicationResult.value();
        applicationContext = current;
        return current;
    }

    private static String encode(Position position) {
        return position.x + "," + position.y + ","
                + position.spanX + "," + position.spanY;
    }

    private static Position decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        String[] parts = encoded.split(",");
        if (parts.length != 4) return null;
        try {
            return new Position(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
