package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeFolderOutlinePolicyTest {
    @Test public void disabledStrokeIsTransparentFromDrawableInitialization() {
        assertEquals(0x00abcdef, NativeFolderOutlinePolicy.strokeColor(0x13abcdef, false));
        assertEquals(0x00000000, NativeFolderOutlinePolicy.strokeColor(0xff000000, false));
    }

    @Test public void enabledStrokeRestoresExactOriginalColorAndOpacity() {
        assertEquals(0x13abcdef, NativeFolderOutlinePolicy.strokeColor(0x13abcdef, true));
        assertEquals(0xff102030, NativeFolderOutlinePolicy.strokeColor(0xff102030, true));
    }

    @Test public void repeatedNativePaintInitializationIsIdempotent() {
        int original = 0x70f1f2f3;
        int disabled = NativeFolderOutlinePolicy.strokeColor(original, false);
        assertEquals(disabled, NativeFolderOutlinePolicy.strokeColor(original, false));
        assertEquals(original, NativeFolderOutlinePolicy.strokeColor(original, true));
    }
}
