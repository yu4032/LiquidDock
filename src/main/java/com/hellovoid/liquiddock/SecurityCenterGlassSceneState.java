package com.hellovoid.liquiddock;

/**
 * Android-free scene-generation state machine for Security Center Global Dock / All Apps glass.
 *
 * <p>The state object owns only lifecycle and authorization decisions. Android Views, vendor
 * objects, rendering, and material mutations stay in the coordinator/session layers.</p>
 */
final class SecurityCenterGlassSceneState {
    enum Scene {
        DETACHED,
        PREPARING_DOCK,
        DOCK,
        TRANSITIONING,
        PREPARING_ALL_APPS,
        ALL_APPS
    }

    enum Target { DOCK, ALL_APPS }

    static final class Decision {
        final boolean ensureSession;
        final boolean invalidateGeneration;
        final boolean requestFresh;
        final boolean hideCustom;
        final boolean releaseCustomOwnership;
        final boolean claimCustomOwnership;
        final boolean revealCustom;
        final boolean shutdownSession;
        final long generation;

        Decision(
                boolean ensureSession,
                boolean invalidateGeneration,
                boolean requestFresh,
                boolean hideCustom,
                boolean releaseCustomOwnership,
                boolean claimCustomOwnership,
                boolean revealCustom,
                boolean shutdownSession,
                long generation) {
            this.ensureSession = ensureSession;
            this.invalidateGeneration = invalidateGeneration;
            this.requestFresh = requestFresh;
            this.hideCustom = hideCustom;
            this.releaseCustomOwnership = releaseCustomOwnership;
            this.claimCustomOwnership = claimCustomOwnership;
            this.revealCustom = revealCustom;
            this.shutdownSession = shutdownSession;
            this.generation = generation;
        }
    }

    private Scene scene = Scene.DETACHED;
    private long generation;

    Decision onRootAttached() {
        if (scene != Scene.DETACHED) return none();
        generation++;
        scene = Scene.PREPARING_DOCK;
        return decision(true, true, false, true, false, false, false, false);
    }

    Decision onTransitionStarted() {
        if (scene == Scene.DETACHED) return none();
        // A new vendor d0() invalidates freshness, not presentation ownership. Once a custom
        // frame has been authorized, it remains visible until a newer custom frame replaces it.
        // This forbids a custom -> vendor -> custom flash during Dock/All Apps transitions.
        generation++;
        scene = Scene.TRANSITIONING;
        return decision(false, true, false, false, false, false, false, false);
    }

    Decision onGeometrySettled(Target target) {
        if (target == null) return none();
        // Initial Dock geometry is accepted only while preparing the newly attached root.
        // A later observer cannot retarget an already revealed generation.
        if (scene == Scene.PREPARING_DOCK && target == Target.DOCK) {
            return settle(target);
        }
        if (scene != Scene.TRANSITIONING) return none();
        return settle(target);
    }

    Decision onGeometrySettled(Target target, long expectedGeneration) {
        if (expectedGeneration < 0L || expectedGeneration != generation) return none();
        if (scene != Scene.TRANSITIONING || target == null) return none();
        return settle(target);
    }

    Decision onFreshFrameRendered(long renderedGeneration) {
        if (renderedGeneration != generation) return none();
        if (scene == Scene.PREPARING_DOCK) {
            scene = Scene.DOCK;
        } else if (scene == Scene.PREPARING_ALL_APPS) {
            scene = Scene.ALL_APPS;
        } else {
            return none();
        }
        return decision(false, false, false, false, false, true, true, false);
    }

    Decision onRuntimeDisabled() {
        return terminate();
    }

    Decision onTerminalFailure() {
        return terminate();
    }

    Decision onRootDetached() {
        return terminate();
    }

    Scene scene() {
        return scene;
    }

    long generation() {
        return generation;
    }

    private Decision settle(Target target) {
        scene = target == Target.ALL_APPS ? Scene.PREPARING_ALL_APPS : Scene.PREPARING_DOCK;
        return decision(false, false, true, false, false, false, false, false);
    }

    private Decision terminate() {
        generation++;
        scene = Scene.DETACHED;
        return decision(false, true, false, true, true, false, false, true);
    }

    private Decision none() {
        return decision(false, false, false, false, false, false, false, false);
    }

    private Decision decision(
            boolean ensureSession,
            boolean invalidateGeneration,
            boolean requestFresh,
            boolean hideCustom,
            boolean releaseCustomOwnership,
            boolean claimCustomOwnership,
            boolean revealCustom,
            boolean shutdownSession) {
        return new Decision(
                ensureSession,
                invalidateGeneration,
                requestFresh,
                hideCustom,
                releaseCustomOwnership,
                claimCustomOwnership,
                revealCustom,
                shutdownSession,
                generation);
    }
}
