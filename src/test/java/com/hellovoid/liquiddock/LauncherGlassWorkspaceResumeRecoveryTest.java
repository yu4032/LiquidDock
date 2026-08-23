package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression coverage for HyperOS Mingou hiding/restoring the current Workspace CellLayout. */
public class LauncherGlassWorkspaceResumeRecoveryTest {
    private static Object tracker() throws Exception {
        Class<?> type;
        try {
            type = Class.forName(
                    "com.hellovoid.liquiddock.LauncherGlassEffectiveVisibilityTracker");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing effective ancestor visibility tracker", missing);
        }
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static boolean update(Object tracker, float alpha) throws Exception {
        Method method = tracker.getClass().getDeclaredMethod("update", float.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(tracker, alpha);
    }

    @Test public void ancestorHideAndRestoreBothInvalidateGeometry() throws Exception {
        Object tracker = tracker();
        assertTrue(update(tracker, 1.0f));
        assertFalse(update(tracker, 1.0f));
        assertTrue(update(tracker, 0.0f));
        assertFalse(update(tracker, 0.0f));
        assertTrue(update(tracker, 1.0f));
        assertFalse(update(tracker, 1.0f));
    }

    @Test public void nonFiniteEffectiveAlphaStillTransitionsDeterministically() throws Exception {
        Object tracker = tracker();
        assertTrue(update(tracker, Float.NaN));
        assertFalse(update(tracker, Float.NaN));
        assertTrue(update(tracker, 1.0f));
    }

    @Test public void staticNodeTracksAncestorEffectiveVisibilityEverySceneSync() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java"));
        assertTrue(source.contains("LauncherGlassEffectiveVisibilityTracker"));
        assertTrue(source.contains("LauncherGlassVisibility.effectiveAlpha(material, sceneRoot)"));
    }

    @Test public void verifiedLauncherResumeAndMingouRestoreBoundariesRecoverCurrentPage()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java"));
        assertTrue(source.contains("\"com.miui.home.launcher.Launcher\", \"onResume\""));
        assertTrue(source.contains("\"restoreMingouDesktopIconBlurSourceIfNeeded\""));
        assertTrue(source.contains("reconcileCurrentWorkspacePage"));
        assertTrue(source.contains("LauncherGlassSceneController.requestFreshForRoot"));
    }
}
