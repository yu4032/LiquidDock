package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static authority bans for unlock recovery; behavior remains covered by state-machine tests. */
public class UnlockCaptureRecoveryArchitectureTest {
    private static final Path HOME_PRESENTATION =
            Path.of("src/main/java/com/hellovoid/liquiddock/LauncherGlassHomePresentationHook.java");
    private static final Path STATE =
            Path.of("src/main/java/com/hellovoid/liquiddock/UnlockCaptureRecoveryState.java");

    @Test
    public void productionUnlockAuthorityHasNoTimerFailOpen() throws Exception {
        String hook = Files.readString(HOME_PRESENTATION);

        assertFalse(hook.contains("UNLOCK_CAPTURE_FAIL_OPEN_MS"));
        assertFalse(hook.contains("failOpenUnlockBarrier"));
        assertFalse(hook.contains("postDelayed("));
        assertFalse(hook.contains("unlock-timeout"));
        assertTrue(hook.contains("failClosedUnlockCapture()"));
    }

    @Test
    public void timeoutStateCannotReleaseCaptureBarrier() throws Exception {
        String state = Files.readString(STATE);
        int start = state.indexOf("Decision onWatchdogTimeout");
        int end = state.indexOf("synchronized boolean isBlocked", start);
        String watchdog = state.substring(start, end);

        assertTrue(watchdog.contains("FAILED_CLOSED"));
        assertFalse(watchdog.contains("releaseBarrier = true"));
        assertFalse(watchdog.contains("decision(false, false, true"));
    }
}
