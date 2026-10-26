package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Process-local native gesture alpha ownership. Tracks the latest vendor-requested alpha,
 * not the alpha temporarily written by LiquidDock, so detach restores the vendor's intent.
 *
 * <p>Weak keys avoid retaining destroyed SystemUI handles. All calls are serialized because
 * vendor setAlpha can arrive from a different thread than NavigationBar capture.</p>
 */
final class GestureHandleAlphaState<T> {
    interface Writer<T> {
        boolean write(T handle, float alpha);
    }

    private final WeakHashMap<T, Float> vendorTargets = new WeakHashMap<>();
    private boolean hidden;
    private boolean needsReconcile;

    synchronized boolean capture(T handle, float vendorAlpha, Writer<T> writer) {
        if (handle == null || writer == null) throw new IllegalArgumentException("handle/writer");
        if (vendorTargets.containsKey(handle)) return true;
        vendorTargets.put(handle, vendorAlpha);
        // A new NavigationBar can appear while HOME is already hidden. It must inherit the
        // current visibility even though the scene state has not changed.
        if (!hidden) return true;
        boolean applied = safeWrite(writer, handle, 0f);
        if (!applied) needsReconcile = true;
        return applied;
    }

    synchronized float onVendorAlpha(T handle, float alpha) {
        if (!vendorTargets.containsKey(handle)) return alpha;
        vendorTargets.put(handle, alpha);
        return hidden ? 0f : alpha;
    }

    synchronized boolean setHidden(boolean next, Writer<T> writer) {
        if (writer == null) throw new IllegalArgumentException("writer");
        if (hidden == next && !needsReconcile) return true;
        hidden = next;
        boolean complete = true;
        // Native setAlpha may synchronously reenter another NavigationBar capture. Snapshot
        // the keys so a newly discovered handle cannot invalidate WeakHashMap iteration.
        for (T handle : new ArrayList<>(vendorTargets.keySet())) {
            Float vendorTarget = vendorTargets.get(handle);
            if (handle != null && vendorTarget != null
                    && !safeWrite(writer, handle, next ? 0f : vendorTarget)) {
                complete = false;
            }
        }
        needsReconcile = !complete;
        return complete;
    }

    /**
     * Call only after the owning hook domain has drained and unhooked. Failed native writes
     * retain their targets for a later retry; never report a complete owner teardown on failure.
     */
    synchronized boolean restoreAndClear(Writer<T> writer) {
        if (writer == null) throw new IllegalArgumentException("writer");
        hidden = false;
        needsReconcile = false;
        boolean complete = true;
        Iterator<Map.Entry<T, Float>> iterator = vendorTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<T, Float> entry = iterator.next();
            T handle = entry.getKey();
            if (handle == null || safeWrite(writer, handle, entry.getValue())) {
                iterator.remove();
            } else {
                complete = false;
            }
        }
        return complete;
    }

    synchronized int trackedCount() {
        return vendorTargets.size();
    }

    private static <T> boolean safeWrite(Writer<T> writer, T handle, float alpha) {
        try {
            return writer.write(handle, alpha);
        } catch (Throwable ignored) {
            // Keep this native handle in the retry ledger, but continue restoring the others.
            return false;
        }
    }
}
