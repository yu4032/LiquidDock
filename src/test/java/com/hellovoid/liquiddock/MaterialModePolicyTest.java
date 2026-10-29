package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MaterialModePolicyTest {
    @Test public void nativeWinsOverConflictingImportedLiquidMode() {
        assertFalse(MaterialModePolicy.liquidEnabled(true, true));
        assertFalse(MaterialModePolicy.liquidEnabled(false, true));
    }

    @Test public void oldLiquidProfilesRemainCompatible() {
        assertTrue(MaterialModePolicy.liquidEnabled(true, false));
        assertFalse(MaterialModePolicy.liquidEnabled(false, false));
    }
}
