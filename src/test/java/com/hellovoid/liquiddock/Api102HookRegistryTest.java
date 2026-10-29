package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import io.github.libxposed.api.XposedInterface;

/** Typed API 102 handle tests: no framework attachment or Android process required. */
public class Api102HookRegistryTest {
    static final class Target {
        void observed(int value) { }
        void observed(String value) { }
    }

    private static XposedInterface.HookHandle fakeHandle(
            String id, List<String> events, boolean failUnhook) {
        return (XposedInterface.HookHandle) Proxy.newProxyInstance(
                XposedInterface.HookHandle.class.getClassLoader(),
                new Class<?>[]{XposedInterface.HookHandle.class},
                (proxy, method, args) -> {
                    if ("unhook".equals(method.getName())) {
                        events.add(id);
                        if (failUnhook) throw new IllegalStateException("unhook failure");
                        return null;
                    }
                    if ("getId".equals(method.getName())) return id;
                    if ("toString".equals(method.getName())) return id;
                    return null;
                });
    }

    @Test
    public void stableIdsAreDeterministicAndSeparateOverloads() throws Exception {
        Method integer = Target.class.getDeclaredMethod("observed", int.class);
        Method string = Target.class.getDeclaredMethod("observed", String.class);
        String a = Api102HookRegistry.stableId("systemui.keyguard.gone", integer);
        String b = Api102HookRegistry.stableId("systemui.keyguard.gone", string);
        assertEquals(a, Api102HookRegistry.stableId("systemui.keyguard.gone", integer));
        assertTrue(a.startsWith("liquiddock.systemui.keyguard.gone:"));
        assertTrue(a.endsWith("#observed(int)"));
        assertTrue(b.endsWith("#observed(java.lang.String)"));
        assertFalse(a.equals(b));
        assertThrows(IllegalArgumentException.class,
                () -> Api102HookRegistry.stableId("", integer));
    }

    @Test
    public void duplicateIdIsRejectedWithoutCallingFrameworkInstallerTwice() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicInteger installs = new AtomicInteger();
        Api102HookRegistry registry = new Api102HookRegistry((method, id, callback) -> {
            installs.incrementAndGet();
            return fakeHandle(id, events, false);
        });
        Method method = Target.class.getDeclaredMethod("observed", int.class);
        XposedInterface.Hooker cb = chain -> chain.proceed();
        String id = Api102HookRegistry.stableId("pilot", method);
        registry.install(method, id, cb);
        assertEquals(1, registry.count());
        assertEquals(Arrays.asList(id), registry.idSnapshot());
        assertThrows(IllegalStateException.class, () -> registry.install(method, id, cb));
        assertEquals(1, installs.get());
        registry.rollback(List.of(id));
        assertEquals(List.of(id), events);
        assertEquals(0, registry.count());
    }

    @Test
    public void replacingAnIdentifiedHookKeepsOnlyTheNewLiveHandle() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicInteger replacements = new AtomicInteger();
        XposedInterface.HookHandle nextHandle = fakeHandle("new", events, false);
        XposedInterface.HookHandle oldHandle = (XposedInterface.HookHandle)
                Proxy.newProxyInstance(
                        XposedInterface.HookHandle.class.getClassLoader(),
                        new Class<?>[]{XposedInterface.HookHandle.class},
                        (proxy, method, args) -> {
                            if ("replaceHook".equals(method.getName())) {
                                replacements.incrementAndGet();
                                return nextHandle;
                            }
                            if ("unhook".equals(method.getName())) {
                                events.add("old");
                                return null;
                            }
                            return null;
                        });
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> oldHandle);
        Method method = Target.class.getDeclaredMethod("observed", int.class);
        registry.install(method, "stable", chain -> chain.proceed());
        assertSame(nextHandle, registry.replaceIdentified("stable", chain -> chain.proceed()));
        assertEquals(1, replacements.get());
        registry.rollback(List.of("stable"));
        assertEquals(List.of("new"), events);
        assertEquals(0, registry.count());
        assertThrows(IllegalStateException.class,
                () -> registry.replaceIdentified("absent", chain -> chain.proceed()));
    }

    @Test
    public void failedAtomicReplacementKeepsOldHandleAvailableForRollback() throws Exception {
        List<String> events = new ArrayList<>();
        XposedInterface.HookHandle old = (XposedInterface.HookHandle)
                Proxy.newProxyInstance(
                        XposedInterface.HookHandle.class.getClassLoader(),
                        new Class<?>[]{XposedInterface.HookHandle.class},
                        (proxy, method, args) -> {
                            if ("replaceHook".equals(method.getName())) {
                                throw new IllegalStateException("failed replacement");
                            }
                            if ("unhook".equals(method.getName())) events.add("old");
                            return null;
                        });
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> old);
        Method method = Target.class.getDeclaredMethod("observed", int.class);
        registry.install(method, "stable", chain -> chain.proceed());
        assertThrows(IllegalStateException.class,
                () -> registry.replaceIdentified("stable", chain -> chain.proceed()));
        assertEquals(1, registry.count());
        registry.rollback(List.of("stable"));
        assertEquals(List.of("old"), events);
    }

    private static XposedInterface.HookHandle siteHandle(
            String id, int generation, Map<String, Integer> unhooked,
            AtomicBoolean failReplacement, AtomicInteger replacementCount) {
        return (XposedInterface.HookHandle) Proxy.newProxyInstance(
                XposedInterface.HookHandle.class.getClassLoader(),
                new Class<?>[]{XposedInterface.HookHandle.class},
                (proxy, method, args) -> {
                    if ("replaceHook".equals(method.getName())) {
                        if (failReplacement.get()) {
                            throw new IllegalStateException("injected replace failure");
                        }
                        replacementCount.incrementAndGet();
                        return siteHandle(id, generation + 1, unhooked,
                                failReplacement, replacementCount);
                    }
                    if ("unhook".equals(method.getName())) unhooked.put(id, generation);
                    if ("getId".equals(method.getName())) return id;
                    return null;
                });
    }

    @Test
    public void allHookUtilSitesAreIndependentlyReplaceableWithoutStacking() throws Exception {
        List<String> ids = new ArrayList<>();
        List<Integer> priorities = new ArrayList<>();
        List<Executable> targets = new ArrayList<>();
        Map<String, Integer> unhooked = new LinkedHashMap<>();
        AtomicBoolean failReplacement = new AtomicBoolean(false);
        AtomicInteger replacements = new AtomicInteger();
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> fakeHandle(id, new ArrayList<>(), false),
                (executable, id, priority, callback) -> {
                    ids.add(id);
                    priorities.add(priority);
                    targets.add(executable);
                    return siteHandle(id, 0, unhooked, failReplacement, replacements);
                });
        Method sameMethod = Target.class.getDeclaredMethod("observed", int.class);
        Constructor<?> constructor = Target.class.getDeclaredConstructor();
        XposedInterface.Hooker hooker = chain -> chain.proceed();

        registry.installSite(sameMethod, "FeatureA#install:31", 50, hooker);
        registry.installSite(sameMethod, "FeatureB#install:90", 50, hooker);
        registry.installSite(sameMethod, "FeatureA#install:31", 100, hooker);
        registry.installSite(constructor, "CtorOwner#install:12", 50, hooker);
        assertEquals(4, registry.count());
        assertEquals(4, ids.size());
        assertEquals(Arrays.asList(50, 50, 100, 50), priorities);
        assertEquals(Arrays.asList(sameMethod, sameMethod, sameMethod, constructor), targets);
        assertEquals(4, new java.util.HashSet<>(ids).size());
        assertTrue("all HookUtil hooks are named", registry.unidentifiedCount() == 0);

        registry.installSite(sameMethod, "FeatureA#install:31", 50,
                chain -> chain.proceed());
        registry.installSite(constructor, "CtorOwner#install:12", 50,
                chain -> chain.proceed());
        assertEquals(2, replacements.get());
        assertEquals("atomic replace must not register again", 4, ids.size());
        assertEquals(4, registry.count());
        assertTrue("replacement must not unhook old callbacks", unhooked.isEmpty());

        failReplacement.set(true);
        assertThrows(IllegalStateException.class, () -> registry.installSite(
                sameMethod, "FeatureB#install:90", 50, chain -> chain.proceed()));
        assertEquals(4, registry.count());
        assertEquals(4, ids.size());
        assertEquals(2, replacements.get());
        assertTrue(unhooked.isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                registry.installSite(sameMethod, "", 50, hooker));
    }

    @Test
    public void sameTextualSiteButDifferentExecutableNeverReplacesOtherClass() throws Exception {
        List<String> ids = new ArrayList<>();
        AtomicInteger replacementCount = new AtomicInteger();
        Map<String, Integer> unhooked = new LinkedHashMap<>();
        AtomicBoolean failed = new AtomicBoolean(false);
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> fakeHandle(id, new ArrayList<>(), false),
                (executable, id, priority, callback) -> {
                    ids.add(id);
                    return siteHandle(id, 0, unhooked, failed, replacementCount);
                });
        Method one = Target.class.getDeclaredMethod("observed", int.class);
        Method other = Target.class.getDeclaredMethod("observed", String.class);
        XposedInterface.Hooker hooker = chain -> chain.proceed();
        registry.installSite(one, "shared#loop:12", 50, hooker);
        registry.installSite(other, "shared#loop:12", 50, hooker);
        assertEquals(2, registry.count());
        assertEquals(2, ids.size());
        registry.installSite(one, "shared#loop:12", 50, hooker);
        assertEquals(1, replacementCount.get());
        assertEquals(2, ids.size());
    }

    @Test
    public void legacyMultiHooksRemainSeparateAndBlockHotReloadReadiness() throws Exception {
        List<String> events = new ArrayList<>();
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> fakeHandle(id, events, false));
        Method method = Target.class.getDeclaredMethod("observed", int.class);
        // Legacy callers may install more than one callback for the same target.
        registry.trackUnidentified(fakeHandle("legacy-first", events, false));
        registry.trackUnidentified(fakeHandle("legacy-second", events, false));
        assertEquals(2, registry.unidentifiedCount());
        assertEquals(2, registry.count());
        assertTrue(registry.idSnapshot().isEmpty());
        registry.install(method, "identified", chain -> chain.proceed());
        assertEquals(3, registry.count());
        assertTrue(registry.rollback(List.of("identified")));
        assertEquals(2, registry.count());
        assertEquals(2, registry.unidentifiedCount());
        assertEquals(List.of("identified"), events);
        assertThrows(IllegalArgumentException.class,
                () -> registry.trackUnidentified(null));
    }

    @Test
    public void rollbackRunsInReverseOrderAndPreservesUnremovedHandles() throws Exception {
        List<String> events = new ArrayList<>();
        Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> fakeHandle(id, events, "stuck".equals(id)));
        Method method = Target.class.getDeclaredMethod("observed", int.class);
        XposedInterface.Hooker cb = chain -> chain.proceed();
        registry.install(method, "first", cb);
        registry.install(method, "stuck", cb);
        registry.install(method, "last", cb);

        // A failed unhook must remain registered to block duplicate installation.
        assertFalse(registry.rollback(List.of("first", "stuck", "last")));
        assertEquals(Arrays.asList("last", "stuck", "first"), events);
        assertEquals(List.of("stuck"), registry.idSnapshot());
        assertThrows(IllegalStateException.class, () -> registry.install(method, "stuck", cb));
    }
}
