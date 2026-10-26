package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import org.junit.Test;

/** Deterministic interleavings for the owner-scoped API 102 observer drain barrier. */
public class Api102HookDomainConcurrencyTest {
    static final class Target {
        void observed() { }
    }

    private static final class Fixture {
        final AtomicInteger unhookCount = new AtomicInteger();
        final Api102HookRegistry registry = new Api102HookRegistry(
                (method, id, callback) -> (XposedInterface.HookHandle)
                        Proxy.newProxyInstance(
                                XposedInterface.HookHandle.class.getClassLoader(),
                                new Class<?>[]{XposedInterface.HookHandle.class},
                                (proxy, invoked, args) -> {
                                    if ("unhook".equals(invoked.getName())) {
                                        unhookCount.incrementAndGet();
                                    }
                                    return null;
                                }));
        final Api102HookDomain domain = new Api102HookDomain(registry, "pilot");

        Fixture() throws Exception {
            Method method = Target.class.getDeclaredMethod("observed");
            domain.begin();
            domain.hook(method, chain -> chain.proceed());
            domain.commit();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for test coordination");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrupted", interrupted);
        }
    }

    @Test
    public void stopCannotSucceedUntilEnteredObserverFinishesAndCannotRunAfterStop()
            throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean stoppedCleanly = new AtomicBoolean(false);
        AtomicInteger effects = new AtomicInteger();
        Thread observer = new Thread(() -> fixture.domain.runActiveSideEffect(() -> {
            entered.countDown();
            await(release);
            effects.incrementAndGet();
        }), "observer-in-flight");
        Thread stopper = new Thread(() -> {
            stoppedCleanly.set(fixture.domain.stop());
            stopped.countDown();
        }, "observer-stop");
        observer.start();
        try {
            await(entered);
            stopper.start();
            // Stop must either drain the callback or fail closed; it must never report success
            // while the callback still holds its read-side lease.
            assertFalse(stopped.await(60, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
        }
        observer.join(5000);
        stopper.join(5000);
        assertFalse(observer.isAlive());
        assertFalse(stopper.isAlive());
        assertTrue(stoppedCleanly.get());
        assertEquals(1, effects.get());
        assertEquals(1, fixture.unhookCount.get());
        assertEquals(0, fixture.registry.count());
        assertFalse(fixture.domain.runActiveSideEffect(effects::incrementAndGet));
        assertEquals(1, effects.get());
    }

    @Test
    public void drainTimeoutRemainsBlockedAndLaterRetryRemovesOldHandle() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread observer = new Thread(() -> fixture.domain.runActiveSideEffect(() -> {
            entered.countDown();
            await(release);
        }), "blocked-observer");
        observer.start();
        try {
            await(entered);
            assertFalse("in-flight drain timeout must NOT be reported as success",
                    fixture.domain.stop());
            assertEquals(Api102HookDomain.State.BLOCKED, fixture.domain.state());
            assertFalse(fixture.domain.isActive());
            assertEquals(1, fixture.registry.count());
            assertEquals(0, fixture.unhookCount.get());
            assertFalse(fixture.domain.runActiveSideEffect(
                    () -> fail("stopped domain ran new observer")));
            assertThrows(IllegalStateException.class, fixture.domain::begin);
        } finally {
            release.countDown();
        }
        observer.join(5000);
        assertFalse(observer.isAlive());
        assertTrue(fixture.domain.stop());
        assertEquals(1, fixture.unhookCount.get());
        assertEquals(Api102HookDomain.State.IDLE, fixture.domain.state());
    }

    @Test
    public void exceptionalObserverReleasesLeaseSoStopCanProceed() throws Exception {
        Fixture fixture = new Fixture();
        assertThrows(IllegalStateException.class,
                () -> fixture.domain.runActiveSideEffect(() -> {
                    throw new IllegalStateException("observer failed");
                }));
        assertTrue(fixture.domain.stop());
        assertEquals(1, fixture.unhookCount.get());
    }
}
