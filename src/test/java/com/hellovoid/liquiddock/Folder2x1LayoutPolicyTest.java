package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class Folder2x1LayoutPolicyTest {
    @Test public void visualBoundsAreExactlyTwoWorkspaceIconsByOne() {
        Folder2x1LayoutPolicy.Layout layout =
                Folder2x1LayoutPolicy.resolve(100, 3, 7);
        assertEquals(200, layout.width);
        assertEquals(100, layout.height);
        assertFalse(layout.aggregateThirdSlot);
        assertEquals(3, layout.visibleChildren);
        assertNotNull(layout.slots[0]);
        assertNotNull(layout.slots[1]);
        assertNotNull(layout.slots[2]);
        assertNull(layout.slots[3]);
    }

    @Test public void fourthContentTurnsThirdPositionIntoFourGrid() {
        Folder2x1LayoutPolicy.Layout layout =
                Folder2x1LayoutPolicy.resolve(100, 6, 7);
        assertTrue(layout.aggregateThirdSlot);
        assertEquals(6, layout.visibleChildren);
        assertEquals(layout.slots[0].width(), layout.slots[1].width());
        assertTrue(layout.slots[2].width() < layout.slots[0].width());
        assertEquals(layout.slots[2].width(), layout.slots[3].width());
        assertEquals(layout.slots[2].width(), layout.slots[4].width());
        assertEquals(layout.slots[2].width(), layout.slots[5].width());
        assertNull(layout.slots[6]);
        assertEquals(layout.slots[2].top, layout.slots[3].top);
        assertTrue(layout.slots[4].top > layout.slots[2].top);
    }

    @Test public void runtimeUsesSemanticFolderAuthoritiesOnly() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Folder2x1Hook.java"));
        assertTrue(hook.contains(
                "com.miui.home.launcher.convertsize.FolderIconConvertSizeController"));
        assertTrue(hook.contains(
                "com.miui.home.launcher.folder.BaseFolderIconPreviewContainer2X2"));
        assertTrue(hook.contains("getFolderSpanXFromType"));
        assertTrue(hook.contains("getFolderSpanYFromType"));
        assertTrue(hook.contains("FolderIcon2x2_4"));
        assertFalse(hook.contains("setScaleX"));
        assertFalse(hook.contains("setScaleY"));
        assertFalse(hook.contains("libapp.so"));
    }
}
