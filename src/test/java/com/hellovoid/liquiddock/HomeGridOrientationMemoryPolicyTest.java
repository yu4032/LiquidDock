package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Pure guards for delayed per-orientation layout-memory work. */
public class HomeGridOrientationMemoryPolicyTest {
    private static Method requireGuard() throws Exception {
        try {
            Class<?> policy = Class.forName(
                    "com.hellovoid.liquiddock.HomeGridOrientationMemoryPolicy");
            Method method = policy.getDeclaredMethod("shouldResolve",
                    boolean.class, boolean.class, long.class, long.class,
                    HomeGridOrientation.class, HomeGridOrientation.class);
            method.setAccessible(true);
            return method;
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            fail("missing runtime HomeGridOrientationMemoryPolicy.shouldResolve");
            throw missing;
        }
    }

    private static boolean shouldResolve(
            boolean workstationMode, boolean sameWorkspace,
            long scheduledGeneration, long currentGeneration,
            HomeGridOrientation target, HomeGridOrientation current) throws Exception {
        return (Boolean) requireGuard().invoke(null,
                workstationMode, sameWorkspace,
                scheduledGeneration, currentGeneration, target, current);
    }

    @Test public void staleGenerationCannotRestoreOldOrientation() throws Exception {
        assertFalse(shouldResolve(false, true, 4L, 5L,
                HomeGridOrientation.LANDSCAPE, HomeGridOrientation.LANDSCAPE));
    }

    @Test public void targetMustStillMatchPhysicalOrientation() throws Exception {
        assertFalse(shouldResolve(false, true, 5L, 5L,
                HomeGridOrientation.LANDSCAPE, HomeGridOrientation.PORTRAIT));
    }

    @Test public void workstationAndReplacedWorkspaceAreExcluded() throws Exception {
        assertFalse(shouldResolve(true, true, 5L, 5L,
                HomeGridOrientation.LANDSCAPE, HomeGridOrientation.LANDSCAPE));
        assertFalse(shouldResolve(false, false, 5L, 5L,
                HomeGridOrientation.LANDSCAPE, HomeGridOrientation.LANDSCAPE));
    }

    @Test public void currentNormalWorkspaceMayResolveCurrentTarget() throws Exception {
        assertTrue(shouldResolve(false, true, 5L, 5L,
                HomeGridOrientation.LANDSCAPE, HomeGridOrientation.LANDSCAPE));
    }
}
