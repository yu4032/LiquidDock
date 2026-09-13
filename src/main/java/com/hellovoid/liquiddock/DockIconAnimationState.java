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
        final boolean proxyActive;
        final float[] proxyRect;

        Sample(float opacity, boolean fading, boolean proxyActive, float[] proxyRect) {
            this.opacity = opacity;
            this.fading = fading;
            this.proxyActive = proxyActive;
            this.proxyRect = proxyRect != null ? proxyRect.clone() : null;
        }
    }

    private static final Sample VISIBLE_SAMPLE = new Sample(1f, false, false, null);
    private static final Sample HIDDEN_SAMPLE = new Sample(0f, false, false, null);

    private static final class Record {
        long fadeStartedMs = HIDDEN;
        boolean ended;
        boolean fadeOwned;
        final LauncherGlassVisualOwnerState visualOwner = new LauncherGlassVisualOwnerState();
    }

    private final long fadeDurationMs;
    private final WeakHashMap<Object, Record> states = new WeakHashMap<>();

    DockIconAnimationState(long fadeDurationMs) {
        this.fadeDurationMs = Math.max(0L, fadeDurationMs);
    }

    synchronized void begin(Object icon) {
        if (icon == null) return;
        Record record = new Record();
        record.fadeOwned = true;
        states.put(icon, record);
    }

    synchronized boolean observeProxyFrame(Object icon, float progress, long nowMs) {
        if (icon == null) return false;
        Record record = states.get(icon);
        if (record == null) {
            record = new Record();
            states.put(icon, record);
        }
        record.fadeOwned = true;
        if (record.fadeStartedMs == HIDDEN && Float.isFinite(progress)
                && progress >= RESTORE_PROGRESS) {
            record.fadeStartedMs = nowMs;
            return true;
        }
        return false;
    }

    synchronized boolean holdProxyHidden(Object icon) {
        if (icon == null) return false;
        Record record = states.get(icon);
        if (record == null) {
            record = new Record();
            states.put(icon, record);
        }
        return record.visualOwner.holdLaunchProxyHidden();
    }

    synchronized boolean updateProxyGeometry(Object icon, float[] rect) {
        if (icon == null) return false;
        Record record = states.get(icon);
        if (record == null) {
            record = new Record();
            states.put(icon, record);
        }
        return record.visualOwner.updateLaunchProxyRect(rect);
    }

    synchronized boolean endProxyGeometry(Object icon) {
        if (icon == null) return false;
        Record record = states.get(icon);
        if (record == null) return false;
        boolean changed = record.visualOwner.endLaunchProxy();
        maybeRemoveStableRecord(icon, record);
        return changed;
    }

    synchronized void end(Object icon, long nowMs) {
        if (icon == null) return;
        Record record = states.get(icon);
        if (record == null || !record.fadeOwned) return;
        record.ended = true;
        if (record.fadeStartedMs == HIDDEN) record.fadeStartedMs = nowMs;
        if (record.fadeStartedMs == COMPLETE) {
            record.fadeOwned = false;
            maybeRemoveStableRecord(icon, record);
        }
    }

    synchronized Sample sample(Object icon, long nowMs) {
        Record record = icon != null ? states.get(icon) : null;
        if (record == null) return VISIBLE_SAMPLE;

        boolean proxyActive = record.visualOwner.isLaunchProxyActive();
        float[] proxyRect = record.visualOwner.copyLaunchProxyRect();
        if (!record.fadeOwned) {
            return new Sample(1f, false, proxyActive, proxyRect);
        }
        if (record.fadeStartedMs == COMPLETE) {
            return new Sample(1f, false, proxyActive, proxyRect);
        }
        if (record.fadeStartedMs == HIDDEN) {
            return new Sample(0f, false, proxyActive, proxyRect);
        }
        if (fadeDurationMs == 0L) {
            finishFade(icon, record);
            return new Sample(1f, false, proxyActive, proxyRect);
        }
        float progress = Math.max(0f, Math.min(1f,
                (nowMs - record.fadeStartedMs) / (float) fadeDurationMs));
        if (progress >= 1f) {
            finishFade(icon, record);
            return new Sample(1f, false, proxyActive, proxyRect);
        }
        float remaining = 1f - progress;
        return new Sample(1f - remaining * remaining, true, proxyActive, proxyRect);
    }

    synchronized float opacity(Object icon, long nowMs) {
        return sample(icon, nowMs).opacity;
    }

    synchronized boolean isFading(Object icon) {
        Record record = icon != null ? states.get(icon) : null;
        return record != null && record.fadeOwned
                && record.fadeStartedMs != HIDDEN
                && record.fadeStartedMs != COMPLETE;
    }

    synchronized void remove(Object icon) {
        if (icon != null) states.remove(icon);
    }

    synchronized void clear() {
        states.clear();
    }

    private void finishFade(Object icon, Record record) {
        if (record.ended) {
            record.fadeOwned = false;
            record.fadeStartedMs = COMPLETE;
            record.ended = false;
            maybeRemoveStableRecord(icon, record);
        } else {
            record.fadeStartedMs = COMPLETE;
        }
    }

    private void maybeRemoveStableRecord(Object icon, Record record) {
        if (!record.fadeOwned && !record.visualOwner.isLaunchProxyActive()) {
            states.remove(icon);
        }
    }
}
