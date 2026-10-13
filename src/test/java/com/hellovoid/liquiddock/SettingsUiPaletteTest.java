package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/** No Android/Compose runtime needed for private UI palette storage decoding. */
public class SettingsUiPaletteTest {
    @Test public void missingOrUnknownChoiceRestoresSystemMonet() {
        assertSame(SettingsUiPalette.SYSTEM, SettingsUiPalette.fromStored(null));
        assertSame(SettingsUiPalette.SYSTEM, SettingsUiPalette.fromStored(""));
        assertSame(SettingsUiPalette.SYSTEM, SettingsUiPalette.fromStored("unsupported"));
        assertNull(SettingsUiPalette.SYSTEM.getSeedArgb());
    }

    @Test public void fixedChoicesRoundTripAndHaveDistinctOpaqueSeeds() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        assertEquals(7, SettingsUiPalette.values().length);
        for (SettingsUiPalette palette : SettingsUiPalette.values()) {
            assertSame(palette, SettingsUiPalette.fromStored(palette.getPersistedValue()));
            if (palette != SettingsUiPalette.SYSTEM) {
                Integer seed = palette.getSeedArgb();
                assertNotNull(seed);
                assertEquals(0xFF000000, seed & 0xFF000000);
                // Every user-selected fixed color must be vivid rather than grayish.
                int red = (seed >> 16) & 0xFF;
                int green = (seed >> 8) & 0xFF;
                int blue = seed & 0xFF;
                int channelSpread = Math.max(red, Math.max(green, blue))
                        - Math.min(red, Math.min(green, blue));
                org.junit.Assert.assertTrue(palette + " has desaturated seed",
                        channelSpread >= 128);
                org.junit.Assert.assertTrue(seen.add(seed));
            }
        }
        assertEquals(6, seen.size());
    }
}
