package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.hellovoid.prismal.PrismalGeometry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;

/**
 * Device-accepted contracts recovered from Security Center 11.0.6-260825.1.2.
 *
 * These tests intentionally describe carrier authority rather than the old page-scene model:
 * TurboLayout/root may be reused while Dock/Game material children are rebuilt, All Apps is a
 * temporary material sibling, and output readiness must not depend on vendor animation alpha.
 */
public class SecurityCenterMaterialAuthorityDesignTest {
    @Test
    public void materialEpochTracksCarrierIdentityInsteadOfRootIdentity() throws Exception {
        Object state = newMaterialEpochState();
        Method bind = state.getClass().getDeclaredMethod(
                "bindAssistant", Object.class, Object.class, Object.class, int.class);
        Method generation = state.getClass().getDeclaredMethod("generation");
        bind.setAccessible(true);
        generation.setAccessible(true);

        Object turbo = new Object();
        Object dockOne = new Object();
        Object gameOne = new Object();
        Object dockTwo = new Object();
        Object gameTwo = new Object();

        assertTrue((Boolean) bind.invoke(state, turbo, dockOne, gameOne, 1));
        assertEquals(1L, ((Number) generation.invoke(state)).longValue());

        assertFalse("rebinding the same live carriers must not create a fake epoch",
                (Boolean) bind.invoke(state, turbo, dockOne, gameOne, 1));
        assertEquals(1L, ((Number) generation.invoke(state)).longValue());

        assertTrue("same TurboLayout with rebuilt material carriers must create a new material epoch",
                (Boolean) bind.invoke(state, turbo, dockTwo, gameTwo, 1));
        assertEquals(2L, ((Number) generation.invoke(state)).longValue());
    }

    @Test
    public void allAppsAttachAndRemoveAreMaterialEpochBoundaries() throws Exception {
        Object state = newMaterialEpochState();
        Method bind = state.getClass().getDeclaredMethod(
                "bindAssistant", Object.class, Object.class, Object.class, int.class);
        Method attach = state.getClass().getDeclaredMethod(
                "attachAllApps", Object.class, Object.class);
        Method detach = state.getClass().getDeclaredMethod(
                "detachAllApps", Object.class, Object.class);
        Method generation = state.getClass().getDeclaredMethod("generation");
        bind.setAccessible(true);
        attach.setAccessible(true);
        detach.setAccessible(true);
        generation.setAccessible(true);

        Object turbo = new Object();
        Object dock = new Object();
        Object game = new Object();
        Object apps = new Object();
        bind.invoke(state, turbo, dock, game, 1);

        assertTrue((Boolean) attach.invoke(state, turbo, apps));
        assertEquals(2L, ((Number) generation.invoke(state)).longValue());
        assertFalse("duplicate observation of the same All Apps carrier must be idempotent",
                (Boolean) attach.invoke(state, turbo, apps));
        assertEquals(2L, ((Number) generation.invoke(state)).longValue());

        assertFalse("a stale TurboLayout may not retire the current All Apps carrier",
                (Boolean) detach.invoke(state, new Object(), apps));
        assertEquals(2L, ((Number) generation.invoke(state)).longValue());

        assertTrue((Boolean) detach.invoke(state, turbo, apps));
        assertEquals(3L, ((Number) generation.invoke(state)).longValue());
    }

    @Test
    public void rootCropChangesPresentationUvWithoutChangingGlassShape() throws Exception {
        SecurityCenterGlassGeometry geometry = SecurityCenterGlassGeometry.resolve(
                1200, 1800,
                0f, 0f,
                180f, 260f, 1100f, 1780f,
                48f);
        Method rootCrop;
        try {
            rootCrop = SecurityCenterGlassGeometry.class.getDeclaredMethod("withRootCrop");
        } catch (NoSuchMethodException missing) {
            fail("SecurityCenterGlassGeometry.withRootCrop() is required for All Apps root-space output");
            return;
        }
        rootCrop.setAccessible(true);
        SecurityCenterGlassGeometry cropped =
                (SecurityCenterGlassGeometry) rootCrop.invoke(geometry);

        PrismalGeometry before = geometry.toPrismalGeometry();
        PrismalGeometry after = cropped.toPrismalGeometry();
        assertEquals(before.centerX, after.centerX, 0.001f);
        assertEquals(before.centerY, after.centerY, 0.001f);
        assertEquals(before.glassWidth, after.glassWidth, 0.001f);
        assertEquals(before.glassHeight, after.glassHeight, 0.001f);

        Method crop = SecurityCenterGlassGeometry.class.getDeclaredMethod("toCropUvRect");
        crop.setAccessible(true);
        assertArrayEquals(new float[]{0f, 0f, 1f, 1f},
                (float[]) crop.invoke(cropped), 0.0001f);
    }

    @Test
    public void presentationReadinessIsIndependentFromAnimationAlpha() throws Exception {
        Method ready;
        try {
            ready = SecurityCenterSinkPresentationState.class.getDeclaredMethod(
                    "isPresentationReady",
                    boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
        } catch (NoSuchMethodException missing) {
            fail("presentation readiness must be an alpha-independent structural policy");
            return;
        }
        ready.setAccessible(true);

        assertTrue((Boolean) ready.invoke(null, true, true, true, true, true));
        assertFalse((Boolean) ready.invoke(null, false, true, true, true, true));
        assertFalse((Boolean) ready.invoke(null, true, true, true, true, false));
    }

    @Test
    public void sinkHasTypedOverlayHostResolutionInsteadOfDirectParentAuthority() {
        boolean hasOverlayHost = Arrays.stream(SecurityCenterGlassSinkView.class.getDeclaredClasses())
                .anyMatch(type -> type.getSimpleName().equals("OverlayHost"));
        boolean hasResolver = Arrays.stream(SecurityCenterGlassSinkView.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("resolveOverlayHost")
                        && method.getParameterCount() == 1);

        assertTrue("sink needs a typed outer overlay host", hasOverlayHost);
        assertTrue("sink needs a typed overlay-host resolver", hasResolver);
    }

    @Test
    public void sinkAttachmentRequiresExplicitSemanticMaterialRole() {
        Method[] methods = SecurityCenterGlassSinkView.class.getDeclaredMethods();
        boolean hasLegacyAttach = Arrays.stream(methods)
                .anyMatch(method -> method.getName().equals("attachBefore")
                        && method.getParameterCount() == 2);
        boolean hasTypedAttach = Arrays.stream(methods)
                .anyMatch(method -> method.getName().equals("attachBefore")
                        && method.getParameterCount() == 3
                        && method.getParameterTypes()[2]
                                == SecurityCenterSinkOutputPolicy.MaterialRole.class);

        assertFalse("every sink must declare Dock/Toolbox/All Apps semantics explicitly",
                hasLegacyAttach);
        assertTrue("typed material-role attachment is the only supported sink boundary",
                hasTypedAttach);
    }

    @Test
    public void coordinatorUsesCarrierEpochInsteadOfParallelPageSceneState() throws Exception {
        boolean hasMaterialEpoch = Arrays.stream(SecurityCenterGlassCoordinator.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == SecurityCenterMaterialEpochState.class);
        boolean hasLegacyScene = Arrays.stream(SecurityCenterGlassCoordinator.class.getDeclaredFields())
                .anyMatch(field -> field.getType().getSimpleName().equals("SecurityCenterGlassSceneState"));
        boolean hasLegacySettle = Arrays.stream(SecurityCenterGlassCoordinator.class.getDeclaredFields())
                .anyMatch(field -> field.getType().getSimpleName().equals("SecurityCenterAllAppsSettleState"));
        boolean hasLegacyTarget = Arrays.stream(SecurityCenterGlassCoordinator.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("targetKind"));

        assertTrue("material carrier identity must be coordinator lifecycle authority", hasMaterialEpoch);
        assertFalse("page-scene state must not shadow carrier lifecycle", hasLegacyScene);
        assertFalse("All Apps settle state must not synthesize a second lifecycle", hasLegacySettle);
        assertFalse("targetKind must be derived from live carriers, not cached page state", hasLegacyTarget);

        Method sourceRollover = SecurityCenterGlassCoordinator.class.getDeclaredMethod(
                "onSourceAuthorityChanged", Object.class, Object.class);
        assertEquals(boolean.class, sourceRollover.getReturnType());
    }

    private static Object newMaterialEpochState() throws Exception {
        final Class<?> type;
        try {
            type = Class.forName(
                    "com.hellovoid.liquiddock.SecurityCenterMaterialEpochState");
        } catch (ClassNotFoundException missing) {
            fail("SecurityCenterMaterialEpochState is required by the material-authority design");
            throw missing;
        }
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
