package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Typed compatibility contract for Security Center's native activity source authority. */
public class SecurityCenterSourceAuthorityContractResolverTest {
    @Test
    public void activityAuthorityResolvesByExactCallbackShape() {
        SecurityCenterSourceAuthorityContractResolver.Contract contract =
                SecurityCenterSourceAuthorityContractResolver.resolveForTest(
                        ServiceWithAuthority.class, FakeComponent.class);

        assertSame(ActivityAuthority.class, contract.listenerClass());
        assertEquals("onActivityChanged", contract.onActivityChanged().getName());
        assertEquals(2, contract.onActivityChanged().getParameterCount());
        assertSame(FakeComponent.class, contract.onActivityChanged().getParameterTypes()[0]);
        assertSame(FakeComponent.class, contract.onActivityChanged().getParameterTypes()[1]);
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

    public static class ActivityAuthority {
        public void onActivityChanged(FakeComponent previous, FakeComponent current) {}
    }

    public static class NoiseAuthority {
        public void onActivityChanged(String previous, String current) {}
    }

    public static class ServiceWithAuthority {
        private final ActivityAuthority activity = new ActivityAuthority();
        private final NoiseAuthority noise = new NoiseAuthority();
    }

    public static class ServiceWithoutAuthority {
        private final NoiseAuthority noise = new NoiseAuthority();
    }

    public static class ServiceWithTwoAuthorities {
        private final ActivityAuthority first = new ActivityAuthority();
        private final ActivityAuthority second = new ActivityAuthority();
    }
}
