package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DockFrameSyncConfigTest {
    @Test
    public void dockFrameSyncIsEnabledByDefaultAndExported() {
        ConfigKey<Boolean> key = ConfigSchema.Dock.FRAME_SYNC;
        assertEquals("dock_frame_sync", key.name());
        assertEquals(Boolean.TRUE, key.uiDefault());
        assertEquals(Boolean.TRUE, key.runtimeFallback());
        assertEquals(Boolean.TRUE, key.exportDefault());
        assertEquals(ConfigKey.ExportMode.ALWAYS, key.exportMode());
        assertTrue(ConfigSchema.all().contains(key));
    }

    @Test
    public void renewForceRefreshIsDockOnlyAndUsesCachedRuntimeGate() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java");
        int start = source.indexOf("static void renewForceRefresh(Binding binding)");
        int end = source.indexOf("static void unbind(Binding binding)", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);

        assertTrue(method.contains("if (!VisualRuntimeState.isDockFrameSyncEnabled()) return;"));
        assertTrue(method.contains("binding.domain != PassBlurDomain.DOCK"));
        assertFalse(method.contains("SharedPreferences"));
        assertTrue(source.contains("private static final int FORCE_REFRESH_LEASE_MS = 250;"));
        assertTrue(source.contains("private static final long FORCE_REFRESH_MIN_INTERVAL_MS = 50L;"));
    }

    @Test
    public void producerArrivalRemainsTheOnlyRenewalDriver() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");
        assertTrue(source.contains("Miuix307PassBlurBridge.renewForceRefresh(binding);"));
        assertFalse(source.contains("postDelayed(() -> Miuix307PassBlurBridge.renewForceRefresh"));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        return Files.readString(path);
    }
}
