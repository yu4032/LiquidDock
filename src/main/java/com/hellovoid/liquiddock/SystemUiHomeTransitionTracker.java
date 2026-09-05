package com.hellovoid.liquiddock;

import java.util.IdentityHashMap;

/**
 * Android-free lifecycle tracker for WMShell HOME transition tokens.
 *
 * <p>HOME visibility is classified by SystemUI's own HomeTransitionObserver during
 * onTransitionReady. LiquidDock only carries that already-computed classification forward to the
 * matching onTransitionStarting/onTransitionFinished callbacks. If WMShell merges a ready source
 * before it can start, the merge itself becomes that source's single START boundary on the playing
 * target token.</p>
 */
final class SystemUiHomeTransitionTracker {
    static final class Event {
        private final boolean homeVisible;
        private final long serial;

        Event(boolean homeVisible, long serial) {
            this.homeVisible = homeVisible;
            this.serial = serial;
        }

        boolean homeVisible() { return homeVisible; }
        long serial() { return serial; }
    }

    private static final class Entry {
        final boolean homeVisible;
        final long serial;
        boolean started;

        Entry(boolean homeVisible, long serial) {
            this.homeVisible = homeVisible;
            this.serial = serial;
        }
    }

    private final IdentityHashMap<Object, Entry> entries = new IdentityHashMap<>();
    private Object readyToken;
    private Boolean readyVisibility;
    private long nextSerial = 1L;

    void beginReady(Object token) {
        readyToken = token;
        readyVisibility = null;
    }

    void recordCurrentReadyVisibility(boolean visible) {
        if (readyToken == null) return;
        readyVisibility = visible;
    }

    void endReady() {
        Object token = readyToken;
        Boolean visible = readyVisibility;
        readyToken = null;
        readyVisibility = null;
        if (token == null || visible == null) return;
        entries.put(token, new Entry(visible, nextSerial++));
    }

    Event onStarting(Object token) {
        Entry entry = token != null ? entries.get(token) : null;
        if (entry == null || entry.started) return null;
        entry.started = true;
        return new Event(entry.homeVisible, entry.serial);
    }

    Event onFinished(Object token) {
        Entry entry = token != null ? entries.remove(token) : null;
        if (entry == null) return null;
        return new Event(entry.homeVisible, entry.serial);
    }

    Event onMerged(Object sourceToken, Object targetToken) {
        if (sourceToken == null || targetToken == null || sourceToken == targetToken) return null;
        Entry source = entries.remove(sourceToken);
        if (source == null) return null;

        Entry target = entries.get(targetToken);
        if (target != null && target.serial > source.serial) {
            return null;
        }

        entries.put(targetToken, source);
        if (source.started) return null;
        source.started = true;
        return new Event(source.homeVisible, source.serial);
    }
}
