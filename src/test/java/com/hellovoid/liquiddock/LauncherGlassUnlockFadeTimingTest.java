package com.hellovoid.liquiddock;
import org.junit.Test;
import static org.junit.Assert.*;
public class LauncherGlassUnlockFadeTimingTest {
    @Test public void fadeEndsAtNativeDeadline() {
        assertEquals(650, LauncherGlassUnlockFadeTiming.fadeDelayMs(1100, 450));
        assertEquals(450, LauncherGlassUnlockFadeTiming.fadeDurationMs(1100, 450));
    }
    @Test public void longerFadeStartsImmediatelyAndFitsRemainingAnimation() {
        assertEquals(0, LauncherGlassUnlockFadeTiming.fadeDelayMs(1100, 1265));
        assertEquals(1100, LauncherGlassUnlockFadeTiming.fadeDurationMs(1100, 1265));
        assertEquals(0, LauncherGlassUnlockFadeTiming.fadeDurationMs(-1, 450));
    }
    @Test public void disabledFadeIsImmediate() {
        assertEquals(0, LauncherGlassUnlockFadeTiming.fadeDurationMs(1100, 0));
    }
    @Test public void nativeSpringParametersPredictFiniteEquilibrium() {
        long duration = LauncherGlassUnlockFadeTiming.springDurationMs(.85, .6, .0001);
        assertTrue(duration > 900 && duration < 1500);
        assertTrue(LauncherGlassUnlockFadeTiming.springDurationMs(.85, .8, .0001) > duration);
        assertEquals(0, LauncherGlassUnlockFadeTiming.springDurationMs(Double.NaN, .6, .0001));
    }
}
