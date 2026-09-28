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
    private static volatile Method vendorShowEntryMethod;
    private static volatile boolean vendorShowProbeInstalled;

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
                boolean desktop = intent.getBooleanExtra(
                        SidebarCommandContract.EXTRA_DESKTOP, false);
                if (desktop) {
                    ensureDesktopDockContext();
                }
                boolean ready = vendorShowEndpointReady();
                if (ready) logVendorBooleanDiagnostics();
                setResultCode(ready
                        ? SidebarCommandContract.RESULT_READY
                        : SidebarCommandContract.RESULT_UNAVAILABLE);
                return;
            }
            if (SidebarCommandContract.ACTION_CONFIRM_START.equals(action)) {
                boolean desktop = intent.getBooleanExtra(
                        SidebarCommandContract.EXTRA_DESKTOP, false);
                if (desktop) ensureDesktopDockContext();
                performNativeConfirmationHaptic();
                return;
            }
            if (SidebarCommandContract.ACTION_CONFIRM_END.equals(action)) {
                return;
            }
            if (!SidebarCommandContract.ACTION_SHOW.equals(action)) return;
            boolean accepted = showSidebar(
                    intent.getIntExtra(SidebarCommandContract.EXTRA_X, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_Y, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_WIDTH, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_HEIGHT, 0),
                    intent.getIntExtra(SidebarCommandContract.EXTRA_RADIUS, 0),
                    intent.getBooleanExtra(SidebarCommandContract.EXTRA_DESKTOP, false));
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
            installVendorShowProbe(source);
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

    private static void installVendorShowProbe(Object service) {
        if (vendorShowProbeInstalled || service == null) return;
        try {
            Object manager = resolveDockWindowManager(service);
            if (manager == null) {
                SideSlideHoldDiagnostics.log(TAG
                        + " vendor show probe unavailable: manager unresolved");
                return;
            }
            Method entry = resolveManagerFiveIntShowMethod(manager.getClass());
            if (entry == null) {
                SideSlideHoldDiagnostics.log(TAG
                        + " vendor show probe unavailable: five-int entry ambiguous");
                return;
            }
            vendorShowEntryMethod = entry;
            HookUtil.hook(entry, chain -> {
                logVendorShowEntry(chain.getThisObject(), chain.getArgs().toArray(new Object[0]));
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            vendorShowProbeInstalled = true;
            SideSlideHoldDiagnostics.log(TAG
                    + " vendor show probe installed method=" + entry.getName());
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " vendor show probe install failed", error);
        }
    }

    private static Method resolveManagerFiveIntShowMethod(Class<?> type) {
        Method match = null;
        for (Method method : type.getDeclaredMethods()) {
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
            method.setAccessible(true);
            match = method;
        }
        return match;
    }

    private static void logVendorShowEntry(Object manager, Object[] args) {
        int dockType = -1;
        boolean wrapperPresent = false;
        boolean wrapperAttached = false;
        int lineVisibility = -1;
        boolean lineActive = false;
        try {
            Object dockState = resolveDockState(manager);
            if (dockState != null) {
                for (Method method : dockState.getClass().getMethods()) {
                    if (method.getParameterTypes().length != 0
                            || method.getReturnType() != int.class) continue;
                    int value = ((Number) method.invoke(dockState)).intValue();
                    if (value == 0 || value == 1 || value == 3
                            || value == 4 || value == 5) {
                        if (value == 4) {
                            dockType = 4;
                            break;
                        }
                        if (dockType == -1) dockType = value;
                    }
                }
            }
            Object wrapper = resolveMainSidebarWrapper(manager);
            wrapperPresent = wrapper != null;
            View line = wrapper != null ? resolveSidebarLineView(wrapper) : null;
            if (line != null) {
                wrapperAttached = line.isAttachedToWindow() || line.getWindowToken() != null;
                lineVisibility = line.getVisibility();
                try {
                    Method isActive = line.getClass().getMethod("isActive");
                    lineActive = Boolean.TRUE.equals(isActive.invoke(line));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " vendor show entry diagnostics failed", error);
        }

        SideSlideHoldDiagnostics.log(TAG
                + " VENDOR_SHOW_ENTRY dockType=" + dockType
                + " wrapper=" + wrapperPresent
                + " attached=" + wrapperAttached
                + " lineVisibility=" + lineVisibility
                + " lineActive=" + lineActive
                + " geometry=" + formatGeometry(args));
    }

    private static String formatGeometry(Object[] args) {
        if (args == null || args.length != 5) return "invalid";
        return String.valueOf(args[0]) + "," + args[1] + " "
                + args[2] + "x" + args[3] + " r=" + args[4];
    }

    private static Object resolveMainSidebarWrapper(Object manager)
            throws ReflectiveOperationException {
        Object attached = resolveAttachedSidebarWrapper(manager);
        if (attached != null) return attached;

        Object unique = null;
        for (Field field : manager.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object candidate = field.get(manager);
            if (candidate == null
                    || !candidate.getClass().getName().startsWith("com.miui.dock.sidebar.")) {
                continue;
            }
            View line = resolveSidebarLineView(candidate);
            if (line == null) continue;
            if (unique != null) return null;
            unique = candidate;
        }
        return unique;
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

    private static void performNativeConfirmationHaptic() {
        Object service = serviceOwner;
        if (service == null) {
            SideSlideHoldDiagnostics.log(TAG + " confirmation haptic unavailable: no service");
            return;
        }
        try {
            Object manager = resolveDockWindowManager(service);
            Object wrapper = manager != null ? resolveMainSidebarWrapper(manager) : null;
            View line = wrapper != null ? resolveSidebarLineView(wrapper) : null;
            if (line != null) {
                line.performHapticFeedback(0);
                SideSlideHoldDiagnostics.log(TAG + " native confirmation haptic");
            } else {
                SideSlideHoldDiagnostics.log(TAG
                        + " confirmation haptic unavailable: Sidebar line unresolved");
            }
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " confirmation haptic failed", error);
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

    private static void ensureDesktopDockContext() {
        Object service = serviceOwner;
        if (service == null) return;
        try {
            Object manager = resolveDockWindowManager(service);
            if (manager == null) {
                SideSlideHoldDiagnostics.log(TAG
                        + " desktop dock context unavailable: manager unresolved");
                return;
            }

            Object dockState = resolveDockState(manager);
            if (dockState == null) {
                SideSlideHoldDiagnostics.log(TAG
                        + " desktop dock context unavailable: dock state unresolved");
                return;
            }
            Method setType = resolveUniqueVoidIntMethod(dockState.getClass());
            if (setType == null) {
                SideSlideHoldDiagnostics.log(TAG
                        + " desktop dock context unavailable: dock type setter ambiguous");
                return;
            }
            setType.invoke(dockState, 4);

            Object wrapper = resolveMainSidebarWrapper(manager);
            if (wrapper == null) {
                wrapper = prepareMainSidebarWrapper(manager);
            }
            SideSlideHoldDiagnostics.log(TAG
                    + " desktop dock context type4 ready state=" + dockState
                    + " wrapperReady=" + (wrapper != null));
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG
                    + " desktop dock context setup failed", error);
        }
    }

    private static Object prepareMainSidebarWrapper(Object manager)
            throws ReflectiveOperationException {
        Method candidate = null;
        for (Method method : manager.getClass().getMethods()) {
            if (method.isSynthetic() || method.getParameterTypes().length != 0) continue;
            if (!method.getReturnType().getName().startsWith("com.miui.dock.sidebar.")) {
                continue;
            }
            if (candidate != null) {
                SideSlideHoldDiagnostics.log(TAG
                        + " prepare SidebarWrapper entry ambiguous; fail open");
                return null;
            }
            candidate = method;
        }
        if (candidate == null) {
            SideSlideHoldDiagnostics.log(TAG
                    + " prepare SidebarWrapper entry unavailable");
            return null;
        }
        Object wrapper = candidate.invoke(manager);
        if (wrapper != null && resolveSidebarLineView(wrapper) != null) {
            SideSlideHoldDiagnostics.log(TAG + " main SidebarWrapper prepared");
            return wrapper;
        }
        return null;
    }

    private static Object resolveDockState(Object manager) throws IllegalAccessException {
        Object match = null;
        for (Field field : manager.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object candidate = field.get(manager);
            if (candidate == null) continue;

            String stateText;
            try {
                stateText = String.valueOf(candidate);
            } catch (Throwable ignored) {
                continue;
            }
            if (!stateText.startsWith("DockWindowType{")
                    || !stateText.contains("dockType=")
                    || !stateText.contains("lastType=")) {
                continue;
            }
            if (resolveUniqueVoidIntMethod(candidate.getClass()) == null) continue;

            if (match != null) {
                SideSlideHoldDiagnostics.log(TAG
                        + " dock state semantic match ambiguous");
                return null;
            }
            match = candidate;
        }
        return match;
    }

    private static Method resolveUniqueVoidIntMethod(Class<?> type) {
        Method match = null;
        for (Method method : type.getMethods()) {
            if (method.isSynthetic() || method.getReturnType() != void.class) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || params[0] != int.class) continue;
            if (match != null) return null;
            match = method;
        }
        return match;
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

    private static boolean showSidebar(
            int x, int y, int width, int height, int radius, boolean desktop) {
        if (desktop) ensureDesktopDockContext();
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
