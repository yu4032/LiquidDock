package com.hellovoid.liquiddock;

import java.util.WeakHashMap;

/** Per-icon Dock glass ownership while MIUI's launch animation owns the icon visual. */
final class DockIconAnimationState {
    private static final long HIDDEN = Long.MAX_VALUE;
    private static final long COMPLETE = Long.MIN_VALUE;
    private static final float RESTORE_PROGRESS = 0.90f;

    private static final class Record {
        long fadeStartedMs = HIDDEN;
        boolean ended;
    }

    private final long fadeDurationMs;
    private final WeakHashMap<Object, Record> states = new WeakHashMap<>();

    DockIconAnimationState(long fadeDurationMs) {
        this.fadeDurationMs = Math.max(1L, fadeDurationMs);
    }

    synchronized void begin(Object icon) {
        if (icon != null) states.put(icon, new Record());
    }

    synchronized void observeProxyFrame(Object icon, float progress, long nowMs) {
        if (icon == null) return;
        Record record = states.get(icon);
        if (record == null) {
            record = new Record();
            states.put(icon, record);
        }
        if (record.fadeStartedMs == HIDDEN && Float.isFinite(progress)
                && progress >= RESTORE_PROGRESS) {
            record.fadeStartedMs = nowMs;
        }
    }

    synchronized void end(Object icon, long nowMs) {
        if (icon == null) return;
        Record record = states.get(icon);
        if (record == null) return;
        record.ended = true;
        if (record.fadeStartedMs == HIDDEN) record.fadeStartedMs = nowMs;
        if (record.fadeStartedMs == COMPLETE) states.remove(icon);
    }

    synchronized float opacity(Object icon, long nowMs) {
        Record record = icon != null ? states.get(icon) : null;
        if (record == null || record.fadeStartedMs == COMPLETE) return 1f;
        if (record.fadeStartedMs == HIDDEN) return 0f;
        float progress = Math.max(0f, Math.min(1f,
                (nowMs - record.fadeStartedMs) / (float) fadeDurationMs));
        if (progress >= 1f) {
            if (record.ended) states.remove(icon);
            else record.fadeStartedMs = COMPLETE;
            return 1f;
        }
        float remaining = 1f - progress;
        return 1f - remaining * remaining;
    }

    synchronized boolean isFading(Object icon) {
        Record record = icon != null ? states.get(icon) : null;
        return record != null && record.fadeStartedMs != HIDDEN
                && record.fadeStartedMs != COMPLETE;
    }

    synchronized void remove(Object icon) {
        if (icon != null) states.remove(icon);
    }

    synchronized void clear() {
        states.clear();
    }
}
