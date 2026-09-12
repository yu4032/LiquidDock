package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** Regression coverage for a manager with noisy same-arity methods around terminal cleanup. */
public class SecurityCenterRealManagerShapeRegressionTest {
    private static final Set<String> RESOURCES = Set.of(
            "dimen:dp_24", "dimen:game_toolbox_background_radius", "id:main_content");

    @Test
    public void noisyWrapperBooleanMethodsDoNotAffectTerminalCleanupAuthority() {
        SecurityCenterSemanticContractResolver.ResolvedContract contract =
                SecurityCenterSemanticContractResolver.resolveForTest(
                        Turbo.class, FakeView.class, RESOURCES);

        assertSame(NoisyManager.class, contract.managerClass());
        SecurityCenterSemanticContractResolver.TerminalCleanupContract terminal =
                SecurityCenterSemanticContractResolver.resolveTerminalCleanupForTest(
                        contract.managerClass(), Turbo.class, Wrapper.class, FakeView.class);
        assertEquals(3, terminal.methods().size());
    }

    public static class FakeView {}

    public static class Wrapper {
        public Turbo owner() { return null; }
    }

    public static class NoisyManager {
        private void setAnimationFlag(Wrapper wrapper, boolean value) {}
        private void setSidebarFlag(Wrapper wrapper, boolean value) {}
        private void setLayoutFlag(Wrapper wrapper, boolean value) {}
        private void setWindowFlag(Wrapper wrapper, boolean value) {}
        private void setDockFlag(Wrapper wrapper, boolean value) {}
        private void setModeFlag(Wrapper wrapper, boolean value) {}

        private void finishPrimary(Turbo turbo, Wrapper wrapper, FakeView panel) {}
        private void finishSecondary(Turbo turbo, Wrapper wrapper, FakeView panel) {}
        private void finishWithMove(boolean moveSidebar, Turbo turbo, Wrapper wrapper, FakeView panel) {}
    }

    public static class Type {
        private int value;
        public void setValue(int next) { value = next; }
        public int value() { return value; }
        public int presentation() { return value + 10; }
    }

    public static class Dock extends FakeView {}
    public static class Apps extends FakeView {}
    public static class Box extends FakeView {}
    public static class Material extends FakeView {}
    public static class Game extends FakeView { public Material getMainView() { return null; } }
    public static class Video {}

    public static class Turbo extends FakeView {
        private Wrapper wrapper;
        private NoisyManager manager;
        private Type type;

        public void configurePanel(Wrapper wrapper, boolean left, String pkg, int mode, Type type,
                                   boolean force, boolean vertical, boolean extra) {}
        public Dock getDockLayout() { return null; }
        public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; }
        public Game getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }
}
