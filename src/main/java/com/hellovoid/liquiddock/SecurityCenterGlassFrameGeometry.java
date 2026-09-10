package com.hellovoid.liquiddock;

/** One Security Center frame: one root backdrop, one or two glass nodes, one output region. */
final class SecurityCenterGlassFrameGeometry {
    private final SecurityCenterGlassGeometry dock;
    private final SecurityCenterGlassGeometry apps;
    private final SecurityCenterGlassGeometry presentation;

    private SecurityCenterGlassFrameGeometry(
            SecurityCenterGlassGeometry dock,
            SecurityCenterGlassGeometry apps,
            SecurityCenterGlassGeometry presentation) {
        this.dock = dock;
        this.apps = apps;
        this.presentation = presentation;
    }

    static SecurityCenterGlassFrameGeometry dockOnly(SecurityCenterGlassGeometry dock) {
        if (dock == null) throw new IllegalArgumentException("dock == null");
        return new SecurityCenterGlassFrameGeometry(dock, null, dock);
    }

    static SecurityCenterGlassFrameGeometry dockAndApps(
            SecurityCenterGlassGeometry dock,
            SecurityCenterGlassGeometry apps) {
        if (dock == null || apps == null) {
            throw new IllegalArgumentException("missing Security Center frame node");
        }
        SecurityCenterGlassGeometry presentation = SecurityCenterGlassGeometry.covering(dock, apps);
        if (presentation == null) {
            throw new IllegalArgumentException("Security Center frame nodes use different roots");
        }
        return new SecurityCenterGlassFrameGeometry(dock, apps, presentation);
    }

    int nodeCount() { return apps != null ? 2 : 1; }

    SecurityCenterGlassGeometry nodeAt(int index) {
        if (index == 0) return dock;
        if (index == 1 && apps != null) return apps;
        throw new IndexOutOfBoundsException("index=" + index);
    }

    SecurityCenterGlassGeometry dockGeometry() { return dock; }
    SecurityCenterGlassGeometry appsGeometry() { return apps; }
    SecurityCenterGlassGeometry presentationGeometry() { return presentation; }

    boolean sameAs(SecurityCenterGlassFrameGeometry other) {
        if (other == null || !dock.sameAs(other.dock)) return false;
        if (apps == null || other.apps == null) return apps == other.apps;
        return apps.sameAs(other.apps);
    }
}
