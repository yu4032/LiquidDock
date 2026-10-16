package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

/** Launcher-side receiver for the single SystemUI keyguard-finished handoff. */
final class SystemUiKeyguardGoneRuntime {
    private static final Object LOCK = new Object();
    private static boolean registered;
    private static BroadcastReceiver receiver;
    private static boolean screenLifecycleRegistered;
    private static BroadcastReceiver screenLifecycleReceiver;

    private SystemUiKeyguardGoneRuntime() {}

    static void install() {
        HookUtil.InvocationResult<Object> applicationResult =
                HookUtil.tryInvokeActivityThreadCurrentApplication();
        Object application = applicationResult.succeeded() ? applicationResult.value() : null;
        if (application instanceof Context) {
            ensureRegistered((Context) application);
        }
    }

    static void ensureRegistered(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (registered && screenLifecycleRegistered) return;
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            final Context receiverContext = app;
            if (!registered) {
            BroadcastReceiver created = new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    if (intent == null
                            || !SystemUiKeyguardGoneProtocol.ACTION.equals(intent.getAction())) {
                        return;
                    }
                    LauncherGlassHomePresentationHook.onSystemUiLockscreenGoneFinished();
                }
            };
            try {
                receiverContext.registerReceiver(
                        created,
                        new IntentFilter(SystemUiKeyguardGoneProtocol.ACTION),
                        SystemUiKeyguardGoneProtocol.SENDER_PERMISSION,
                        new Handler(Looper.getMainLooper()),
                        Context.RECEIVER_EXPORTED);
                receiver = created; // keep a strong process-lifetime reference
                registered = true;
                MainHook.log("[DC][GlassScene] SystemUI keyguard-finished receiver registered");
            } catch (Throwable error) {
                MainHook.log("[DC][GlassScene] SystemUI keyguard-finished receiver unavailable: "
                        + error);
            }
            }
            if (!screenLifecycleRegistered) {
                BroadcastReceiver lifecycle = new BroadcastReceiver() {
                    @Override public void onReceive(Context ignored, Intent intent) {
                        if (intent == null) return;
                        String action = intent.getAction();
                        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                            LauncherGlassHomePresentationHook.onSystemScreenOff();
                        } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                            LauncherGlassHomePresentationHook.onSystemUserPresent();
                        }
                    }
                };
                try {
                    IntentFilter actions = new IntentFilter();
                    actions.addAction(Intent.ACTION_SCREEN_OFF);
                    actions.addAction(Intent.ACTION_USER_PRESENT);
                    // Both actions are protected system broadcasts. No third-
                    // party sender may force a Workspace capture/visibility.
                    receiverContext.registerReceiver(lifecycle, actions,
                            Context.RECEIVER_NOT_EXPORTED);
                    screenLifecycleReceiver = lifecycle;
                    screenLifecycleRegistered = true;
                    MainHook.log("[DC][GlassScene] screen-off / user-present freshness receiver registered");
                } catch (Throwable error) {
                    MainHook.log("[DC][GlassScene] screen lifecycle receiver unavailable: "
                            + error);
                }
            }
        }
    }
}
