package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges a semantic Security Center activity-authority switch into the existing scene and
 * zero-copy producer gates without adding timers or polling.
 *
 * <p>The coordinator/session internals are exact LiquidDock-owned contracts. Reflection here is
 * intentional so the rollover can stay isolated from the large rendering coordinator; all member
 * lookups are exact and fail closed if an internal contract changes.</p>
 */
final class SecurityCenterSourceAuthorityController {
    private static final String TAG = "[DC][SecurityCenterGlass]";

    private static final Field SCENE = field(SecurityCenterGlassCoordinator.class, "scene");
    private static final Field TARGET = field(SecurityCenterGlassCoordinator.class, "targetKind");
    private static final Field CURRENT_FRAME = field(SecurityCenterGlassCoordinator.class, "currentFrame");
    private static final Field SESSION = field(SecurityCenterGlassCoordinator.class, "session");
    private static final Field TURBO_REF = field(SecurityCenterGlassCoordinator.class, "turboRef");
    private static final Field SOURCE_BACKEND = field(SecurityCenterGlassSession.class, "sourceBackend");

    private static final Method RECONCILE = method(
            SecurityCenterGlassCoordinator.class, "reconcileSinks");
    private static final Method SYNC = method(
            SecurityCenterGlassCoordinator.class, "syncSinksFromMaterials");
    private static final Method CAPTURE = method(
            SecurityCenterGlassCoordinator.class, "captureFrame", boolean.class);
    private static final Method RESTORE_VENDOR = method(
            SecurityCenterGlassCoordinator.class, "hideAndRestoreVendor");
    private static final Method PREPARE_CUSTOM = method(
            SecurityCenterGlassCoordinator.class, "prepareCustomOwnershipForPresentation");
    private static final Method REQUEST_CURRENT = method(
            SecurityCenterGlassCoordinator.class,
            "requestCurrentGeneration", SecurityCenterGlassFrameGeometry.class);

    private SecurityCenterSourceAuthorityController() {}

    static boolean rollover(
            SecurityCenterGlassCoordinator coordinator,
            Object previousAuthority,
            Object currentAuthority) {
        if (coordinator == null || previousAuthority == null || currentAuthority == null
                || previousAuthority.equals(currentAuthority)
                || !SecurityCenterGlassRuntimeState.isEnabled()) return false;
        try {
            @SuppressWarnings("unchecked")
            WeakReference<View> turboReference = (WeakReference<View>) TURBO_REF.get(coordinator);
            View turbo = turboReference != null ? turboReference.get() : null;
            if (turbo == null || !turbo.isAttachedToWindow()) return false;

            SecurityCenterGlassSceneState scene =
                    (SecurityCenterGlassSceneState) SCENE.get(coordinator);
            SecurityCenterGlassSceneState.Target target =
                    (SecurityCenterGlassSceneState.Target) TARGET.get(coordinator);
            if (scene == null || target == null
                    || scene.scene() == SecurityCenterGlassSceneState.Scene.DETACHED) return false;

            SecurityCenterGlassSceneState.Decision decision =
                    scene.onSourceAuthorityChanged(target);
            if (!decision.invalidateGeneration) return false;

            RECONCILE.invoke(coordinator);
            SYNC.invoke(coordinator);
            SecurityCenterGlassFrameGeometry frame = (SecurityCenterGlassFrameGeometry)
                    CAPTURE.invoke(coordinator,
                            target == SecurityCenterGlassSceneState.Target.ALL_APPS);

            RESTORE_VENDOR.invoke(coordinator);
            PREPARE_CUSTOM.invoke(coordinator);
            CURRENT_FRAME.set(coordinator, frame);

            SecurityCenterGlassSession session = (SecurityCenterGlassSession) SESSION.get(coordinator);
            if (frame == null || session == null || session.isShutdown()) {
                log("source authority changed; waiting fail-closed for a capturable frame", null);
                return true;
            }

            RootPassBlurBackend backend = (RootPassBlurBackend) SOURCE_BACKEND.get(session);
            if (backend == null) {
                log("source authority changed but source backend is unavailable", null);
                return true;
            }

            backend.requestRebind("security-center-source-authority");
            REQUEST_CURRENT.invoke(coordinator, frame);
            log("source authority rollover generation=" + decision.generation
                    + " target=" + target
                    + " previous=" + previousAuthority
                    + " current=" + currentAuthority, null);
            return true;
        } catch (Throwable error) {
            log("source authority rollover failed closed", error);
            try { coordinator.releaseAll(); } catch (Throwable ignored) {}
            return false;
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Throwable error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message + ": " + error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
