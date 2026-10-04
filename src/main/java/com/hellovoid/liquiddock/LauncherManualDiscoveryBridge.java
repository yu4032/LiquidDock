package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

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
    public static final String KIND_GRID_PREFLIGHT = "grid_preflight";
    public static final String ACTION_GRID_PREFLIGHT_RESULT =
            "com.hellovoid.liquiddock.GRID_PREFLIGHT_RESULT";
    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_COLUMNS = "columns";
    public static final String EXTRA_ROWS = "rows";
    public static final String EXTRA_AVAILABLE = "available";
    public static final String EXTRA_COMPATIBLE = "compatible";
    public static final String EXTRA_BLOCKING_SPAN_X = "blocking_span_x";
    public static final String EXTRA_BLOCKING_SPAN_Y = "blocking_span_y";
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final long GRID_PREFLIGHT_TIMEOUT_MS = 1500L;

    private static volatile boolean receiverRegistered;
    private static volatile WeakReference<Object> dockProvider = new WeakReference<>(null);

    private LauncherManualDiscoveryBridge() {}

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
                    } else if (KIND_GRID_PREFLIGHT.equals(kind)) {
                        String requestId = intent.getStringExtra(EXTRA_REQUEST_ID);
                        int columns = intent.getIntExtra(EXTRA_COLUMNS, 0);
                        int rows = intent.getIntExtra(EXTRA_ROWS, 0);
                        HomeGridWorkspaceSpanPreflight.Result result =
                                HomeGridWorkspaceSpanRuntime.scan(columns, rows);
                        Intent response = new Intent(ACTION_GRID_PREFLIGHT_RESULT);
                        response.setPackage(WidgetComponentStore.MODULE_PACKAGE);
                        response.putExtra(EXTRA_TOKEN, token);
                        response.putExtra(EXTRA_REQUEST_ID, requestId);
                        response.putExtra(EXTRA_AVAILABLE, result.available);
                        response.putExtra(EXTRA_COMPATIBLE, result.compatible);
                        response.putExtra(EXTRA_BLOCKING_SPAN_X, result.blockingSpanX);
                        response.putExtra(EXTRA_BLOCKING_SPAN_Y, result.blockingSpanY);
                        app.sendBroadcast(response);
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

    public interface GridPreflightCallback {
        void onResult(GridPreflightResult result);
    }

    public static final class GridPreflightResult {
        public final boolean available;
        public final boolean compatible;
        public final int blockingSpanX;
        public final int blockingSpanY;

        GridPreflightResult(
                boolean available,
                boolean compatible,
                int blockingSpanX,
                int blockingSpanY) {
            this.available = available;
            this.compatible = compatible;
            this.blockingSpanX = blockingSpanX;
            this.blockingSpanY = blockingSpanY;
        }
    }

    public static boolean requestGridPreflight(
            Context context,
            String token,
            int columns,
            int rows,
            GridPreflightCallback callback) {
        if (context == null || token == null || token.isEmpty()
                || columns <= 0 || rows <= 0 || callback == null) {
            return false;
        }
        Context app = context.getApplicationContext();
        String requestId = UUID.randomUUID().toString();
        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] completed = {false};
        final BroadcastReceiver[] holder = new BroadcastReceiver[1];

        Runnable finishUnavailable = () -> {
            if (completed[0]) return;
            completed[0] = true;
            try {
                app.unregisterReceiver(holder[0]);
            } catch (Throwable ignored) {}
            callback.onResult(new GridPreflightResult(false, false, 0, 0));
        };

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (completed[0] || intent == null
                        || !ACTION_GRID_PREFLIGHT_RESULT.equals(intent.getAction())) {
                    return;
                }
                String actualToken = intent.getStringExtra(EXTRA_TOKEN);
                String actualRequestId = intent.getStringExtra(EXTRA_REQUEST_ID);
                if (!requestId.equals(actualRequestId)
                        || !secureEquals(token, actualToken)) {
                    return;
                }
                completed[0] = true;
                handler.removeCallbacks(finishUnavailable);
                try {
                    app.unregisterReceiver(this);
                } catch (Throwable ignoredError) {}
                callback.onResult(new GridPreflightResult(
                        intent.getBooleanExtra(EXTRA_AVAILABLE, false),
                        intent.getBooleanExtra(EXTRA_COMPATIBLE, false),
                        intent.getIntExtra(EXTRA_BLOCKING_SPAN_X, 0),
                        intent.getIntExtra(EXTRA_BLOCKING_SPAN_Y, 0)));
            }
        };
        holder[0] = receiver;

        try {
            IntentFilter filter = new IntentFilter(ACTION_GRID_PREFLIGHT_RESULT);
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                app.registerReceiver(receiver, filter);
            }
            Intent request = new Intent(ACTION_REQUEST);
            request.setPackage(LAUNCHER_PACKAGE);
            request.putExtra(EXTRA_KIND, KIND_GRID_PREFLIGHT);
            request.putExtra(EXTRA_TOKEN, token);
            request.putExtra(EXTRA_REQUEST_ID, requestId);
            request.putExtra(EXTRA_COLUMNS, columns);
            request.putExtra(EXTRA_ROWS, rows);
            app.sendBroadcast(request);
            handler.postDelayed(finishUnavailable, GRID_PREFLIGHT_TIMEOUT_MS);
            return true;
        } catch (Throwable error) {
            finishUnavailable.run();
            return false;
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

    private static boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
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
