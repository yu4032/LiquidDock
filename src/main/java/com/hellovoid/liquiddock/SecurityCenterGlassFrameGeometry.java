package com.hellovoid.liquiddock;

/** One Security Center frame: one root backdrop, Dock + optional Toolbox or All Apps. */
final class SecurityCenterGlassFrameGeometry {
    private final SecurityCenterGlassGeometry dock;
    private final SecurityCenterGlassGeometry preview;
    private final SecurityCenterGlassGeometry box;
    private final SecurityCenterGlassGeometry apps;
    private final SecurityCenterGlassGeometry presentation;

    private SecurityCenterGlassFrameGeometry(
            SecurityCenterGlassGeometry dock,
            SecurityCenterGlassGeometry preview,
            SecurityCenterGlassGeometry box,
            SecurityCenterGlassGeometry apps,
            SecurityCenterGlassGeometry presentation) {
        this.dock = dock;
        this.preview = preview;
        this.box = box;
        this.apps = apps;
        this.presentation = presentation;
    }

    static SecurityCenterGlassFrameGeometry dockOnly(SecurityCenterGlassGeometry dock) {
        return compose(dock, null, null, null);
    }

    static SecurityCenterGlassFrameGeometry dockAndApps(
            SecurityCenterGlassGeometry dock,
            SecurityCenterGlassGeometry apps) {
        return compose(dock, null, null, apps);
    }

    static SecurityCenterGlassFrameGeometry compose(
            SecurityCenterGlassGeometry dock,
            SecurityCenterGlassGeometry preview,
            SecurityCenterGlassGeometry box,
            SecurityCenterGlassGeometry apps) {
        if (dock == null) throw new IllegalArgumentException("dock == null");

        // All Apps is a sibling material carrier in the same TurboLayout. While it is present it
        // owns the toolbox material slot: the vendor moves the normal toolbox away instead of
        // presenting three simultaneous material panes. Keep the shared root backdrop, but never
        // ask Prismal to render Toolbox and All Apps in one frame.
        SecurityCenterGlassGeometry effectiveBox = apps != null ? null : box;
        SecurityCenterGlassGeometry presentation = dock;
        if (preview != null) {
            presentation = SecurityCenterGlassGeometry.covering(presentation, preview);
            if (presentation == null) {
                throw new IllegalArgumentException("Security Center preview uses different root");
            }
        }
        if (effectiveBox != null) {
            presentation = SecurityCenterGlassGeometry.covering(presentation, effectiveBox);
            if (presentation == null) {
                throw new IllegalArgumentException("Security Center frame nodes use different roots");
            }
        }
        if (apps != null) {
            presentation = SecurityCenterGlassGeometry.covering(presentation, apps);
            if (presentation == null) {
                throw new IllegalArgumentException("Security Center frame nodes use different roots");
            }
        }
        return new SecurityCenterGlassFrameGeometry(
                dock, preview, effectiveBox, apps, presentation);
    }

    int nodeCount() {
        return 1 + (preview != null ? 1 : 0)
                + (box != null ? 1 : 0) + (apps != null ? 1 : 0);
    }

    SecurityCenterGlassGeometry nodeAt(int index) {
        if (index == 0) return dock;
        int cursor = 1;
        if (preview != null) {
            if (index == cursor) return preview;
            cursor++;
        }
        if (box != null) {
            if (index == cursor) return box;
            cursor++;
        }
        if (apps != null && index == cursor) return apps;
        throw new IndexOutOfBoundsException("index=" + index);
    }

    SecurityCenterGlassGeometry dockGeometry() { return dock; }
    SecurityCenterGlassGeometry previewGeometry() { return preview; }
    SecurityCenterGlassGeometry boxGeometry() { return box; }
    SecurityCenterGlassGeometry appsGeometry() { return apps; }
    SecurityCenterGlassGeometry presentationGeometry() { return presentation; }

    boolean sameAs(SecurityCenterGlassFrameGeometry other) {
        return other != null
                && dock.sameAs(other.dock)
                && sameNullable(preview, other.preview)
                && sameNullable(box, other.box)
                && sameNullable(apps, other.apps);
    }

    private static boolean sameNullable(
            SecurityCenterGlassGeometry first,
            SecurityCenterGlassGeometry second) {
        if (first == null || second == null) return first == second;
        return first.sameAs(second);
    }
}
