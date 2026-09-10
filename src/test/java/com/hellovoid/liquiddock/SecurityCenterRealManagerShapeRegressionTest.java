package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** Regression coverage for the real Security Center manager method shape. */
public class SecurityCenterRealManagerShapeRegressionTest {
    private static final Set<String> RESOURCES = Set.of(
            "dimen:dp_24", "dimen:game_toolbox_background_radius", "id:main_content");

    @Test
    public void extraPrivateWrapperBooleanMethodsDoNotHideTheTwoTeardownAuthorities() {
        SecurityCenterSemanticContractResolver.ResolvedContract contract =
                SecurityCenterSemanticContractResolver.resolveForTest(
                        Turbo.class, FakeView.class, RESOURCES);

        assertSame(NoisyManager.class, contract.managerClass());
        assertEquals("d2", contract.removeAnimated().getName());
        assertEquals("f2", contract.removeWithoutAnimation().getName());
    }

    public static class FakeView {}

    public static class Wrapper {
        public Turbo C() { return null; }
    }

    /** Mirrors the real 40011320/40011355 manager: six private (wrapper, boolean) void methods. */
    public static class NoisyManager {
        private void I2(Wrapper wrapper, boolean value) {}
        private void Y(Wrapper wrapper, boolean value) {}
        private void d2(Wrapper wrapper, boolean value) {}
        private void f2(Wrapper wrapper, boolean value) {}
        private void s0(Wrapper wrapper, boolean value) {}
        private void u0(Wrapper wrapper, boolean value) {}
    }

    public static class Type {
        private int value;
        public void a(int next) { value = next; }
        public int c() { return value; }
        public int d() { return value + 10; }
    }

    public static class Dock extends FakeView {}
    public static class Apps extends FakeView {}
    public static class Box extends FakeView {}
    public static class Material extends FakeView { public void o() {} }
    public static class Game extends FakeView { public Material getMainView() { return null; } }
    public static class Video { public void t() {} }

    public static class Turbo extends FakeView {
        private Wrapper wrapper;
        private NoisyManager manager;
        private Type type;
        private boolean q;
        private boolean s;

        public void V(Wrapper w, boolean l, String p, int m, Type t,
                      boolean a, boolean b, boolean c) {}
        public void c0() {}
        public void d0() {}
        public void U() {}
        public Dock getDockLayout() { return null; }
        public Apps getAppsLayout() { return null; }
        public Box getBoxView() { return null; }
        public Game getGameTurboLayout() { return null; }
        public Video getVideoBoxViewAdapter() { return null; }
    }
}
