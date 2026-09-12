package com.hellovoid.liquiddock;

/** One Security Center frame: one root backdrop, Dock + optional Toolbox + optional All Apps. */
final class SecurityCenterGlassFrameGeometry {
    private final SecurityCenterGlassGeometry dock;
    private final SecurityCenterGlassGeometry box;
    private final SecurityCenterGlassGeometry apps;
    private final SecurityCenterGlassGeometry presentation;

    private SecurityCenterGlassFrameGeometry(
            SecurityCenterGlassGeometry dock,
            SecurityCenterGlassGeometry box,
            SecurityCenterGlassGeometry apps,
            SecurityCenterGlassGeometry presentation) {
        this.dock = dock;
        this.box = box;
        this.apps = apps;
        this.presentation = presentation;
    }

    static SecurityCenterGlassFrameGeometry dockOnly(SecurityCenterGlassGeometry dock) {
        return compose(dock, null, null);
    }

    static SecurityCenterGlassFrameGeometry dockAndApps(
            SecurityCenterGlassGeometry dock,
            SecurityCenterGlassGeometry apps) {
        return compose(dock, null, apps);
    }

    static SecurityCenterGlassFrameGeometry compose(
            SecurityCenterGlassGeometry dock,
            SecurityCenterGlassGeometry box,
            SecurityCenterGlassGeometry apps) {
        if (dock == null) throw new IllegalArgumentException("dock == null");
        SecurityCenterGlassGeometry presentation = dock;
        if (box != null) {
            presentation = SecurityCenterGlassGeometry.covering(presentation, box);
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
        return new SecurityCenterGlassFrameGeometry(dock, box, apps, presentation);
    }

    int nodeCount() {
        return 1 + (box != null ? 1 : 0) + (apps != null ? 1 : 0);
    }

    SecurityCenterGlassGeometry nodeAt(int index) {
        if (index == 0) return dock;
        int cursor = 1;
        if (box != null) {
            if (index == cursor) return box;
            cursor++;
        }
        if (apps != null && index == cursor) return apps;
        throw new IndexOutOfBoundsException("index=" + index);
    }

    SecurityCenterGlassGeometry dockGeometry() { return dock; }
    SecurityCenterGlassGeometry boxGeometry() { return box; }
    SecurityCenterGlassGeometry appsGeometry() { return apps; }
    SecurityCenterGlassGeometry presentationGeometry() { return presentation; }

    boolean sameAs(SecurityCenterGlassFrameGeometry other) {
        return other != null
                && dock.sameAs(other.dock)
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
