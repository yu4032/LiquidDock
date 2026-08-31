package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Transient ownership bridge for Launcher 4.50 WidgetTypeAnimTarget animations.
 *
 * Widget launch-away reuses the StaticNode's existing reversible visibility animator so the glass
 * fades out while MIUI hides the native widget. Widget return is stricter: the widget glass is
 * removed from the cached StaticLayer immediately and is not allowed to fade back in until the
 * SceneController has reached HOME_VISIBLE for the matching fresh scene generation.
 */
final class LauncherWidgetTransitionCoordinator {
    private static final String TAG = "[DC][WidgetTransition]";
    private static final int MAX_FRESH_WAIT_FRAMES = 120;
    private static final int FORCE_FRESH_AFTER_FRAMES = 24;

    private static final Map<View, Entry> ENTRIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Entry {
        final WeakReference<View> materialRef;
        final WeakReference<LauncherGlassStaticNode> nodeRef;
        final LauncherWidgetTransitionState state = new LauncherWidgetTransitionState();

        Entry(View material, LauncherGlassStaticNode node) {
            materialRef = new WeakReference<>(material);
            nodeRef = new WeakReference<>(node);
        }
    }

    private static final class SceneSnapshot {
        final long generation;
        final String state;
        final boolean homePending;

        SceneSnapshot(long generation, String state, boolean homePending) {
            this.generation = generation;
            this.state = state;
            this.homePending = homePending;
        }

        boolean isFreshHomeVisible() {
            return generation > 0L && "HOME_VISIBLE".equals(state);
        }
    }

    private LauncherWidgetTransitionCoordinator() {}

    /** Called immediately before Launcher 4.50 sets a widget anim target INVISIBLE. */
    static void onAnimTargetWillHide(View material) {
        Entry entry = entryFor(material);
        if (entry == null) return;
        if (entry.state.isReturnTransition()) {
            hideImmediately(entry);
            return;
        }

        LauncherGlassStaticNode node = entry.nodeRef.get();
        if (node == null) return;
        entry.state.beginLaunchFadeOut();
        float startAlpha = node.visibilityAlpha();
        node.setSuppressedByDrag(true);
        long duration = LauncherGlassVisibilityTransition.plan(startAlpha, false).durationMs;
        material.postDelayed(() -> {
            Entry current = ENTRIES.get(material);
            if (current == entry) entry.state.finishLaunchFadeOut();
        }, Math.max(0L, duration) + 32L);
        MainHook.log(TAG + " widget->app fade-out host="
                + material.getClass().getSimpleName());
    }

    /**
     * Called from the vendor's real findClosingWidgetView() result, before it hides the widget for
     * App -> HOME. This removes the large widget from the old cached StaticLayer immediately.
     */
    static void markWidgetReturnTarget(View material) {
        Entry entry = entryFor(material);
        if (entry == null) return;

        if (!entry.state.isReturnTransition()) {
            SceneSnapshot scene = readScene(material);
            long generation = scene != null && scene.homePending ? scene.generation : -1L;
            entry.state.beginReturnWaitingFresh(generation);
        }
        hideImmediately(entry);
        MainHook.log(TAG + " app->widget target hidden pendingFresh="
                + entry.state.expectedFreshGeneration()
                + " host=" + material.getClass().getSimpleName());
    }

    /** Called immediately after HOME pending invalidates the SceneController generation. */
    static void onHomeOpeningStarted() {
        for (Entry entry : snapshotEntries()) {
            View material = entry.materialRef.get();
            if (material == null || !entry.state.isReturnWaitingFresh()) continue;
            SceneSnapshot scene = readScene(material);
            if (scene == null || scene.generation <= 0L) continue;
            entry.state.beginReturnWaitingFresh(scene.generation);
            hideImmediately(entry);
            MainHook.log(TAG + " widget return armed generation=" + scene.generation
                    + " host=" + material.getClass().getSimpleName());
        }
    }

    /**
     * HOME FINISH merely releases the producer barrier. The widget stays hidden until the fresh
     * scene has actually rendered and SceneController reaches HOME_VISIBLE for that generation.
     */
    static void onHomeBarrierReleased() {
        for (Entry entry : snapshotEntries()) {
            View material = entry.materialRef.get();
            if (material == null || !entry.state.isReturnWaitingFresh()) continue;
            material.postOnAnimation(() -> awaitFreshHome(entry, 0));
        }
    }

    private static void awaitFreshHome(Entry entry, int attempt) {
        View material = entry.materialRef.get();
        LauncherGlassStaticNode node = entry.nodeRef.get();
        if (material == null || node == null || !material.isAttachedToWindow()) {
            removeEntry(entry);
            return;
        }
        Entry current = ENTRIES.get(material);
        if (current != entry || !entry.state.isReturnWaitingFresh()) return;

        SceneSnapshot scene = readScene(material);
        if (scene != null && scene.generation > 0L) {
            long expected = entry.state.expectedFreshGeneration();
            // Producer/ViewRoot rollover may invalidate the scene again while recovering the fresh
            // frame. Follow only newer generations; never accept an older cached generation.
            if (expected < 0L || scene.generation > expected) {
                entry.state.beginReturnWaitingFresh(scene.generation);
                expected = scene.generation;
            }
            if (scene.isFreshHomeVisible() && scene.generation == expected
                    && entry.state.onFreshFrame(scene.generation)) {
                node.setSuppressedByDrag(false);
                long duration = LauncherGlassVisibilityTransition.plan(0f, true).durationMs;
                material.postDelayed(() -> finishReturnFade(entry),
                        Math.max(0L, duration) + 32L);
                MainHook.log(TAG + " app->widget fresh fade-in generation=" + scene.generation
                        + " host=" + material.getClass().getSimpleName());
                return;
            }
        }

        if (attempt == FORCE_FRESH_AFTER_FRAMES) {
            View root = LauncherGlassSessionRegistry.resolveStableRoot(material);
            if (root != null) LauncherGlassSceneController.requestFreshForRoot(root);
            MainHook.log(TAG + " widget return requested fresh retry host="
                    + material.getClass().getSimpleName());
        }
        if (attempt >= MAX_FRESH_WAIT_FRAMES) {
            // Fail open only after a long bounded wait. This avoids permanently losing widget glass
            // on an unexpected vendor edge path while still preventing the normal stale-cache jump.
            entry.state.cancel();
            node.setSuppressedByDrag(false);
            removeEntry(entry);
            MainHook.log(TAG + " widget return fresh wait timed out; fail-open host="
                    + material.getClass().getSimpleName());
            return;
        }
        material.postOnAnimation(() -> awaitFreshHome(entry, attempt + 1));
    }

    private static void finishReturnFade(Entry entry) {
        View material = entry.materialRef.get();
        if (material == null || ENTRIES.get(material) != entry) return;
        entry.state.finishReturnFadeIn();
        removeEntry(entry);
    }

    private static Entry entryFor(View material) {
        if (material == null || !GlassRuntimeState.isWidgetEnabled()) return null;
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(material);
        if (node == null || node.kind() != LauncherGlassDragState.Kind.WIDGET) return null;
        Entry current = ENTRIES.get(material);
        if (current != null && current.nodeRef.get() == node) return current;
        Entry created = new Entry(material, node);
        ENTRIES.put(material, created);
        return created;
    }

    private static void hideImmediately(Entry entry) {
        LauncherGlassStaticNode node = entry.nodeRef.get();
        if (node == null) return;
        // Use the node's existing suppression flag so normal Session geometry cleanup remains
        // coherent, but cancel the fade immediately for return-to-widget: stale pixels must not be
        // exposed while HOME early-reveal is showing the previous StaticLayer generation.
        node.setSuppressedByDrag(true);
        HookUtil.invoke(node, "hideImmediately");
        node.requestLifecycleRefresh();
    }

    private static SceneSnapshot readScene(View material) {
        try {
            LauncherGlassSceneController controller = LauncherGlassSceneController.find(material);
            if (controller == null) return null;
            Object stateMachine = HookUtil.getField(controller, "state");
            Object generationValue = HookUtil.invoke(stateMachine, "generation");
            Object stateValue = HookUtil.invoke(stateMachine, "state");
            long generation = generationValue instanceof Number
                    ? ((Number) generationValue).longValue() : -1L;
            boolean homePending = HookUtil.getBooleanField(controller, "homeTransitionPending");
            return new SceneSnapshot(generation, String.valueOf(stateValue), homePending);
        } catch (Throwable error) {
            return null;
        }
    }

    private static ArrayList<Entry> snapshotEntries() {
        synchronized (ENTRIES) {
            return new ArrayList<>(ENTRIES.values());
        }
    }

    private static void removeEntry(Entry entry) {
        View material = entry != null ? entry.materialRef.get() : null;
        if (material == null) return;
        synchronized (ENTRIES) {
            if (ENTRIES.get(material) == entry) ENTRIES.remove(material);
        }
    }
}
