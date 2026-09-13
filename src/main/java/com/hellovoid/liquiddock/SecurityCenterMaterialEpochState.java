package com.hellovoid.liquiddock;

/**
 * Identity-only lifetime state for Security Center material carriers.
 *
 * Root/session lifetime is intentionally owned elsewhere. This state advances only when the
 * vendor's live material subtree changes: rebuilt Dock/Toolbox carriers or All Apps attach/remove.
 */
final class SecurityCenterMaterialEpochState {
    private Object turbo;
    private Object dock;
    private Object box;
    private Object apps;
    private int assistantType;
    private long generation;

    boolean bindAssistant(Object nextTurbo, Object nextDock, Object nextBox, int nextAssistantType) {
        if (nextTurbo == null || nextDock == null) return false;
        boolean changed = turbo != nextTurbo
                || dock != nextDock
                || box != nextBox
                || assistantType != nextAssistantType;
        if (!changed) return false;
        turbo = nextTurbo;
        dock = nextDock;
        box = nextBox;
        assistantType = nextAssistantType;
        apps = null;
        generation++;
        return true;
    }

    boolean attachAllApps(Object ownerTurbo, Object nextApps) {
        if (ownerTurbo == null || ownerTurbo != turbo || nextApps == null || nextApps == apps) {
            return false;
        }
        apps = nextApps;
        generation++;
        return true;
    }

    boolean detachAllApps(Object ownerTurbo, Object currentApps) {
        if (ownerTurbo == null || ownerTurbo != turbo || currentApps == null || currentApps != apps) {
            return false;
        }
        apps = null;
        generation++;
        return true;
    }

    /**
     * Forget the terminally released material subtree while keeping the epoch counter monotonic.
     * This is required because the vendor may later reuse the same Java View objects after the
     * root/session lifetime has ended; identity from a dead lifetime must never suppress a new
     * material epoch.
     */
    boolean reset() {
        if (turbo == null && dock == null && box == null && apps == null && assistantType == 0) {
            return false;
        }
        turbo = null;
        dock = null;
        box = null;
        apps = null;
        assistantType = 0;
        generation++;
        return true;
    }

    long generation() {
        return generation;
    }
}
