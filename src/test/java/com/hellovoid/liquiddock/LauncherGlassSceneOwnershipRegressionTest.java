package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression coverage for startup binding and vendor visual-owner handoff. */
public class LauncherGlassSceneOwnershipRegressionTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    private static Object rootTransformTracker() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.LauncherGlassRootTransformTracker");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing root-space ancestor transform tracker", missing);
        }
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static boolean updateTransform(Object tracker, float[] points) throws Exception {
        Method update = tracker.getClass().getDeclaredMethod("update", float[].class);
        update.setAccessible(true);
        return (Boolean) update.invoke(tracker, new Object[]{points});
    }

    @Test public void existingBootstrapObserverStillRetriesAnAttachedHost() throws Exception {
        String hook = source("MiuixLauncherStaticGlassHook.java");
        assertFalse(hook.contains("if (BOOTSTRAP_OBSERVERS.containsKey(host)) return;"));
        assertTrue(hook.contains(
                "if (BOOTSTRAP_OBSERVERS.containsKey(host)) {\n"
                        + "                if (host.isAttachedToWindow()) "
                        + "scheduleBind(host, kind, glassConfig, 0);\n"
                        + "                return;\n"
                        + "            }"));
    }

    @Test public void failedWorkspaceSessionAcquireRetriesInsteadOfAbandoningHost() throws Exception {
        String hook = source("MiuixLauncherStaticGlassHook.java");
        assertTrue(hook.contains("node == null && attempt < MAX_BIND_ATTEMPTS"));
        assertTrue(hook.contains("scheduleBind(host, kind, glassConfig, attempt + 1)"));
    }

    @Test public void rootTransformTrackerDetectsParentOnlyMotion() throws Exception {
        Object tracker = rootTransformTracker();
        float[] initial = new float[]{0f, 0f, 100f, 0f, 0f, 100f, 100f, 100f};
        assertTrue(updateTransform(tracker, initial));
        assertFalse(updateTransform(tracker, initial.clone()));
        assertTrue(updateTransform(tracker,
                new float[]{12f, 4f, 112f, 4f, 12f, 104f, 112f, 104f}));
        assertFalse(updateTransform(tracker,
                new float[]{12f, 4f, 112f, 4f, 12f, 104f, 112f, 104f}));
        assertTrue(updateTransform(tracker,
                new float[]{12f, 4f, 102f, 8f, 16f, 96f, 106f, 100f}));
    }

    @Test public void staticNodeSamplesRootSpaceTransformOnEverySceneSync() throws Exception {
        String node = source("LauncherGlassStaticNode.java");
        assertTrue(node.contains("LauncherGlassRootTransformTracker"));
        assertTrue(node.contains("consumeRootSpaceTransformMotion(material)"));
        assertTrue(node.contains("material.transformMatrixToGlobal"));
        assertTrue(node.contains("root.transformMatrixToGlobal"));
    }

    @Test public void vendorProxyVisibilityReleaseRestoresWorkspaceScene() throws Exception {
        String hook = source("MiuixLauncherStaticGlassHook.java");
        String node = source("LauncherGlassStaticNode.java");
        assertTrue(hook.contains("\"com.miui.home.launcher.ShortcutIcon\""));
        assertTrue(hook.contains("\"setAnimTargetVisibility\""));
        assertTrue(hook.contains("scheduleWorkspaceRecoveryFromHost"));
        assertTrue(hook.contains("anim-target-visible"));
        assertTrue(node.contains("beginLaunchProxy"));
        assertTrue(node.contains("endLaunchProxy"));
        assertTrue(node.contains("LauncherGlassVisualOwnerState"));
        assertFalse(node.contains("suppressedByLaunchProxy"));
    }
}
