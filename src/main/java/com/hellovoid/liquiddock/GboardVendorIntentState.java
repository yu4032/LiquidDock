package com.hellovoid.liquiddock;

/** Android-free ownership state that remembers the latest vendor intent while LiquidDock owns it. */
final class GboardVendorIntentState<T> {
    private boolean claimed;
    private T latestVendorValue;

    void claim(T currentVendorValue) {
        if (claimed) return;
        claimed = true;
        latestVendorValue = currentVendorValue;
    }

    boolean recordVendorWrite(T value) {
        if (!claimed) return false;
        latestVendorValue = value;
        return true;
    }

    RestoreDecision<T> release() {
        if (!claimed) return new RestoreDecision<>(false, null);
        T value = latestVendorValue;
        claimed = false;
        latestVendorValue = null;
        return new RestoreDecision<>(true, value);
    }

    boolean isClaimed() {
        return claimed;
    }

    static final class RestoreDecision<T> {
        final boolean restore;
        final T value;

        RestoreDecision(boolean restore, T value) {
            this.restore = restore;
            this.value = value;
        }
    }
}
