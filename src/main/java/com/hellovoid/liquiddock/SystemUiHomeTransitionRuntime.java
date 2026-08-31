package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

/** Launcher-side receiver for precise WMShell HOME transition timing. */
final class SystemUiHomeTransitionRuntime {
    private static final Object LOCK = new Object();
    private static boolean registered;
    private static BroadcastReceiver receiver;

    private SystemUiHomeTransitionRuntime() {}

    static void install() {
        Object application = HookUtil.invokeStatic(
                "android.app.ActivityThread", "currentApplication");
        if (application instanceof Context) ensureRegistered((Context) application);
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
                    int phase = intent.getIntExtra(SystemUiHomeTransitionProtocol.EXTRA_PHASE, 0);
                    if (!SystemUiHomeTransitionProtocol.isKnownPhase(phase)) return;
                    boolean homeVisible = intent.getBooleanExtra(
                            SystemUiHomeTransitionProtocol.EXTRA_HOME_VISIBLE, false);
                    long serial = intent.getLongExtra(
                            SystemUiHomeTransitionProtocol.EXTRA_SERIAL, -1L);
                    long eventTimeNanos = intent.getLongExtra(
                            SystemUiHomeTransitionProtocol.EXTRA_EVENT_TIME_NANOS, -1L);
                    boolean aborted = intent.getBooleanExtra(
                            SystemUiHomeTransitionProtocol.EXTRA_ABORTED, false);
                    if (serial <= 0L || eventTimeNanos <= 0L) return;
                    if (phase == SystemUiHomeTransitionProtocol.PHASE_START) {
                        LauncherGlassHomePresentationHook.onSystemUiHomeTransitionStarted(
                                homeVisible, serial, eventTimeNanos);
                    } else {
                        LauncherGlassHomePresentationHook.onSystemUiHomeTransitionFinished(
                                homeVisible, serial, eventTimeNanos, aborted);
                    }
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
                MainHook.log("[DC][GlassScene] SystemUI HOME timing receiver registered");
            } catch (Throwable error) {
                MainHook.log("[DC][GlassScene] SystemUI HOME timing receiver unavailable: "
                        + error);
            }
        }
    }
}
