package com.hellovoid.liquiddock;

/** Opacity follows the closing icon's native progress, including retargets. */
final class LauncherGlassReturnFade {
    private LauncherGlassReturnFade() {}

    static float alpha(float progress) {
        if (!Float.isFinite(progress)) return 0f;
        float phase = Math.max(0f, Math.min(1f, (progress - .9f) / .1f));
        return phase * phase * (3f - 2f * phase);
    }
}
