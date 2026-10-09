package com.hellovoid.liquiddock;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Android-free, per-root producer ownership ledger for the single native PassBlur Surface slot.
 * The bridge synchronizes transactions and calls into this ledger, so no stale unbind can clear
 * an active sibling binding. Only Launcher Workspace and Recents capsules use these records.
 */
final class PassBlurRootOwnerState {
    private final Map<Integer, WeakReference<Object>> owners = new HashMap<>();

    Object owner(int layerId) {
        if (layerId < 0) return null;
        WeakReference<Object> reference = owners.get(layerId);
        Object owner = reference != null ? reference.get() : null;
        if (owner == null && reference != null) owners.remove(layerId);
        return owner;
    }

    boolean isOwner(int layerId, Object binding) {
        return binding != null && owner(layerId) == binding;
    }

    void claim(int layerId, Object binding) {
        if (layerId < 0 || binding == null) return;
        owners.put(layerId, new WeakReference<>(binding));
    }

    boolean release(int layerId, Object binding) {
        if (!isOwner(layerId, binding)) return false;
        owners.remove(layerId);
        return true;
    }
}
