package com.hellovoid.liquiddock;

/** Android-free planner for effective Dock visual ownership transitions. */
final class VisualRuntimeTransitionPolicy {
    static final class Snapshot {
        final boolean dockCustomization;
        final boolean stroke;
        final boolean dockShadow;
        final boolean strokeShadow;
        final boolean divider;
        final boolean mirrorHidden;

        Snapshot(boolean dockCustomization, boolean stroke, boolean dockShadow,
                 boolean strokeShadow, boolean divider, boolean mirrorHidden) {
            this.dockCustomization = dockCustomization;
            this.stroke = stroke;
            this.dockShadow = dockShadow;
            this.strokeShadow = strokeShadow;
            this.divider = divider;
            this.mirrorHidden = mirrorHidden;
        }
    }

    static final class Transition {
        final boolean dockCustomizationDisabled;
        final boolean strokeDisabled;
        final boolean strokeEnabled;
        final boolean dockShadowDisabled;
        final boolean dockShadowEnabled;
        final boolean strokeShadowChanged;
        final boolean dividerDisabled;
        final boolean mirrorVisibilityChanged;

        Transition(boolean dockCustomizationDisabled,
                   boolean strokeDisabled, boolean strokeEnabled,
                   boolean dockShadowDisabled, boolean dockShadowEnabled,
                   boolean strokeShadowChanged, boolean dividerDisabled,
                   boolean mirrorVisibilityChanged) {
            this.dockCustomizationDisabled = dockCustomizationDisabled;
            this.strokeDisabled = strokeDisabled;
            this.strokeEnabled = strokeEnabled;
            this.dockShadowDisabled = dockShadowDisabled;
            this.dockShadowEnabled = dockShadowEnabled;
            this.strokeShadowChanged = strokeShadowChanged;
            this.dividerDisabled = dividerDisabled;
            this.mirrorVisibilityChanged = mirrorVisibilityChanged;
        }
    }

    private VisualRuntimeTransitionPolicy() {}

    static Transition plan(Snapshot before, Snapshot after) {
        return new Transition(
                before.dockCustomization && !after.dockCustomization,
                before.stroke && !after.stroke,
                !before.stroke && after.stroke,
                before.dockShadow && !after.dockShadow,
                !before.dockShadow && after.dockShadow,
                before.strokeShadow != after.strokeShadow,
                before.divider && !after.divider,
                before.mirrorHidden != after.mirrorHidden);
    }
}
