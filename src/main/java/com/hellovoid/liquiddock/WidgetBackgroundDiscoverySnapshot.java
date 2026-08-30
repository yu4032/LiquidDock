package com.hellovoid.liquiddock;

import java.util.List;

/** One currently discovered widget identity and its safe selectable background targets. */
public final class WidgetBackgroundDiscoverySnapshot {
    private final WidgetBackgroundIdentity identity;
    private final List<WidgetBackgroundDiscoveryTarget> targets;
    private final long lastSeenMillis;

    public WidgetBackgroundDiscoverySnapshot(
            WidgetBackgroundIdentity identity,
            List<WidgetBackgroundDiscoveryTarget> targets,
            long lastSeenMillis) {
        if (identity == null) throw new IllegalArgumentException("identity == null");
        this.identity = identity;
        this.targets = targets == null ? List.of() : List.copyOf(targets);
        this.lastSeenMillis = lastSeenMillis;
    }

    public WidgetBackgroundIdentity identity() { return identity; }
    public List<WidgetBackgroundDiscoveryTarget> targets() { return targets; }
    public long lastSeenMillis() { return lastSeenMillis; }
}
