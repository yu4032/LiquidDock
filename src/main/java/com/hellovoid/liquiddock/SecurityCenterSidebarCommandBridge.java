package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Security Center :ui bridge for Launcher-triggered Sidebar commands.
 *
 * <p>Diagnostic compatibility bridge. It binds the vendor DockWindowManagerService inside the
 * Security Center :ui process and validates the stable AIDL descriptor. Final production code
 * must replace structural method discovery with the recovered stable Binder contract.</p>
 */
final class SecurityCenterSidebarCommandBridge {
    private static final String TAG = "[DC][SidebarBridge]";

    private static volatile boolean installed;
    private static volatile Context appContext;
    private static volatile IBinder sidebarBinder;
    private static volatile Method showMethod;
    private static volatile Method[] stateMethods;

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service == null) {
                clearBinder("null service");
                return;
            }
            try {
                String descriptor = service.getInterfaceDescriptor();
                if (!SidebarCommandContract.SIDEBAR_DESCRIPTOR.equals(descriptor)) {
                    clearBinder("descriptor mismatch=" + descriptor);
                    return;
                }
                Method candidate = resolveShowMethod(service.getClass());
                Method[] states = resolveStateMethods(service.getClass());
                if (candidate == null || states == null) {
                    clearBinder("Sidebar structural contract unavailable or ambiguous");
                    return;
                }
                candidate.setAccessible(true);
                for (Method state : states) state.setAccessible(true);
                sidebarBinder = service;
                showMethod = candidate;
                stateMethods = states;
                SideSlideHoldDiagnostics.log(TAG + " ready descriptor=" + descriptor
                        + " owner=" + service.getClass().getName());
            } catch (Throwable error) {
                clearBinder("bind validation failed: " + error);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            clearBinder("service disconnected");
            Context context = appContext;
            if (context != null) bindVendorService(context);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            clearBinder("binding died");
            Context context = appContext;
            if (context != null) bindVendorService(context);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            clearBinder("null binding");
        }
    };

    private static final BroadcastReceiver RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (SidebarCommandContract.ACTION_PREPARE.equals(action)) {
                boolean ready = vendorAvailableOrShowing();
                SideSlideHoldDiagnostics.log(TAG + " prepare result=" + ready);
                setResultCode(ready
                        ? SidebarCommandContract.RESULT_READY
                        : SidebarCommandContract.RESULT_UNAVAILABLE);
                return;
            }
            if (!SidebarCommandContract.ACTION_SHOW.equals(action)) return;

            boolean shown = showSidebar(
                    intent.getIntExtra(SidebarCommandContract.EXTRA_X, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_Y, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_WIDTH, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_HEIGHT, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_RADIUS, 0));
            setResultCode(shown
                    ? SidebarCommandContract.RESULT_SHOWN
                    : SidebarCommandContract.RESULT_UNAVAILABLE);
        }
    };

    private SecurityCenterSidebarCommandBridge() {}

    static boolean install() {
        if (installed) return true;
        HookUtil.InvocationResult<Object> appResult =
                HookUtil.tryInvokeActivityThreadCurrentApplication();
        Object application = appResult.succeeded() ? appResult.value() : null;
        if (!(application instanceof Context)) {
            SideSlideHoldDiagnostics.log(TAG + " no application context; fail closed"
                    + (appResult.failure() == null ? "" : " " + appResult.failure()));
            return false;
        }

        Context context = ((Context) application).getApplicationContext();
        if (context == null) context = (Context) application;
        appContext = context;
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(SidebarCommandContract.ACTION_PREPARE);
            filter.addAction(SidebarCommandContract.ACTION_SHOW);
            context.registerReceiver(RECEIVER, filter, Context.RECEIVER_EXPORTED);
            installed = true;
            bindVendorService(context);
            SideSlideHoldDiagnostics.log(TAG + " installed process=" + android.os.Process.myPid());
            return true;
        } catch (Throwable error) {
            installed = false;
            SideSlideHoldDiagnostics.log(TAG + " install failed: " + error);
            return false;
        }
    }

    private static void bindVendorService(Context context) {
        try {
            Intent intent = new Intent(SidebarCommandContract.SERVICE_ACTION)
                    .setComponent(new ComponentName(
                            SidebarCommandContract.SECURITY_CENTER_PACKAGE,
                            SidebarCommandContract.SERVICE_CLASS));
            boolean bound = context.bindService(intent, CONNECTION, Context.BIND_AUTO_CREATE);
            SideSlideHoldDiagnostics.log(TAG + " bindService requested result=" + bound);
            if (!bound) clearBinder("bindService returned false");
        } catch (Throwable error) {
            clearBinder("bindService failed: " + error);
        }
    }

    private static Method resolveShowMethod(Class<?> binderClass) {
        Method match = null;
        for (Method method : binderClass.getDeclaredMethods()) {
            if (method.isSynthetic() || method.getReturnType() != void.class) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 5) continue;
            boolean allInts = true;
            for (Class<?> param : params) {
                if (param != int.class) {
                    allInts = false;
                    break;
                }
            }
            if (!allInts) continue;
            if (match != null) {
                SideSlideHoldDiagnostics.log(TAG + " ambiguous five-int Sidebar methods: "
                        + match + " and " + method);
                return null;
            }
            match = method;
        }
        return match;
    }

    private static Method[] resolveStateMethods(Class<?> binderClass) {
        List<Method> matches = new ArrayList<>(2);
        for (Method method : binderClass.getDeclaredMethods()) {
            if (method.isSynthetic()) continue;
            if (method.getReturnType() != boolean.class) continue;
            if (method.getParameterTypes().length != 0) continue;
            matches.add(method);
        }
        if (matches.size() != 2) {
            SideSlideHoldDiagnostics.log(TAG + " expected two boolean() Sidebar state methods, found="
                    + matches.size());
            return null;
        }
        return matches.toArray(new Method[0]);
    }

    private static boolean vendorAvailableOrShowing() {
        IBinder binder = sidebarBinder;
        Method[] states = stateMethods;
        if (binder == null || states == null || !binder.isBinderAlive()) {
            SideSlideHoldDiagnostics.log(TAG + " prepare rejected: binder unavailable");
            Context context = appContext;
            if (context != null) bindVendorService(context);
            return false;
        }
        try {
            boolean any = false;
            for (Method state : states) {
                Object value = state.invoke(binder);
                SideSlideHoldDiagnostics.log(TAG + " state " + state.getName() + "=" + value);
                if (Boolean.TRUE.equals(value)) any = true;
            }
            return any;
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " state query failed: " + error);
            return false;
        }
    }

    private static boolean showSidebar(int x, int y, int width, int height, int radius) {
        IBinder binder = sidebarBinder;
        Method method = showMethod;
        if (binder == null || method == null || !binder.isBinderAlive()) {
            SideSlideHoldDiagnostics.log(TAG + " show rejected: binder unavailable");
            Context context = appContext;
            if (context != null) bindVendorService(context);
            return false;
        }
        if (width <= 0 || height <= 0 || radius < 0) {
            SideSlideHoldDiagnostics.log(TAG + " show rejected: invalid geometry "
                    + x + "," + y + " " + width + "x" + height + " r=" + radius);
            return false;
        }
        if (!vendorAvailableOrShowing()) {
            SideSlideHoldDiagnostics.log(TAG + " show rejected: vendor reports unavailable and not showing");
            return false;
        }
        try {
            method.invoke(binder, x, y, width, height, radius);
            SideSlideHoldDiagnostics.log(TAG + " show accepted x=" + x + " y=" + y
                    + " w=" + width + " h=" + height + " r=" + radius);
            return true;
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " show failed: " + error);
            return false;
        }
    }

    private static void clearBinder(String reason) {
        sidebarBinder = null;
        showMethod = null;
        stateMethods = null;
        SideSlideHoldDiagnostics.log(TAG + " not ready: " + reason);
    }
}
