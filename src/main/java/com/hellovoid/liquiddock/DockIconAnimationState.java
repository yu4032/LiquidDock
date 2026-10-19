package com.hellovoid.liquiddock;

import java.util.WeakHashMap;

/**
 * Dock icon glass follows the same CLOSE_TO_HOME progress-owned opacity as Workspace.
 * Native ShortcutIcon visibility and FloatingIcon proxy geometry remain separate owners.
 */
final class DockIconAnimationState {
    static final class Sample {
        final float opacity;
        final boolean proxyActive;
        final float[] proxyRect;

        Sample(float opacity, boolean proxyActive, float[] proxyRect) {
            this.opacity = opacity;
            this.proxyActive = proxyActive;
            this.proxyRect = proxyRect;
        }
    }

    private static final Sample VISIBLE_SAMPLE = new Sample(1f, false, null);

    private static final class Record {
        float returnAlpha = 1f;
        boolean returnActive;
        boolean sourceRestored;
        final LauncherGlassVisualOwnerState visualOwner = new LauncherGlassVisualOwnerState();
    }

    private final WeakHashMap<Object, Record> states = new WeakHashMap<>();

    private Record getOrCreate(Object icon) {
        Record record = states.get(icon);
        if (record == null) {
            record = new Record();
            states.put(icon, record);
        }
        return record;
    }

    /** Actual OEM source concealment, idempotent across repeated hide callbacks. */
    synchronized boolean onNativeHidden(Object icon) {
        if (icon == null) return false;
        Record record = getOrCreate(icon);
        if (record.returnActive && !record.sourceRestored) return false;
        record.returnActive = true;
        record.sourceRestored = false;
        record.returnAlpha = 0f;
        return true;
    }

    /** Native CLOSE_TO_HOME retargeted into launch: drop the partial return glow. */
    synchronized boolean cancelHomeReturn(Object icon) {
        if (icon == null) return false;
        Record record = getOrCreate(icon);
        boolean changed = !record.returnActive || record.returnAlpha != 0f;
        record.returnActive = true;
        record.sourceRestored = false;
        record.returnAlpha = 0f;
        return changed;
    }

    /**
     * The Workspace and Dock glass curves are the same shared native-progress curve.
     * No post-animation clock or independent fade runner may delay the handoff.
     */
    synchronized boolean observeHomeReturnProgress(Object icon, float progress) {
        if (icon == null || !Float.isFinite(progress)) return false;
        Record record = getOrCreate(icon);
        if (record.sourceRestored) return false;
        float alpha = LauncherGlassReturnFade.alpha(progress);
        if (record.returnActive && record.returnAlpha == alpha) return false;
        record.returnActive = true;
        record.returnAlpha = alpha;
        return true;
    }

    synchronized boolean holdProxyHidden(Object icon) {
        if (icon == null) return false;
        return getOrCreate(icon).visualOwner.holdLaunchProxyHidden();
    }

    synchronized boolean updateProxyGeometry(Object icon, float[] rect) {
        if (icon == null) return false;
        return getOrCreate(icon).visualOwner.updateLaunchProxyRect(rect);
    }

    synchronized boolean endProxyGeometry(Object icon) {
        if (icon == null) return false;
        Record record = states.get(icon);
        if (record == null) return false;
        boolean changed = record.visualOwner.endLaunchProxy();
        maybeRemoveStableRecord(icon, record);
        return changed;
    }

    /** Native source becomes visible at the formal end, just like Workspace endLaunchProxy. */
    synchronized boolean end(Object icon) {
        if (icon == null) return false;
        Record record = states.get(icon);
        if (record == null) return false;
        boolean changed = record.returnActive && record.returnAlpha != 1f;
        record.returnActive = false;
        record.sourceRestored = true;
        record.returnAlpha = 1f;
        maybeRemoveStableRecord(icon, record);
        return changed;
    }

    synchronized Sample sample(Object icon) {
        Record record = icon != null ? states.get(icon) : null;
        if (record == null) return VISIBLE_SAMPLE;
        return new Sample(record.returnActive ? record.returnAlpha : 1f,
                record.visualOwner.isLaunchProxyActive(),
                record.visualOwner.copyLaunchProxyRect());
    }

    synchronized void remove(Object icon) {
        if (icon != null) states.remove(icon);
    }

    synchronized void clear() {
        states.clear();
    }

    private void maybeRemoveStableRecord(Object icon, Record record) {
        // Retain a terminal marker until the next native hide or unregistration:
        // late proxy update() callbacks must not reopen a completed return fade.
        if (!record.returnActive && !record.sourceRestored
                && !record.visualOwner.isLaunchProxyActive()) {
            states.remove(icon);
        }
    }
}
