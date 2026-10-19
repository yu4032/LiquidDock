package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class LauncherGlassReturnFadeTest {
    @Test public void glassWaitsUntilTheFinalTenthOfTheReturn() {
        assertEquals(0f, LauncherGlassReturnFade.alpha(0f), 0f);
        assertEquals(0f, LauncherGlassReturnFade.alpha(.89f), 0f);
        assertEquals(0f, LauncherGlassReturnFade.alpha(.9f), .00001f);
    }
    @Test public void glassGraduallyAppearsBeforeTheNativeEnd() {
        assertEquals(.5f, LauncherGlassReturnFade.alpha(.95f), .00001f);
        assertEquals(1f, LauncherGlassReturnFade.alpha(1f), 0f);
        assertEquals(1f, LauncherGlassReturnFade.alpha(1.1f), 0f);
        assertEquals(0f, LauncherGlassReturnFade.alpha(Float.NaN), 0f);
    }
}
