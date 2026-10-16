package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenFolderCloseAlignmentTest {
    @Test public void differentNativeImageOriginNeverShiftsTheFirstClosingFrame() {
        OpenFolderCloseAlignment a = new OpenFolderCloseAlignment();
        OpenFolderGlassGeometry.Frame opened = frame(100, 200, 500, 700);
        OpenFolderGlassGeometry.Frame nativeAtHandoff = frame(100, 264, 500, 764);
        a.calibrate(opened, nativeAtHandoff);
        assertTrue(a.isCalibrated());
        assertEquals(-64f, a.deltaY(), .001f);
        check(a.apply(nativeAtHandoff), 100, 200, 500, 700);
        // Folme translates and scales: only the actual native motion remains.
        check(a.apply(frame(170, 380, 430, 680)), 170, 316, 430, 616);
        check(a.apply(frame(260, 550, 340, 650)), 260, 486, 340, 586);
    }

    @Test public void xAndYLayoutOriginsAreCalibratedTogether() {
        OpenFolderCloseAlignment a = new OpenFolderCloseAlignment();
        a.calibrate(frame(50, 90, 650, 790), frame(75, 105, 675, 805));
        check(a.apply(frame(220, 430, 370, 580)), 195, 415, 345, 565);
        a.reset();
        assertFalse(a.isCalibrated());
        check(a.apply(frame(220, 430, 370, 580)), 220, 430, 370, 580);
    }

    private static OpenFolderGlassGeometry.Frame frame(float l,float t,float r,float b) {
        return new OpenFolderGlassGeometry.Frame(l,t,r,b,20);
    }
    private static void check(OpenFolderGlassGeometry.Frame f,
                              float l,float t,float r,float b) {
        assertEquals(l,f.left,.001f);assertEquals(t,f.top,.001f);
        assertEquals(r,f.right,.001f);assertEquals(b,f.bottom,.001f);
    }
}
