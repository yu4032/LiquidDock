package com.hellovoid.liquiddock;

/** Predicts the native spring's equilibrium; its real end callback remains authoritative. */
final class LauncherGlassUnlockFadeTiming {
    static long fadeDelayMs(long remainingMs, long fadeMs) {
        return Math.max(0L, remainingMs - Math.max(0L, fadeMs));
    }
    static long fadeDurationMs(long remainingMs, long fadeMs) {
        return Math.max(0L, Math.min(remainingMs, fadeMs));
    }
    static long springDurationMs(double damping, double response, double minimumChange) {
        if (!(damping > 0 && damping < 1 && response > 0 && minimumChange > 0)
                || !Double.isFinite(damping + response + minimumChange)) return 0;
        double omega = 2 * Math.PI / response;
        double decay = damping * omega;
        double frequency = omega * Math.sqrt(1 - damping * damping);
        double positionThreshold = minimumChange * 0.75;
        double velocityThreshold = positionThreshold * 62.5;
        for (long ms = 1; ms <= 10000; ms++) {
            double t = ms / 1000.0;
            double envelope = Math.exp(-decay * t);
            double position = -envelope * (Math.cos(frequency * t)
                    + decay / frequency * Math.sin(frequency * t));
            double velocity = envelope * omega * omega / frequency * Math.sin(frequency * t);
            if (Math.abs(position) < positionThreshold && Math.abs(velocity) < velocityThreshold)
                return ms;
        }
        return 0;
    }
}
