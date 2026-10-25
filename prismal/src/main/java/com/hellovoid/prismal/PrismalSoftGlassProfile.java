package com.hellovoid.prismal;

/** Independent HyperOS 4 soft-glass stages that do not share an existing Prismal control. */
public final class PrismalSoftGlassProfile {
    public static final PrismalSoftGlassProfile DEFAULT = new Builder().build();

    public final float edgePower;
    public final float lightZ;
    public final float edgeAmbient;
    public final float edgeLightLength;
    public final float backgroundBlurRadiusPx;
    public final float backgroundBlurMix;
    public final float darkerLow;
    public final float darkerHigh;
    public final float backgroundSaturation;
    public final float backgroundBrightness;
    public final float innerBottom;
    public final float innerWhite;
    public final float innerMix;
    public final float luminanceAmount;
    public final float luminanceC0;
    public final float luminanceC1;
    public final float luminanceC2;
    public final float luminanceC3;
    public final float colorPower;
    public final float unshade;
    public final float burn;

    private PrismalSoftGlassProfile(Builder b) {
        edgePower = b.edgePower;
        lightZ = b.lightZ;
        edgeAmbient = b.edgeAmbient;
        edgeLightLength = b.edgeLightLength;
        backgroundBlurRadiusPx = b.backgroundBlurRadiusPx;
        backgroundBlurMix = b.backgroundBlurMix;
        darkerLow = b.darkerLow;
        darkerHigh = b.darkerHigh;
        backgroundSaturation = b.backgroundSaturation;
        backgroundBrightness = b.backgroundBrightness;
        innerBottom = b.innerBottom;
        innerWhite = b.innerWhite;
        innerMix = b.innerMix;
        luminanceAmount = b.luminanceAmount;
        luminanceC0 = b.luminanceC0;
        luminanceC1 = b.luminanceC1;
        luminanceC2 = b.luminanceC2;
        luminanceC3 = b.luminanceC3;
        colorPower = b.colorPower;
        unshade = b.unshade;
        burn = b.burn;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        public float edgePower = 2.2f;
        public float lightZ = 1f;
        public float edgeAmbient;
        public float edgeLightLength = 1f;
        /** Independent OS4 uBgColorTex blur scale, processed through the existing PassBlur kernel. */
        public float backgroundBlurRadiusPx = 30f;
        /** Adaptation mix between the unblurred capture and the dedicated OS4 background blur. */
        public float backgroundBlurMix = 0.5f;
        public float darkerLow;
        public float darkerHigh = 1f;
        public float backgroundSaturation = 1f;
        public float backgroundBrightness;
        public float innerBottom;
        public float innerWhite;
        public float innerMix;
        public float luminanceAmount;
        public float luminanceC0;
        public float luminanceC1;
        public float luminanceC2 = 1f;
        public float luminanceC3;
        public float colorPower = 1f;
        public float unshade;
        public float burn = 1f;

        public PrismalSoftGlassProfile build() { return new PrismalSoftGlassProfile(this); }
    }

    private PrismalSoftGlassProfile() {
        throw new AssertionError("Use Builder");
    }
}
