package com.hellovoid.liquiddock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.github.libxposed.api.XposedInterface;

/**
 * One owner-scoped, fail-closed installation transaction for explicitly identified hooks.
 *
 * <p>This is deliberately NOT a process hot-reload coordinator. It handles only its Java hook
 * handles; callers must separately release receivers, listeners, Surface/EGL and other resources.
 * A failed unhook leaves the domain BLOCKED and inactive, and forbids reinstallation.</p>
 */
final class Api102HookDomain {
    enum State { IDLE, INSTALLING, ACTIVE, BLOCKED }

    private final Api102HookRegistry registry;
    private final String domainName;
    private final List<String> ids = new ArrayList<>();
    private volatile State state = State.IDLE;
    // The observer side effect is deliberately outside chain.proceed(), but must be drained
    // before stop() can report that this generation is fully detached.
    private final ReentrantReadWriteLock effectsBarrier = new ReentrantReadWriteLock(true);
    private static final long STOP_DRAIN_TIMEOUT_MS = 200L;

    Api102HookDomain(Api102HookRegistry registry, String domainName) {
        if (registry == null || domainName == null || domainName.isBlank()) {
            throw new IllegalArgumentException("registry/domainName");
        }
        this.registry = registry;
        this.domainName = domainName;
    }

    static Api102HookDomain forProcess(String domainName) {
        return new Api102HookDomain(Api102HookRegistry.processRegistry(), domainName);
    }

    synchronized void begin() {
        if (state != State.IDLE || !ids.isEmpty()) {
            throw new IllegalStateException("domain cannot begin in state " + state);
        }
        state = State.INSTALLING;
    }

    synchronized String hook(Method method, XposedInterface.Hooker callback) {
        if (state != State.INSTALLING) {
            throw new IllegalStateException("domain not installing: " + domainName);
        }
        String id = Api102HookRegistry.stableId(domainName, method);
        registry.install(method, id, callback);
        // No ID is added until the framework returns a valid handle. On failure, abort() only
        // unhooks handles actually acquired by the transaction.
        ids.add(id);
        return id;
    }

    /**
     * Some vendor listeners only become discoverable after the owner has been activated.
     * Track those handles in the same domain; no partial install may acquire an unowned handle.
     */
    synchronized String hookDynamic(Method method, XposedInterface.Hooker callback) {
        if (state != State.ACTIVE) {
            throw new IllegalStateException("domain is not active: " + domainName);
        }
        String id = Api102HookRegistry.stableId(domainName, method);
        registry.install(method, id, callback);
        ids.add(id);
        return id;
    }

    synchronized void commit() {
        if (state != State.INSTALLING || ids.isEmpty()) {
            throw new IllegalStateException("empty or non-installing domain: " + domainName);
        }
        state = State.ACTIVE;
    }

    boolean isActive() {
        return state == State.ACTIVE;
    }

    /**
     * Run a bounded, synchronous post-hook side effect with an in-flight reader lease.
     * Stop() closes the gate first, then obtains the exclusive lease before unhooking.
     * Never call stop() or dispatch an asynchronous callback from within this action.
     */
    boolean runActiveSideEffect(Runnable action) {
        if (action == null) throw new IllegalArgumentException("action");
        if (state != State.ACTIVE) return false;
        effectsBarrier.readLock().lock();
        try {
            if (state != State.ACTIVE) return false;
            action.run();
            return true;
        } finally {
            effectsBarrier.readLock().unlock();
        }
    }

    State state() {
        return state;
    }

    synchronized int ownedCount() {
        return ids.size();
    }

    /**
     * Disarm new callbacks and wait for in-flight side effects before unhooking. A timed-out
     * drain is a failure, not success: stay BLOCKED and retain every handle for a later retry.
     * Never await this from a callback protected by runActiveSideEffect().
     */
    synchronized boolean stop() {
        if (state == State.IDLE) return true;
        state = State.BLOCKED;
        boolean drained;
        try {
            drained = effectsBarrier.writeLock().tryLock(
                    STOP_DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (!drained) return false;
        try {
            boolean clean = registry.rollback(new ArrayList<>(ids));
            if (clean) {
                ids.clear();
                state = State.IDLE;
            }
            return clean;
        } finally {
            effectsBarrier.writeLock().unlock();
        }
    }

    synchronized boolean abort() {
        if (state != State.INSTALLING && state != State.BLOCKED) {
            throw new IllegalStateException("domain not in installation/cleanup: " + domainName);
        }
        return stop();
    }
}
