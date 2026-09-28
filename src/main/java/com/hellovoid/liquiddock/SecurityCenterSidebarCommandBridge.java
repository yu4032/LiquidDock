package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.app.Service;
import android.os.IBinder;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Security Center :ui bridge to the exported DockWindowManagerService Sidebar endpoint.
 *
 * <p>OS4 decompilation confirms ISidebarOverlay exposes one five-int show method plus two
 * zero-argument boolean state methods (canShowNewDock and isNewDockShowing). The bridge keeps
 * structural resolution so R8 method names are not part of LiquidDock's compatibility contract.</p>
 */
final class SecurityCenterSidebarCommandBridge {
    private static final String TAG = "[DC][SidebarBridge]";

    private static volatile boolean installed;
    private static volatile Context appContext;
    private static volatile Object serviceOwner;
    private static volatile IBinder sidebarBinder;
    private static volatile Method showMethod;
    private static volatile Method[] availabilityMethods;

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
                Method show = resolveShowMethod(service.getClass());
                Method[] states = resolveBooleanStateMethods(service.getClass());
                if (show == null || states == null) {
                    clearBinder("vendor Sidebar contract unavailable or ambiguous");
                    return;
                }
                show.setAccessible(true);
                for (Method state : states) state.setAccessible(true);
                sidebarBinder = service;
                showMethod = show;
                availabilityMethods = states;
                SideSlideHoldDiagnostics.log(TAG + " ready descriptor=" + descriptor);
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
                boolean ready = vendorShowEndpointReady();
                if (ready) logVendorBooleanDiagnostics();
                setResultCode(ready
                        ? SidebarCommandContract.RESULT_READY
                        : SidebarCommandContract.RESULT_UNAVAILABLE);
                return;
            }
            if (SidebarCommandContract.ACTION_CONFIRM_START.equals(action)) {
                invokeNativeConfirmation(true);
                return;
            }
            if (SidebarCommandContract.ACTION_CONFIRM_END.equals(action)) {
                invokeNativeConfirmation(false);
                return;
            }
            if (!SidebarCommandContract.ACTION_SHOW.equals(action)) return;
            boolean accepted = showSidebar(
                    intent.getIntExtra(SidebarCommandContract.EXTRA_X, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_Y, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_WIDTH, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_HEIGHT, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_RADIUS, 0));
            setResultCode(accepted
                    ? SidebarCommandContract.RESULT_ACCEPTED
                    : SidebarCommandContract.RESULT_UNAVAILABLE);
        }
    };

    private SecurityCenterSidebarCommandBridge() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> serviceClass = Class.forName(
                    SecurityCenterHookSpec.BOOTSTRAP_SERVICE_CLASS, false, classLoader);
            Method onCreate = HookUtil.findMethodExact(
                    serviceClass, "onCreate", new Class<?>[0]);
            HookUtil.hook(onCreate, chain -> {
                Object service = chain.getThisObject();
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (service instanceof Context) {
                    ensureInstalled((Context) service);
                } else {
                    SideSlideHoldDiagnostics.log(TAG
                            + " DockWindowManagerService.onCreate owner is not Context");
                }
                return result;
            });
            installed = true;
            SideSlideHoldDiagnostics.log(TAG
                    + " lifecycle hook installed; waiting DockWindowManagerService.onCreate");
            return true;
        } catch (Throwable error) {
            installed = false;
            SideSlideHoldDiagnostics.log(TAG + " lifecycle hook install failed", error);
            return false;
        }
    }

    private static void ensureInstalled(Context source) {
        if (source == null || appContext != null) return;
        serviceOwner = source;
        Context context = source.getApplicationContext();
        if (context == null) context = source;
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(SidebarCommandContract.ACTION_PREPARE);
            filter.addAction(SidebarCommandContract.ACTION_SHOW);
            filter.addAction(SidebarCommandContract.ACTION_CONFIRM_START);
            filter.addAction(SidebarCommandContract.ACTION_CONFIRM_END);
            context.registerReceiver(RECEIVER, filter, Context.RECEIVER_EXPORTED);
            appContext = context;
            bindVendorService(context);
            SideSlideHoldDiagnostics.log(TAG
                    + " receiver ready from DockWindowManagerService.onCreate");
        } catch (Throwable error) {
            appContext = null;
            SideSlideHoldDiagnostics.log(TAG + " runtime registration failed", error);
        }
    }

    private static void bindVendorService(Context context) {
        try {
            Intent intent = new Intent(SidebarCommandContract.SERVICE_ACTION)
                    .setComponent(new ComponentName(
                            SidebarCommandContract.SECURITY_CENTER_PACKAGE,
                            SidebarCommandContract.SERVICE_CLASS));
            boolean bound = context.bindService(intent, CONNECTION, Context.BIND_AUTO_CREATE);
            SideSlideHoldDiagnostics.log(TAG + " bindService=" + bound);
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
            if (match != null) return null;
            match = method;
        }
        return match;
    }

    private static Method[] resolveBooleanStateMethods(Class<?> binderClass) {
        List<Method> matches = new ArrayList<>(2);
        for (Method method : binderClass.getDeclaredMethods()) {
            if (method.isSynthetic()) continue;
            if (method.getReturnType() != boolean.class) continue;
            if (method.getParameterTypes().length != 0) continue;
            matches.add(method);
        }
        return matches.size() == 2 ? matches.toArray(new Method[0]) : null;
    }

    private static void invokeNativeConfirmation(boolean widen) {
        Object service = serviceOwner;
        if (service == null) {
            SideSlideHoldDiagnostics.log(TAG + " native confirmation unavailable: no service");
            return;
        }
        try {
            Object manager = resolveDockWindowManager(service);
            Object wrapper = manager != null ? resolveAttachedSidebarWrapper(manager) : null;
            if (wrapper == null) {
                SideSlideHoldDiagnostics.log(TAG
                        + " native confirmation unavailable: no attached SidebarWrapper");
                return;
            }

            // OS4 decompilation: SidebarWrapper.U() -> widenSidebarLine(),
            // SidebarWrapper.R() -> narrowSidebarLine(). Resolve only after strong structural
            // validation of the live attached com.miui.dock.sidebar wrapper.
            if (widen) {
                // OS4 SidebarTouchListener.onLongClick():
                // DockWindowManager.L0(true, true) -> haptic -> SidebarWrapper.U().
                Method activate = manager.getClass().getMethod(
                        "L0", boolean.class, boolean.class);
                Method widenMethod = wrapper.getClass().getMethod("U");
                if (activate.getReturnType() != void.class
                        || widenMethod.getReturnType() != void.class) {
                    SideSlideHoldDiagnostics.log(TAG
                            + " native confirmation start signature mismatch");
                    return;
                }
                activate.invoke(manager, true, true);
                widenMethod.invoke(wrapper);
                SideSlideHoldDiagnostics.log(TAG
                        + " native Sidebar confirmation activate+widen");
            } else {
                // OS4 long-click ACTION_UP visual cleanup:
                // SidebarWrapper.D() -> R() -> DockWindowManager.e3() -> Q1(wrapper).
                Method hideMoving = wrapper.getClass().getMethod("D");
                Method narrow = wrapper.getClass().getMethod("R");
                Method updateAssistant = manager.getClass().getMethod("e3");
                Method removePassDown = manager.getClass().getMethod(
                        "Q1", wrapper.getClass());
                hideMoving.invoke(wrapper);
                narrow.invoke(wrapper);
                updateAssistant.invoke(manager);
                removePassDown.invoke(manager, wrapper);
                SideSlideHoldDiagnostics.log(TAG
                        + " native Sidebar confirmation cleanup+narrow");
            }
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " native confirmation failed", error);
        }
    }

    private static Object resolveDockWindowManager(Object service) throws IllegalAccessException {
        for (Field field : service.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object candidate = field.get(service);
            if (candidate == null || candidate instanceof IBinder) continue;
            if (hasFiveIntVoidMethod(candidate.getClass())
                    && hasSidebarWrapperReturn(candidate.getClass())) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasFiveIntVoidMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.getReturnType() != void.class) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 5) continue;
            boolean allInts = true;
            for (Class<?> param : params) {
                if (param != int.class) {
                    allInts = false;
                    break;
                }
            }
            if (allInts) return true;
        }
        return false;
    }

    private static boolean hasSidebarWrapperReturn(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.getParameterTypes().length != 0) continue;
            String name = method.getReturnType().getName();
            if (name.startsWith("com.miui.dock.sidebar.")) return true;
        }
        return false;
    }

    private static Object resolveAttachedSidebarWrapper(Object manager)
            throws ReflectiveOperationException {
        for (Field field : manager.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object candidate = field.get(manager);
            if (candidate == null
                    || !candidate.getClass().getName().startsWith("com.miui.dock.sidebar.")) {
                continue;
            }
            View line = resolveSidebarLineView(candidate);
            if (line != null && (line.isAttachedToWindow() || line.getWindowToken() != null)) {
                return candidate;
            }
        }
        return null;
    }

    private static View resolveSidebarLineView(Object wrapper)
            throws ReflectiveOperationException {
        for (Method method : wrapper.getClass().getMethods()) {
            if (method.getParameterTypes().length != 0) continue;
            if (!View.class.isAssignableFrom(method.getReturnType())) continue;
            if (!"com.miui.dock.sidebar.RegionSamplingImageView"
                    .equals(method.getReturnType().getName())) {
                continue;
            }
            Object value = method.invoke(wrapper);
            return value instanceof View ? (View) value : null;
        }
        return null;
    }

    private static boolean vendorShowEndpointReady() {
        IBinder binder = sidebarBinder;
        Method show = showMethod;
        if (binder == null || show == null || !binder.isBinderAlive()) {
            Context context = appContext;
            if (context != null) bindVendorService(context);
            return false;
        }
        return true;
    }

    private static void logVendorBooleanDiagnostics() {
        IBinder binder = sidebarBinder;
        Method[] states = availabilityMethods;
        if (binder == null || states == null || !binder.isBinderAlive()) return;
        try {
            boolean first = Boolean.TRUE.equals(states[0].invoke(binder));
            boolean second = Boolean.TRUE.equals(states[1].invoke(binder));
            SideSlideHoldDiagnostics.log(TAG
                    + " vendor boolean diagnostics first=" + first + " second=" + second);
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " vendor boolean diagnostics failed", error);
        }
    }

    private static boolean showSidebar(int x, int y, int width, int height, int radius) {
        IBinder binder = sidebarBinder;
        Method show = showMethod;
        if (binder == null || show == null || !binder.isBinderAlive()) {
            Context context = appContext;
            if (context != null) bindVendorService(context);
            return false;
        }
        if (width <= 0 || height <= 0 || radius < 0) return false;
        if (!vendorShowEndpointReady()) {
            SideSlideHoldDiagnostics.log(TAG + " vendor show endpoint not ready");
            return false;
        }
        try {
            show.invoke(binder, x, y, width, height, radius);
            SideSlideHoldDiagnostics.log(TAG + " vendor show accepted geometry="
                    + x + "," + y + " " + width + "x" + height + " r=" + radius);
            return true;
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " vendor show failed", error);
            return false;
        }
    }

    private static void clearBinder(String reason) {
        sidebarBinder = null;
        showMethod = null;
        availabilityMethods = null;
        SideSlideHoldDiagnostics.log(TAG + " not ready: " + reason);
    }
}
