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
            View.OnAttachStateChangeListener listener = BOOTSTRAP_OBSERVERS.remove(host);
            if (listener != null) host.removeOnAttachStateChangeListener(listener);
            DockGlassItemRegistry.unregister(host);
            LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
            if (node != null) node.dispose();
        }
        BOOTSTRAP_OBSERVERS.clear();
    }

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        if (!glassConfig.widgetEnabled && !glassConfig.iconEnabled) return false;
        boolean any = false;
        if (glassConfig.iconEnabled) {
            any |= installHostClass(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    LauncherGlassDragState.Kind.ICON, glassConfig);
        }
        if (glassConfig.widgetEnabled) {
            any |= installHostClass(classLoader, "com.miui.home.launcher.LauncherAppWidgetHostView",
                    LauncherGlassDragState.Kind.WIDGET, glassConfig);
            any |= installHostClass(classLoader, "com.miui.home.launcher.maml.MaMlHostView",
                    LauncherGlassDragState.Kind.WIDGET, glassConfig);
        }
        installed = any;
        if (any) {
            installWorkspacePageReconcileHook(classLoader, glassConfig);
            installWorkspaceResumeRecoveryHooks(classLoader, glassConfig);
            if (glassConfig.iconEnabled) {
                installShortcutIconVisualOwnerHook(classLoader, glassConfig);
                installFloatingProxyVisualGeometryHooks(classLoader);
            }
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
                        Object owner = chain.getThisObject();
                        if (!(owner instanceof View) || args.length == 0
                                || !(args[0] instanceof Number)) return result;
                        View host = (View) owner;
                        if (!LauncherGlassHierarchy.isWorkspace(host)) return result;
                        int visibility = ((Number) args[0]).intValue();
                        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
                        if (visibility == View.VISIBLE) {
                            if (node != null) node.endLaunchProxy();
                            scheduleWorkspaceRecoveryFromHost(
                                    host, glassConfig, "anim-target-visible");
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
                        if (args.length == 10 && args[0] instanceof RectF
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

    private static void scheduleWorkspaceRecoveryFromHost(
            View host, LiquidDockConfig.Glass glassConfig, String reason) {
        if (!GlassRuntimeState.isEnabled() || host == null) return;
        View workspace = findWorkspaceAncestor(host);
        if (workspace == null) return;
        workspace.postOnAnimation(() -> {
            if (!GlassRuntimeState.isEnabled() || !workspace.isAttachedToWindow()) return;
            reconcileCurrentWorkspacePage(workspace, glassConfig);
            View root = LauncherGlassSessionRegistry.resolveStableRoot(workspace);
            if (root != null) {
                // Consume geometryDirty/root-space owner changes before the one-shot producer pulse.
                root.postInvalidateOnAnimation();
                LauncherGlassSceneController.requestFreshForRoot(root);
            }
            MainHook.log(TAG + " Workspace visual owner recovery reason=" + reason);
        });
    }

    private static View findWorkspaceAncestor(View host) {
        View cursor = host;
        while (cursor != null) {
            Class<?> type = cursor.getClass();
            if ("com.miui.home.launcher.Workspace".equals(type.getName())
                    || "Workspace".equals(type.getSimpleName())) return cursor;
            android.view.ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static void installWorkspaceResumeRecoveryHooks(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher", "onResume",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        scheduleWorkspaceHomeRecovery(
                                chain.getThisObject(), glassConfig, "launcher-onResume");
                        return result;
                    });
            MainHook.log(TAG + " Launcher HOME resume recovery hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Launcher HOME resume recovery hook unavailable: " + error);
        }

        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher",
                    "restoreMingouDesktopIconBlurSourceIfNeeded",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        scheduleWorkspaceHomeRecovery(
                                chain.getThisObject(), glassConfig, "mingou-source-restored");
                        return result;
                    });
            MainHook.log(TAG + " Mingou Workspace source restore recovery hook installed");
        } catch (Throwable error) {
            // Optional across Launcher versions. Generic ancestor visibility tracking remains the
            // fallback and Launcher.onResume still provides a stable HOME lifecycle boundary.
            MainHook.log(TAG + " Mingou Workspace source restore recovery hook unavailable: "
                    + error);
        }
    }

    private static void scheduleWorkspaceHomeRecovery(
            Object launcher, LiquidDockConfig.Glass glassConfig, String reason) {
        if (!GlassRuntimeState.isEnabled() || launcher == null) return;
        Object value = HookUtil.invoke(launcher, "getWorkspace");
        if (!(value instanceof View)) return;
        View workspace = (View) value;
        workspace.postOnAnimation(() -> {
            if (!GlassRuntimeState.isEnabled() || !workspace.isAttachedToWindow()) return;
            reconcileCurrentWorkspacePage(workspace, glassConfig);
            View root = LauncherGlassSessionRegistry.resolveStableRoot(workspace);
            if (root != null) LauncherGlassSceneController.requestFreshForRoot(root);
            MainHook.log(TAG + " Workspace HOME recovery reason=" + reason);
        });
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
        if (glassConfig.iconStyle.enabled && (name.endsWith(".ShortcutIcon")
                || "ShortcutIcon".equals(host.getClass().getSimpleName()))) {
            observeHost(host, LauncherGlassDragState.Kind.ICON, glassConfig);
        } else if (glassConfig.widgetStyle.enabled
                && (name.endsWith(".LauncherAppWidgetHostView")
                || name.endsWith(".MaMlHostView"))) {
            LauncherGlassVendorMaterialSuppressor.claimWidget(host);
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
        if (!GlassRuntimeState.isEnabled() || host == null || !host.isAttachedToWindow()
                || attempt > MAX_BIND_ATTEMPTS) return;
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
        if (node != null && kind == LauncherGlassDragState.Kind.WIDGET) {
            LauncherGlassVendorMaterialSuppressor.claimWidget(host);
        }
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
