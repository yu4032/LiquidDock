package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Minimal HyperOS 3.0.307 bridge that asks SurfaceFlinger PassBlur to render into a caller-owned
 * producer Surface. Pixel ownership remains in GPU buffers; this class never captures or maps the
 * backdrop on the CPU.
 *
 * A bind is always continuous. The independent Dock relies on that historical behavior. Workspace
 * sessions may explicitly pulse or pause their own binding after bind; those calls must never be
 * inferred from the material-host hierarchy because Floating Dock window topology is vendor-specific.
 */
final class Miuix307PassBlurBridge {
    private static final String TAG = "[DC][PBGL]";
    private static final float DEMO_SCALE = 1.0f;
    private static final int INITIAL_UPDATE_FRAMES = 4;

    static final class Binding {
        final SurfaceControl rootSurface;
        final Method setPassBlurSurface;
        final Method setUpdateTextureFlag;
        final Method setMiBlurWinExc;
        final float scale;
        final String rootName;
        // Immutable snapshots. ViewRootImpl may mutate the same SurfaceControl Java wrapper
        // to point at a new BLAST/native layer, so keeping only rootSurface aliases away the
        // old generation identity that a later recovery needs to compare.
        final int viewRootIdentity;
        final int surfaceSequenceId;
        final int rootLayerId;
        boolean bound = true;
        boolean updatesEnabled = true;

        Binding(
                SurfaceControl rootSurface,
                Method setPassBlurSurface,
                Method setUpdateTextureFlag,
                Method setMiBlurWinExc,
                float scale,
                String rootName,
                int viewRootIdentity,
                int surfaceSequenceId,
                int rootLayerId) {
            this.rootSurface = rootSurface;
            this.setPassBlurSurface = setPassBlurSurface;
            this.setUpdateTextureFlag = setUpdateTextureFlag;
            this.setMiBlurWinExc = setMiBlurWinExc;
            this.scale = scale;
            this.rootName = rootName;
            this.viewRootIdentity = viewRootIdentity;
            this.surfaceSequenceId = surfaceSequenceId;
            this.rootLayerId = rootLayerId;
        }
    }

    private Miuix307PassBlurBridge() {}

    static Binding bind(View materialHost, Surface producerSurface, float requestedScale) {
        if (materialHost == null || producerSurface == null) return null;
        try {
            Method getViewRootImpl = View.class.getDeclaredMethod("getViewRootImpl");
            getViewRootImpl.setAccessible(true);
            Object viewRoot = getViewRootImpl.invoke(materialHost);
            if (viewRoot == null) {
                MainHook.log(TAG + " PassBlur bind unavailable: ViewRootImpl=null");
                return null;
            }

            Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object rootValue = getSurfaceControl.invoke(viewRoot);
            if (!(rootValue instanceof SurfaceControl)) {
                MainHook.log(TAG + " PassBlur bind unavailable: root SurfaceControl missing");
                return null;
            }
            SurfaceControl rootSurface = (SurfaceControl) rootValue;
            if (!rootSurface.isValid()) {
                MainHook.log(TAG + " PassBlur bind unavailable: invalid root surface");
                return null;
            }

            Class<?> transactionClass = SurfaceControl.Transaction.class;
            Method setPassBlurSurface = transactionClass.getMethod(
                    "SetPassBlurSurface", SurfaceControl.class, Surface.class);
            Method setUpdateTextureFlag = transactionClass.getMethod(
                    "setUpdateTextureFlag", SurfaceControl.class, Boolean.TYPE, Float.TYPE);
            Method setMiBlurWinExc = transactionClass.getMethod(
                    "setMiBlurWinExc", SurfaceControl.class, String[].class);

            String rootName = surfaceName(rootSurface);
            int viewRootIdentity = System.identityHashCode(viewRoot);
            int surfaceSequenceId = readSurfaceSequenceId(viewRoot);
            int rootLayerId = surfaceLayerId(rootSurface);
            String[] exclusions = new String[]{
                    rootName,
                    "NavigationBar",
                    "StatusBar",
                    "GestureStub",
                    "DockAssistantView"
            };

            // Keep the calibration producer at full resolution. TextureView output is composited
            // into the already-excluded root, so no child-layer exclusion is required.
            float scale = DEMO_SCALE;
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                setMiBlurWinExc.invoke(transaction, rootSurface, (Object) exclusions);
                setPassBlurSurface.invoke(transaction, rootSurface, producerSurface);
                setUpdateTextureFlag.invoke(
                        transaction, rootSurface, Boolean.TRUE, Float.valueOf(scale));
                transaction.apply();
            }

            Binding binding = new Binding(
                    rootSurface,
                    setPassBlurSurface,
                    setUpdateTextureFlag,
                    setMiBlurWinExc,
                    scale,
                    rootName,
                    viewRootIdentity,
                    surfaceSequenceId,
                    rootLayerId);

            MainHook.log(TAG + " PassBlur producer bound scale=" + scale
                    + " requestedScale=" + requestedScale
                    + " root=" + rootName
                    + " layerId=" + rootLayerId
                    + " surfaceSeq=" + surfaceSequenceId
                    + " viewRootId=" + viewRootIdentity
                    + " output=TextureView-in-root"
                    + " mode=continuous-on-bind"
                    + " exclusions=" + Arrays.toString(exclusions));
            return binding;
        } catch (Throwable error) {
            MainHook.log(TAG + " PassBlur bind unavailable: " + error);
            return null;
        }
    }

    /** Workspace-only demand pulse. Dock keeps main's persistent continuous-on-bind mode. */
    static void requestSingleUpdate(Binding binding, View host) {
        if (binding == null || host == null || !binding.bound) return;
        setUpdatesEnabled(binding, true);
        // A static Launcher may have no pending ViewRoot damage. Force one UI frame so the
        // compositor has a reason to publish a fresh PassBlur buffer before the pulse is paused.
        host.postInvalidateOnAnimation();
        schedulePauseUpdates(host, binding, INITIAL_UPDATE_FRAMES);
    }

    /** Persistent resume used by Dock when HyperOS leaves its HOME snapshot state. */
    static void resumeUpdates(Binding binding) {
        if (binding == null) return;
        setUpdatesEnabled(binding, true);
    }

    /** Workspace idle suspension and vendor-snapshot Dock suspension. */
    static void pauseUpdates(Binding binding) {
        if (binding == null) return;
        setUpdatesEnabled(binding, false);
    }

    private static void schedulePauseUpdates(View host, Binding binding, int framesLeft) {
        if (host == null || binding == null || !binding.bound) return;
        if (framesLeft <= 0) {
            if (WorkstationProducerPolicy.shouldPauseSharedProducer(
                    true, MainHook.isWorkstationMode())) {
                pauseUpdates(binding);
            }
            return;
        }
        host.postOnAnimation(() -> schedulePauseUpdates(host, binding, framesLeft - 1));
    }

    private static void setUpdatesEnabled(Binding binding, boolean enabled) {
        if (binding == null || !binding.bound || !binding.rootSurface.isValid()) return;
        if (binding.updatesEnabled == enabled) return;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            binding.setUpdateTextureFlag.invoke(
                    transaction,
                    binding.rootSurface,
                    Boolean.valueOf(enabled),
                    Float.valueOf(binding.scale));
            transaction.apply();
            binding.updatesEnabled = enabled;
            MainHook.log(TAG + " PassBlur producer updates=" + enabled
                    + " root=" + binding.rootName);
        } catch (Throwable error) {
            MainHook.log(TAG + " PassBlur update toggle failed: " + error);
        }
    }

    static void unbind(Binding binding) {
        if (binding == null || !binding.bound) return;
        try {
            if (!binding.rootSurface.isValid()) {
                binding.bound = false;
                binding.updatesEnabled = false;
                return;
            }
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                binding.setPassBlurSurface.invoke(transaction, binding.rootSurface, null);
                binding.setUpdateTextureFlag.invoke(
                        transaction,
                        binding.rootSurface,
                        Boolean.FALSE,
                        Float.valueOf(binding.scale));
                binding.setMiBlurWinExc.invoke(
                        transaction, binding.rootSurface, (Object) new String[0]);
                transaction.apply();
            }
            binding.bound = false;
            binding.updatesEnabled = false;
            MainHook.log(TAG + " PassBlur producer unbound root=" + binding.rootName);
        } catch (Throwable error) {
            binding.bound = false;
            binding.updatesEnabled = false;
            MainHook.log(TAG + " PassBlur unbind failed: " + error);
        }
    }

    static int surfaceLayerId(SurfaceControl surface) {
        if (surface == null) return -1;
        try {
            Method method = SurfaceControl.class.getDeclaredMethod("getLayerId");
            method.setAccessible(true);
            Object value = method.invoke(surface);
            return value instanceof Number ? ((Number) value).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    static int readSurfaceSequenceId(Object viewRoot) {
        if (viewRoot == null) return -1;
        Class<?> type = viewRoot.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("getSurfaceSequenceId");
                method.setAccessible(true);
                Object value = method.invoke(viewRoot);
                if (value instanceof Number) return ((Number) value).intValue();
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
                continue;
            } catch (Throwable ignored) {
                break;
            }
        }
        type = viewRoot.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("mSurfaceSequenceId");
                field.setAccessible(true);
                Object value = field.get(viewRoot);
                return value instanceof Number ? ((Number) value).intValue() : -1;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String surfaceName(SurfaceControl surface) {
        if (surface == null) return "";
        try {
            Method getName = SurfaceControl.class.getDeclaredMethod("getName");
            getName.setAccessible(true);
            Object value = getName.invoke(surface);
            if (value instanceof String) return (String) value;
        } catch (Throwable ignored) {}
        return surface.toString();
    }
}
