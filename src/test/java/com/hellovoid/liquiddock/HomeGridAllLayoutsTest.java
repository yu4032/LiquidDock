package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class HomeGridAllLayoutsTest {
    @Test
    public void everyConfiguredTwoToTenByTwoToSixLayoutIsSupportedInBothOrientations() {
        int count = 0;
        Set<String> runtimePairs = new HashSet<>();
        for (int columns = 2; columns <= 10; columns++) {
            for (int rows = 2; rows <= 6; rows++) {
                HomeGridInstallConfig config = new HomeGridInstallConfig(
                        true, columns, rows,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 0, 1f);
                assertEquals(columns, config.countX(false));
                assertEquals(rows, config.countY(false));
                assertEquals(rows, config.countX(true));
                assertEquals(columns, config.countY(true));
                assertTrue(config.matchesGrid(columns, rows));
                assertTrue(config.matchesGrid(rows, columns));
                runtimePairs.add(columns + "x" + rows);
                runtimePairs.add(rows + "x" + columns);
                count++;
            }
        }
        assertEquals(45, count);
        assertFalse(runtimePairs.isEmpty());
    }

    @Test
    public void rotationMemoryKeysExposeItemIdentityForGarbageCollection() {
        assertEquals(Long.valueOf(123L), HomeGridRotationMemoryKey.itemIdFromStoredKey(
                HomeGridRotationMemoryKey.layout(8, 4, 1L, 123L)));
        assertEquals(Long.valueOf(123L), HomeGridRotationMemoryKey.itemIdFromStoredKey(
                HomeGridRotationMemoryKey.owner(123L)));
        assertEquals(null, HomeGridRotationMemoryKey.itemIdFromStoredKey("other:key"));
    }

    @Test
    public void pageAndGridArePartOfTheRotationMemoryNamespace() {
        String base = HomeGridRotationMemoryKey.layout(8, 4, 1L, 123L);
        assertFalse(base.equals(HomeGridRotationMemoryKey.layout(8, 4, 2L, 123L)));
        assertFalse(base.equals(HomeGridRotationMemoryKey.layout(4, 8, 1L, 123L)));
        assertFalse(base.equals(HomeGridRotationMemoryKey.layout(8, 4, 1L, 124L)));
        assertTrue(base.endsWith(HomeGridRotationMemoryKey.itemSuffix(123L)));
    }
}
