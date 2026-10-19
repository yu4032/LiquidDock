package com.hellovoid.prismal;

/** Immutable per-draw highlight selection. Optics remain shared by Dock and Launcher surfaces. */
public final class PrismalHighlightProfile {
    public static final PrismalHighlightProfile ALL_ENABLED =
            new PrismalHighlightProfile(true, true, true, true, true, true, true, true, true);

    public final boolean skyHaze;
    public final boolean specular;
    public final boolean litRim;
    public final boolean oppositeRim;
    public final boolean cornerRim;
    public final boolean faceSheen;
    public final boolean plainHighlight;
    public final boolean caustics;
    public final boolean pressGlow;
    public final boolean os4Edge;
    public final boolean ios27Dock;
    private volatile PrismalHighlightProfile os4Replacement;

    public PrismalHighlightProfile(
            boolean skyHaze,
            boolean specular,
            boolean litRim,
            boolean oppositeRim,
            boolean cornerRim,
            boolean faceSheen,
            boolean plainHighlight,
            boolean caustics,
            boolean pressGlow) {
        this(skyHaze, specular, litRim, oppositeRim, cornerRim, faceSheen,
                plainHighlight, caustics, pressGlow, false, false);
    }

    private PrismalHighlightProfile(
            boolean skyHaze,
            boolean specular,
            boolean litRim,
            boolean oppositeRim,
            boolean cornerRim,
            boolean faceSheen,
            boolean plainHighlight,
            boolean caustics,
            boolean pressGlow,
            boolean os4Edge,
            boolean ios27Dock) {
        this.skyHaze = skyHaze;
        this.specular = specular;
        this.litRim = litRim;
        this.oppositeRim = oppositeRim;
        this.cornerRim = cornerRim;
        this.faceSheen = faceSheen;
        this.plainHighlight = plainHighlight;
        this.caustics = caustics;
        this.pressGlow = pressGlow;
        this.os4Edge = os4Edge;
        this.ios27Dock = ios27Dock;
    }

    /** Dock-body-only preset; remains selected when legacy rim is replaced by OS4. */
    public PrismalHighlightProfile withIos27DockOptics() {
        if (ios27Dock) return this;
        return new PrismalHighlightProfile(
                skyHaze, specular, litRim, oppositeRim, cornerRim,
                faceSheen, plainHighlight, caustics, pressGlow, os4Edge, true);
    }

    /** Replace both parameter-driven legacy edge families with one OS4 optical edge. */
    public PrismalHighlightProfile withOs4EdgeReplacingLegacyEdge() {
        if (os4Edge && !specular && !litRim && !oppositeRim
                && !cornerRim && !faceSheen && !plainHighlight) {
            return this;
        }
        PrismalHighlightProfile cached = os4Replacement;
        if (cached != null) return cached;
        PrismalHighlightProfile created = new PrismalHighlightProfile(
                skyHaze,
                false,
                false,
                false,
                false,
                false,
                false,
                caustics,
                pressGlow,
                true,
                ios27Dock);
        os4Replacement = created;
        return created;
    }
}
