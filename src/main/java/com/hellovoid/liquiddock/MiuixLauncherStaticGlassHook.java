package com.hellovoid.liquiddock;

import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds ShortcutIcon and widget hosts to the one root-wide static Launcher glass compositor. */
final class MiuixLauncherStaticGlassHook {
    private static final String TAG = "[DC][StaticGlassHook]";
    private static final int MAX_BIND_ATTEMPTS = 8;
    private static final Map<View, View.OnAttachStateChangeListener> BOOTSTRAP_OBSERVERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private MiuixLauncherStaticGlassHook() {}

    static void onRuntimeGlassDisabled() {
        for (View host : new ArrayList<>(BOOTSTRAP_OBSERVERS.keySet())) {
            if (isWidgetHost(host)) {
                LauncherWidgetDarkContentAdapter.release(host);
                LauncherWidgetBackgroundController.release(host);
            }
            View.OnAttachStateChangeListener listener = BOOTSTRAP_OBSERVERS.remove(host);
            if (listener != null) host.removeOnAttachStateChangeListener(listener);
            DockGlassItemRegistry.unregister(host);
            LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
            if (node != null) node.dispose();
        }
        BOOTSTRAP_OBSERVERS.clear();
    }

    static void onRuntimeIconGlassDisabled() {
        for (View host : new ArrayList<>(BOOTSTRAP_OBSERVERS.keySet())) {
            if (!isIconHost(host)) continue;
            DockGlassItemRegistry.unregister(host);
            LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
            if (node != null && node.kind() == LauncherGlassDragState.Kind.ICON) node.dispose();
        }
    }

    static void onRuntimeWidgetGlassDisabled() {
        for (View host : new ArrayList<>(BOOTSTRAP_OBSERVERS.keySet())) {
            if (!isWidgetHost(host)) continue;
            LauncherWidgetDarkContentAdapter.release(host);
            LauncherWidgetBackgroundController.release(host);
            DockGlassItemRegistry.unregister(host);
            LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
            if (node != null && node.kind() == LauncherGlassDragState.Kind.WIDGET) node.dispose();
        }
    }

    static void onRuntimeWidgetDarkContentChanged(boolean enabled) {
        for (View host : new ArrayList<>(BOOTSTRAP_OBSERVERS.keySet())) {
            if (!isWidgetHost(host)) continue;
            if (enabled && host.isAttachedToWindow()
                    && LauncherGlassHierarchy.isWorkspace(host)) {
                LauncherWidgetDarkContentAdapter.apply(host);
            } else {
                LauncherWidgetDarkContentAdapter.release(host);
            }
        }
    }

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        boolean any = false;
        any |= installHostClass(classLoader, "com.miui.home.launcher.ShortcutIcon",
                LauncherGlassDragState.Kind.ICON, glassConfig);
        any |= installHostClass(classLoader, "com.miui.home.launcher.LauncherAppWidgetHostView",
                LauncherGlassDragState.Kind.WIDGET, glassConfig);
        any |= installHostClass(classLoader, "com.miui.home.launcher.maml.MaMlHostView",
                LauncherGlassDragState.Kind.WIDGET, glassConfig);
        installWidgetBackgroundOwnershipHook(classLoader, glassConfig);
        installMamlBackgroundOwnershipHooks(classLoader, glassConfig);
        installed = any;
        if (any) {
            installWorkspacePageReconcileHook(classLoader, glassConfig);
            installWorkspaceResumeReconcileHook(classLoader, glassConfig);
            installShortcutIconVisualOwnerHook(classLoader, glassConfig);
            installFloatingProxyVisualGeometryHooks(classLoader);
            MainHook.log(TAG + " widget/icon static glass hooks installed");
        }
        return any;
    }

    private static void installWorkspacePageReconcileHook(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.Workspace", "setCurrentScreenInner",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = chain.getThisObject();
                        if (owner instanceof View) {
                            View workspace = (View) owner;
                            // The vendor has committed mCurrentScreen/current screenId here. Wait one
                            // animation turn for the selected CellLayout's child transforms to settle.
                            workspace.postOnAnimation(() ->
                                    reconcileCurrentWorkspacePage(workspace, glassConfig));
                        }
                        return result;
                    }, int.class);
            MainHook.log(TAG + " Workspace current-page reconcile hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Workspace page reconcile hook unavailable: " + error);
        }
    }

    private static void reconcileCurrentWorkspacePage(
            View workspace, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || workspace == null
                || !workspace.isAttachedToWindow()) return;
        Object current = HookUtil.invoke(workspace, "getCurrentCellLayout");
        if (!(current instanceof View)) {
            MainHook.log(TAG + " current Workspace page reconcile skipped: CellLayout unavailable");
            return;
        }
        int visited = reconcileStaticSubtree((View) current, glassConfig);
        MainHook.log(TAG + " current Workspace page reconciled views=" + visited);
    }

    private static int reconcileStaticSubtree(View view, LiquidDockConfig.Glass glassConfig) {
        if (view == null) return 0;
        reconcileExistingHost(view, glassConfig);
        int visited = 1;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                visited += reconcileStaticSubtree(group.getChildAt(i), glassConfig);
            }
        }
        return visited;
    }

    private static void installShortcutIconVisualOwnerHook(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    "setAnimTargetVisibility",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        if (!GlassRuntimeState.isIconEnabled()) return result;
                        Object owner = chain.getThisObject();
                        if (!(owner instanceof View) || args.length == 0
                                || !(args[0] instanceof Number)) return result;
                        View host = (View) owner;
                        if (!LauncherGlassHierarchy.isWorkspace(host)) return result;
                        int visibility = ((Number) args[0]).intValue();
                        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
                        if (visibility == View.VISIBLE && node != null) {
                            node.endLaunchProxy();
                        }
                        return result;
                    }, int.class);
            MainHook.log(TAG + " ShortcutIcon launch-proxy ownership hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " ShortcutIcon launch-proxy ownership hook unavailable: " + error);
        }
    }

    private static void installFloatingProxyVisualGeometryHooks(ClassLoader classLoader) {
        installFloatingProxyVisualGeometryHook(classLoader,
                "com.miui.home.recents.views.FloatingIconView2", false);
        installFloatingProxyVisualGeometryHook(classLoader,
                "com.miui.home.recents.views.FloatingIconLayer2", true);
    }

    private static void installFloatingProxyVisualGeometryHook(
            ClassLoader classLoader, String className, boolean useRotationRect) {
        try {
            HookUtil.hookMethod(classLoader, className, "update",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        // Launcher 4.50 WindowElement and FastLaunchWindowElement both pass
                        // (animType != CLOSE_TO_HOME) as the second boolean in this overload.
                        if (GlassRuntimeState.isIconEnabled()
                                && args.length == 10 && args[0] instanceof RectF
                                && args[1] instanceof RectF && args[6] instanceof Boolean
                                && !((Boolean) args[6])) {
                            Object owner = chain.getThisObject();
                            Object target = HookUtil.invoke(owner, "getAnimTarget");
                            if (target instanceof View
                                    && LauncherGlassHierarchy.isWorkspace((View) target)) {
                                LauncherGlassStaticNode node =
                                        LauncherGlassStaticNode.find((View) target);
                                if (node != null && args[2] instanceof Number) {
                                    float proxyAlpha = ((Number) args[2]).floatValue();
                                    boolean drawIcon;
                                    if (useRotationRect) {
                                        // FloatingIconLayer2.isDrawIcon() returns true unconditionally
                                        // in Launcher 4.50; its SurfaceControl uses the actual field.
                                        try {
                                            drawIcon = HookUtil.getBooleanField(owner, "mIsDrawIcon");
                                        } catch (Throwable ignored) {
                                            drawIcon = false;
                                        }
                                    } else {
                                        Object draw = HookUtil.invoke(owner, "isDrawIcon");
                                        drawIcon = draw instanceof Boolean && ((Boolean) draw);
                                    }
                                    boolean proxyVisible = useRotationRect
                                            ? LauncherGlassProxyVisibility.isLayer2Visible(
                                                    proxyAlpha, drawIcon)
                                            : LauncherGlassProxyVisibility.isView2Visible(
                                                    proxyAlpha, drawIcon);
                                    if (!proxyVisible) {
                                        if (node.holdLaunchProxyHidden()) {
                                            MainHook.log(TAG + " proxy owner hidden class="
                                                    + owner.getClass().getSimpleName()
                                                    + " target=" + target.getClass().getSimpleName()
                                                    + " alpha=" + proxyAlpha);
                                        }
                                    } else {
                                        // FloatingIconView2 draws iconRect; FloatingIconLayer2 places
                                        // its SurfaceControl from rotationIconRect. Do not publish the
                                        // task-sized morph rect until MIUI itself shows the icon proxy.
                                        RectF proxyRect = (RectF) args[useRotationRect ? 1 : 0];
                                        boolean firstVisible = node.updateLaunchProxyGeometry(
                                                proxyRect.left, proxyRect.top,
                                                proxyRect.right, proxyRect.bottom);
                                        if (firstVisible) {
                                            MainHook.log(TAG + " proxy geometry visible class="
                                                    + owner.getClass().getSimpleName()
                                                    + " target=" + target.getClass().getSimpleName()
                                                    + " alpha=" + proxyAlpha
                                                    + " rect=" + proxyRect);
                                        }
                                    }
                                }
                            }
                        }
                        return chain.proceed(args);
                    }, RectF.class, RectF.class,
                    float.class, float.class, float.class,
                    boolean.class, boolean.class, boolean.class,
                    float.class, boolean.class);
            MainHook.log(TAG + " final floating proxy geometry hook installed class=" + className);
        } catch (Throwable error) {
            MainHook.log(TAG + " final floating proxy geometry hook unavailable class="
                    + className + ": " + error);
        }
    }

    private static void installWorkspaceResumeReconcileHook(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher", "onResume",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        scheduleWorkspaceResumeReconcile(chain.getThisObject(), glassConfig);
                        return result;
                    });
            MainHook.log(TAG + " Launcher HOME resume reconcile hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Launcher HOME resume reconcile hook unavailable: " + error);
        }
    }

    private static void scheduleWorkspaceResumeReconcile(
            Object launcher, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || launcher == null) return;
        Object value = HookUtil.invoke(launcher, "getWorkspace");
        if (!(value instanceof View)) return;
        View workspace = (View) value;
        workspace.postOnAnimation(() -> {
            if (!GlassRuntimeState.isEnabled() || !workspace.isAttachedToWindow()) return;
            reconcileCurrentWorkspacePage(workspace, glassConfig);
            // Resume is not a source invalidation. The invalidate only schedules normal pre-draw
            // geometry reconciliation; Surface/wallpaper/rotation freshness stays with its owner.
            workspace.postInvalidateOnAnimation();
            MainHook.log(TAG + " Workspace HOME resume reconciled");
        });
    }

    private static void installWidgetBackgroundOwnershipHook(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.LauncherAppWidgetHostView", "updateAppWidget",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Object owner = chain.getThisObject();
                        if (owner instanceof View && GlassRuntimeState.isWidgetEnabled()) {
                            // RemoteViews may recreate android.R.id.widget_frame. Re-run the normal
                            // Workspace bind path after every provider update so the fallback plate
                            // is only claimed when a live LiquidDock widget node owns the material.
                            scheduleBind((View) owner, LauncherGlassDragState.Kind.WIDGET,
                                    glassConfig, 0);
                        }
                        return result;
                    }, android.widget.RemoteViews.class);
            MainHook.log(TAG + " LauncherAppWidgetHostView background ownership hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " LauncherAppWidgetHostView background hook unavailable: " + error);
        }
    }

    private static void installMamlBackgroundOwnershipHooks(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        installMamlBackgroundOwnershipHook(classLoader, glassConfig, "onResume");
        installMamlBackgroundOwnershipHook(classLoader, glassConfig, "updateColor", int.class);
    }

    private static void installMamlBackgroundOwnershipHook(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig,
            String methodName, Class<?>... parameterTypes) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.maml.MaMlHostView",
                    methodName, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Object owner = chain.getThisObject();
                        if (owner instanceof View && GlassRuntimeState.isWidgetEnabled()) {
                            // Launcher 4.50 MAML roots are loaded asynchronously. putVariableNumber
                            // is a no-op before mRoot exists, and updateColor can later let vendor
                            // blur logic write enable_background_blur back to 0. Re-enter the normal
                            // bind/claim path only after those vendor lifecycle boundaries complete.
                            scheduleBind((View) owner, LauncherGlassDragState.Kind.WIDGET,
                                    glassConfig, 0);
                        }
                        return result;
                    }, parameterTypes);
            MainHook.log(TAG + " MaMlHostView background ownership hook installed method="
                    + methodName);
        } catch (Throwable error) {
            MainHook.log(TAG + " MaMlHostView background hook unavailable method="
                    + methodName + ": " + error);
        }
    }

    private static boolean installHostClass(
            ClassLoader classLoader, String className,
            LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            if (constructors.length == 0) return false;
            for (Constructor<?> constructor : constructors) {
                HookUtil.hook(constructor, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    Object owner = chain.getThisObject();
                    if (owner instanceof View) observeHost((View) owner, kind, glassConfig);
                    return result;
                });
            }
            MainHook.log(TAG + " hooked " + className + " constructors=" + constructors.length);
            return true;
        } catch (ClassNotFoundException missing) {
            MainHook.log(TAG + " optional host absent " + className);
            return false;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook failed " + className + ": " + error);
            return false;
        }
    }

    static void reconcileExistingHost(View host, LiquidDockConfig.Glass glassConfig) {
        if (host == null || glassConfig == null) return;
        String name = host.getClass().getName();
        if (GlassRuntimeState.isIconEnabled() && (name.endsWith(".ShortcutIcon")
                || "ShortcutIcon".equals(host.getClass().getSimpleName()))) {
            observeHost(host, LauncherGlassDragState.Kind.ICON, glassConfig);
        } else if (GlassRuntimeState.isWidgetEnabled()
                && (name.endsWith(".LauncherAppWidgetHostView")
                || name.endsWith(".MaMlHostView"))) {
            observeHost(host, LauncherGlassDragState.Kind.WIDGET, glassConfig);
        }
    }

    private static void observeHost(
            View host, LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || host == null) return;
        synchronized (BOOTSTRAP_OBSERVERS) {
            if (BOOTSTRAP_OBSERVERS.containsKey(host)) {
                if (host.isAttachedToWindow()) scheduleBind(host, kind, glassConfig, 0);
                return;
            }
            View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    scheduleBind(v, kind, glassConfig, 0);
                }
                @Override public void onViewDetachedFromWindow(View v) {
                    DockGlassItemRegistry.unregister(v);
                    // Preserve the Workspace static node across transient page cache detach.
                    // The node's own attach listener handles session unregister/re-register.
                }
            };
            BOOTSTRAP_OBSERVERS.put(host, listener);
            host.addOnAttachStateChangeListener(listener);
        }
        if (host.isAttachedToWindow()) scheduleBind(host, kind, glassConfig, 0);
    }

    private static void scheduleBind(
            View host, LauncherGlassDragState.Kind kind,
            LiquidDockConfig.Glass glassConfig, int attempt) {
        if (host == null || attempt > MAX_BIND_ATTEMPTS) return;
        if (kind == LauncherGlassDragState.Kind.ICON && !GlassRuntimeState.isIconEnabled()) {
            DockGlassItemRegistry.unregister(host);
            LauncherGlassStaticNode staleNode = LauncherGlassStaticNode.find(host);
            if (staleNode != null && staleNode.kind() == LauncherGlassDragState.Kind.ICON) {
                staleNode.dispose();
            }
            return;
        }
        if (kind == LauncherGlassDragState.Kind.WIDGET && !GlassRuntimeState.isWidgetEnabled()) {
            LauncherWidgetDarkContentAdapter.release(host);
            LauncherWidgetBackgroundController.release(host);
            DockGlassItemRegistry.unregister(host);
            LauncherGlassStaticNode staleNode = LauncherGlassStaticNode.find(host);
            if (staleNode != null && staleNode.kind() == LauncherGlassDragState.Kind.WIDGET) {
                staleNode.dispose();
            }
            return;
        }
        if (!GlassRuntimeState.isEnabled() || !host.isAttachedToWindow()) return;
        if (host.getWidth() <= 0 || host.getHeight() <= 0) {
            host.postOnAnimation(() -> scheduleBind(host, kind, glassConfig, attempt + 1));
            return;
        }
        LauncherGlassHierarchy.Domain domain = LauncherGlassHierarchy.classify(host);
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
        if (kind == LauncherGlassDragState.Kind.ICON
                && domain == LauncherGlassHierarchy.Domain.DOCK) {
            if (node != null) node.dispose();
            DockGlassItemRegistry.register(host);
            return;
        }
        DockGlassItemRegistry.unregister(host);
        if (domain != LauncherGlassHierarchy.Domain.WORKSPACE) {
            if (node != null) node.dispose();
            if (kind == LauncherGlassDragState.Kind.WIDGET) {
                LauncherWidgetDarkContentAdapter.release(host);
                LauncherWidgetBackgroundController.release(host);
            }
            if (domain == LauncherGlassHierarchy.Domain.OTHER && node == null
                    && attempt < MAX_BIND_ATTEMPTS) {
                host.postOnAnimation(() -> scheduleBind(host, kind, glassConfig, attempt + 1));
            }
            return;
        }
        if (node == null || node.kind() != kind) {
            float radius = resolveCornerRadius(host, kind);
            node = LauncherGlassStaticNode.attachToMaterial(host, kind, radius, glassConfig);
        } else {
            node.requestLifecycleRefresh();
        }
        if (node == null && attempt < MAX_BIND_ATTEMPTS) {
            host.postOnAnimation(() -> scheduleBind(host, kind, glassConfig, attempt + 1));
            return;
        }
        if (node != null && kind == LauncherGlassDragState.Kind.WIDGET
                && GlassRuntimeState.isWidgetEnabled()) {
            LauncherWidgetBackgroundController.claim(host);
            if (GlassRuntimeState.isWidgetDarkContentEnabled()) {
                LauncherWidgetDarkContentAdapter.apply(host);
            } else {
                LauncherWidgetDarkContentAdapter.release(host);
            }
        }
    }

    private static boolean isIconHost(View host) {
        if (host == null) return false;
        String name = host.getClass().getName();
        return name.endsWith(".ShortcutIcon")
                || "ShortcutIcon".equals(host.getClass().getSimpleName());
    }

    private static boolean isWidgetHost(View host) {
        if (host == null) return false;
        String name = host.getClass().getName();
        return name.endsWith(".LauncherAppWidgetHostView") || name.endsWith(".MaMlHostView");
    }

    private static float resolveCornerRadius(View host, LauncherGlassDragState.Kind kind) {
        if (kind == LauncherGlassDragState.Kind.ICON) {
            LauncherGlassIconGeometry.Bounds bounds = LauncherGlassIconGeometry.resolve(host);
            float min = bounds != null
                    ? Math.min(bounds.width(), bounds.height())
                    : Math.min(Math.max(1, host.getWidth()), Math.max(1, host.getHeight()));
            android.graphics.drawable.Drawable drawable = null;
            if (host instanceof android.widget.TextView) {
                android.graphics.drawable.Drawable[] drawables =
                        ((android.widget.TextView) host).getCompoundDrawables();
                if (drawables.length > 1) drawable = drawables[1];
            }
            return LauncherGlassIconShapeResolver.resolveAutoRadius(
                    drawable, min, min, min * 0.22f);
        }
        float nativeRadius = readCornerRadius(host);
        if (Float.isFinite(nativeRadius) && nativeRadius > 0f) return nativeRadius;
        float min = Math.min(Math.max(1, host.getWidth()), Math.max(1, host.getHeight()));
        return Math.max(0f, min * 0.08f);
    }

    private static float readCornerRadius(View host) {
        if (host == null) return Float.NaN;
        try {
            Field field = findField(host.getClass(), "mCornerRadius");
            field.setAccessible(true);
            Object value = field.get(host);
            if (value instanceof Number) return Math.max(0f, ((Number) value).floatValue());
        } catch (Throwable ignored) {}
        Drawable background = host.getBackground();
        if (background instanceof GradientDrawable) {
            return Math.max(0f, ((GradientDrawable) background).getCornerRadius());
        }
        return Float.NaN;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
