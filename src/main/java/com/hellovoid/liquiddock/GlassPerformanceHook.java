package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces the legacy glass polling hot paths without duplicating the established EGL renderer.
 * Expensive producer checks are event-driven; ordinary Launcher frames only do cheap geometry
 * fingerprints, and Dock icon animation reuses the already prepared Prismal backdrop.
 */
final class GlassPerformanceHook {
    private static final String TAG = "[DC][GlassPerf]";
    private static final ThreadLocal<Boolean> FRAME_PRODUCER_REFRESH = new ThreadLocal<>();
    private static final Map<Miuix307PassBlurTextureView, DockState> DOCK_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LauncherGlassSession, LauncherState> LAUNCHER_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, MappingReflection> MAPPING_REFLECTIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static WeakReference<Miuix307PassBlurTextureView> currentDockRef =
            new WeakReference<>(null);
    private static boolean installed;
    private static DockReflection dockReflection;
    private static LauncherReflection launcherReflection;

    private GlassPerformanceHook() {}

    static synchronized void install(ClassLoader launcherClassLoader) {
        if (installed) return;
        installed = true;
        hookVendorBlurSuppression();
        hookDockRenderer();
        hookLauncherSession();
        hookWallpaperFreshness(launcherClassLoader);
        optimizeExistingLauncherSessions();
        MainHook.log(TAG + " scheduler installed mode=event-driven");
    }

    static void registerDock(Miuix307PassBlurTextureView view) {
        if (view == null) return;
        currentDockRef = new WeakReference<>(view);
        DockState state = stateFor(view);
        if (state.attachListener == null) {
            state.attachListener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    v.post(() -> installLightDockObserver(view));
                }

                @Override public void onViewDetachedFromWindow(View v) {
                    removeLightDockObserver(view);
                }
            };
            view.addOnAttachStateChangeListener(state.attachListener);
        }
        if (view.isAttachedToWindow()) installLightDockObserver(view);
        View host = materialHost(view);
        if (host != null) host.post(() -> cleanupLegacyVendorSuppressor(host));
    }

    static void unregisterDock(Miuix307PassBlurTextureView view) {
        if (view == null) return;
        DockState state;
        synchronized (DOCK_STATES) { state = DOCK_STATES.get(view); }
        removeLightDockObserver(view);
        removeLegacyDockObserver(view);
        if (state != null && state.attachListener != null) {
            try { view.removeOnAttachStateChangeListener(state.attachListener); }
            catch (Throwable ignored) {}
        }
        synchronized (DOCK_STATES) { DOCK_STATES.remove(view); }
        if (currentDockRef.get() == view) currentDockRef = new WeakReference<>(null);
    }

    /** Dock icon motion changes only Prismal scene geometry, not the captured wallpaper source. */
    static void requestDockAnimationFrame(Miuix307PassBlurTextureView view) {
        if (view == null) return;
        DockState state = stateFor(view);
        ensureLightDockObserver(view);
        try { state.sceneMapping = dock().backdropSnapshot.get(view); }
        catch (Throwable ignored) { state.sceneMapping = null; }
        state.policy.requestScene();
        view.requestDockSceneRefresh();
    }

    /** Explicit recovery boundary for a real root/geometry generation change. */
    static void requestDockGeometryRefresh(Miuix307PassBlurTextureView view) {
        if (view == null) return;
        DockState state = stateFor(view);
        state.cachedWindowFrame = null;
        state.producerFingerprint = Long.MIN_VALUE;
        state.mappingFingerprint = Long.MIN_VALUE;
        refreshDockGeometry(view, state, true, true);
    }

    /** One-shot normal-HOME backdrop refresh; workstation keeps its continuous source. */
    static void requestFreshDockBackdrop(String reason) {
        Miuix307PassBlurTextureView view = currentDockRef.get();
        if (view != null) pulseDockBackdrop(view, reason != null ? reason : "freshness-event");
    }

    private static void hookVendorBlurSuppression() {
        try {
            Method install = HookUtil.findMethodExact(
                    MiuixGlassHook.class, "installVendorGpuBlurSuppressor",
                    new Class<?>[]{View.class});
            HookUtil.hook(install, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                View background = args.length > 0 && args[0] instanceof View
                        ? (View) args[0] : null;
                if (background != null) MiuixGlassHook.suppressVendorGpuBlur(background);
                return null;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " vendor preDraw suppressor hook unavailable: " + error);
        }
        hookDockBlurSetter("setPassWindowBlurEnabled", boolean.class, Boolean.FALSE);
        hookDockBlurSetter("setMiViewBlurMode", int.class, Integer.valueOf(0));
        hookDockBlurSetter("setMiBackgroundBlurRadius", int.class, Integer.valueOf(0));
    }

    private static void hookDockBlurSetter(String name, Class<?> parameterType, Object replacement) {
        try {
            Method method = View.class.getMethod(name, parameterType);
            HookUtil.hook(method, chain -> {
                Object target = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (target instanceof View && MiuixGlassHook.isBoundTo((View) target)
                        && GlassRuntimeState.isEnabled() && args.length > 0) {
                    args[0] = replacement;
                }
                return chain.proceed(args);
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " terminal vendor blur hook unavailable " + name + ": " + error);
        }
    }

    private static void cleanupLegacyVendorSuppressor(View background) {
        try {
            Field observerField = MiuixGlassHook.class.getDeclaredField("vendorBlurObserver");
            Field listenerField = MiuixGlassHook.class.getDeclaredField("vendorBlurSuppressor");
            observerField.setAccessible(true);
            listenerField.setAccessible(true);
            Object observerRef = observerField.get(null);
            Object listenerValue = listenerField.get(null);
            ViewTreeObserver observer = observerRef instanceof WeakReference<?>
                    && ((WeakReference<?>) observerRef).get() instanceof ViewTreeObserver
                    ? (ViewTreeObserver) ((WeakReference<?>) observerRef).get() : null;
            if (observer != null && listenerValue instanceof ViewTreeObserver.OnPreDrawListener) {
                try {
                    if (observer.isAlive()) observer.removeOnPreDrawListener(
                            (ViewTreeObserver.OnPreDrawListener) listenerValue);
                } catch (Throwable ignored) {}
            }
            observerField.set(null, new WeakReference<ViewTreeObserver>(null));
            listenerField.set(null, null);
            if (background != null) MiuixGlassHook.suppressVendorGpuBlur(background);
        } catch (Throwable error) {
            MainHook.log(TAG + " legacy vendor suppressor cleanup unavailable: " + error);
        }
    }

    private static void hookDockRenderer() {
        try {
            Method installObserver = HookUtil.findMethodExact(
                    Miuix307PassBlurTextureView.class, "installGeometryObserver", new Class<?>[0]);
            HookUtil.hook(installObserver, chain -> {
                installLightDockObserver((Miuix307PassBlurTextureView) chain.getThisObject());
                return null;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock observer install hook unavailable: " + error);
        }
        try {
            Method removeObserver = HookUtil.findMethodExact(
                    Miuix307PassBlurTextureView.class, "removeGeometryObserver", new Class<?>[0]);
            HookUtil.hook(removeObserver, chain -> {
                Miuix307PassBlurTextureView view =
                        (Miuix307PassBlurTextureView) chain.getThisObject();
                removeLightDockObserver(view);
                removeLegacyDockObserver(view);
                return null;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock observer remove hook unavailable: " + error);
        }
        try {
            Method draw = HookUtil.findMethodExact(
                    Miuix307PassBlurTextureView.class, "drawLatestFrame",
                    new Class<?>[]{boolean.class});
            HookUtil.hook(draw, chain -> {
                Miuix307PassBlurTextureView view =
                        (Miuix307PassBlurTextureView) chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                boolean sourceFrame = args.length > 0 && args[0] instanceof Boolean
                        && (Boolean) args[0];
                DockState state = stateFor(view);
                if (sourceFrame) {
                    Object result = chain.proceed(args);
                    state.pulsePending = false;
                    state.backdropReady = true;
                    state.policy.requestSource();
                    state.policy.consume();
                    return result;
                }
                DockGlassFramePolicy.Work work = state.policy.consume();
                Object currentMapping = null;
                try { currentMapping = dock().backdropSnapshot.get(view); }
                catch (Throwable ignored) {}
                Object requestedMapping = state.sceneMapping;
                state.sceneMapping = null;
                if (work.renderScene && !work.prepareBackdrop && state.backdropReady
                        && requestedMapping != null && requestedMapping == currentMapping
                        && drawDockSceneOnly(view, currentMapping)) {
                    return null;
                }
                Object result = chain.proceed(args);
                if (hasConsumedFrame(view)) state.backdropReady = true;
                return result;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock scene fast-path hook unavailable: " + error);
        }
        try {
            Method setUpdates = HookUtil.findMethodExact(
                    Miuix307PassBlurTextureView.class, "setProducerUpdatesEnabled",
                    new Class<?>[]{boolean.class, String.class});
            HookUtil.hook(setUpdates, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                boolean enabled = args.length > 0 && args[0] instanceof Boolean && (Boolean) args[0];
                if (enabled && !WorkstationProducerPolicy.shouldKeepDockProducerContinuous(
                        MainHook.isWorkstationMode())) {
                    Miuix307PassBlurTextureView view =
                            (Miuix307PassBlurTextureView) chain.getThisObject();
                    try { HookUtil.setField(view, "producerUpdatesEnabled", Boolean.TRUE); }
                    catch (Throwable ignored) {}
                    String reason = args.length > 1 && args[1] instanceof String
                            ? (String) args[1] : "producer-enable";
                    pulseDockBackdrop(view, reason);
                    return null;
                }
                return chain.proceed(args);
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock producer policy hook unavailable: " + error);
        }
        try {
            Method finishBind = findDeclaredMethod(
                    Miuix307PassBlurTextureView.class, "finishBindProducer", 3);
            HookUtil.hook(finishBind, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                applyDockProducerMode((Miuix307PassBlurTextureView) chain.getThisObject(),
                        "producer-bind");
                return result;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock producer bind hook unavailable: " + error);
        }
        try {
            Method readWindowFrame = HookUtil.findMethodExact(
                    Miuix307PassBlurTextureView.class, "readViewRootRectField",
                    new Class<?>[]{View.class, String.class});
            HookUtil.hook(readWindowFrame, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length >= 2 && args[0] instanceof Miuix307PassBlurTextureView
                        && "mWinFrameInScreen".equals(args[1])) {
                    Miuix307PassBlurTextureView view = (Miuix307PassBlurTextureView) args[0];
                    DockState state = stateFor(view);
                    Rect cached = state.cachedWindowFrame;
                    if (cached != null) return new Rect(cached);
                    Object result = chain.proceed(args);
                    if (result instanceof Rect) state.cachedWindowFrame = new Rect((Rect) result);
                    return result;
                }
                return chain.proceed(args);
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock window-frame cache hook unavailable: " + error);
        }
    }

    private static void hookLauncherSession() {
        try {
            Method installObserver = HookUtil.findMethodExact(
                    LauncherGlassSession.class, "installRootObserver", new Class<?>[0]);
            HookUtil.hook(installObserver, chain -> {
                installLauncherObserver((LauncherGlassSession) chain.getThisObject());
                return null;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " Launcher observer hook unavailable: " + error);
        }
        try {
            Method refreshProducer = HookUtil.findMethodExact(
                    LauncherGlassSession.class, "refreshProducerGeometryOnUi",
                    new Class<?>[]{View.class});
            HookUtil.hook(refreshProducer, chain -> {
                if (Boolean.FALSE.equals(FRAME_PRODUCER_REFRESH.get())) return Boolean.FALSE;
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " Launcher producer gate hook unavailable: " + error);
        }
    }

    private static void hookWallpaperFreshness(ClassLoader classLoader) {
        if (classLoader == null) return;
        hookWallpaperMethod(classLoader,
                "com.miui.home.wallpaper.WallpaperZoomManager", "onWallpaperChanged");
        hookWallpaperMethod(classLoader,
                "com.miui.home.wallpaper.WallpaperZoomManager", "onWallpaperFirstFrameRendered");
        hookWallpaperMethod(classLoader,
                "com.miui.home.wallpaper.WallpaperZoomManager", "onDrawFrameEnd");
        hookWallpaperMethod(classLoader,
                "com.miui.home.launcher.Workspace", "onWallpaperColorChanged");
    }

    private static void hookWallpaperMethod(ClassLoader loader, String className, String name) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            int count = 0;
            for (Method method : type.getDeclaredMethods()) {
                if (!name.equals(method.getName())) continue;
                HookUtil.hook(method, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    requestFreshDockBackdrop("wallpaper:" + name);
                    return result;
                });
                count++;
            }
            if (count == 0) MainHook.log(TAG + " wallpaper callback absent " + className + "#" + name);
        } catch (Throwable error) {
            MainHook.log(TAG + " wallpaper freshness hook unavailable " + name + ": " + error);
        }
    }

    private static DockState stateFor(Miuix307PassBlurTextureView view) {
        synchronized (DOCK_STATES) {
            DockState state = DOCK_STATES.get(view);
            if (state == null) {
                state = new DockState();
                DOCK_STATES.put(view, state);
            }
            return state;
        }
    }

    private static void ensureLightDockObserver(Miuix307PassBlurTextureView view) {
        DockState state = stateFor(view);
        View root = view != null ? view.getRootView() : null;
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) return;
        if (state.observer == observer && state.listener != null) return;
        installLightDockObserver(view);
    }

    private static void installLightDockObserver(Miuix307PassBlurTextureView view) {
        if (view == null) return;
        DockState state = stateFor(view);
        removeLegacyDockObserver(view);
        removeLightDockObserver(view);
        View root = view.getRootView();
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) return;
        state.producerFingerprint = producerFingerprint(view);
        state.mappingFingerprint = mappingFingerprint(view);
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            if (!view.isAttachedToWindow()) return true;
            long producer = producerFingerprint(view);
            long mapping = mappingFingerprint(view);
            boolean producerChanged = producer != state.producerFingerprint;
            boolean mappingChanged = mapping != state.mappingFingerprint;
            if (!producerChanged && !mappingChanged) return true;
            state.producerFingerprint = producer;
            state.mappingFingerprint = mapping;
            if (producerChanged) state.cachedWindowFrame = null;
            refreshDockGeometry(view, state, producerChanged, mappingChanged || producerChanged);
            return true;
        };
        observer.addOnPreDrawListener(listener);
        state.observer = observer;
        state.listener = listener;
    }

    private static void removeLegacyDockObserver(Miuix307PassBlurTextureView view) {
        if (view == null) return;
        try {
            DockReflection reflection = dock();
            Object observerValue = reflection.preDrawObserver.get(view);
            Object listenerValue = reflection.preDrawListener.get(view);
            if (observerValue instanceof ViewTreeObserver
                    && listenerValue instanceof ViewTreeObserver.OnPreDrawListener) {
                ViewTreeObserver observer = (ViewTreeObserver) observerValue;
                try {
                    if (observer.isAlive()) observer.removeOnPreDrawListener(
                            (ViewTreeObserver.OnPreDrawListener) listenerValue);
                } catch (Throwable ignored) {}
            }
            reflection.preDrawObserver.set(view, null);
            reflection.preDrawListener.set(view, null);
        } catch (Throwable ignored) {}
    }

    private static void removeLightDockObserver(Miuix307PassBlurTextureView view) {
        DockState state;
        synchronized (DOCK_STATES) { state = DOCK_STATES.get(view); }
        if (state == null) return;
        ViewTreeObserver observer = state.observer;
        ViewTreeObserver.OnPreDrawListener listener = state.listener;
        state.observer = null;
        state.listener = null;
        if (observer != null && listener != null) {
            try { if (observer.isAlive()) observer.removeOnPreDrawListener(listener); }
            catch (Throwable ignored) {}
        }
    }

    private static void refreshDockGeometry(Miuix307PassBlurTextureView view, DockState state,
                                            boolean producerChanged, boolean mappingChanged) {
        if (view == null) return;
        try {
            DockReflection reflection = dock();
            if (producerChanged) {
                reflection.refreshProducerGeometry.invoke(view);
                state.policy.requestMapping();
            }
            if (mappingChanged) reflection.updateBackdropMapping.invoke(view);
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock geometry refresh failed: " + rootCause(error));
        }
    }

    private static long producerFingerprint(Miuix307PassBlurTextureView view) {
        View root = view != null ? view.getRootView() : null;
        if (root == null) return 0L;
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, root.getWidth());
        hash = mix(hash, root.getHeight());
        hash = mix(hash, System.identityHashCode(root.getWindowToken()));
        Display display = root.getDisplay();
        return mix(hash, display != null ? display.getRotation() : 0);
    }

    private static long mappingFingerprint(Miuix307PassBlurTextureView view) {
        if (view == null) return 0L;
        View host = materialHost(view);
        View root = view.getRootView();
        long hash = 0xcbf29ce484222325L;
        hash = mixViewGeometry(hash, view);
        hash = mixViewGeometry(hash, host);
        hash = mixViewGeometry(hash, root);
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        hash = mix(hash, location[0]);
        hash = mix(hash, location[1]);
        if (host != null) {
            host.getLocationOnScreen(location);
            hash = mix(hash, location[0]);
            hash = mix(hash, location[1]);
        }
        return hash;
    }

    private static long mixViewGeometry(long hash, View view) {
        if (view == null) return mix(hash, 0L);
        hash = mix(hash, view.getWidth());
        hash = mix(hash, view.getHeight());
        hash = mix(hash, Float.floatToIntBits(view.getTranslationX()));
        hash = mix(hash, Float.floatToIntBits(view.getTranslationY()));
        hash = mix(hash, Float.floatToIntBits(view.getScaleX()));
        return mix(hash, Float.floatToIntBits(view.getScaleY()));
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }

    private static View materialHost(Miuix307PassBlurTextureView view) {
        if (view == null) return null;
        try {
            Object value = dock().materialHostRef.get(view);
            if (value instanceof WeakReference<?>) {
                Object host = ((WeakReference<?>) value).get();
                return host instanceof View ? (View) host : null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void applyDockProducerMode(Miuix307PassBlurTextureView view, String reason) {
        if (view == null) return;
        if (WorkstationProducerPolicy.shouldKeepDockProducerContinuous(MainHook.isWorkstationMode())) {
            try {
                Object value = dock().binding.get(view);
                if (value instanceof Miuix307PassBlurBridge.Binding) {
                    Miuix307PassBlurBridge.resumeUpdates((Miuix307PassBlurBridge.Binding) value);
                }
            } catch (Throwable ignored) {}
            return;
        }
        pulseDockBackdrop(view, reason);
    }

    private static void pulseDockBackdrop(Miuix307PassBlurTextureView view, String reason) {
        if (view == null || WorkstationProducerPolicy.shouldKeepDockProducerContinuous(
                MainHook.isWorkstationMode())) return;
        DockState state = stateFor(view);
        if (state.pulsePending) return;
        try {
            Object value = dock().binding.get(view);
            View host = materialHost(view);
            if (!(value instanceof Miuix307PassBlurBridge.Binding) || host == null) return;
            Miuix307PassBlurBridge.Binding binding = (Miuix307PassBlurBridge.Binding) value;
            if (!binding.bound) return;
            state.pulsePending = true;
            long serial = ++state.pulseSerial;
            Miuix307PassBlurBridge.requestSingleUpdate(binding, host);
            clearPulseIfStale(view, state, serial, 8);
            MainHook.log(TAG + " Dock backdrop pulse reason=" + reason);
        } catch (Throwable error) {
            state.pulsePending = false;
            MainHook.log(TAG + " Dock backdrop pulse failed: " + rootCause(error));
        }
    }

    private static void clearPulseIfStale(Miuix307PassBlurTextureView view, DockState state,
                                          long serial, int framesLeft) {
        if (view == null || state == null || !state.pulsePending || state.pulseSerial != serial) return;
        if (framesLeft <= 0) {
            state.pulsePending = false;
            return;
        }
        view.postOnAnimation(() -> clearPulseIfStale(view, state, serial, framesLeft - 1));
    }

    private static boolean hasConsumedFrame(Miuix307PassBlurTextureView view) {
        try { return dock().hasConsumedFrame.getBoolean(view); }
        catch (Throwable ignored) { return false; }
    }

    private static boolean drawDockSceneOnly(Miuix307PassBlurTextureView view, Object mapping) {
        if (view == null || mapping == null) return false;
        try {
            DockReflection reflection = dock();
            PrismalRenderer renderer = (PrismalRenderer) reflection.prismalRenderer.get(view);
            DockGlassCompositor compositor = (DockGlassCompositor) reflection.dockCompositor.get(view);
            if (renderer == null || compositor == null || !reflection.hasConsumedFrame.getBoolean(view)) {
                return false;
            }
            reflection.makeCurrent.invoke(view);
            MappingReflection mapped = mapping(mapping.getClass());
            PrismalParams params = (PrismalParams) mapped.prismalParams.get(mapping);
            int sampleWidth = mapped.sampleWidth.getInt(mapping);
            int sampleHeight = mapped.sampleHeight.getInt(mapping);
            PrismalGeometry body = (PrismalGeometry) reflection.createPrismalGeometry.invoke(view, mapping);
            compositor.drawFrame(renderer, body, params, compositor.latestScene(), sampleWidth, sampleHeight);
            reflection.renderCompositePass.invoke(view, renderer.outputTexture(), mapping);
            if (reflection.backdropSnapshot.get(view) != mapping) return false;
            EGLDisplay display = (EGLDisplay) reflection.eglDisplay.get(view);
            EGLSurface surface = (EGLSurface) reflection.eglWindowSurface.get(view);
            if (display == null || surface == null
                    || display == EGL14.EGL_NO_DISPLAY || surface == EGL14.EGL_NO_SURFACE) return false;
            if (!EGL14.eglSwapBuffers(display, surface)) return false;
            reflection.renderedFrameCount.setLong(
                    view, reflection.renderedFrameCount.getLong(view) + 1L);
            reflection.maybeLogPowerStats.invoke(view);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " scene-only redraw fallback: " + rootCause(error));
            return false;
        }
    }

    private static void installLauncherObserver(LauncherGlassSession session) {
        if (session == null) return;
        try {
            LauncherReflection reflection = launcher();
            @SuppressWarnings("unchecked")
            WeakReference<View> rootReference = (WeakReference<View>) reflection.rootRef.get(session);
            View root = rootReference != null ? rootReference.get() : null;
            if (root == null) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer == null || !observer.isAlive()) return;

            LauncherState mutableState;
            synchronized (LAUNCHER_STATES) {
                mutableState = LAUNCHER_STATES.get(session);
                if (mutableState == null) {
                    mutableState = new LauncherState();
                    LAUNCHER_STATES.put(session, mutableState);
                }
            }
            final LauncherState state = mutableState;
            if (state.attachListener == null) {
                state.attachListener = new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View v) {
                        v.post(() -> installLauncherObserver(session));
                    }
                    @Override public void onViewDetachedFromWindow(View v) {}
                };
                root.addOnAttachStateChangeListener(state.attachListener);
            }

            Object currentObserver = reflection.rootObserver.get(session);
            Object currentListener = reflection.preDrawListener.get(session);
            if (currentObserver == observer && currentListener == state.listener
                    && state.listener != null) return;
            if (currentObserver instanceof ViewTreeObserver
                    && currentListener instanceof ViewTreeObserver.OnPreDrawListener) {
                try {
                    ViewTreeObserver old = (ViewTreeObserver) currentObserver;
                    if (old.isAlive()) old.removeOnPreDrawListener(
                            (ViewTreeObserver.OnPreDrawListener) currentListener);
                } catch (Throwable ignored) {}
            }

            state.lastDisplayRotation = displayRotation(root);
            ViewTreeObserver.OnPreDrawListener listener = () -> {
                int storedWidth;
                int storedHeight;
                try {
                    storedWidth = reflection.rootWidth.getInt(session);
                    storedHeight = reflection.rootHeight.getInt(session);
                } catch (Throwable ignored) {
                    storedWidth = root.getWidth();
                    storedHeight = root.getHeight();
                }
                int rotation = displayRotation(root);
                boolean rootGeometryChanged = (root.getWidth() > 0 && root.getHeight() > 0
                        && (root.getWidth() != storedWidth || root.getHeight() != storedHeight))
                        || rotation != state.lastDisplayRotation;
                state.lastDisplayRotation = rotation;
                FRAME_PRODUCER_REFRESH.set(rootGeometryChanged);
                try {
                    reflection.syncScene.invoke(session);
                } catch (Throwable error) {
                    MainHook.log(TAG + " Launcher frame sync failed: " + rootCause(error));
                } finally {
                    FRAME_PRODUCER_REFRESH.remove();
                }
                return true;
            };
            state.listener = listener;
            observer.addOnPreDrawListener(listener);
            reflection.rootObserver.set(session, observer);
            reflection.preDrawListener.set(session, listener);
        } catch (Throwable error) {
            MainHook.log(TAG + " Launcher observer install failed: " + rootCause(error));
        }
    }

    private static void optimizeExistingLauncherSessions() {
        try {
            Field sessionsField = LauncherGlassSessionRegistry.class.getDeclaredField("SESSIONS");
            sessionsField.setAccessible(true);
            Object value = sessionsField.get(null);
            if (!(value instanceof Map<?, ?>)) return;
            ArrayList<LauncherGlassSession> sessions = new ArrayList<>();
            synchronized (value) {
                for (Object item : ((Map<?, ?>) value).values()) {
                    if (item instanceof LauncherGlassSession) sessions.add((LauncherGlassSession) item);
                }
            }
            for (LauncherGlassSession session : sessions) installLauncherObserver(session);
        } catch (Throwable error) {
            MainHook.log(TAG + " existing Launcher session optimization unavailable: " + error);
        }
    }

    private static int displayRotation(View view) {
        Display display = view != null ? view.getDisplay() : null;
        return display != null ? display.getRotation() : 0;
    }

    private static Method findDeclaredMethod(Class<?> type, String name, int parameterCount)
            throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (name.equals(method.getName()) && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + "/" + parameterCount);
    }

    private static DockReflection dock() throws ReflectiveOperationException {
        DockReflection current = dockReflection;
        if (current != null) return current;
        synchronized (GlassPerformanceHook.class) {
            if (dockReflection == null) dockReflection = new DockReflection();
            return dockReflection;
        }
    }

    private static LauncherReflection launcher() throws ReflectiveOperationException {
        LauncherReflection current = launcherReflection;
        if (current != null) return current;
        synchronized (GlassPerformanceHook.class) {
            if (launcherReflection == null) launcherReflection = new LauncherReflection();
            return launcherReflection;
        }
    }

    private static MappingReflection mapping(Class<?> type) throws ReflectiveOperationException {
        synchronized (MAPPING_REFLECTIONS) {
            MappingReflection result = MAPPING_REFLECTIONS.get(type);
            if (result == null) {
                result = new MappingReflection(type);
                MAPPING_REFLECTIONS.put(type, result);
            }
            return result;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static String rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        return String.valueOf(current != null ? current : error);
    }

    private static final class DockState {
        final DockGlassFramePolicy policy = new DockGlassFramePolicy();
        ViewTreeObserver observer;
        ViewTreeObserver.OnPreDrawListener listener;
        View.OnAttachStateChangeListener attachListener;
        long producerFingerprint = Long.MIN_VALUE;
        long mappingFingerprint = Long.MIN_VALUE;
        Rect cachedWindowFrame;
        Object sceneMapping;
        boolean backdropReady;
        boolean pulsePending;
        long pulseSerial;
    }

    private static final class LauncherState {
        ViewTreeObserver.OnPreDrawListener listener;
        View.OnAttachStateChangeListener attachListener;
        int lastDisplayRotation;
    }

    private static final class DockReflection {
        final Field materialHostRef;
        final Field binding;
        final Field hasConsumedFrame;
        final Field backdropSnapshot;
        final Field prismalRenderer;
        final Field dockCompositor;
        final Field eglDisplay;
        final Field eglWindowSurface;
        final Field renderedFrameCount;
        final Field preDrawObserver;
        final Field preDrawListener;
        final Method refreshProducerGeometry;
        final Method updateBackdropMapping;
        final Method makeCurrent;
        final Method createPrismalGeometry;
        final Method renderCompositePass;
        final Method maybeLogPowerStats;

        DockReflection() throws ReflectiveOperationException {
            Class<?> type = Miuix307PassBlurTextureView.class;
            materialHostRef = field(type, "materialHostRef");
            binding = field(type, "binding");
            hasConsumedFrame = field(type, "hasConsumedFrame");
            backdropSnapshot = field(type, "backdropSnapshot");
            prismalRenderer = field(type, "prismalRenderer");
            dockCompositor = field(type, "dockCompositor");
            eglDisplay = field(type, "eglDisplay");
            eglWindowSurface = field(type, "eglWindowSurface");
            renderedFrameCount = field(type, "renderedFrameCount");
            preDrawObserver = field(type, "preDrawObserver");
            preDrawListener = field(type, "preDrawListener");
            refreshProducerGeometry = findDeclaredMethod(type, "refreshProducerGeometryInPlace", 0);
            updateBackdropMapping = findDeclaredMethod(type, "updateBackdropMapping", 0);
            makeCurrent = findDeclaredMethod(type, "makeCurrent", 0);
            createPrismalGeometry = findDeclaredMethod(type, "createPrismalGeometry", 1);
            renderCompositePass = findDeclaredMethod(type, "renderCompositePass", 2);
            maybeLogPowerStats = findDeclaredMethod(type, "maybeLogPowerStats", 0);
        }
    }

    private static final class LauncherReflection {
        final Field rootRef;
        final Field rootWidth;
        final Field rootHeight;
        final Field rootObserver;
        final Field preDrawListener;
        final Method syncScene;

        LauncherReflection() throws ReflectiveOperationException {
            Class<?> type = LauncherGlassSession.class;
            rootRef = field(type, "rootRef");
            rootWidth = field(type, "rootWidth");
            rootHeight = field(type, "rootHeight");
            rootObserver = field(type, "rootObserver");
            preDrawListener = field(type, "preDrawListener");
            syncScene = findDeclaredMethod(type, "syncSceneOnUiThread", 0);
        }
    }

    private static final class MappingReflection {
        final Field sampleWidth;
        final Field sampleHeight;
        final Field prismalParams;

        MappingReflection(Class<?> type) throws ReflectiveOperationException {
            sampleWidth = field(type, "sampleWidth");
            sampleHeight = field(type, "sampleHeight");
            prismalParams = field(type, "prismalParams");
        }
    }
}
