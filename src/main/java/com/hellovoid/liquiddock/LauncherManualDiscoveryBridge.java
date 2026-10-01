package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private static final String LAUNCHER_PACKAGE = "com.miui.home";

    private static volatile boolean receiverRegistered;
    private static volatile WeakReference<Object> dockProvider = new WeakReference<>(null);

    private LauncherManualDiscoveryBridge() {}

    static void captureDockProvider(Object provider) {
        if (provider != null) dockProvider = new WeakReference<>(provider);
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
                    if (!validToken(token)) return;
                    String kind = intent.getStringExtra(EXTRA_KIND);
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

    private static boolean send(Context context, String kind, String token) {
        if (context == null || token == null || token.isEmpty()) return false;
        try {
            Intent intent = new Intent(ACTION_REQUEST);
            intent.setPackage(LAUNCHER_PACKAGE);
            intent.putExtra(EXTRA_KIND, kind);
            intent.putExtra(EXTRA_TOKEN, token);
            context.sendBroadcast(intent);
            return true;
        } catch (Throwable ignored) {
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
