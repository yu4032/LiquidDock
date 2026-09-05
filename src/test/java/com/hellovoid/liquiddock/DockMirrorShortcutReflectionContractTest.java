package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards runtime mirror refresh against false-success accounting from reflection failure. */
public class DockMirrorShortcutReflectionContractTest {
    @Test public void refreshedCountOnlyAdvancesAfterSuccessfulVendorInvocation() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockMirrorShortcutHook.java"));

        assertTrue(source.contains(
                "HookUtil.InvocationResult<Object> refresh = HookUtil.tryInvoke(hotSeats, \"onMirrorSeatUpdate\")"));
        assertTrue(source.contains("if (refresh.succeeded())"));
        assertTrue(source.contains("refreshed++;"));
        assertTrue(source.contains("refresh.failure()"));
        assertFalse(source.contains("HookUtil.invoke(hotSeats, \"onMirrorSeatUpdate\")"));
    }
}
