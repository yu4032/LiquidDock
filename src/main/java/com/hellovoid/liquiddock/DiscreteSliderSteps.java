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

    /** Keep tick rendering bounded: thousands of dp/ms values remain snap-to-unit
     * controls without allocating thousands of native Slider tick positions. */
    public static int forStoragePrecision(int min, int max, boolean tenths) {
        long intervals = ((long) max - min) * (tenths ? 10L : 1L);
        return intervals >= 2L && intervals <= 64L ? (int) intervals - 1 : 0;
    }

    /** Quantize numeric previews and release targets to persisted precision, even for wide
     * ranges where drawing every native Slider tick would be prohibitively costly. */
    public static float snapToIncrement(float raw, float min, float max, float increment) {
        float bounded = Math.max(min, Math.min(max, raw));
        if (!(increment > 0f) || !(max > min)) return bounded;
        double index = Math.floor(((double) bounded - min) / increment + 0.5d);
        double snapped = (double) min + index * increment;
        return (float) Math.max(min, Math.min(max, snapped));
    }

    public static float snap(float raw, float min, float max, int steps, float increment) {
        return steps > 0 ? snap(raw, min, max, steps)
                : snapToIncrement(raw, min, max, increment);
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
