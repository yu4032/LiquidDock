package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Native folder plates stay hidden while the shared Workspace glass scene is covered. */
public class FolderNativeMaterialCoverageContractTest {
    @Test public void semanticFolderCoverageAlsoCoversClaimedNativeMaterials() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java"));
        assertTrue(source.contains("setNativeFolderMaterialsCovered(true)"));
        assertTrue(source.contains("setNativeFolderMaterialsCovered(false)"));
        assertTrue(source.contains("ORIGINAL_COVERED_VISIBILITY"));
        assertTrue(source.contains("view.setVisibility(View.INVISIBLE)"));
        assertTrue(source.contains("view.setVisibility(original)"));
    }
}
