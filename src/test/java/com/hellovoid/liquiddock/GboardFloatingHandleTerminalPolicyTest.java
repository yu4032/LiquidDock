package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for suppressing Gboard's terminal resize transition. */
public class GboardFloatingHandleTerminalPolicyTest {
    private static final Path POLICY = Path.of(
            "src/main/java/com/hellovoid/liquiddock/GboardFloatingHandlePolicy.java");

    private static String readPolicy() throws Exception {
        return Files.exists(POLICY) ? Files.readString(POLICY) : "";
    }

    @Test public void disabledAutoResizeCancelsEveryTerminalHandleRelease() throws Exception {
        String policy = readPolicy();

        assertTrue(policy.contains("terminalActivePointer && !autoResizeAfterHandleDragEnabled()"));
        assertFalse(policy.contains("terminalActivePointer && dragged && !autoResizeAfterHandleDragEnabled()"));
    }
}
