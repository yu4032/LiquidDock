package com.hellovoid.liquiddock;

import java.util.WeakHashMap;

/** Per-icon Dock glass ownership while MIUI's launch animation owns the icon visual. */
final class DockIconAnimationState {
    private static final long HIDDEN = Long.MAX_VALUE;
    private static final long COMPLETE = Long.MIN_VALUE;
    private static final float RESTORE_PROGRESS = 0.90f;

    static final class Sample {
        final float opacity;
        final boolean fading;

        Sample(float opacity, boolean fading) {
            this.opacity = opacity;
            this.fading = fading;
        }
    }

    private static final Sample VISIBLE_SAMPLE = new Sample(1f, false);
    private static final Sample HIDDEN_SAMPLE = new Sample(0f, false);

    private static final class Record {
        long fadeStartedMs = HIDDEN;
        boolean ended;
    }

    private final long fadeDurationMs;
    private final WeakHashMap<Object, Record> states = new WeakHashMap<>();

    DockIconAnimationState(long fadeDurationMs) {
        this.fadeDurationMs = Math.max(0L, fadeDurationMs);
    }

    synchronized void begin(Object icon) {
        if (icon != null) states.put(icon, new Record());
    }

    synchronized boolean observeProxyFrame(Object icon, float progress, long nowMs) {
        return observeProxyFrame(icon, progress, nowMs, true);
    }

    synchronized boolean observeProxyFrame(
            Object icon, float progress, long nowMs, boolean allowEarlyRestore) {
        if (icon == null) return false;
        Record record = states.get(icon);
        if (record == null) {
            record = new Record();
            states.put(icon, record);
        }
        if (allowEarlyRestore && record.fadeStartedMs == HIDDEN && Float.isFinite(progress)
                && progress >= RESTORE_PROGRESS) {
            record.fadeStartedMs = nowMs;
            return true;
        }
        return false;
    }

    synchronized void end(Object icon, long nowMs) {
        if (icon == null) return;
        Record record = states.get(icon);
        if (record == null) return;
        record.ended = true;
        if (record.fadeStartedMs == HIDDEN) record.fadeStartedMs = nowMs;
        if (record.fadeStartedMs == COMPLETE) states.remove(icon);
    }

    synchronized Sample sample(Object icon, long nowMs) {
        Record record = icon != null ? states.get(icon) : null;
        if (record == null || record.fadeStartedMs == COMPLETE) return VISIBLE_SAMPLE;
        if (record.fadeStartedMs == HIDDEN) return HIDDEN_SAMPLE;
        if (fadeDurationMs == 0L) {
            if (record.ended) states.remove(icon);
            else record.fadeStartedMs = COMPLETE;
            return VISIBLE_SAMPLE;
        }
        float progress = Math.max(0f, Math.min(1f,
                (nowMs - record.fadeStartedMs) / (float) fadeDurationMs));
        if (progress >= 1f) {
            if (record.ended) states.remove(icon);
            else record.fadeStartedMs = COMPLETE;
            return VISIBLE_SAMPLE;
        }
        float remaining = 1f - progress;
        return new Sample(1f - remaining * remaining, true);
    }

    synchronized float opacity(Object icon, long nowMs) {
        return sample(icon, nowMs).opacity;
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
