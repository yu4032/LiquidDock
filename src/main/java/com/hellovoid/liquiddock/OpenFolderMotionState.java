package com.hellovoid.liquiddock;

/** Uses the native background spring; icon/content completion is a separate lifecycle event. */
final class OpenFolderMotionState {
    private float progress;
    private boolean sampled, contentFinished, closing;

    void onContentFinished() { if (!closing) contentFinished = true; }

    void beginClose() {
        if (closing) return;
        // Capture can start late (after OEM hides Dock). If opening had no
        // usable animated sample, an already-open folder still begins its
        // reverse spring from the expanded endpoint, not an opacity of zero.
        if (!sampled && contentFinished) progress = 1f;
        closing = true;
    }

    boolean isClosing() { return closing; }

    float sample(float nativeProgress) {
        if (Float.isFinite(nativeProgress)) {
            sampled = true;
            float observed = OpenFolderBackdropPolicy.backdropOpacity(nativeProgress);
            // Native close setTo(expanded) can jump to progress=1 before its reverse
            // spring starts. Never grow an interrupted opening on close; once
            // the native position falls below the existing frame, follow the
            // same curve in reverse without multiplying two progress values.
            progress = closing ? Math.min(progress, observed) : Math.max(progress, observed);
        } else if (!sampled && contentFinished && !closing) {
            progress = 1f; // Animator duration scale is zero: no background spring exists.
        }
        return progress;
    }
}
