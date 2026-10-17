package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenFolderNativeCloseProgressTest {
    @Test public void stationaryTranslationDoesNotFreezeTheScaleSpring() {
        OpenFolderNativeCloseProgress p = new OpenFolderNativeCloseProgress();
        p.capture(100f, 0f, 3f, 100f, 200f, 3f);
        assertEquals("x=true y=false scale=true", p.channelDescription());
        assertEquals(1f, p.sample(100f, 0f, 3f, 100f, 200f, 3f), 0f);
        assertEquals(.5f, p.sample(100f, 0f, 2f, 100f, 200f, 3f), 0f);
        assertEquals(0f, p.sample(100f, 0f, 1f, 100f, 200f, 3f), 0f);
    }

    @Test public void stationaryScaleDoesNotForceEarlyCollapse() {
        OpenFolderNativeCloseProgress p = new OpenFolderNativeCloseProgress();
        p.capture(0f, 200f, 1f, 100f, 200f, 3f);
        assertEquals("x=false y=true scale=false", p.channelDescription());
        assertEquals(.75f, p.sample(0f, 150f, 1f, 100f, 200f, 3f), .001f);
        assertEquals(0f, p.sample(0f, 0f, 1f, 100f, 200f, 3f), 0f);
    }

    @Test public void inactiveChannelsDoNotInventProgress() {
        OpenFolderNativeCloseProgress p = new OpenFolderNativeCloseProgress();
        p.capture(0f, 0f, 1f, 0f, 0f, 1f);
        assertFalse(p.hasChannels());
        assertTrue(Float.isNaN(p.sample(0f, 0f, 1f, 0f, 0f, 1f)));
        p.reset();
        p.capture(100f, 0f, 1f, 100f, 0f, 1f);
        assertEquals(0f, p.sample(0f, 0f, 1f, 100f, 0f, 1f), 0f);
    }

    @Test public void independentFolmeChannelsCanContractAtDifferentRates() {
        OpenFolderNativeCloseProgress p = new OpenFolderNativeCloseProgress();
        p.capture(200f, 150f, 3f, 200f, 150f, 3f);
        assertEquals(.4f, p.sample(160f, 60f, 2f, 200f, 150f, 3f), .001f);
    }
}
