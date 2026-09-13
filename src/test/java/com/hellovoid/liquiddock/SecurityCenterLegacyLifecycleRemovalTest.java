package com.hellovoid.liquiddock;

import static org.junit.Assert.fail;

import org.junit.Test;

/** Obsolete page-scene/reflection helpers must not survive the material-carrier migration. */
public class SecurityCenterLegacyLifecycleRemovalTest {
    @Test
    public void obsoleteLifecycleHelpersAreAbsentFromRuntimeClasspath() throws Exception {
        assertMissing("com.hellovoid.liquiddock.SecurityCenterGlassSceneState");
        assertMissing("com.hellovoid.liquiddock.SecurityCenterAllAppsSettleState");
        assertMissing("com.hellovoid.liquiddock.SecurityCenterSourceAuthorityController");
    }

    private static void assertMissing(String className) throws Exception {
        try {
            Class.forName(className);
            fail(className + " must be removed after material-carrier migration");
        } catch (ClassNotFoundException expected) {
            // Required state: the obsolete helper is no longer part of the runtime artifact.
        }
    }
}
