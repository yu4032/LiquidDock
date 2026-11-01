package com.hellovoid.liquiddock;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Field;

/** Real Android/Skia resource lifetime check; no Launcher state or preferences are changed. */
final class WorkspaceShaderReuseCheck {
    private WorkspaceShaderReuseCheck() {}

    static void run(Instrumentation owner) {
        Bundle result = new Bundle();
        Throwable[] failure = new Throwable[1];
        owner.runOnMainSync(() -> {
            Bitmap first = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            Bitmap second = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
            LauncherGlassHwuiProbeView view = new LauncherGlassHwuiProbeView(owner.getTargetContext(), null);
            try {
                long start = SystemClock.elapsedRealtimeNanos();
                require(view.setBackdrops(first, first, 128, 128, 0, 1f), "first bind failed");
                result.putDouble("firstBindMs", (SystemClock.elapsedRealtimeNanos() - start) / 1e6);
                Object baseline = shader(view, "baselineShader");
                Object decorated = shader(view, "decoratedShader");
                Object motion = shader(view, "motionDecoratedShader");
                require(baseline != null && decorated != null, "required programs unavailable");
                start = SystemClock.elapsedRealtimeNanos();
                require(view.setBackdrops(second, second, 192, 192, 1, 1.1f), "replacement bind failed");
                result.putDouble("replacementBindMs", (SystemClock.elapsedRealtimeNanos() - start) / 1e6);
                require(shader(view, "baselineShader") == baseline, "baseline program recreated");
                require(shader(view, "decoratedShader") == decorated, "decorated program recreated");
                require(shader(view, "motionDecoratedShader") == motion, "motion program recreated");
                LauncherGlassHwuiProbeView.BackdropSnapshot snapshot = view.snapshotBackdrops();
                require(snapshot != null && snapshot.raw == second && snapshot.blurred == second,
                        "replacement image was not published");
                require(snapshot.logicalWidth == 192 && snapshot.logicalHeight == 192
                        && snapshot.rotation == 1, "replacement geometry was not published");
                view.clearBackdrops();
                require(view.snapshotBackdrops() == null && shader(view, "baselineShader") == null,
                        "clear retained resources");
                require(view.setBackdrops(first, first, 128, 128, 0, 1f), "bind after clear failed");
                require(shader(view, "baselineShader") != baseline, "clear reused retired programs");
            } catch (Throwable error) {
                failure[0] = error;
            } finally {
                view.clearBackdrops();
                first.recycle();
                second.recycle();
            }
        });
        if (failure[0] != null) {
            result.putString("failure", android.util.Log.getStackTraceString(failure[0]));
            owner.finish(1, result);
        } else {
            result.putString("result", "PASS: programs reused, images updated, clear retires resources");
            owner.finish(-1, result);
        }
    }

    private static Object shader(LauncherGlassHwuiProbeView view, String name) throws Exception {
        Field field = LauncherGlassHwuiProbeView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(view);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
