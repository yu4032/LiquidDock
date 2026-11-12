package com.hellovoid.liquiddock;

/** Native applied transforms, in the same monotonic clock domain as SurfaceTexture timestamps. */
final class LauncherWallpaperTransformState {
    static final class Sample {
        static final Sample NEUTRAL = new Sample(0, 1f, .5f, .5f);
        final long timestampNs;
        final float scale, pivotX, pivotY;

        Sample(long timestampNs, float scale, float pivotX, float pivotY) {
            this.timestampNs = timestampNs;
            this.scale = scale;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
        }
    }

    private final Sample[] history = new Sample[256];
    private int next, count;
    private volatile Sample latest;

    synchronized boolean update(long timestampNs, float scale, float pivotX, float pivotY) {
        if (timestampNs <= 0
                || !Float.isFinite(scale)
                || scale <= 0f
                || scale > 4f
                || !Float.isFinite(pivotX)
                || !Float.isFinite(pivotY)
                || (latest != null && timestampNs <= latest.timestampNs)) return false;
        Sample sample = new Sample(timestampNs, scale, pivotX, pivotY);
        history[next] = sample;
        next = (next + 1) % history.length;
        count = Math.min(count + 1, history.length);
        latest = sample;
        return true;
    }

    Sample latest() {
        return latest;
    }

    synchronized Sample at(long timestampNs) {
        if (timestampNs <= 0) return latest;
        for (int i = 0; i < count; i++) {
            Sample sample = history[(next - 1 - i + history.length) % history.length];
            if (sample.timestampNs <= timestampNs) return sample;
        }
        return null;
    }

    /** Invert the current native transform, then apply the transform baked into the capture. */
    static void map(Sample captured, Sample current, float[] output) {
        float scale = captured.scale / current.scale;
        output[0] = output[1] = scale;
        output[2] =
                captured.scale * (1f - 1f / current.scale) * current.pivotX
                        + (1f - captured.scale) * captured.pivotX;
        output[3] =
                captured.scale * (1f - 1f / current.scale) * current.pivotY
                        + (1f - captured.scale) * captured.pivotY;
    }
}
