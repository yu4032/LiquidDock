package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Typed compatibility contract for Security Center's native activity source authority. */
public class SecurityCenterSourceAuthorityContractResolverTest {
    @Test
    public void inheritedStubShapeResolvesConcreteActivityCallbackAfterServiceCreate() {
        ServiceWithAuthority service = new ServiceWithAuthority();
        SecurityCenterSourceAuthorityContractResolver.Contract contract =
                SecurityCenterSourceAuthorityContractResolver.resolveForTest(
                        ServiceWithAuthority.class, FakeComponent.class);

        assertSame(ActivityAuthorityStub.class, contract.listenerClass());
        Method callback = contract.resolveConcreteCallbackForTest(service);
        assertSame(ConcreteActivityAuthority.class, callback.getDeclaringClass());
        assertEquals("onActivityChanged", callback.getName());
        assertEquals(2, callback.getParameterCount());
        assertSame(FakeComponent.class, callback.getParameterTypes()[0]);
        assertSame(FakeComponent.class, callback.getParameterTypes()[1]);
    }

    @Test
    public void missingOrAmbiguousAuthorityFailsClosed() {
        expectRejected(ServiceWithoutAuthority.class);
        expectRejected(ServiceWithTwoAuthorities.class);
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
        void onActivityChanged(FakeComponent previous, FakeComponent current);
    }

    public abstract static class ActivityAuthorityStub implements ActivityAuthority {
        public void binderNoise() {}
    }

    public static class ConcreteActivityAuthority extends ActivityAuthorityStub {
        @Override
        public void onActivityChanged(FakeComponent previous, FakeComponent current) {}
    }

    public static class NoiseAuthority {
        public void onActivityChanged(String previous, String current) {}
    }

    public static class ServiceWithAuthority {
        private final ActivityAuthorityStub activity = new ConcreteActivityAuthority();
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
