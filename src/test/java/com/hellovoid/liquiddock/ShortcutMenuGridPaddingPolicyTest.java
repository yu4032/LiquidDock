package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShortcutMenuGridPaddingPolicyTest {
    @Test public void squareCellKeepsVendorEquivalentPadding() {
        assertEquals(96, ShortcutMenuGridPaddingPolicy.correctedTopPadding(
                96, 96, 1.0f, true));
    }

    @Test public void shorterVerticalCellUsesRealShortcutIconPadding() {
        assertEquals(84, ShortcutMenuGridPaddingPolicy.correctedTopPadding(
                96, 84, 1.0f, true));
    }

    @Test public void splitWorkspaceScaleIsPreserved() {
        assertEquals(76, ShortcutMenuGridPaddingPolicy.correctedTopPadding(
                90, 84, 0.9f, true));
    }

    @Test public void unrelatedCallsRemainVendorOwned() {
        assertEquals(96, ShortcutMenuGridPaddingPolicy.correctedTopPadding(
                96, 84, 1.0f, false));
    }
}
