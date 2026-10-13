package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Exact Apple HIG default system color values for light and dark iOS appearance. */
public class SettingsUiPaletteTest {
    @Test public void missingOrUnknownChoiceRestoresSystemMonet() {
        assertSame(SettingsUiPalette.SYSTEM, SettingsUiPalette.fromStored(null));
        assertSame(SettingsUiPalette.SYSTEM, SettingsUiPalette.fromStored(""));
        assertSame(SettingsUiPalette.SYSTEM, SettingsUiPalette.fromStored("unsupported"));
        assertNull(SettingsUiPalette.SYSTEM.getSeedArgb(false));
        assertNull(SettingsUiPalette.SYSTEM.getSeedArgb(true));
    }

    @Test public void fixedChoicesMatchAppleStandardLightAndDarkSwatches() {
        // https://developer.apple.com/design/human-interface-guidelines/color
        assertEquals(7, SettingsUiPalette.values().length);
        assertAppleColors(SettingsUiPalette.BLUE, 0xFF0088FF, 0xFF0091FF);
        assertAppleColors(SettingsUiPalette.PURPLE, 0xFFCB30E0, 0xFFDB34F2);
        assertAppleColors(SettingsUiPalette.PINK, 0xFFFF2D55, 0xFFFF375F);
        assertAppleColors(SettingsUiPalette.RED, 0xFFFF383C, 0xFFFF4245);
        assertAppleColors(SettingsUiPalette.ORANGE, 0xFFFF8D28, 0xFFFF9230);
        assertAppleColors(SettingsUiPalette.GREEN, 0xFF34C759, 0xFF30D158);
    }

    @Test public void choicesRoundTripAndHaveDistinctOpaqueSeedsInBothModes() {
        Set<Integer> lightSeen = new HashSet<>();
        Set<Integer> darkSeen = new HashSet<>();
        for (SettingsUiPalette palette : SettingsUiPalette.values()) {
            assertSame(palette, SettingsUiPalette.fromStored(palette.getPersistedValue()));
            if (palette == SettingsUiPalette.SYSTEM) continue;
            Integer light = palette.getSeedArgb(false);
            Integer dark = palette.getSeedArgb(true);
            assertNotNull(light);
            assertNotNull(dark);
            assertEquals(0xFF000000, light & 0xFF000000);
            assertEquals(0xFF000000, dark & 0xFF000000);
            assertTrue(palette + " light colors not distinct", lightSeen.add(light));
            assertTrue(palette + " dark colors not distinct", darkSeen.add(dark));
        }
        assertEquals(6, lightSeen.size());
        assertEquals(6, darkSeen.size());
    }

    private static void assertAppleColors(SettingsUiPalette palette, int light, int dark) {
        assertEquals(palette.name() + " light", Integer.valueOf(light),
                palette.getSeedArgb(false));
        assertEquals(palette.name() + " dark", Integer.valueOf(dark),
                palette.getSeedArgb(true));
    }
}
