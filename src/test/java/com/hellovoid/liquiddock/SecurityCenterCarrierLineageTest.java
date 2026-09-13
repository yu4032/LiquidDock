package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;

import org.junit.Test;

/** Material getters are usable only when their result belongs to the current TurboLayout subtree. */
public class SecurityCenterCarrierLineageTest {
    @Test
    public void directAndNestedCurrentCarriersBelongToTurbo() throws Exception {
        Fixture fixture = fixture();
        Object turbo = new Object();
        Object container = new Object();
        Object carrier = new Object();
        fixture.parents.put(container, turbo);
        fixture.parents.put(carrier, container);

        assertTrue(fixture.belongsTo(turbo, container));
        assertTrue(fixture.belongsTo(turbo, carrier));
    }

    @Test
    public void detachedOrReparentedStaleCarrierIsRejected() throws Exception {
        Fixture fixture = fixture();
        Object turbo = new Object();
        Object oldContainer = new Object();
        Object stale = new Object();
        fixture.parents.put(stale, oldContainer);

        assertFalse(fixture.belongsTo(turbo, stale));

        Object replacementContainer = new Object();
        Object replacement = new Object();
        fixture.parents.put(replacementContainer, turbo);
        fixture.parents.put(replacement, replacementContainer);
        assertTrue(fixture.belongsTo(turbo, replacement));
    }

    @Test
    public void unrelatedCycleFailsClosedInsteadOfLooping() throws Exception {
        Fixture fixture = fixture();
        Object turbo = new Object();
        Object first = new Object();
        Object second = new Object();
        fixture.parents.put(first, second);
        fixture.parents.put(second, first);

        assertFalse(fixture.belongsTo(turbo, first));
    }

    @Test
    public void nullInputsFailClosed() throws Exception {
        Fixture fixture = fixture();
        Object turbo = new Object();
        Object carrier = new Object();
        assertFalse(fixture.belongsTo(null, carrier));
        assertFalse(fixture.belongsTo(turbo, null));
    }

    private static Fixture fixture() throws Exception {
        final Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.SecurityCenterCarrierLineage");
        } catch (ClassNotFoundException missing) {
            fail("SecurityCenterCarrierLineage is required to reject stale material getters");
            throw missing;
        }
        Class<?> lookup = Class.forName(
                "com.hellovoid.liquiddock.SecurityCenterCarrierLineage$ParentLookup");
        Method belongsTo = type.getDeclaredMethod(
                "belongsTo", Object.class, Object.class, lookup);
        belongsTo.setAccessible(true);
        return new Fixture(lookup, belongsTo);
    }

    private static final class Fixture {
        final Map<Object, Object> parents = new IdentityHashMap<>();
        final Object lookup;
        final Method belongsTo;

        Fixture(Class<?> lookupType, Method belongsTo) {
            this.belongsTo = belongsTo;
            lookup = Proxy.newProxyInstance(
                    lookupType.getClassLoader(),
                    new Class<?>[]{lookupType},
                    (proxy, method, args) -> parents.get(args[0]));
        }

        boolean belongsTo(Object turbo, Object carrier) throws Exception {
            return (Boolean) belongsTo.invoke(null, turbo, carrier, lookup);
        }
    }
}
