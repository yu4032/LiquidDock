package com.hellovoid.liquiddock;

/** Native spring progress drives material recovery; geometry and backdrop sampling stay live. */
final class WorkspaceAppMaterialQuality {
    private boolean recovering;
    private float current = 1f;

    void begin() {
        recovering = true;
        current = 0f;
    }

    boolean update(boolean appActive, boolean anyMotion, float progress) {
        boolean previousMode = recovering;
        float previousDetail = current;
        if (appActive) {
            if (!recovering) begin();
            current = Math.max(current, detail(true, progress));
        } else {
            current = 1f;
            if (!anyMotion) recovering = false;
        }
        return previousMode != recovering || Float.compare(previousDetail, current) != 0;
    }

    float currentDetail() {
        return current;
    }

    boolean usesTransitionProgram() {
        return recovering;
    }

    static float detail(boolean active, float progress) {
        if (!active) return 1f;
        if (!Float.isFinite(progress)) return 0f;
        if (progress <= .92f) return 0f;
        if (progress >= .999f) return 1f;
        // The native spring's remaining distance decays exponentially. Normalize that tail
        // before easing so material recovery is spread across its visible duration.
        float t =
                (float)
                        (Math.log((1d - .92f) / (1d - progress))
                                / Math.log((1d - .92f) / (1d - .999f)));
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }
}
