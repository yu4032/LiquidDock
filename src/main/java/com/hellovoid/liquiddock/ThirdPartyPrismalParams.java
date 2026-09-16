package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalParams;

/** Applies third-party blur/tint overrides while preserving every other Prismal parameter. */
final class ThirdPartyPrismalParams {
    private ThirdPartyPrismalParams() {}

    static PrismalParams apply(PrismalParams base, ThirdPartyGlassAppearance appearance) {
        if (base == null || appearance == null) return base;
        PrismalParams.Builder b = PrismalParams.builder();
        b.ior = base.ior;
        b.glassThicknessPx = base.glassThicknessPx;
        b.normalStrength = base.normalStrength;
        b.displacementScale = base.displacementScale;
        b.heightTransitionWidthPx = base.heightTransitionWidthPx;
        b.sminSmoothingPx = base.sminSmoothingPx;
        b.refractionInsetPx = base.refractionInsetPx;
        b.edgeRefractionFalloff = base.edgeRefractionFalloff;
        b.liquidDome = base.liquidDome;
        b.fresnelReflect = base.fresnelReflect;
        b.lensRefractionScale = base.lensRefractionScale;
        b.lensDepthEffect = base.lensDepthEffect;
        b.chromaticAberration = base.chromaticAberration;
        b.dispersionR = base.dispersionR;
        b.dispersionB = base.dispersionB;
        b.vibrancy = base.vibrancy;
        b.plainHighlight = base.plainHighlight;
        b.brightness = base.brightness;
        b.highlightWidth = base.highlightWidth;
        b.os4EdgeWidthPx = base.os4EdgeWidthPx;
        b.os4ReflectOffsetPx = base.os4ReflectOffsetPx;
        b.os4ReflectionStrength = base.os4ReflectionStrength;
        b.os4ReflectionLighten = base.os4ReflectionLighten;
        b.os4DirectionalAngleRange = base.os4DirectionalAngleRange;
        b.os4DirectionalIntensity = base.os4DirectionalIntensity;
        b.os4DirectionalOppositeIntensity = base.os4DirectionalOppositeIntensity;
        b.lightDirX = base.lightDirX;
        b.lightDirY = base.lightDirY;
        b.specular = base.specular;
        b.shininess = base.shininess;
        b.rimStrength = base.rimStrength;
        b.causticIntensity = base.causticIntensity;
        b.shadowSoftness = base.shadowSoftness;
        b.transmittance = base.transmittance;
        b.backdropScaleX = base.backdropScaleX;
        b.backdropScaleY = base.backdropScaleY;
        b.parallaxScale = base.parallaxScale;
        b.blurRadiusPx = appearance.blur;
        b.tintR = appearance.tintR / 255f;
        b.tintG = appearance.tintG / 255f;
        b.tintB = appearance.tintB / 255f;
        b.tintA = appearance.tintAlpha / 255f;
        b.shadowR = base.shadowR;
        b.shadowG = base.shadowG;
        b.shadowB = base.shadowB;
        b.shadowA = base.shadowA;
        b.pressProgress = base.pressProgress;
        b.backdropPinch = base.backdropPinch;
        b.glowCenterX = base.glowCenterX;
        b.glowCenterY = base.glowCenterY;
        b.glowStrength = base.glowStrength;
        b.showNormals = base.showNormals;
        return b.build();
    }
}
