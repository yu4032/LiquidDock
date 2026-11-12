package com.hellovoid.liquiddock;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A producer Binder must not be sent through SetPassBlurSurface again after a native
 * submission attempt. Weak keys avoid retaining retired Java Surface wrappers.
 * Pure JVM so failed/duplicate attempts can be tested without vendor JNI.
 */
final class PassBlurProducerSubmissionGate {
    private static final Map<Object, Boolean> SUBMITTED = new WeakHashMap<>();

    private PassBlurProducerSubmissionGate() {}

    static synchronized boolean trySubmit(Object producer) {
        if (producer == null || SUBMITTED.containsKey(producer)) return false;
        SUBMITTED.put(producer, Boolean.TRUE); // mark BEFORE the native call
        return true;
    }

    static synchronized boolean wasSubmitted(Object producer) {
        return producer != null && SUBMITTED.containsKey(producer);
    }
}
