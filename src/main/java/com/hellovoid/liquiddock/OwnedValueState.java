package com.hellovoid.liquiddock;

/**
 * Android-free lifecycle for a runtime-owned vendor value.
 *
 * <p>The first claim snapshots the vendor value. Repeated claims keep that original snapshot.
 * Release always drops ownership and restores the snapshot only when the caller confirms that
 * LiquidDock's owned value is still present, so a later vendor/third-party write is never clobbered.
 */
final class OwnedValueState<T> {
    static final class ClaimDecision<T> {
        final boolean newClaim;
        final T originalValue;

        ClaimDecision(boolean newClaim, T originalValue) {
            this.newClaim = newClaim;
            this.originalValue = originalValue;
        }
    }

    static final class ReleaseDecision<T> {
        final boolean restoreOriginal;
        final T originalValue;

        ReleaseDecision(boolean restoreOriginal, T originalValue) {
            this.restoreOriginal = restoreOriginal;
            this.originalValue = originalValue;
        }
    }

    private boolean claimed;
    private T originalValue;

    ClaimDecision<T> claim(T currentValue) {
        boolean newClaim = !claimed;
        if (newClaim) {
            claimed = true;
            originalValue = currentValue;
        }
        return new ClaimDecision<>(newClaim, originalValue);
    }

    ReleaseDecision<T> release(boolean ownerValueStillApplied) {
        if (!claimed) return new ReleaseDecision<>(false, null);
        T original = originalValue;
        claimed = false;
        originalValue = null;
        return new ReleaseDecision<>(ownerValueStillApplied, original);
    }

    boolean isClaimed() {
        return claimed;
    }
}
