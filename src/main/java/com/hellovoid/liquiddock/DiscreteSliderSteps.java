package com.hellovoid.liquiddock;

/**
 * Discrete track policy shared by the Prismal touch slider and the grid settings page.
 * The steps parameter matches the Miuix/Compose convention: N interior stops yield N+1 intervals.
 * A zero-step slider remains continuous.
 */
public final class DiscreteSliderSteps {
    private DiscreteSliderSteps() {}

    public static int forIntegerRange(int min, int max) {
        return Math.max(0, max - min - 1);
    }

    public static float snap(float raw, float min, float max, int steps) {
        float bounded = Math.max(min, Math.min(max, raw));
        if (steps <= 0 || max <= min) return bounded;
        float interval = (max - min) / (steps + 1);
        int index = Math.max(0, Math.min(steps + 1,
                Math.round((bounded - min) / interval)));
        return Math.max(min, Math.min(max, min + index * interval));
    }
}
