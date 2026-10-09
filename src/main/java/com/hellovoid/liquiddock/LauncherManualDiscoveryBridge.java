package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import java.lang.reflect.Field;
import android.os.Build;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Live manual-discovery channel between the module settings process and the already-running
 * Launcher process. Manual loads stay in Settings; Launcher is never force-stopped or brought HOME.
 */
public final class LauncherManualDiscoveryBridge {
    public static final String ACTION_REQUEST =
            "com.hellovoid.liquiddock.MANUAL_DISCOVERY_REQUEST";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_TOKEN = "token";
    public static final String KIND_DOCK_RECENTS = "dock_recents";
    public static final String KIND_WIDGETS = "widgets";
    public static final String KIND_GRID_WIDGET_PREFLIGHT = "grid_widget_4x2";
    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_RESULT = "result";
    public static final String ACTION_GRID_WIDGET_RESULT =
            "com.hellovoid.liquiddock.GRID_WIDGET_4X2_RESULT";
    private static final String LAUNCHER_PACKAGE = "com.miui.home";

    private static volatile boolean receiverRegistered;
    private static volatile WeakReference<Object> dockProvider = new WeakReference<>(null);

    private LauncherManualDiscoveryBridge() {}

    static void installGridPreflightRegistration(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher",
                    "setupViews", chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (chain.getThisObject() instanceof Context) {
                            ensureRegistered((Context) chain.getThisObject());
                        }
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC][Grid4x2] Launcher receiver hook unavailable: " + error);
        }
    }

    static void captureDockProvider(Object provider) {
        if (provider != null) {
            dockProvider = new WeakReference<>(provider);
        }
    }

    static void ensureRegistered(Context context) {
        if (context == null || receiverRegistered) return;
        Context app = context.getApplicationContext();
        synchronized (LauncherManualDiscoveryBridge.class) {
            if (receiverRegistered) return;
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    if (intent == null || !ACTION_REQUEST.equals(intent.getAction())) return;
                    String token = intent.getStringExtra(EXTRA_TOKEN);
                    String kind = intent.getStringExtra(EXTRA_KIND);
                    boolean tokenValid = validToken(token);
                    if (!tokenValid) return;
                    if (KIND_DOCK_RECENTS.equals(kind)) {
                        Object provider = dockProvider.get();
                        if (provider == null) {
                            MainHook.log("[DC][ManualDiscover] Dock provider unavailable");
                            return;
                        }
                        HookUtil.InvocationResult<Object> update =
                                HookUtil.tryInvoke(provider, "requestUpdateRecommendTasks");
                        if (!update.succeeded()) {
                            MainHook.log("[DC][ManualDiscover] Dock refresh failed: "
                                    + update.failure());
                        }
                    } else if (KIND_WIDGETS.equals(kind)) {
                        WidgetComponentStore.beginManualDiscoverySession();
                        LauncherWidgetComponentDiscovery.scanTrackedHosts();
                    } else if (KIND_GRID_WIDGET_PREFLIGHT.equals(kind)) {
                        String requestId = intent.getStringExtra(EXTRA_REQUEST_ID);
                        if (requestId == null || !requestId.matches("[0-9a-fA-F-]{36}")) return;
                        // Query all persisted desktop pages without blocking Launcher's main thread.
                        new Thread(() -> replyGridWidgetPreflight(app, token, requestId),
                                "LiquidDockGrid4x2Check").start();
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ACTION_REQUEST);
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                app.registerReceiver(receiver, filter);
            }
            receiverRegistered = true;
            MainHook.log("[DC][ManualDiscover] live request receiver installed");
        }
    }

    public static boolean requestDockRefresh(Context context, String token) {
        return send(context, KIND_DOCK_RECENTS, token);
    }

    public static boolean requestWidgetScan(Context context, String token) {
        return send(context, KIND_WIDGETS, token);
    }

    public static boolean requestGridWidgetPreflight(
            Context context, String token, String requestId) {
        if (context == null || token == null || token.isEmpty()
                || requestId == null || !requestId.matches("[0-9a-fA-F-]{36}")) {
            return false;
        }
        try {
            Intent intent = new Intent(ACTION_REQUEST);
            intent.setPackage(LAUNCHER_PACKAGE);
            intent.putExtra(EXTRA_KIND, KIND_GRID_WIDGET_PREFLIGHT);
            intent.putExtra(EXTRA_TOKEN, token);
            intent.putExtra(EXTRA_REQUEST_ID, requestId);
            context.sendBroadcast(intent);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void replyGridWidgetPreflight(Context context, String token, String requestId) {
        int result = GridWidget4x2PreflightClient.UNKNOWN;
        Cursor cursor = null;
        try {
            Class<?> favorites = Class.forName(
                    "com.miui.home.launcher.LauncherSettings$Favorites",
                    false, context.getClassLoader());
            Field uriField = favorites.getDeclaredField("CONTENT_URI");
            uriField.setAccessible(true);
            Object value = uriField.get(null);
            if (!(value instanceof Uri)) throw new IllegalStateException("Favorites URI unavailable");
            cursor = context.getContentResolver().query(
                    (Uri) value, new String[]{"container", "spanX", "spanY"},
                    null, null, null);
            if (cursor == null) throw new IllegalStateException("Favorites query returned null");
            int containerIndex = cursor.getColumnIndex("container");
            int xIndex = cursor.getColumnIndex("spanX");
            int yIndex = cursor.getColumnIndex("spanY");
            if (containerIndex < 0 || xIndex < 0 || yIndex < 0) {
                throw new IllegalStateException("Favorites is missing span columns");
            }
            result = GridWidget4x2PreflightClient.CLEAR;
            while (cursor.moveToNext()) {
                if (GridWidget4x2PreflightPolicy.matches(
                        cursor.getInt(containerIndex),
                        cursor.getInt(xIndex),
                        cursor.getInt(yIndex))) {
                    result = GridWidget4x2PreflightClient.BLOCKED;
                    break;
                }
            }
        } catch (Throwable error) {
            MainHook.log("[DC][Grid4x2] preflight unavailable: " + error);
        } finally {
            if (cursor != null) cursor.close();
        }
        try {
            Intent reply = new Intent(ACTION_GRID_WIDGET_RESULT);
            reply.setPackage(WidgetComponentStore.MODULE_PACKAGE);
            reply.putExtra(EXTRA_TOKEN, token);
            reply.putExtra(EXTRA_REQUEST_ID, requestId);
            reply.putExtra(EXTRA_RESULT, result);
            context.sendBroadcast(reply);
        } catch (Throwable error) {
            MainHook.log("[DC][Grid4x2] reply failed: " + error);
        }
    }

    private static boolean send(Context context, String kind, String token) {
        if (context == null || token == null || token.isEmpty()) return false;
        try {
            Intent intent = new Intent(ACTION_REQUEST);
            intent.setPackage(LAUNCHER_PACKAGE);
            intent.putExtra(EXTRA_KIND, kind);
            intent.putExtra(EXTRA_TOKEN, token);
            context.sendBroadcast(intent);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static boolean validToken(String actual) {
        if (actual == null || actual.isEmpty()) return false;
        try {
            String expected = ConfigReader.load().s(WidgetComponentStore.DISCOVERY_TOKEN_KEY, "");
            if (expected == null || expected.isEmpty()) return false;
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    actual.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
