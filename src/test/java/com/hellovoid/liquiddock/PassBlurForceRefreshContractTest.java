package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contract derived from HyperOS libgui/libsurfaceflinger PassBlur state machine. */
public class PassBlurForceRefreshContractTest {
    private static final Path BRIDGE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java");

    @Test
    public void dockBindArmsVendorForceRefreshWindow() throws Exception {
        String source = Files.readString(BRIDGE);
        assertTrue(source.contains("setForceRefresh"));
        assertTrue(source.contains("PASSBLUR_FORCE_REFRESH_MS"));
        assertTrue(source.contains("domain == PassBlurDomain.DOCK"));
        assertTrue(source.contains("setForceRefresh.invoke("));
        assertTrue(source.contains("Integer.valueOf(PASSBLUR_FORCE_REFRESH_MS)"));
    }
}
