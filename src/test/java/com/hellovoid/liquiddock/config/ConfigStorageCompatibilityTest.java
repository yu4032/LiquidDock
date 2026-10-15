package com.hellovoid.liquiddock.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigStorageCompatibilityTest {
    @Test
    public void activeDividerStorageModesRemainDirect() {
        assertEquals(ConfigKey.StorageMode.DIRECT,
                ConfigSchema.Divider.WIDTH_DP.storageMode());
        assertEquals(ConfigKey.StorageMode.DIRECT,
                ConfigSchema.Divider.Y_OFFSET_DP.storageMode());
    }
}
