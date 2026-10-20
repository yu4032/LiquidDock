package com.hellovoid.liquiddock;

/** Adds a user translation to the latest native value without accumulating across frames. */
final class WorkstationDockTranslationState {
    private float nativeValue = Float.NaN;
    private float lastApplied = Float.NaN;

    float apply(float observed, boolean active, float offset) {
        boolean owned = Float.isFinite(lastApplied) && Math.abs(observed - lastApplied) < .01f;
        if (!active) {
            float restored = owned ? nativeValue : observed;
            nativeValue = lastApplied = Float.NaN;
            return restored;
        }
        if (!owned || !Float.isFinite(nativeValue)) nativeValue = observed;
        lastApplied = nativeValue + offset;
        return lastApplied;
    }
}
