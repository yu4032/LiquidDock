package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static API contract: mirror refresh must use explicit vendor reflection results. */
public class DockMirrorShortcutReflectionContractTest {
    @Test public void mirrorRefreshUsesExplicitTryInvokeResult() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockMirrorShortcutHook.java"));

        assertTrue(source.contains("HookUtil.InvocationResult<Object> refresh"));
        assertTrue(source.contains("HookUtil.tryInvoke(hotSeats, \"onMirrorSeatUpdate\")"));
        assertTrue(source.contains("refresh.succeeded()"));
        assertTrue(source.contains("refresh.failure()"));
        assertFalse(source.contains("HookUtil.invoke(hotSeats, \"onMirrorSeatUpdate\")"));
    }
}
