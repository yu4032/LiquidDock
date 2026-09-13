package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Contract for the geometry/presentation probe used during Security Center animation diagnosis. */
public class SecurityCenterGlassMorphProbeTest {
    @Test
    public void unchangedGeometryIsSuppressedButShapeOrEpochChangeIsLogged() {
        SecurityCenterGlassMorphProbe.State state = new SecurityCenterGlassMorphProbe.State();

        assertTrue(state.shouldLogGeometry(
                "RESOLVED", "DOCK", 4L,
                10f, 20f, 70f, 220f, 30f,
                300, 1800, true, false));
        assertFalse("stable pre-draw geometry must not flood logcat",
                state.shouldLogGeometry(
                        "RESOLVED", "DOCK", 4L,
                        10f, 20f, 70f, 220f, 30f,
                        300, 1800, true, false));

        assertTrue("a real morph must always be observable",
                state.shouldLogGeometry(
                        "RESOLVED", "DOCK", 4L,
                        8f, 18f, 92f, 280f, 42f,
                        300, 1800, true, false));
        assertTrue("a new scene epoch must be observable even at identical geometry",
                state.shouldLogGeometry(
                        "RESOLVED", "DOCK", 5L,
                        8f, 18f, 92f, 280f, 42f,
                        300, 1800, true, false));
    }

    @Test
    public void formattingKeepsExactCorrelationAndVisibilityFields() {
        String line = SecurityCenterGlassMorphProbe.formatGeometry(
                "SUBMIT", "ALL_APPS", 12L, 37L,
                1.25f, 2.5f, 201.75f, 802.125f, 36.5f,
                1440, 3200, true, true);

        assertTrue(line.contains("stage=SUBMIT"));
        assertTrue(line.contains("role=ALL_APPS"));
        assertTrue(line.contains("generation=12"));
        assertTrue(line.contains("serial=37"));
        assertTrue(line.contains("rect=[1.25,2.5,201.75,802.125]"));
        assertTrue(line.contains("radius=36.5"));
        assertTrue(line.contains("sink=1440x3200"));
        assertTrue(line.contains("ready=true"));
        assertTrue(line.contains("authorized=true"));
    }
}
