package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import io.github.libxposed.api.XposedInterface;

/** Owner-domain lifecycle tests use fake framework handles, not injected target processes. */
public class Api102HookDomainTest {
    static final class Target {
        void observed(int value) { }
        void observed(String value) { }
    }

    private static XposedInterface.HookHandle handle(
            String id, List<String> events, AtomicBoolean failUnhook) {
        return (XposedInterface.HookHandle) Proxy.newProxyInstance(
                XposedInterface.HookHandle.class.getClassLoader(),
                new Class<?>[] {XposedInterface.HookHandle.class},
                (proxy, method, args) -> {
                    if ("unhook".equals(method.getName())) {
                        events.add(id);
                        if (failUnhook.get()) {
                            throw new IllegalStateException("injected unhook failure");
                        }
                    }
                    return null;
                });
    }

    @Test
    public void ownerCommitStopAndReinstallHaveNoDuplicateHandles() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean(false);
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> handle(id, events, fail));
        Api102HookDomain domain = new Api102HookDomain(registry, "systemui.keyguard.gone");
        Method one = Target.class.getDeclaredMethod("observed", int.class);
        Method two = Target.class.getDeclaredMethod("observed", String.class);
        XposedInterface.Hooker cb = chain -> chain.proceed();

        assertFalse(domain.isActive());
        domain.begin();
        assertEquals(Api102HookDomain.State.INSTALLING, domain.state());
        assertFalse(domain.isActive());
        String first = domain.hook(one, cb);
        String second = domain.hook(two, cb);
        assertFalse(first.equals(second));
        assertEquals(2, domain.ownedCount());
        assertThrows(IllegalStateException.class, domain::begin);
        domain.commit();
        assertTrue(domain.isActive());
        assertEquals(2, registry.count());
        assertThrows(IllegalStateException.class, () -> domain.hook(one, cb));
        assertTrue(domain.stop());
        assertFalse(domain.isActive());
        assertEquals(Api102HookDomain.State.IDLE, domain.state());
        assertEquals(0, registry.count());
        assertEquals(Arrays.asList(second, first), events);
        assertTrue(domain.stop()); // idempotent
        assertEquals(2, events.size());

        domain.begin();
        domain.hook(one, cb);
        domain.commit();
        assertEquals(1, registry.count());
        assertTrue(domain.stop());
    }

    @Test
    public void incompleteInstallationAbortsWithoutPublishingActiveCallbacks() throws Exception {
        List<String> events = new ArrayList<>();
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> {
                    if (id.endsWith("java.lang.String)")) {
                        throw new IllegalStateException("injected install failure");
                    }
                    return handle(id, events, new AtomicBoolean(false));
                });
        Api102HookDomain domain = new Api102HookDomain(registry, "pilot");
        Method one = Target.class.getDeclaredMethod("observed", int.class);
        Method two = Target.class.getDeclaredMethod("observed", String.class);
        domain.begin();
        domain.hook(one, chain -> chain.proceed());
        assertThrows(IllegalStateException.class,
                () -> domain.hook(two, chain -> chain.proceed()));
        assertFalse(domain.isActive());
        assertTrue(domain.abort());
        assertEquals(Api102HookDomain.State.IDLE, domain.state());
        assertEquals(0, domain.ownedCount());
        assertEquals(0, registry.count());
        assertEquals(1, events.size());
    }

    @Test
    public void failedStopDisarmsDomainAndForbidsReinstallUntilAllHandlesAreGone()
            throws Exception {
        List<String> events = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean(true);
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> handle(id, events, fail));
        Api102HookDomain domain = new Api102HookDomain(registry, "pilot");
        Method one = Target.class.getDeclaredMethod("observed", int.class);
        domain.begin();
        domain.hook(one, chain -> chain.proceed());
        domain.commit();
        assertTrue(domain.isActive());
        assertFalse(domain.stop());
        assertEquals(Api102HookDomain.State.BLOCKED, domain.state());
        assertFalse(domain.isActive());
        assertEquals(1, registry.count());
        assertThrows(IllegalStateException.class, domain::begin);
        fail.set(false);
        assertTrue(domain.stop());
        assertEquals(Api102HookDomain.State.IDLE, domain.state());
        assertEquals(0, registry.count());
        domain.begin();
        assertTrue(domain.abort()); // empty install cleans up
    }

    @Test
    public void dynamicHookIsTrackedAfterCommitAndStoppedWithItsOwner() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean(false);
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> handle(id, events, fail));
        Api102HookDomain domain = new Api102HookDomain(registry, "systemui.gesture.handle");
        Method first = Target.class.getDeclaredMethod("observed", int.class);
        Method next = Target.class.getDeclaredMethod("observed", String.class);
        XposedInterface.Hooker callback = chain -> chain.proceed();

        assertThrows(IllegalStateException.class, () -> domain.hookDynamic(next, callback));
        domain.begin();
        domain.hook(first, callback);
        assertThrows(IllegalStateException.class, () -> domain.hookDynamic(next, callback));
        domain.commit();
        String id = domain.hookDynamic(next, callback);
        assertEquals(2, registry.count());
        assertEquals(2, domain.ownedCount());
        assertThrows(IllegalStateException.class, () -> domain.hookDynamic(next, callback));
        assertTrue(domain.stop());
        assertEquals(0, registry.count());
        assertEquals(id, Api102HookRegistry.stableId("systemui.gesture.handle", next));
        assertEquals(List.of(id, Api102HookRegistry.stableId("systemui.gesture.handle", first)),
                events);
        assertThrows(IllegalStateException.class, () -> domain.hookDynamic(next, callback));
    }

    private static XposedInterface.HookHandle replaceableHandle(
            int generation, AtomicInteger replacements, List<Integer> unhooked,
            AtomicBoolean failReplacement) {
        return (XposedInterface.HookHandle) Proxy.newProxyInstance(
                XposedInterface.HookHandle.class.getClassLoader(),
                new Class<?>[] {XposedInterface.HookHandle.class},
                (proxy, method, args) -> {
                    if ("replaceHook".equals(method.getName())) {
                        if (failReplacement.get()) {
                            throw new IllegalStateException("injected replacement failure");
                        }
                        replacements.incrementAndGet();
                        return replaceableHandle(generation + 1, replacements,
                                unhooked, failReplacement);
                    }
                    if ("unhook".equals(method.getName())) unhooked.add(generation);
                    return null;
                });
    }

    @Test
    public void activeOwnerReplacesOnlyItsHookWithoutReinstallOrUnhookGap() throws Exception {
        AtomicInteger installations = new AtomicInteger();
        AtomicInteger replacements = new AtomicInteger();
        AtomicBoolean failReplacement = new AtomicBoolean(false);
        List<Integer> unhooked = new ArrayList<>();
        Api102HookRegistry registry = new Api102HookRegistry((method, id, callback) -> {
            installations.incrementAndGet();
            return replaceableHandle(0, replacements, unhooked, failReplacement);
        });
        Api102HookDomain domain = new Api102HookDomain(registry, "systemui.gesture.handle");
        Method observed = Target.class.getDeclaredMethod("observed", int.class);
        Method other = Target.class.getDeclaredMethod("observed", String.class);
        XposedInterface.Hooker hooker = chain -> chain.proceed();

        assertThrows(IllegalStateException.class, () -> domain.replaceActive(observed, hooker));
        domain.begin();
        domain.hook(observed, hooker);
        assertThrows(IllegalStateException.class, () -> domain.replaceActive(observed, hooker));
        domain.commit();
        assertThrows(IllegalStateException.class, () -> domain.replaceActive(other, hooker));

        domain.replaceActive(observed, chain -> chain.proceed());
        domain.replaceActive(observed, chain -> chain.proceed());
        assertEquals(1, installations.get());
        assertEquals(2, replacements.get());
        assertTrue(unhooked.isEmpty());
        assertEquals(1, registry.count());
        assertEquals(1, domain.ownedCount());

        failReplacement.set(true);
        assertThrows(IllegalStateException.class,
                () -> domain.replaceActive(observed, chain -> chain.proceed()));
        assertEquals(2, replacements.get());
        assertEquals(1, registry.count());

        assertTrue(domain.stop());
        assertEquals(List.of(2), unhooked); // only the newest valid handle is retired
        assertEquals(0, registry.count());
        assertThrows(IllegalStateException.class, () -> domain.replaceActive(observed, hooker));
    }

    @Test
    public void stoppedDomainDoesNotRemoveOtherOwnerHook() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean(false);
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> handle(id, events, fail));
        Api102HookDomain a = new Api102HookDomain(registry, "domain.a");
        Api102HookDomain b = new Api102HookDomain(registry, "domain.b");
        Method m = Target.class.getDeclaredMethod("observed", int.class);
        a.begin();
        a.hook(m, chain -> chain.proceed());
        a.commit();
        b.begin();
        b.hook(m, chain -> chain.proceed());
        b.commit();
        assertEquals(2, registry.count());
        assertTrue(a.stop());
        assertTrue(b.isActive());
        assertEquals(1, registry.count());
        assertTrue(b.stop());
        assertEquals(0, registry.count());
    }
}
