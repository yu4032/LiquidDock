package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Typed compatibility contract for Security Center's native activity source authority. */
public class SecurityCenterSourceAuthorityContractResolverTest {
    @Test
    public void inheritedStubShapeResolvesRenamedConcreteActivityCallbackAfterServiceCreate() {
        ServiceWithAuthority service = new ServiceWithAuthority();
        SecurityCenterSourceAuthorityContractResolver.Contract contract =
                SecurityCenterSourceAuthorityContractResolver.resolveForTest(
                        ServiceWithAuthority.class, FakeComponent.class);

        assertSame(ActivityAuthorityStub.class, contract.listenerClass());
        Method callback = contract.resolveConcreteCallbackForTest(service);
        assertSame(ConcreteActivityAuthority.class, callback.getDeclaringClass());
        assertEquals("dispatchSourceChange", callback.getName());
        assertEquals(2, callback.getParameterCount());
        assertSame(FakeComponent.class, callback.getParameterTypes()[0]);
        assertSame(FakeComponent.class, callback.getParameterTypes()[1]);
    }

    @Test
    public void concreteActivityCallbackAmbiguityFailsClosed() {
        ServiceWithAmbiguousConcrete service = new ServiceWithAmbiguousConcrete();
        SecurityCenterSourceAuthorityContractResolver.Contract contract =
                SecurityCenterSourceAuthorityContractResolver.resolveForTest(
                        ServiceWithAmbiguousConcrete.class, FakeComponent.class);
        try {
            contract.resolveConcreteCallbackForTest(service);
            fail("expected concrete source-authority callback rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    @Test
    public void missingOrAmbiguousAuthorityFailsClosed() {
        expectRejected(ServiceWithoutAuthority.class);
        expectRejected(ServiceWithTwoAuthorities.class);
    }

    @Test
    public void customGlassStaysDisabledUntilLiveSourceAuthorityIsInstalled() {
        try {
            SecurityCenterGlassRuntimeState.initialize(null, true, true, true);
            assertFalse(SecurityCenterGlassRuntimeState.isEnabled());
            SecurityCenterGlassRuntimeState.onSourceAuthorityAvailable();
            assertTrue(SecurityCenterGlassRuntimeState.isEnabled());
        } finally {
            SecurityCenterGlassRuntimeState.initialize(null, false, false, false);
        }
    }

    private static void expectRejected(Class<?> serviceClass) {
        try {
            SecurityCenterSourceAuthorityContractResolver.resolveForTest(
                    serviceClass, FakeComponent.class);
            fail("expected source-authority contract rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    public static class FakeComponent {}

    public interface ActivityAuthority {
        void dispatchSourceChange(FakeComponent previous, FakeComponent current);
    }

    public abstract static class ActivityAuthorityStub implements ActivityAuthority {
        public void binderNoise() {}
    }

    public static class ConcreteActivityAuthority extends ActivityAuthorityStub {
        @Override
        public void dispatchSourceChange(FakeComponent previous, FakeComponent current) {}
    }

    public static class AmbiguousConcreteActivityAuthority extends ActivityAuthorityStub {
        @Override
        public void dispatchSourceChange(FakeComponent previous, FakeComponent current) {}
        public void duplicateSourceChange(FakeComponent previous, FakeComponent current) {}
    }

    public static class NoiseAuthority {
        public void onActivityChanged(String previous, String current) {}
    }

    public static class ServiceWithAuthority {
        private final ActivityAuthorityStub activity = new ConcreteActivityAuthority();
        private final NoiseAuthority noise = new NoiseAuthority();
    }

    public static class ServiceWithAmbiguousConcrete {
        private final ActivityAuthorityStub activity = new AmbiguousConcreteActivityAuthority();
        private final NoiseAuthority noise = new NoiseAuthority();
    }

    public static class ServiceWithoutAuthority {
        private final NoiseAuthority noise = new NoiseAuthority();
    }

    public static class ServiceWithTwoAuthorities {
        private final ActivityAuthorityStub first = new ConcreteActivityAuthority();
        private final ActivityAuthorityStub second = new ConcreteActivityAuthority();
    }
}
