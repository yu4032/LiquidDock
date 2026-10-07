package com.hellovoid.liquiddock;

import android.os.SystemClock;
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
    private static final String FRAME_SYNC_TAG = "[DC][DockFrameSync]";
    private static final String GBOARD_FRAME_SYNC_TAG = "[DC][GboardFrameSync]";
    private static final int INITIAL_UPDATE_FRAMES = 4;
    private static final int FORCE_REFRESH_LEASE_MS = 250;
    private static final long FORCE_REFRESH_MIN_INTERVAL_MS = 50L;
    private static final long FORCE_REFRESH_ERROR_LOG_MIN_MS = 5000L;
    private static long lastForceRefreshErrorLogMs;

    static final class Binding {
        final SurfaceControl rootSurface;
        final Surface producerSurface;
        final Method setPassBlurSurface;
        final Method setUpdateTextureFlag;
        final Method setMiBlurWinExc;
        final Method setForceRefresh;
        final float scale;
        final String rootName;
        final int viewRootIdentity;
        final int surfaceSequenceId;
        final int rootLayerId;
        final PassBlurDomain domain;
        boolean bound = true;
        boolean updatesEnabled = true;
        long lastForceRefreshMs;

        Binding(
                SurfaceControl rootSurface,
                Surface producerSurface,
                Method setPassBlurSurface,
                Method setUpdateTextureFlag,
                Method setMiBlurWinExc,
                Method setForceRefresh,
                float scale,
                String rootName,
                int viewRootIdentity,
                int surfaceSequenceId,
                int rootLayerId,
                PassBlurDomain domain) {
            this.rootSurface = rootSurface;
            this.producerSurface = producerSurface;
            this.setPassBlurSurface = setPassBlurSurface;
            this.setUpdateTextureFlag = setUpdateTextureFlag;
            this.setMiBlurWinExc = setMiBlurWinExc;
            this.setForceRefresh = setForceRefresh;
            this.scale = scale;
            this.rootName = rootName;
            this.viewRootIdentity = viewRootIdentity;
            this.surfaceSequenceId = surfaceSequenceId;
            this.rootLayerId = rootLayerId;
            this.domain = domain;
        }
    }

    private Miuix307PassBlurBridge() {}

    static Binding bind(PassBlurBindRequest request, Surface producerSurface) {
        if (request == null || request.host() == null || producerSurface == null) return null;
        View materialHost = request.host();
        PassBlurDomain domain = request.domain();
        boolean launcherWorkspace = domain == PassBlurDomain.LAUNCHER_WORKSPACE;
        if (PassBlurBindPolicy.requiresUnlockGate(domain)
                && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()) {
            MainHook.log(TAG + " PassBlur Workspace bind blocked by unlock presentation");
            return null;
        }
        SurfaceControl rootSurface = null;
        boolean securityCenterClaimed = false;
        boolean gboardClaimed = false;
        boolean searchboxClaimed = false;
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
            rootSurface = (SurfaceControl) rootValue;
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
            Method setForceRefresh = null;
            if (domain == PassBlurDomain.DOCK
                    || domain == PassBlurDomain.GBOARD_FLOATING) {
                try {
                    setForceRefresh = transactionClass.getMethod(
                            "setForceRefresh", SurfaceControl.class, Integer.TYPE);
                } catch (Throwable error) {
                    MainHook.log((domain == PassBlurDomain.GBOARD_FLOATING
                            ? GBOARD_FRAME_SYNC_TAG : FRAME_SYNC_TAG)
                            + " force refresh lease unavailable: " + error);
                }
            }

            String rootName = surfaceName(rootSurface);
            int viewRootIdentity = System.identityHashCode(viewRoot);
            int surfaceSequenceId = readSurfaceSequenceId(viewRoot);
            int rootLayerId = surfaceLayerId(rootSurface);
            String[] exclusions = PassBlurBindPolicy.exclusions(
                    rootName, request.extraExclusions());
            float scale = request.nativeScale();

            if (domain == PassBlurDomain.SECURITY_CENTER) {
                SecurityCenterPassBlurContinuousAuthority.claim(rootSurface, producerSurface, scale);
                securityCenterClaimed = true;
            }
            if (domain == PassBlurDomain.GBOARD_FLOATING) {
                GboardPassBlurContinuousAuthority.claim(rootSurface, producerSurface, scale);
                gboardClaimed = true;
            }
            if (domain == PassBlurDomain.MIUI_SEARCHBOX) {
                MiuiSearchboxPassBlurContinuousAuthority.claim(rootSurface, producerSurface, scale);
                searchboxClaimed = true;
            }

            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                setMiBlurWinExc.invoke(transaction, rootSurface, (Object) exclusions);
                setPassBlurSurface.invoke(transaction, rootSurface, producerSurface);
                setUpdateTextureFlag.invoke(
                        transaction, rootSurface, Boolean.TRUE, Float.valueOf(scale));
                transaction.apply();
            }

            Binding binding = new Binding(
                    rootSurface,
                    producerSurface,
                    setPassBlurSurface,
                    setUpdateTextureFlag,
                    setMiBlurWinExc,
                    setForceRefresh,
                    scale,
                    rootName,
                    viewRootIdentity,
                    surfaceSequenceId,
                    rootLayerId,
                    domain);

            MainHook.log(TAG + " PassBlur producer bound scale=" + scale
                    + " requestedScale=" + request.requestedScale()
                    + " root=" + rootName
                    + " layerId=" + rootLayerId
                    + " surfaceSeq=" + surfaceSequenceId
                    + " viewRootId=" + viewRootIdentity
                    + " launcherWorkspace=" + launcherWorkspace
                    + " output=TextureView-in-root"
                    + " mode=continuous-on-bind"
                    + " exclusions=" + Arrays.toString(exclusions));
            return binding;
        } catch (Throwable error) {
            if (securityCenterClaimed && rootSurface != null) {
                SecurityCenterPassBlurContinuousAuthority.release(rootSurface, producerSurface);
            }
            if (gboardClaimed && rootSurface != null) {
                GboardPassBlurContinuousAuthority.release(rootSurface, producerSurface);
            }
            if (searchboxClaimed && rootSurface != null) {
                MiuiSearchboxPassBlurContinuousAuthority.release(rootSurface, producerSurface);
            }
            MainHook.log(TAG + " PassBlur bind unavailable: " + error);
            return null;
        }
    }

    /** Workspace-only demand pulse. Dock keeps main's persistent continuous-on-bind mode. */
    static void requestSingleUpdate(Binding binding, View host) {
        if (binding == null || host == null || !binding.bound) return;
        if (PassBlurBindPolicy.requiresUnlockGate(binding.domain)
                && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()) {
            MainHook.log(TAG + " PassBlur Workspace single update blocked by unlock presentation");
            return;
        }
        setUpdatesEnabled(binding, true, false);
        host.postInvalidateOnAnimation();
        schedulePauseUpdates(host, binding, INITIAL_UPDATE_FRAMES);
    }

    /** Persistent resume used by Dock and root-bound live-capture domains. */
    static void resumeUpdates(Binding binding) {
        if (binding == null) return;
        if (PassBlurBindPolicy.requiresUnlockGate(binding.domain)
                && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()) {
            MainHook.log(TAG + " PassBlur Workspace resume blocked by unlock presentation");
            return;
        }
        boolean force = binding.domain == PassBlurDomain.SECURITY_CENTER
                || binding.domain == PassBlurDomain.GBOARD_FLOATING
                || binding.domain == PassBlurDomain.MIUI_SEARCHBOX
                || binding.domain == PassBlurDomain.RECENTS_CAPSULE;
        setUpdatesEnabled(binding, true, force);
        if (binding.domain == PassBlurDomain.DOCK) {
            // APP -> HOME can resume after the producer-driven lease has already expired.
            // Prime the existing lease immediately instead of waiting for the first slow source
            // frame to arrive; subsequent real OES arrivals keep renewing it.
            renewForceRefresh(binding);
            if (VisualRuntimeState.isDockFrameSyncEnabled()) {
                MainHook.log(FRAME_SYNC_TAG + " force refresh primed on producer resume"
                        + " root=" + binding.rootName);
            }
        }
    }

    /** Workspace idle suspension and vendor-snapshot Dock suspension. */
    static void pauseUpdates(Binding binding) {
        if (binding == null) return;
        setUpdatesEnabled(binding, false, false);
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

    private static void setUpdatesEnabled(Binding binding, boolean enabled, boolean force) {
        if (binding == null || !binding.bound || !binding.rootSurface.isValid()) return;
        if (!force && binding.updatesEnabled == enabled) return;
        if (binding.domain == PassBlurDomain.GBOARD_FLOATING) {
            GboardPassBlurContinuousAuthority.setUpdatesEnabled(
                    binding.rootSurface, enabled);
        }
        if (binding.domain == PassBlurDomain.MIUI_SEARCHBOX) {
            MiuiSearchboxPassBlurContinuousAuthority.setUpdatesEnabled(
                    binding.rootSurface, enabled);
        }
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            binding.setUpdateTextureFlag.invoke(
                    transaction,
                    binding.rootSurface,
                    Boolean.valueOf(enabled),
                    Float.valueOf(binding.scale));
            if (!enabled
                    && binding.domain == PassBlurDomain.GBOARD_FLOATING
                    && binding.setForceRefresh != null) {
                binding.setForceRefresh.invoke(
                        transaction,
                        binding.rootSurface,
                        Integer.valueOf(0));
                binding.lastForceRefreshMs = 0L;
            }
            transaction.apply();
            binding.updatesEnabled = enabled;
            MainHook.log(TAG + " PassBlur producer updates=" + enabled
                    + " force=" + force
                    + " domain=" + binding.domain
                    + " root=" + binding.rootName);
        } catch (Throwable error) {
            MainHook.log(TAG + " PassBlur update toggle failed: " + error);
        }
    }

    /**
     * Producer-driven force-refresh lease for high-refresh domains. Dock honors its GUI switch;
     * floating Gboard uses the lease only while its PassBlur producer updates remain enabled.
     * Once arrivals stop, no more transactions are sent and vendor pacing resumes when the
     * existing lease expires.
     */
    static void renewForceRefresh(Binding binding) {
        if (binding == null || !binding.bound || !binding.updatesEnabled) return;
        boolean dock = binding.domain == PassBlurDomain.DOCK;
        boolean gboard = binding.domain == PassBlurDomain.GBOARD_FLOATING;
        if (!dock && !gboard) return;
        if (dock && !VisualRuntimeState.isDockFrameSyncEnabled()) return;
        if (binding.setForceRefresh == null || !binding.rootSurface.isValid()) return;
        long now = SystemClock.uptimeMillis();
        if (now - binding.lastForceRefreshMs < FORCE_REFRESH_MIN_INTERVAL_MS) return;
        binding.lastForceRefreshMs = now;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            binding.setForceRefresh.invoke(
                    transaction,
                    binding.rootSurface,
                    Integer.valueOf(FORCE_REFRESH_LEASE_MS));
            transaction.apply();
        } catch (Throwable error) {
            long errorNow = SystemClock.uptimeMillis();
            if (errorNow - lastForceRefreshErrorLogMs >= FORCE_REFRESH_ERROR_LOG_MIN_MS) {
                lastForceRefreshErrorLogMs = errorNow;
                MainHook.log((gboard ? GBOARD_FRAME_SYNC_TAG : FRAME_SYNC_TAG)
                        + " force refresh renew failed: " + error);
            }
        }
    }

    static void unbind(Binding binding) {
        if (binding == null || !binding.bound) return;
        if (binding.domain == PassBlurDomain.SECURITY_CENTER) {
            SecurityCenterPassBlurContinuousAuthority.release(
                    binding.rootSurface, binding.producerSurface);
        }
        if (binding.domain == PassBlurDomain.GBOARD_FLOATING) {
            GboardPassBlurContinuousAuthority.release(
                    binding.rootSurface, binding.producerSurface);
        }
        if (binding.domain == PassBlurDomain.MIUI_SEARCHBOX) {
            MiuiSearchboxPassBlurContinuousAuthority.release(
                    binding.rootSurface, binding.producerSurface);
        }
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
