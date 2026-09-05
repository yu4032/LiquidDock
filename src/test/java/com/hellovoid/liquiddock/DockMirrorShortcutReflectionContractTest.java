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

        String invocation = "HookUtil.tryInvoke(hotSeats, \"onMirrorSeatUpdate\")";
        assertTrue(source.contains("HookUtil.InvocationResult<Object> refresh"));
        assertTrue(source.contains(invocation));
        assertTrue(source.contains("if (refresh.succeeded())"));
        assertTrue(source.contains("refresh.failure()"));
        assertFalse(source.contains("HookUtil.invoke(hotSeats, \"onMirrorSeatUpdate\")"));

        int invokeAt = source.indexOf(invocation);
        int successAt = source.indexOf("if (refresh.succeeded())", invokeAt);
        int countAt = source.indexOf("refreshed++;", successAt);
        int failureAt = source.indexOf("refresh.failure()", countAt);
        assertTrue("refresh accounting must be inside the success branch",
                invokeAt >= 0 && successAt > invokeAt && countAt > successAt && failureAt > countAt);
    }
}
