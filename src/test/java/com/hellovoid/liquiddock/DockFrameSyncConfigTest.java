package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
}
