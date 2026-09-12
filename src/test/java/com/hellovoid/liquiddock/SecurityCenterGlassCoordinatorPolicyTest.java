package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Android-free identity and callback-authority contract used by the Security Center coordinator. */
public class SecurityCenterGlassCoordinatorPolicyTest {
    @Test
    public void oneRootReusesOneSessionAcrossPageGenerations() {
        SecurityCenterGlassCoordinator.Policy policy = new SecurityCenterGlassCoordinator.Policy();
        Object root = new Object();
        Object session = new Object();

        SecurityCenterGlassCoordinator.BindDecision first = policy.bindRoot(root);
        assertTrue(first.createSession);
        assertFalse(first.reuseSession);
        assertNull(first.sessionToShutdown);

        assertTrue(policy.onSessionCreated(root, session));
        SecurityCenterGlassCoordinator.BindDecision again = policy.bindRoot(root);
        assertFalse(again.createSession);
        assertTrue(again.reuseSession);
        assertSame(session, policy.currentSession());

        assertTrue(policy.acceptsCallback(root, session, 2L, 2L, true));
        assertTrue(policy.acceptsCallback(root, session, 3L, 3L, true));
        assertFalse(policy.acceptsCallback(root, session, 2L, 3L, true));
    }

    @Test
    public void rootReplacementRevokesOldSessionBeforeNewOwnership() {
        SecurityCenterGlassCoordinator.Policy policy = new SecurityCenterGlassCoordinator.Policy();
        Object rootOne = new Object();
        Object rootTwo = new Object();
        Object sessionOne = new Object();
        Object sessionTwo = new Object();

        policy.bindRoot(rootOne);
        assertTrue(policy.onSessionCreated(rootOne, sessionOne));

        SecurityCenterGlassCoordinator.BindDecision replacement = policy.bindRoot(rootTwo);
        assertTrue(replacement.createSession);
        assertFalse(replacement.reuseSession);
        assertSame(sessionOne, replacement.sessionToShutdown);
        assertNull(policy.currentSession());
        assertFalse(policy.acceptsCallback(rootOne, sessionOne, 1L, 1L, true));

        assertTrue(policy.onSessionCreated(rootTwo, sessionTwo));
        assertSame(sessionTwo, policy.currentSession());
        assertTrue(policy.acceptsCallback(rootTwo, sessionTwo, 1L, 1L, true));
    }

    @Test
    public void releaseAllRevokesQueuedCallbacksAndIsIdempotent() {
        SecurityCenterGlassCoordinator.Policy policy = new SecurityCenterGlassCoordinator.Policy();
        Object root = new Object();
        Object session = new Object();
        policy.bindRoot(root);
        policy.onSessionCreated(root, session);

        SecurityCenterGlassCoordinator.ReleaseDecision released = policy.releaseAll();
        assertSame(session, released.sessionToShutdown);
        assertNull(policy.currentRoot());
        assertNull(policy.currentSession());
        assertFalse(policy.acceptsCallback(root, session, 5L, 5L, true));
        assertFalse(policy.acceptsCallback(root, session, 5L, 5L, false));

        SecurityCenterGlassCoordinator.ReleaseDecision duplicate = policy.releaseAll();
        assertNull(duplicate.sessionToShutdown);
    }
}
