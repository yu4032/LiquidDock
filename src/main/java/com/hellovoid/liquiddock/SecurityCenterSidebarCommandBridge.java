package com.hellovoid.liquiddock;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.app.Service;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
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
    private static volatile boolean turboTouchReleaseHookInstalled;
    private static volatile boolean sidebarRootClickReleaseHookInstalled;
    private static volatile boolean windowLayoutPassDownGuardHookInstalled;
    private static volatile View releasedSidebarWindowRoot;
    private static final int MIUI_FLAG_CLICK_PASS_DOWN = 0x20;
    private static final String TURBO_LAYOUT_CLASS =
            "com.miui.gamebooster.windowmanager.newbox.TurboLayout";
    private static final String REGION_SAMPLING_IMAGE_VIEW_CLASS =
            "com.miui.dock.sidebar.RegionSamplingImageView";
    private static volatile Object vendorAnimationCallback;
    private static volatile Method vendorAnimationCallbackRegisterMethod;
    private static volatile int pendingLauncherGeneration = Integer.MIN_VALUE;

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
                registerVendorAnimationCallback(service);
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
                    intent.getIntExtra(
                            SidebarCommandContract.EXTRA_GENERATION,
                            Integer.MIN_VALUE));
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
            installTurboTouchReleaseHook(source.getClassLoader());
            installSidebarRootClickReleaseHook(source.getClassLoader());
            installWindowLayoutPassDownGuardHook(source.getClassLoader());
            SecurityCenterVideoLauncherHandoffGeometry.install(source.getClassLoader());
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

    /**
     * Restores touch pass-through when the stable Security Center TurboLayout becomes hidden.
     *
     * <p>OS4's native sidebar window uses MIUI window flag 0x20 as the click-pass-down bit.
     * showNewDockFromLauncher removes that bit while the panel is interactive. The native exit
     * animation finishes by setting TurboLayout INVISIBLE/GONE. Hooking that stable lifecycle
     * boundary avoids any dependency on R8-obfuscated DockWindowManager method names.</p>
     */
    private static void installTurboTouchReleaseHook(ClassLoader classLoader) {
        if (turboTouchReleaseHookInstalled || classLoader == null) return;
        try {
            Class<?> turboLayoutClass = Class.forName(
                    TURBO_LAYOUT_CLASS, false, classLoader);
            Method setVisibility = HookUtil.findMethodExact(
                    View.class, "setVisibility", new Class<?>[]{int.class});
            HookUtil.hook(setVisibility, chain -> {
                Object owner = chain.getThisObject();
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (!turboLayoutClass.isInstance(owner)) return result;

                Object[] args = chain.getArgs().toArray(new Object[0]);
                int visibility = args.length == 1 && args[0] instanceof Number
                        ? ((Number) args[0]).intValue()
                        : View.VISIBLE;
                if (visibility != View.VISIBLE && owner instanceof View) {
                    restoreClickPassDown((View) owner);
                }
                return result;
            });
            turboTouchReleaseHookInstalled = true;
            SideSlideHoldDiagnostics.log(TAG
                    + " TurboLayout touch-release hook installed");
        } catch (Throwable error) {
            turboTouchReleaseHookInstalled = false;
            SideSlideHoldDiagnostics.log(TAG
                    + " TurboLayout touch-release hook install failed", error);
        }
    }

    /**
     * Releases the Sidebar window as soon as its stable outside-click root handles a click.
     *
     * <p>The normal outside-tap exit is dispatched to the top-level container rather than to
     * TurboLayout itself, so TurboLayout visibility may remain unchanged while the exit animation
     * runs. We identify that root structurally by the presence of a TurboLayout descendant and
     * restore click pass-down after the native click handler has started its dismissal.</p>
     */
    private static void installSidebarRootClickReleaseHook(ClassLoader classLoader) {
        if (sidebarRootClickReleaseHookInstalled || classLoader == null) return;
        try {
            Class<?> turboLayoutClass = Class.forName(
                    TURBO_LAYOUT_CLASS, false, classLoader);
            Method performClick = HookUtil.findMethodExact(
                    View.class, "performClick", new Class<?>[0]);
            HookUtil.hook(performClick, chain -> {
                Object owner = chain.getThisObject();
                boolean sidebarRoot = owner instanceof ViewGroup
                        && containsTurboLayout((ViewGroup) owner, turboLayoutClass);
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (sidebarRoot) {
                    restoreClickPassDown((View) owner);
                }
                return result;
            });
            sidebarRootClickReleaseHookInstalled = true;
            SideSlideHoldDiagnostics.log(TAG
                    + " Sidebar root outside-click touch-release hook installed");
        } catch (Throwable error) {
            sidebarRootClickReleaseHookInstalled = false;
            SideSlideHoldDiagnostics.log(TAG
                    + " Sidebar root outside-click hook install failed", error);
        }
    }

    private static boolean containsTurboLayout(
            ViewGroup root, Class<?> turboLayoutClass) {
        if (root == null || turboLayoutClass == null) return false;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (turboLayoutClass.isInstance(child)) return true;
            if (child instanceof ViewGroup
                    && containsTurboLayout((ViewGroup) child, turboLayoutClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the released Sidebar window pass-through across Security Center animation-time
     * updateViewLayout calls. The native exit path may rewrite the same LayoutParams after the
     * outside-click handler returns; without this guard that stale write clears bit 0x20 again.
     */
    private static void installWindowLayoutPassDownGuardHook(ClassLoader classLoader) {
        if (windowLayoutPassDownGuardHookInstalled) return;
        try {
            Class<?> windowManagerImpl = Class.forName(
                    "android.view.WindowManagerImpl", false, classLoader);
            Method updateViewLayout = HookUtil.findMethodExact(
                    windowManagerImpl,
                    "updateViewLayout",
                    new Class<?>[]{View.class, ViewGroup.LayoutParams.class});
            HookUtil.hook(updateViewLayout, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length >= 2
                        && args[0] == releasedSidebarWindowRoot
                        && args[1] instanceof WindowManager.LayoutParams) {
                    ensureClickPassDownBit((WindowManager.LayoutParams) args[1]);
                    SideSlideHoldDiagnostics.log(TAG
                            + " preserved Sidebar click-pass-down across updateViewLayout");
                }
                return chain.proceed(args);
            });
            windowLayoutPassDownGuardHookInstalled = true;
            SideSlideHoldDiagnostics.log(TAG
                    + " Sidebar updateViewLayout pass-down guard installed");
        } catch (Throwable error) {
            windowLayoutPassDownGuardHookInstalled = false;
            SideSlideHoldDiagnostics.log(TAG
                    + " Sidebar updateViewLayout pass-down guard install failed", error);
        }
    }

    private static void ensureClickPassDownBit(WindowManager.LayoutParams layoutParams)
            throws ReflectiveOperationException {
        Field miuiFlags = WindowManager.LayoutParams.class.getDeclaredField("miuiFlags");
        miuiFlags.setAccessible(true);
        int flags = miuiFlags.getInt(layoutParams);
        if ((flags & MIUI_FLAG_CLICK_PASS_DOWN) == 0) {
            miuiFlags.setInt(layoutParams, flags | MIUI_FLAG_CLICK_PASS_DOWN);
        }
    }

    private static void restoreClickPassDown(View turboLayout) {
        if (turboLayout == null) return;
        try {
            View windowRoot = turboLayout;
            ViewParent parent = turboLayout.getParent();
            while (parent instanceof View) {
                windowRoot = (View) parent;
                parent = parent.getParent();
            }

            Object params = windowRoot.getLayoutParams();
            if (!(params instanceof WindowManager.LayoutParams)) {
                SideSlideHoldDiagnostics.log(TAG
                        + " touch release skipped: window LayoutParams unavailable");
                return;
            }
            WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) params;
            ensureClickPassDownBit(layoutParams);
            releasedSidebarWindowRoot = windowRoot;
            WindowManager windowManager =
                    (WindowManager) windowRoot.getContext().getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null && windowRoot.isAttachedToWindow()) {
                windowManager.updateViewLayout(windowRoot, layoutParams);
            }
            SideSlideHoldDiagnostics.log(TAG
                    + " restored Sidebar click-pass-down and armed layout guard");
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG
                    + " restore Sidebar click-pass-down failed", error);
        }
    }

    private static void registerVendorAnimationCallback(IBinder service)
            throws ReflectiveOperationException {
        ClassLoader loader = service.getClass().getClassLoader();
        Class<?> callbackInterface = Class.forName(
                SidebarCommandContract.SIDEBAR_ANIM_CALLBACK_DESCRIPTOR,
                false,
                loader);
        Method register = resolveAnimationCallbackRegisterMethod(
                service.getClass(), callbackInterface);
        if (register == null) {
            throw new NoSuchMethodException(
                    "unique ISidebarAnimCallback registration method unavailable");
        }
        register.setAccessible(true);

        VendorAnimationCallbackBinder callbackBinder =
                new VendorAnimationCallbackBinder();
        Object callback = Proxy.newProxyInstance(
                loader,
                new Class<?>[]{callbackInterface},
                (proxy, method, args) -> {
                    if ("asBinder".equals(method.getName())
                            && method.getParameterTypes().length == 0) {
                        return callbackBinder;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 1
                            && params[0] == int.class
                            && method.getReturnType() == void.class) {
                        int state = args != null && args.length == 1
                                ? ((Number) args[0]).intValue()
                                : Integer.MIN_VALUE;
                        onVendorAnimationCallback(state);
                        return null;
                    }
                    if ("toString".equals(method.getName())
                            && method.getParameterTypes().length == 0) {
                        return "LiquidDockSidebarAnimCallback";
                    }
                    if ("hashCode".equals(method.getName())
                            && method.getParameterTypes().length == 0) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())
                            && method.getParameterTypes().length == 1) {
                        return proxy == args[0];
                    }
                    return null;
                });
        callbackBinder.setOwner(callback);
        register.invoke(service, callback);
        vendorAnimationCallback = callback;
        vendorAnimationCallbackRegisterMethod = register;
        SideSlideHoldDiagnostics.log(TAG
                + " vendor animation callback registered method=" + register.getName());
    }

    private static Method resolveAnimationCallbackRegisterMethod(
            Class<?> binderClass,
            Class<?> callbackInterface) {
        Method match = null;
        for (Method method : binderClass.getDeclaredMethods()) {
            if (method.isSynthetic() || method.getReturnType() != void.class) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || params[0] != callbackInterface) continue;
            if (match != null) return null;
            match = method;
        }
        return match;
    }

    private static void onVendorAnimationCallback(int state) {
        SideSlideHoldDiagnostics.log(TAG + " vendor animation callback state=" + state);
        if (state != 0) return;
        int generation = pendingLauncherGeneration;
        if (generation == Integer.MIN_VALUE) return;
        pendingLauncherGeneration = Integer.MIN_VALUE;
        Context context = appContext;
        if (context == null) return;
        try {
            Intent handoff = new Intent(SidebarCommandContract.ACTION_VENDOR_ANIM_STARTED)
                    .setPackage(SidebarCommandContract.LAUNCHER_PACKAGE)
                    .putExtra(SidebarCommandContract.EXTRA_GENERATION, generation);
            context.sendBroadcast(handoff);
            SideSlideHoldDiagnostics.log(TAG
                    + " vendor Sidebar animation started -> Launcher handoff generation="
                    + generation);
        } catch (Throwable error) {
            SideSlideHoldDiagnostics.log(TAG + " vendor animation handoff broadcast failed", error);
        }
    }

    private static final class VendorAnimationCallbackBinder extends Binder {
        private volatile Object owner;

        VendorAnimationCallbackBinder() {
            attachInterface(null, SidebarCommandContract.SIDEBAR_ANIM_CALLBACK_DESCRIPTOR);
        }

        void setOwner(Object owner) {
            this.owner = owner;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) {
                    reply.writeString(SidebarCommandContract.SIDEBAR_ANIM_CALLBACK_DESCRIPTOR);
                }
                return true;
            }
            if (code == FIRST_CALL_TRANSACTION) {
                data.enforceInterface(SidebarCommandContract.SIDEBAR_ANIM_CALLBACK_DESCRIPTOR);
                onVendorAnimationCallback(data.readInt());
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
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
            if (candidate == null || !isSidebarWrapperType(candidate.getClass())) continue;
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
            if (isSidebarWrapperType(method.getReturnType())) return true;
        }
        return false;
    }

    /**
     * Resolves the Sidebar wrapper by stable capabilities only. The wrapper class itself is
     * R8-obfuscated and is deliberately never named or package-matched.
     */
    private static boolean isSidebarWrapperType(Class<?> type) {
        if (type == null || type == void.class || type.isPrimitive() || type == Object.class) {
            return false;
        }
        boolean hasLine = false;
        boolean hasTurboLayout = false;
        for (Method method : type.getMethods()) {
            if (method.isSynthetic() || method.getParameterTypes().length != 0) continue;
            String returnType = method.getReturnType().getName();
            if (REGION_SAMPLING_IMAGE_VIEW_CLASS.equals(returnType)) {
                hasLine = true;
            } else if (TURBO_LAYOUT_CLASS.equals(returnType)) {
                hasTurboLayout = true;
            }
        }
        return hasLine && hasTurboLayout;
    }

    private static Object resolveAttachedSidebarWrapper(Object manager)
            throws ReflectiveOperationException {
        for (Field field : manager.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object candidate = field.get(manager);
            if (candidate == null || !isSidebarWrapperType(candidate.getClass())) continue;
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
            if (!REGION_SAMPLING_IMAGE_VIEW_CLASS.equals(method.getReturnType().getName())) {
                continue;
            }
            Object value = method.invoke(wrapper);
            return value instanceof View ? (View) value : null;
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
            int x,
            int y,
            int width,
            int height,
            int radius,
            int generation) {
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
            releasedSidebarWindowRoot = null;
            pendingLauncherGeneration = generation;
            SecurityCenterVideoLauncherHandoffGeometry.arm(
                    generation, x, y, width, height);
            show.invoke(binder, x, y, width, height, radius);
            SideSlideHoldDiagnostics.log(TAG + " vendor show accepted geometry="
                    + x + "," + y + " " + width + "x" + height + " r=" + radius);
            return true;
        } catch (Throwable error) {
            if (pendingLauncherGeneration == generation) {
                pendingLauncherGeneration = Integer.MIN_VALUE;
            }
            SecurityCenterVideoLauncherHandoffGeometry.disarm("vendor show failed");
            SideSlideHoldDiagnostics.log(TAG + " vendor show failed", error);
            return false;
        }
    }

    private static void clearBinder(String reason) {
        sidebarBinder = null;
        showMethod = null;
        availabilityMethods = null;
        vendorAnimationCallback = null;
        vendorAnimationCallbackRegisterMethod = null;
        pendingLauncherGeneration = Integer.MIN_VALUE;
        SecurityCenterVideoLauncherHandoffGeometry.disarm(reason);
        SideSlideHoldDiagnostics.log(TAG + " not ready: " + reason);
    }
}
