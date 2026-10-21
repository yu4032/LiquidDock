package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import java.lang.reflect.Method;
import org.junit.Test;

public class Launcher450IconSizeContractTest {
    @Test
    public void policyClampsToSupportedPercentRange() throws Exception {
        Class<?> policy;
        try {
            policy = Class.forName("com.hellovoid.liquiddock.Launcher450IconSizePolicy");
        } catch (ClassNotFoundException missing) {
            fail("Launcher450IconSizePolicy must exist");
            return;
        }
        Method scale = policy.getDeclaredMethod("scale", boolean.class, int.class);
        scale.setAccessible(true);
        assertEquals(1.0f, (Float) scale.invoke(null, false, 120), 0.0001f);
        assertEquals(0.8f, (Float) scale.invoke(null, true, 60), 0.0001f);
        assertEquals(1.0f, (Float) scale.invoke(null, true, 100), 0.0001f);
        assertEquals(1.2f, (Float) scale.invoke(null, true, 150), 0.0001f);
    }

    @Test
    public void expandedGridKeepsNativeMeasureInBothDirections() {
        for (int percent : new int[]{80, 90, 100, 110, 120}) {
            assertEquals("expanded FolderGridView always returns the OEM value",
                    96, Launcher450IconSizePolicy.measuredIconSize(
                            96, true, percent, true));
            assertEquals(96, Launcher450IconSizePolicy.measuredIconSize(
                    96, false, percent, true));
            assertEquals("other domains retain their requested icon size",
                    Launcher450IconSizePolicy.scaledPx(96, true, percent),
                    Launcher450IconSizePolicy.measuredIconSize(
                            96, true, percent, false));
        }
        assertEquals(0, Launcher450IconSizePolicy.measuredIconSize(
                0, true, 120, true));
        assertEquals(115, Launcher450IconSizePolicy.measuredIconSize(
                96, true, 120, false));
        assertEquals(77, Launcher450IconSizePolicy.measuredIconSize(
                96, true, 80, false));
    }
}
