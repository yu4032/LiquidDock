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
            alphaType.setCode(semanticType);
            betaType.setActive(semanticType);
            assertEquals(semanticType,
                    ((Number) alphaDiscriminator.invoke(alphaType)).intValue());
            assertEquals(semanticType,
                    ((Number) betaDiscriminator.invoke(betaType)).intValue());
        }
    }

    @Test
    public void allAppsMotionResolvesByStructureNotMethodNames() {
        SecurityCenterSemanticContractResolver.AllAppsMotionContract first =
                SecurityCenterSemanticContractResolver.resolveAllAppsMotionForTest(
                        FirstMotionTurbo.class, FakeView.class);
        SecurityCenterSemanticContractResolver.AllAppsMotionContract second =
                SecurityCenterSemanticContractResolver.resolveAllAppsMotionForTest(
                        SecondMotionTurbo.class, FakeView.class);

        assertSame(FirstMotionHelper.class, first.helperClass());
        assertEquals("attachPanel", first.attach().getName());
        assertEquals("dismissPanel", first.dismiss().getName());
        assertEquals("dismissPanelAt", first.dismissToPoint().getName());

        assertSame(SecondMotionHelper.class, second.helperClass());
        assertEquals("mountSurface", second.attach().getName());
        assertEquals("hideSurface", second.dismiss().getName());
        assertEquals("hideSurfaceAt", second.dismissToPoint().getName());
    }

    @Test
    public void allAppsMotionFailsClosedForMissingOrAmbiguousHelper() {
        expectMotionRejected(NoMotionTurbo.class, FakeView.class, "missing motion helper");
        expectMotionRejected(AmbiguousMotionTurbo.class, FakeView.class, "ambiguous motion helper");
    }

    @Test
    public void terminalCleanupResolvesByStructureNotMethodNames() {
        SecurityCenterSemanticContractResolver.TerminalCleanupContract first =
                SecurityCenterSemanticContractResolver.resolveTerminalCleanupForTest(
                        FirstTerminalManager.class, TerminalTurbo.class,
                        TerminalWrapper.class, FakeView.class);
        SecurityCenterSemanticContractResolver.TerminalCleanupContract second =
                SecurityCenterSemanticContractResolver.resolveTerminalCleanupForTest(
                        SecondTerminalManager.class, TerminalTurbo.class,
                        TerminalWrapper.class, FakeView.class);

        assertEquals(3, first.methods().size());
        assertEquals(3, second.methods().size());
    }

    @Test
    public void terminalCleanupFailsClosedForMissingOrAmbiguousAuthority() {
        expectTerminalCleanupRejected(MissingTerminalManager.class, "missing terminal cleanup");
        expectTerminalCleanupRejected(AmbiguousTerminalManager.class, "ambiguous terminal cleanup");
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
        assertTrue(resolve(ValidTurbo.class, FakeView.class, RESOURCES) != null);
    }

    @Test
    public void legacyShapeIsRejected() throws Exception {
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

    private static void expectMotionRejected(
            Class<?> turbo, Class<?> view, String expectedReason) {
        try {
            SecurityCenterSemanticContractResolver.resolveAllAppsMotionForTest(turbo, view);
            fail("expected fail-closed rejection for " + expectedReason);
        } catch (IllegalStateException expected) {
            assertTrue("reason should identify the rejected capability: " + expected,
                    expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    private static void expectTerminalCleanupRejected(
            Class<?> manager, String expectedReason) {
        try {
            SecurityCenterSemanticContractResolver.resolveTerminalCleanupForTest(
                    manager, TerminalTurbo.class, TerminalWrapper.class, FakeView.class);
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
    public static class FakeGroup extends FakeView {}
    public static class FakeLayoutParams {}
    public interface MotionAnchorProvider {
        void location(int[] out);
        int width();
        int height();
    }
    public interface MotionCompletion { void complete(); }

    public static class FirstMotionHelper {
        public FirstMotionHelper(FakeView anchor, MotionAnchorProvider provider) {}
        public void attachPanel(FakeGroup parent, FakeView apps, FakeLayoutParams params) {}
        public void dismissPanel(FakeView apps, MotionCompletion completion) {}
        public void dismissPanelAt(FakeView apps, MotionCompletion completion, int x, int y) {}
        private void animateEntry(FakeView apps) {}
        private void animateExit(FakeView apps, float x, float y, Runnable completion) {}
    }
    public static class SecondMotionHelper {
        public SecondMotionHelper(FakeView anchor, MotionAnchorProvider provider) {}
        public void mountSurface(FakeGroup parent, FakeView apps, FakeLayoutParams params) {}
        public void hideSurface(FakeView apps, MotionCompletion completion) {}
        public void hideSurfaceAt(FakeView apps, MotionCompletion completion, int x, int y) {}
        private void runEntry(FakeView apps) {}
        private void runExit(FakeView apps, float x, float y, Runnable completion) {}
    }
    public static class FirstMotionTurbo extends FakeView { private FirstMotionHelper motion; }
    public static class SecondMotionTurbo extends FakeView { private SecondMotionHelper renamedMotion; }
    public static class NoMotionTurbo extends FakeView { private String unrelated; }
    public static class AmbiguousMotionTurbo extends FakeView {
        private FirstMotionHelper first;
        private SecondMotionHelper second;
    }

    public static class TerminalTurbo extends FakeView {}
    public static class TerminalWrapper {}
    public static class FirstTerminalManager {
        private void finishPrimary(TerminalTurbo turbo, TerminalWrapper wrapper, FakeView panel) {}
        private void finishSecondary(TerminalTurbo turbo, TerminalWrapper wrapper, FakeView panel) {}
        private void finishWithMove(boolean moveSidebar, TerminalTurbo turbo,
                                    TerminalWrapper wrapper, FakeView panel) {}
    }
    public static class SecondTerminalManager {
        private void settleEdge(TerminalTurbo turbo, TerminalWrapper wrapper, FakeView panel) {}
        private void settlePanel(TerminalTurbo turbo, TerminalWrapper wrapper, FakeView panel) {}
        private void settleWithMove(boolean moveSidebar, TerminalTurbo turbo,
                                    TerminalWrapper wrapper, FakeView panel) {}
    }
    public static class MissingTerminalManager {
        private void unrelated(TerminalTurbo turbo, TerminalWrapper wrapper) {}
    }
    public static class AmbiguousTerminalManager {
        private void first(TerminalTurbo turbo, TerminalWrapper wrapper, FakeView panel) {}
        private void second(TerminalTurbo turbo, TerminalWrapper wrapper, FakeView panel) {}
        private void third(TerminalTurbo turbo, TerminalWrapper wrapper, FakeView panel) {}
        private void finish(boolean moveSidebar, TerminalTurbo turbo,
                            TerminalWrapper wrapper, FakeView panel) {}
    }

    public static class Wrapper { public ValidTurbo owner() { return null; } }
    public static class Manager {
        private void finishPrimary(ValidTurbo turbo, Wrapper wrapper, FakeView panel) {}
        private void finishSecondary(ValidTurbo turbo, Wrapper wrapper, FakeView panel) {}
        private void finishWithMove(boolean moveSidebar, ValidTurbo turbo,
                                    Wrapper wrapper, FakeView panel) {}
    }
    public static class SecondManager {
        private void finishPrimary(AmbiguousManagerTurbo turbo, Wrapper wrapper, FakeView panel) {}
        private void finishSecondary(AmbiguousManagerTurbo turbo, Wrapper wrapper, FakeView panel) {}
        private void finishWithMove(boolean moveSidebar, AmbiguousManagerTurbo turbo,
                                    Wrapper wrapper, FakeView panel) {}
    }
    public static class MissingManager {}

    public static class Type {
        private int value;
        public void setValue(int next) { value = next; }
        public int value() { return value; }
        public int presentation() { return value + 10; }
    }
    public static class BadType {
        public void setValue(int next) {}
        public int value() { return 0; }
    }

    public static class Dock extends FakeView {}
    public static class Apps extends FakeView {}
    public static class Box extends FakeView {}
    public static class Material extends FakeView {}
    public static class Game extends FakeView { public Material getMainView() { return null; } }
    public static class BadGame extends FakeView {}
    public static class Video {}

    public static class ValidTurbo extends FakeView {
        private Wrapper wrapper;
        private Manager manager;
        private Type type;

        public void configurePanel(Wrapper wrapper, boolean left, String pkg, int mode, Type type,
                                   boolean force, boolean vertical, boolean extra) {}
        public Dock getDockLayout() { return null; }
        public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; }
        public Game getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }

    public static class LegacyTurbo extends FakeView {
        private Wrapper wrapper;
        public Dock getDockLayout() { return null; }
        public Box getBoxView() { return null; }
    }

    public static class AmbiguousWrapper { public AmbiguousManagerTurbo owner() { return null; } }
    public static class AmbiguousPrimaryManager {
        private void finishPrimary(AmbiguousManagerTurbo turbo, AmbiguousWrapper wrapper, FakeView panel) {}
        private void finishSecondary(AmbiguousManagerTurbo turbo, AmbiguousWrapper wrapper, FakeView panel) {}
        private void finishWithMove(boolean moveSidebar, AmbiguousManagerTurbo turbo,
                                    AmbiguousWrapper wrapper, FakeView panel) {}
    }
    public static class AmbiguousSecondaryManager {
        private void settlePrimary(AmbiguousManagerTurbo turbo, AmbiguousWrapper wrapper, FakeView panel) {}
        private void settleSecondary(AmbiguousManagerTurbo turbo, AmbiguousWrapper wrapper, FakeView panel) {}
        private void settleWithMove(boolean moveSidebar, AmbiguousManagerTurbo turbo,
                                    AmbiguousWrapper wrapper, FakeView panel) {}
    }
    public static class AmbiguousManagerTurbo extends FakeView {
        private AmbiguousPrimaryManager firstManager;
        private AmbiguousSecondaryManager secondManager;
        private Type type;
        public void configurePanel(AmbiguousWrapper wrapper, boolean left, String pkg, int mode, Type type,
                                   boolean force, boolean vertical, boolean extra) {}
        public Dock getDockLayout() { return null; }
        public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; }
        public Game getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }

    public static class MissingWrapper { public MissingTeardownTurbo owner() { return null; } }
    public static class MissingTeardownTurbo extends FakeView {
        private MissingManager manager;
        private Type type;
        public void configurePanel(MissingWrapper wrapper, boolean left, String pkg, int mode, Type type,
                                   boolean force, boolean vertical, boolean extra) {}
        public Dock getDockLayout() { return null; }
        public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; }
        public Game getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }

    public static class BadTypeWrapper { public MissingDiscriminatorTurbo owner() { return null; } }
    public static class BadTypeManager {
        private void finishPrimary(MissingDiscriminatorTurbo turbo, BadTypeWrapper wrapper, FakeView panel) {}
        private void finishSecondary(MissingDiscriminatorTurbo turbo, BadTypeWrapper wrapper, FakeView panel) {}
        private void finishWithMove(boolean moveSidebar, MissingDiscriminatorTurbo turbo,
                                    BadTypeWrapper wrapper, FakeView panel) {}
    }
    public static class MissingDiscriminatorTurbo extends FakeView {
        private BadTypeManager manager;
        private BadType type;
        public void configurePanel(BadTypeWrapper wrapper, boolean left, String pkg, int mode, BadType type,
                                   boolean force, boolean vertical, boolean extra) {}
        public Dock getDockLayout() { return null; }
        public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; }
        public Game getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }

    public static class BadGameWrapper { public MissingMaterialTurbo owner() { return null; } }
    public static class BadGameManager {
        private void finishPrimary(MissingMaterialTurbo turbo, BadGameWrapper wrapper, FakeView panel) {}
        private void finishSecondary(MissingMaterialTurbo turbo, BadGameWrapper wrapper, FakeView panel) {}
        private void finishWithMove(boolean moveSidebar, MissingMaterialTurbo turbo,
                                    BadGameWrapper wrapper, FakeView panel) {}
    }
    public static class MissingMaterialTurbo extends FakeView {
        private BadGameManager manager;
        private Type type;
        public void configurePanel(BadGameWrapper wrapper, boolean left, String pkg, int mode, Type type,
                                   boolean force, boolean vertical, boolean extra) {}
        public Dock getDockLayout() { return null; }
        public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; }
        public BadGame getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }

    public static class BadAdapterWrapper { public MissingAdapterTurbo owner() { return null; } }
    public static class BadAdapterManager {
        private void finishPrimary(MissingAdapterTurbo turbo, BadAdapterWrapper wrapper, FakeView panel) {}
        private void finishSecondary(MissingAdapterTurbo turbo, BadAdapterWrapper wrapper, FakeView panel) {}
        private void finishWithMove(boolean moveSidebar, MissingAdapterTurbo turbo,
                                    BadAdapterWrapper wrapper, FakeView panel) {}
    }
    public static class MissingAdapterTurbo extends FakeView {
        private BadAdapterManager manager;
        private Type type;
        public void configurePanel(BadAdapterWrapper wrapper, boolean left, String pkg, int mode, Type type,
                                   boolean force, boolean vertical, boolean extra) {}
        public Dock getDockLayout() { return null; }
        public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; }
        public Game getGameTurboLayout() { return null; }
        public FakeView getVideoBoxViewAdapter() { return null; }
    }
}
