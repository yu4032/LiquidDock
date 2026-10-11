package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reversible widget-only native self-blur output gate.
 *
 * <p>The Launcher-owned setMiSelfBlur radius/blend parameters are observed at the framework
 * setter boundary, before LiquidDock takes ownership. While widget glass is active we submit
 * radius=0 to that same setter, but keep the most recent vendor-requested parameters intact.
 * Releasing ownership replays those parameters. This must not call clearContentBlur(), which
 * also destroys the independent PassWindow blur modes/radius/blends.
 */
final class LauncherWidgetNativeSelfBlurGate {
    private static final String TAG = "[DC][WidgetSelfBlur]";
    private static final Map<View, BlurState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> INTERNAL_WRITE = new ThreadLocal<>();

    private static Method setter;
    private static boolean installed;

    private static final class BlurState {
        boolean known;
        boolean claimed;
        boolean outputMuted;
        int vendorRadius;
        ArrayList<?> vendorColors;
    }

    private LauncherWidgetNativeSelfBlurGate() {}

    static synchronized void install() {
        if (installed) return;
        try {
            // Hook the exact framework setter, not View#draw or a per-frame method. This captures
            // native writes even when the vendor reasserts blur on MAML resume/color changes.
            Method method = View.class.getMethod("setMiSelfBlur", int.class, ArrayList.class);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (Boolean.TRUE.equals(INTERNAL_WRITE.get())
                        || !(chain.getThisObject() instanceof View)
                        || args.length != 2 || !(args[0] instanceof Number)) {
                    return chain.proceed(args);
                }
                View view = (View) chain.getThisObject();
                BlurState state;
                synchronized (STATES) {
                    state = STATES.computeIfAbsent(view, ignored -> new BlurState());
                }
                boolean muted;
                synchronized (state) {
                    state.known = true;
                    state.vendorRadius = ((Number) args[0]).intValue();
                    state.vendorColors = copy(args[1]);
                    muted = state.claimed;
                    if (muted) state.outputMuted = true;
                }
                if (muted) {
                    // Preserve the latest vendor intent, but do not let MAML's async onResume
                    // reinstate a second material while LiquidDock renders its own glass.
                    return chain.proceed(new Object[]{0, null});
                }
                return chain.proceed(args);
            });
            setter = method;
            installed = true;
            MainHook.log(TAG + " reversible setMiSelfBlur gate installed");
        } catch (Throwable error) {
            // If this framework method cannot be observed, do not disable its output: without
            // the provider's real radius/blend list we could not restore it on App -> HOME.
            MainHook.log(TAG + " native self-blur gate unavailable: " + error);
        }
    }

    static void claim(View host) {
        if (host == null || !installed) return;
        BlurState state;
        synchronized (STATES) {
            state = STATES.computeIfAbsent(host, ignored -> new BlurState());
        }
        boolean shouldMute;
        synchronized (state) {
            if (state.claimed) return;
            state.claimed = true;
            // If the vendor has not called its setter yet, we deliberately leave the existing
            // RenderNode state untouched. A later vendor write is intercepted and muted.
            shouldMute = state.known;
        }
        if (!shouldMute) {
            MainHook.log(TAG + " claim without observed native baseline class="
                    + host.getClass().getSimpleName());
            return;
        }
        boolean success = callNative(host, 0, null);
        int nativeRadius;
        synchronized (state) {
            if (state.claimed) state.outputMuted = success;
            nativeRadius = state.vendorRadius;
        }
        MainHook.log(TAG + " claim class=" + host.getClass().getSimpleName()
                + " nativeRadius=" + nativeRadius + " muted=" + success);
    }

    static void release(View host) {
        if (host == null || !installed) return;
        BlurState state;
        synchronized (STATES) {
            state = STATES.get(host);
        }
        if (state == null) return;
        int radius;
        ArrayList<?> colors;
        boolean shouldRestore;
        synchronized (state) {
            if (!state.claimed) return;
            state.claimed = false;
            shouldRestore = state.outputMuted && state.known;
            state.outputMuted = false;
            radius = state.vendorRadius;
            colors = copy(state.vendorColors);
        }
        if (shouldRestore) {
            boolean success = callNative(host, radius, colors);
            MainHook.log(TAG + " restore class=" + host.getClass().getSimpleName()
                    + " radius=" + radius + " restored=" + success);
        }
    }

    private static boolean callNative(View view, int radius, ArrayList<?> colors) {
        Method method = setter;
        if (method == null) return false;
        INTERNAL_WRITE.set(Boolean.TRUE);
        try {
            Object result = method.invoke(view, radius, colors);
            return !(result instanceof Boolean) || Boolean.TRUE.equals(result);
        } catch (Throwable error) {
            MainHook.log(TAG + " native self-blur write failed: " + error);
            return false;
        } finally {
            INTERNAL_WRITE.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<?> copy(Object value) {
        return value instanceof ArrayList ? new ArrayList<>((ArrayList<?>) value) : null;
    }
}
