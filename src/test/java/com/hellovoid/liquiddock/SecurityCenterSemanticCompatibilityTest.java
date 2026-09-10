package com.hellovoid.liquiddock;

import fixture.alpha.AlphaContract;
import fixture.beta.BetaContract;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SecurityCenterSemanticCompatibilityTest {
    private static final Set<String> RESOURCES = Set.of(
            "dimen:dp_24", "dimen:game_toolbox_background_radius", "id:main_content");

    @Test
    public void renamedContractsResolveToTheSameSemanticRoles() throws Exception {
        Object alpha = resolve(AlphaContract.Turbo.class, AlphaContract.FakeView.class, RESOURCES);
        Object beta = resolve(BetaContract.GlassHost.class, BetaContract.RenderNode.class, RESOURCES);

        assertRole(alpha, "wrapperClass", AlphaContract.Wrapper.class);
        assertRole(alpha, "managerClass", AlphaContract.Manager.class);
        assertRole(alpha, "assistantTypeClass", AlphaContract.AssistantType.class);
        assertRole(alpha, "gameBoxClass", AlphaContract.GameBox.class);
        assertRole(alpha, "gameMaterialClass", AlphaContract.GameMaterial.class);
        assertRole(alpha, "videoAdapterClass", AlphaContract.VideoAdapter.class);
        assertRole(alpha, "allAppsClass", AlphaContract.AllAppsView.class);

        assertRole(beta, "wrapperClass", BetaContract.RailShell.class);
        assertRole(beta, "managerClass", BetaContract.WindowAuthority.class);
        assertRole(beta, "assistantTypeClass", BetaContract.ModeToken.class);
        assertRole(beta, "gameBoxClass", BetaContract.GameContainer.class);
        assertRole(beta, "gameMaterialClass", BetaContract.GameSurface.class);
        assertRole(beta, "videoAdapterClass", BetaContract.MediaBridge.class);
        assertRole(beta, "allAppsClass", BetaContract.AppSurface.class);

        Method alphaDiscriminator = (Method) invoke(alpha, "assistantTypeDiscriminator");
        Method betaDiscriminator = (Method) invoke(beta, "assistantTypeDiscriminator");
        AlphaContract.AssistantType alphaType = new AlphaContract.AssistantType();
        BetaContract.ModeToken betaType = new BetaContract.ModeToken();
        for (int semanticType : new int[]{1, 3, 4}) {
            alphaType.a(semanticType);
            betaType.a(semanticType);
            assertEquals(semanticType,
                    ((Number) alphaDiscriminator.invoke(alphaType)).intValue());
            assertEquals(semanticType,
                    ((Number) betaDiscriminator.invoke(betaType)).intValue());
        }
    }

    @Test
    public void resolutionIsCapabilityDrivenNotVersionWhitelisted() throws Exception {
        Method method = resolverClass().getDeclaredMethod(
                "resolveForTest", Class.class, Class.class, Set.class);
        assertEquals(3, method.getParameterCount());
        for (Class<?> parameter : method.getParameterTypes()) {
            assertFalse("versionCode must not control capability resolution",
                    parameter == long.class || parameter == Long.class);
        }
        assertTrue(resolve(AlphaContract.Turbo.class, AlphaContract.FakeView.class, RESOURCES) != null);
    }

    @Test
    public void legacy40011261LikeShapeIsRejected() throws Exception {
        expectRejected(LegacyTurbo.class, FakeView.class, RESOURCES, "legacy shape");
    }

    @Test
    public void missingOrAmbiguousCapabilitiesRejectWholeContract() throws Exception {
        expectRejected(AmbiguousManagerTurbo.class, FakeView.class, RESOURCES, "ambiguous manager");
        expectRejected(MissingTeardownTurbo.class, FakeView.class, RESOURCES, "missing teardown");
        expectRejected(MissingDiscriminatorTurbo.class, FakeView.class, RESOURCES, "type discriminator");
        expectRejected(MissingMaterialTurbo.class, FakeView.class, RESOURCES, "material carrier");
        expectRejected(MissingAdapterTurbo.class, FakeView.class, RESOURCES, "adapter");
        Set<String> missingResource = new HashSet<>(RESOURCES);
        missingResource.remove("id:main_content");
        expectRejected(ValidTurbo.class, FakeView.class, missingResource, "resource");
    }

    @Test
    public void failedContractCannotEnableMutationCallbacks() throws Exception {
        Object state = activationStateClass().getDeclaredConstructor().newInstance();
        assertFalse((Boolean) invoke(state, "allowsMutation"));
        invoke(state, "onCallbacksRegistered");
        assertFalse("registration before final validation must stay inert",
                (Boolean) invoke(state, "allowsMutation"));
        invoke(state, "onValidationFailed");
        assertFalse((Boolean) invoke(state, "allowsMutation"));
    }

    @Test
    public void callbacksEnableOnlyAfterFinalValidatedCommit() throws Exception {
        Object state = activationStateClass().getDeclaredConstructor().newInstance();
        invoke(state, "onCallbacksRegistered");
        invoke(state, "onValidationCommitted");
        assertTrue((Boolean) invoke(state, "allowsMutation"));
    }

    @Test
    public void repeatedFailuresEmitOneTipOnly() throws Exception {
        Class<?> type = tipPolicyClass();
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object policy = constructor.newInstance();
        assertTrue((Boolean) invoke(policy, "shouldEmit"));
        assertFalse((Boolean) invoke(policy, "shouldEmit"));
        assertFalse((Boolean) invoke(policy, "shouldEmit"));
    }

    private static Object resolve(Class<?> turbo, Class<?> view, Set<String> resources)
            throws Exception {
        try {
            Method method = resolverClass().getDeclaredMethod(
                    "resolveForTest", Class.class, Class.class, Set.class);
            method.setAccessible(true);
            return method.invoke(null, turbo, view, resources);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof IllegalStateException) throw (IllegalStateException) cause;
            throw new AssertionError("semantic resolver invocation failed", cause);
        }
    }

    private static void expectRejected(Class<?> turbo, Class<?> view, Set<String> resources,
                                       String expectedReason) throws Exception {
        try {
            resolve(turbo, view, resources);
            fail("expected fail-closed rejection for " + expectedReason);
        } catch (IllegalStateException expected) {
            assertTrue("reason should identify the rejected capability: " + expected,
                    expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    private static void assertRole(Object contract, String method, Class<?> expected) throws Exception {
        assertSame(expected, invoke(contract, method));
    }

    private static Object invoke(Object target, String method, Object... args) throws Exception {
        Method selected = null;
        for (Method candidate : target.getClass().getDeclaredMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) throw new NoSuchMethodException(target.getClass().getName() + "#" + method);
        selected.setAccessible(true);
        return selected.invoke(target, args);
    }

    private static Class<?> resolverClass() {
        return requireClass("com.hellovoid.liquiddock.SecurityCenterSemanticContractResolver",
                "semantic resolver is required");
    }

    private static Class<?> activationStateClass() {
        return requireClass("com.hellovoid.liquiddock.SecurityCenterHookActivationState",
                "fail-closed activation state is required");
    }

    private static Class<?> tipPolicyClass() {
        return requireClass("com.hellovoid.liquiddock.SecurityCenterOneShotTipPolicy",
                "one-shot tip policy is required");
    }

    private static Class<?> requireClass(String name, String message) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException error) {
            throw new AssertionError(message, error);
        }
    }

    public static class FakeView {}
    public static class Wrapper { public ValidTurbo C() { return null; } }
    public static class Manager {
        private void d2(Wrapper wrapper, boolean animate) {}
        private void f2(Wrapper wrapper, boolean moveSidebar) {}
    }
    public static class SecondManager {
        private void d2(Wrapper wrapper, boolean animate) {}
        private void f2(Wrapper wrapper, boolean moveSidebar) {}
    }
    public static class MissingF2Manager { private void d2(Wrapper wrapper, boolean animate) {} }
    public static class Type {
        private int value;
        public void a(int next) { value = next; }
        public int c() { return value; }
        public int d() { return value + 10; }
    }
    public static class BadType {
        public void a(int next) {}
        public int c() { return 0; }
    }
    public static class Dock extends FakeView {}
    public static class Apps extends FakeView {}
    public static class Box extends FakeView {}
    public static class Material extends FakeView { public void o() {} }
    public static class Game extends FakeView { public Material getMainView() { return null; } }
    public static class BadGame extends FakeView {}
    public static class Video { public void t() {} }
    public static class BadVideo {}

    public static class ValidTurbo extends FakeView {
        private Wrapper wrapper; private Manager manager; private Type type; private boolean q; private boolean s;
        public void V(Wrapper w, boolean l, String p, int m, Type t, boolean a, boolean b, boolean c) {}
        public void c0() {} public void d0() {} public void U() {}
        public Wrapper getSidebarWrapper() { return wrapper; }
        public Dock getDockLayout() { return null; }
        public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; }
        public Game getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }
    public static class LegacyTurbo extends FakeView {
        private Wrapper wrapper; private Manager manager;
        public Wrapper getSidebarWrapper() { return wrapper; }
        public Dock getDockLayout() { return null; }
        public Box getBoxView() { return null; }
    }
    public static class AmbiguousManagerTurbo extends ValidTurbo { private SecondManager secondManager; }
    public static class MissingTeardownTurbo extends FakeView {
        private Wrapper wrapper; private MissingF2Manager manager; private Type type; private boolean q; private boolean s;
        public void V(Wrapper w, boolean l, String p, int m, Type t, boolean a, boolean b, boolean c) {}
        public void c0() {} public void d0() {} public void U() {}
        public Wrapper getSidebarWrapper() { return wrapper; }
        public Dock getDockLayout() { return null; } public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; } public Game getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }
    public static class MissingDiscriminatorTurbo extends FakeView {
        private Wrapper wrapper; private Manager manager; private BadType type; private boolean q; private boolean s;
        public void V(Wrapper w, boolean l, String p, int m, BadType t, boolean a, boolean b, boolean c) {}
        public void c0() {} public void d0() {} public void U() {}
        public Wrapper getSidebarWrapper() { return wrapper; }
        public Dock getDockLayout() { return null; } public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; } public Game getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }
    public static class MissingMaterialTurbo extends FakeView {
        private Wrapper wrapper; private Manager manager; private Type type; private boolean q; private boolean s;
        public void V(Wrapper w, boolean l, String p, int m, Type t, boolean a, boolean b, boolean c) {}
        public void c0() {} public void d0() {} public void U() {}
        public Wrapper getSidebarWrapper() { return wrapper; }
        public Dock getDockLayout() { return null; } public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; } public BadGame getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }
    public static class MissingAdapterTurbo extends FakeView {
        private Wrapper wrapper; private Manager manager; private Type type; private boolean q; private boolean s;
        public void V(Wrapper w, boolean l, String p, int m, Type t, boolean a, boolean b, boolean c) {}
        public void c0() {} public void d0() {} public void U() {}
        public Wrapper getSidebarWrapper() { return wrapper; }
        public Dock getDockLayout() { return null; } public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; } public Game getGameTurboLayout() { return null; }
        public BadVideo getVideoBoxViewAdapter() { return null; }
    }
}
