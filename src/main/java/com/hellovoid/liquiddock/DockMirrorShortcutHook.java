package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makes Xiaomi Launcher treat Mirror Desktop as inactive when the Dock mirror shortcut is hidden.
 *
 * <p>This intentionally operates at the Mirror Desktop registration boundary. Handoff/relay
 * registration remains owned by RelayIconManager, while the mirror-specific SDK callback is not
 * registered. No HotSeats/adapter/view geometry is modified.
 */
final class DockMirrorShortcutHook {
    private static final String TAG = "[DC][DockMirror]";
    private static final String MIRROR_DESKTOP_HELPER =
            "com.xiaomi.mirror.synergy.MirrorDesktopHelper";
    private static final String MIRROR_DESKTOP_CALLBACK =
            "com.xiaomi.mirror.synergy.MirrorDesktopCallback";

    private static final Map<Object, RegistrationState> HELPERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> INTERNAL_REBIND =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static boolean installed;

    private DockMirrorShortcutHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        try {
            Class<?> helperType = Class.forName(MIRROR_DESKTOP_HELPER, false, classLoader);
            Class<?> callbackType = Class.forName(MIRROR_DESKTOP_CALLBACK, false, classLoader);

            HookUtil.hookMethod(
                    helperType,
                    "registerMirrorDesktopCallback",
                    new Class<?>[]{Context.class, callbackType, Handler.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object helper = chain.getThisObject();
                        RegistrationState state = stateFor(helper);

                        synchronized (state) {
                            state.context = (Context) args[0];
                            state.callback = args[1];
                            state.handler = (Handler) args[2];
                            if (!isInternalRebind()) state.desiredRegistered = true;
                        }

                        if (VisualRuntimeState.isMirrorShortcutHidden()) {
                            MainHook.log(TAG
                                    + " mirror desktop registration suppressed; handoff untouched");
                            return null;
                        }

                        Object result = chain.proceed(args);
                        synchronized (state) {
                            state.actualRegistered = true;
                        }
                        return result;
                    });

            HookUtil.hookMethod(
                    helperType,
                    "unRegisterMirrorDesktopCallback",
                    new Class<?>[]{Context.class},
                    chain -> {
                        Object helper = chain.getThisObject();
                        RegistrationState state = stateFor(helper);
                        boolean shouldProceed;
                        synchronized (state) {
                            if (!isInternalRebind()) state.desiredRegistered = false;
                            shouldProceed = state.actualRegistered;
                        }
                        if (!shouldProceed) return null;

                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        synchronized (state) {
                            state.actualRegistered = false;
                        }
                        return result;
                    });

            installed = true;
            MainHook.log(TAG + " Mirror Desktop feature gate installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Mirror Desktop feature gate unavailable: " + error);
        }
    }

    static void onRuntimeVisibilityChanged() {
        ArrayList<Map.Entry<Object, RegistrationState>> snapshot;
        synchronized (HELPERS) {
            snapshot = new ArrayList<>(HELPERS.entrySet());
        }

        boolean hidden = VisualRuntimeState.isMirrorShortcutHidden();
        int changed = 0;
        for (Map.Entry<Object, RegistrationState> entry : snapshot) {
            Object helper = entry.getKey();
            RegistrationState state = entry.getValue();
            if (helper == null || state == null) continue;

            if (hidden) {
                if (disableMirrorRegistration(helper, state)) changed++;
            } else {
                if (restoreMirrorRegistration(helper, state)) changed++;
            }
        }
        MainHook.log(TAG + " hidden=" + hidden + " mirrorRegistrationsChanged=" + changed);
    }

    private static boolean disableMirrorRegistration(
            Object helper, RegistrationState state) {
        Object callback;
        Context context;
        boolean actualRegistered;
        synchronized (state) {
            callback = state.callback;
            context = state.context;
            actualRegistered = state.actualRegistered;
        }
        if (!actualRegistered) return false;

        // Feed the vendor callback its normal empty-device state before detaching. RelayIconManager
        // then clears MirrorShortcutInfo and tells HotSeats through its own onUpdateMirrorDevice()
        // path; LiquidDock never hides or resizes a Dock item itself.
        if (callback != null) {
            HookUtil.InvocationResult<Object> clear =
                    HookUtil.tryInvoke(callback, "onDeviceListUpdate", (Object) null);
            if (!clear.succeeded()) {
                MainHook.log(TAG + " mirror model clear failed: " + clear.failure());
            }
        }

        if (context == null) return false;
        HookUtil.InvocationResult<Object> unregister = runInternal(() ->
                HookUtil.tryInvoke(helper, "unRegisterMirrorDesktopCallback", context));
        if (!unregister.succeeded()) {
            MainHook.log(TAG + " mirror unregister failed: " + unregister.failure());
            return false;
        }
        return true;
    }

    private static boolean restoreMirrorRegistration(
            Object helper, RegistrationState state) {
        Context context;
        Object callback;
        Handler handler;
        boolean desiredRegistered;
        boolean actualRegistered;
        synchronized (state) {
            context = state.context;
            callback = state.callback;
            handler = state.handler;
            desiredRegistered = state.desiredRegistered;
            actualRegistered = state.actualRegistered;
        }

        if (!desiredRegistered || actualRegistered
                || context == null || callback == null || handler == null) {
            return false;
        }

        HookUtil.InvocationResult<Object> register = runInternal(() ->
                HookUtil.tryInvoke(
                        helper,
                        "registerMirrorDesktopCallback",
                        context,
                        callback,
                        handler));
        if (!register.succeeded()) {
            MainHook.log(TAG + " mirror register restore failed: " + register.failure());
            return false;
        }
        return true;
    }

    private static RegistrationState stateFor(Object helper) {
        synchronized (HELPERS) {
            RegistrationState state = HELPERS.get(helper);
            if (state == null) {
                state = new RegistrationState();
                HELPERS.put(helper, state);
            }
            return state;
        }
    }

    private static boolean isInternalRebind() {
        return Boolean.TRUE.equals(INTERNAL_REBIND.get());
    }

    private static <T> T runInternal(InternalCall<T> call) {
        boolean previous = isInternalRebind();
        INTERNAL_REBIND.set(Boolean.TRUE);
        try {
            return call.run();
        } finally {
            INTERNAL_REBIND.set(previous);
        }
    }

    private interface InternalCall<T> {
        T run();
    }

    private static final class RegistrationState {
        Context context;
        Object callback;
        Handler handler;
        boolean desiredRegistered;
        boolean actualRegistered;
    }
}
