package com.hellovoid.liquiddock.config;

/** Persisted shared glass render-quality controls; native PassBlur geometry remains authoritative at 1.0. */
public final class PassBlurQualityKeys {
    public static final String CAPTURE_SCALE = "liquid_passblur_capture_scale";
    public static final int CAPTURE_SCALE_DEFAULT = 100;
    public static final int CAPTURE_SCALE_MIN = 50;
    public static final int CAPTURE_SCALE_MAX = 100;

    public static final String RENDER_FPS = "liquid_passblur_render_fps";
    public static final int RENDER_FPS_DEFAULT = 0;
    public static final int RENDER_FPS_MIN = 0;
    // Storage/import safety only. Runtime/UI limits are display-driven, not a fixed refresh cap.
    public static final int RENDER_FPS_MAX = Integer.MAX_VALUE;

    private PassBlurQualityKeys() {}
}