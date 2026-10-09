package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * One-shot, authenticated Launcher database check before a dangerous grid edit.
 *
 * Do not persist a below-4 value without a fresh CLEAR answer. Missing receiver,
 * stale responses, unavailable provider and process death all fail closed.
 */
public final class GridWidget4x2PreflightClient {
    public static final int CLEAR = 0;
    public static final int BLOCKED = 1;
    public static final int UNKNOWN = 2;
    private static final long TIMEOUT_MS = 4500L;

    private GridWidget4x2PreflightClient() {}

    public static Request start(Context context, String token, IntConsumer onResult) {
        Request request = new Request(context.getApplicationContext(), token, onResult);
        request.begin();
        return request;
    }

    public static final class Request {
        private final Context context;
        private final String token;
        private final IntConsumer callback;
        private final String id = UUID.randomUUID().toString();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private boolean registered;
        private boolean finished;
        private final Runnable timeout = () -> finish(UNKNOWN);
        private final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (intent == null
                        || !LauncherManualDiscoveryBridge.ACTION_GRID_WIDGET_RESULT.equals(
                                intent.getAction())
                        || !id.equals(intent.getStringExtra(
                                LauncherManualDiscoveryBridge.EXTRA_REQUEST_ID))) {
                    return;
                }
                String actual = intent.getStringExtra(LauncherManualDiscoveryBridge.EXTRA_TOKEN);
                if (actual == null || token == null
                        || !MessageDigest.isEqual(
                                token.getBytes(StandardCharsets.UTF_8),
                                actual.getBytes(StandardCharsets.UTF_8))) {
                    return;
                }
                int result = intent.getIntExtra(LauncherManualDiscoveryBridge.EXTRA_RESULT, UNKNOWN);
                finish(result == CLEAR || result == BLOCKED ? result : UNKNOWN);
            }
        };

        private Request(Context context, String token, IntConsumer callback) {
            this.context = context;
            this.token = token;
            this.callback = callback;
        }

        private void begin() {
            try {
                IntentFilter filter = new IntentFilter(
                        LauncherManualDiscoveryBridge.ACTION_GRID_WIDGET_RESULT);
                if (Build.VERSION.SDK_INT >= 33) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    context.registerReceiver(receiver, filter);
                }
                registered = true;
                handler.postDelayed(timeout, TIMEOUT_MS);
                if (!LauncherManualDiscoveryBridge.requestGridWidgetPreflight(
                        context, token, id)) {
                    handler.post(() -> finish(UNKNOWN));
                }
            } catch (Throwable error) {
                handler.post(() -> finish(UNKNOWN));
            }
        }

        private void finish(int result) {
            if (finished) return;
            cancel();
            callback.accept(result);
        }

        public void cancel() {
            if (finished) return;
            finished = true;
            handler.removeCallbacks(timeout);
            if (registered) {
                registered = false;
                try {
                    context.unregisterReceiver(receiver);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
