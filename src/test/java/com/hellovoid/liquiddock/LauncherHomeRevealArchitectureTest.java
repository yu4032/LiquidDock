package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static hook ownership only; HOME timing behavior belongs in HomeTransitionAuthorityStateTest. */
public class LauncherHomeRevealArchitectureTest {
    private static final Path HOME_HOOK = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassHomePresentationHook.java");

    @Test
    public void cachedHomeRevealIsOwnedOnlyByLauncherSpringStart() throws Exception {
        String source = Files.readString(HOME_HOOK);

        assertTrue(source.contains("hookHomeAnimationStart(classLoader)"));
        assertTrue(source.contains("HOME_AUTHORITY.onLauncherHomeAnimationStarted()"));
        assertFalse("WindowElement.animTo must freeze only; it must not reveal before spring start",
                source.contains("HOME_AUTHORITY.shouldRevealFromLauncherFallback()"));
        assertFalse(source.contains("APP HOME reveal armed by Launcher fallback"));
    }
}
