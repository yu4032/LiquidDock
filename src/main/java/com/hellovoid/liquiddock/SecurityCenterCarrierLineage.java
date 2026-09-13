package com.hellovoid.liquiddock;

import java.util.IdentityHashMap;
import java.util.Map;

/** Identity-safe ancestry check for vendor material carriers. */
final class SecurityCenterCarrierLineage {
    interface ParentLookup {
        Object parentOf(Object node);
    }

    private SecurityCenterCarrierLineage() {}

    static boolean belongsTo(Object ancestor, Object candidate, ParentLookup lookup) {
        if (ancestor == null || candidate == null || lookup == null) return false;
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        Object current = candidate;
        while (current != null && seen.put(current, Boolean.TRUE) == null) {
            if (current == ancestor) return true;
            current = lookup.parentOf(current);
        }
        return false;
    }
}
