package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeFolderOutlinePolicyTest {
    @Test public void onlyNativeLargeFolderBorderCanBeDisabled() {
        assertFalse(NativeFolderOutlinePolicy.shouldSuppress(true, true));
        assertFalse(NativeFolderOutlinePolicy.shouldSuppress(true, false));
        assertFalse(NativeFolderOutlinePolicy.shouldSuppress(false, false));
        assertTrue(NativeFolderOutlinePolicy.shouldSuppress(false, true));
    }
}
