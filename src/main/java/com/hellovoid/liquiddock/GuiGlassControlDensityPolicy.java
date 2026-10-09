package com.hellovoid.liquiddock;

/**
 * One PrismalAGSL slider records a separate track layer, combines two backdrop
 * sources and owns a spring-animated refracting thumb. Dense settings groups
 * prefer the MIUIX native slider to avoid multiplying these capture/render
 * chains while keeping their enclosing Prismal cards and steppers untouched.
 */
final class GuiGlassControlDensityPolicy {
    static final int DENSE_SLIDER_THRESHOLD = 5;

    private GuiGlassControlDensityPolicy() {}

    static boolean useLightweightTrack(int sliderCount) {
        return sliderCount >= DENSE_SLIDER_THRESHOLD;
    }
}
