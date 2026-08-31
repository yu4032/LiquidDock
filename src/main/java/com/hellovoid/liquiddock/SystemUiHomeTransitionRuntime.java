package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Launcher-side receiver for WMShell HOME visibility timing events. */
final class SystemUiHomeTransitionRuntime {
    private static final Object LOCK = new Object();
    private static boolean registered;
    private static BroadcastReceiver receiver;

    private SystemUiHomeTransitionRuntime() {}

    static void install() {
        Object application = HookUtil.invokeStatic(
                "android.app.ActivityThread", "currentApplication");
        if (application instanceof Context) {
            ensureRegistered((Context) application);
        }
    }

    static void ensureRegistered(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (registered) return;
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            final Context receiverContext = app;
            BroadcastReceiver created = new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    if (intent == null
                            || !SystemUiHomeTransitionProtocol.ACTION.equals(intent.getAction())) {
                        return;
                    }
                    boolean visible = intent.getBooleanExtra(
                            SystemUiHomeTransitionProtocol.EXTRA_VISIBLE, false);
                    long sourceUptimeMs = intent.getLongExtra(
                            SystemUiHomeTransitionProtocol.EXTRA_SOURCE_UPTIME_MS, 0L);
                    long receiveUptimeMs = SystemClock.uptimeMillis();
                    LauncherGlassHomePresentationHook.onSystemUiHomeVisibilityReady(
                            visible, sourceUptimeMs, receiveUptimeMs);
                }
            };
            try {
                receiverContext.registerReceiver(
                        created,
                        new IntentFilter(SystemUiHomeTransitionProtocol.ACTION),
                        SystemUiHomeTransitionProtocol.SENDER_PERMISSION,
                        new Handler(Looper.getMainLooper()),
                        Context.RECEIVER_EXPORTED);
                receiver = created;
                registered = true;
                MainHook.log("[DC][GlassScene] SystemUI HOME visibility receiver registered");
            } catch (Throwable error) {
                MainHook.log("[DC][GlassScene] SystemUI HOME visibility receiver unavailable: "
                        + error);
            }
        }
    }
}
