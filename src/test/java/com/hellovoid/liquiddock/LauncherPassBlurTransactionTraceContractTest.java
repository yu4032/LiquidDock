package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class LauncherPassBlurTransactionTraceContractTest {
    @Test public void sourceIsDebugOnlyAndPreservesVendorTransaction() throws Exception {
        String cls = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherPassBlurTransactionTrace.java"));
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassRecentsHook.java"));
        assertTrue(hook.contains("if (MainHook.debugLogging) LauncherPassBlurTransactionTrace.install()"));
        assertTrue(cls.contains("return chain.proceed(args)"));
        assertTrue(cls.contains("MAX_REPORTS = 160"));
        assertTrue(cls.contains("surfaceLayerId(target)"));
        assertTrue(cls.contains("scale - 0.25f"));
        assertTrue(cls.contains("scale - 1.0f"));
        assertFalse(cls.contains("setPassBlurSurface("));
        assertFalse(cls.contains("requestRebind("));
    }
}
