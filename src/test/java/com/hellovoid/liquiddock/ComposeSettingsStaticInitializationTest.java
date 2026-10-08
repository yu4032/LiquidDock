package com.hellovoid.liquiddock;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/** Regression guard for top-level settings initialization ordering. */
public class ComposeSettingsStaticInitializationTest {
    @Test
    public void settingsFileInitializesWithoutForwardReferenceCrash() throws Exception {
        Class<?> settingsKt = Class.forName(
                "com.hellovoid.liquiddock.ComposeSettingsActivityKt",
                true,
                getClass().getClassLoader());
        assertNotNull(settingsKt);
    }
}
