package com.hellovoid.liquiddock;

/**
 * Native-frame-paced capture on Launcher resume. Never start an unnecessary
 * PassBlur normalization / static EGL swap in the initial HOME spring frame.
 * Native HOME presentation retains authority over final wallpaper freshness.
 */
final class WorkspaceResumeCapturePolicy {
    enum Decision { DEFER_TO_PRESENTATION, NEXT_FRAME, REQUEST_FRESH, NO_REQUEST }

    private WorkspaceResumeCapturePolicy() {}

    static Decision onFrame(int framesLeft, boolean presentationPending,
                            boolean needsFreshSource) {
        if (presentationPending) return Decision.DEFER_TO_PRESENTATION;
        if (framesLeft > 1) return Decision.NEXT_FRAME;
        return needsFreshSource ? Decision.REQUEST_FRESH : Decision.NO_REQUEST;
    }
}
