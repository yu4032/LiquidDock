package com.hellovoid.liquiddock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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

    synchronized void commit() {
        if (state != State.INSTALLING || ids.isEmpty()) {
            throw new IllegalStateException("empty or non-installing domain: " + domainName);
        }
        state = State.ACTIVE;
    }

    boolean isActive() {
        return state == State.ACTIVE;
    }

    State state() {
        return state;
    }

    synchronized int ownedCount() {
        return ids.size();
    }

    /** Disarm callback side effects before calling the framework to detach any hook. */
    synchronized boolean stop() {
        if (state == State.IDLE) return true;
        // Volatile state is visible to callbacks immediately, even if some unhook fails.
        state = State.BLOCKED;
        boolean clean = registry.rollback(new ArrayList<>(ids));
        if (clean) {
            ids.clear();
            state = State.IDLE;
        }
        // BLOCKED on failure: do not admit a second set of callbacks while the old set remains.
        return clean;
    }

    synchronized boolean abort() {
        if (state != State.INSTALLING && state != State.BLOCKED) {
            throw new IllegalStateException("domain not in installation/cleanup: " + domainName);
        }
        return stop();
    }
}
