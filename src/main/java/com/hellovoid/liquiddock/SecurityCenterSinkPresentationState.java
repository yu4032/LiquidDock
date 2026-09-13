package com.hellovoid.liquiddock;

/** Allocation-free visibility/readiness policy for a Security Center TextureView sink. */
final class SecurityCenterSinkPresentationState {
    private SecurityCenterSinkPresentationState() {}

    static boolean shouldCompose(boolean materialVisible) {
        return materialVisible;
    }

    static boolean isPresentationReady(
            boolean attached,
            boolean hardwareAccelerated,
            boolean windowVisible,
            boolean structurallyVisible,
            boolean surfaceAvailable) {
        return attached
                && hardwareAccelerated
                && windowVisible
                && structurallyVisible
                && surfaceAvailable;
    }

    static float contentAlpha(boolean materialVisible, boolean authorized, float materialAlpha) {
        if (!materialVisible) return 0f;
        if (!authorized) return 0f;
        if (!Float.isFinite(materialAlpha)) return 0f;
        return Math.max(0f, Math.min(1f, materialAlpha));
    }
}
