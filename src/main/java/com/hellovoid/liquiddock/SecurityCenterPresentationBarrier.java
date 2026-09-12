package com.hellovoid.liquiddock;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Android-free all-output presentation barrier for one Security Center frame serial. */
final class SecurityCenterPresentationBarrier {
    private long serial = -1L;
    private Set<Object> required = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> acknowledged = Collections.newSetFromMap(new IdentityHashMap<>());

    synchronized void begin(long nextSerial, Object[] outputs) {
        if (nextSerial < 0L || outputs == null || outputs.length == 0) {
            throw new IllegalArgumentException("invalid presentation barrier request");
        }
        Set<Object> nextRequired = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object output : outputs) {
            if (output == null || !nextRequired.add(output)) {
                throw new IllegalArgumentException("invalid presentation barrier outputs");
            }
        }
        serial = nextSerial;
        required = nextRequired;
        acknowledged.clear();
    }

    synchronized boolean acknowledge(long candidateSerial, Object output) {
        if (candidateSerial != serial || output == null || !required.contains(output)) return false;
        acknowledged.add(output);
        return acknowledged.size() == required.size();
    }

    synchronized boolean isCurrent(long candidateSerial) {
        return candidateSerial == serial && serial >= 0L;
    }

    synchronized void cancel(long candidateSerial) {
        if (candidateSerial != serial) return;
        serial = -1L;
        required = Collections.newSetFromMap(new IdentityHashMap<>());
        acknowledged.clear();
    }

    synchronized void reset() {
        serial = -1L;
        required = Collections.newSetFromMap(new IdentityHashMap<>());
        acknowledged.clear();
    }
}
