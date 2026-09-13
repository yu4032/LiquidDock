package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceMigrationTest {

    @Test
    public void legacySixColumnLayoutMovesEveryItemRightByTwo() {
        List<HomeGridItemPosition> input = Arrays.asList(
                item(1, 10, 0, 0, 1, 1),
                item(2, 10, 5, 3, 1, 1),
                item(3, 11, 2, 1, 2, 2));

        StageWorkspaceMigration.PlanResult result =
                StageWorkspaceMigration.plan(input, false);

        assertTrue(result.success());
        assertTrue(result.changed());
        assertPosition(result.positions().get(0), 1, 10, 2, 0, 1, 1);
        assertPosition(result.positions().get(1), 2, 10, 7, 3, 1, 1);
        assertPosition(result.positions().get(2), 3, 11, 4, 1, 2, 2);
    }

    @Test
    public void migrationPreservesSpansRowsScreensAndIdentity() {
        HomeGridItemPosition widget = item(42, 7, 3, 1, 3, 2);

        StageWorkspaceMigration.PlanResult result =
                StageWorkspaceMigration.plan(Arrays.asList(widget), false);

        assertTrue(result.success());
        assertPosition(result.positions().get(0), 42, 7, 5, 1, 3, 2);
    }

    @Test
    public void oneInvalidLegacyItemFailsWholePlanWithoutPartialOutput() {
        List<HomeGridItemPosition> input = Arrays.asList(
                item(1, 10, 0, 0, 1, 1),
                item(2, 10, 5, 1, 2, 1));

        StageWorkspaceMigration.PlanResult result =
                StageWorkspaceMigration.plan(input, false);

        assertFalse(result.success());
        assertFalse(result.changed());
        assertTrue(result.positions().isEmpty());
    }

    @Test
    public void alreadyMappedLayoutIsReturnedWithoutSecondShift() {
        List<HomeGridItemPosition> mapped = Arrays.asList(
                item(1, 10, 2, 0, 1, 1),
                item(2, 10, 7, 3, 1, 1));

        StageWorkspaceMigration.PlanResult result =
                StageWorkspaceMigration.plan(mapped, true);

        assertTrue(result.success());
        assertFalse(result.changed());
        assertPosition(result.positions().get(0), 1, 10, 2, 0, 1, 1);
        assertPosition(result.positions().get(1), 2, 10, 7, 3, 1, 1);
    }

    @Test
    public void nullAndDuplicateIdentityFailClosed() {
        assertFalse(StageWorkspaceMigration.plan(null, false).success());

        StageWorkspaceMigration.PlanResult duplicate = StageWorkspaceMigration.plan(
                Arrays.asList(
                        item(1, 10, 0, 0, 1, 1),
                        item(1, 11, 1, 0, 1, 1)),
                false);
        assertFalse(duplicate.success());
        assertTrue(duplicate.positions().isEmpty());
    }

    private static HomeGridItemPosition item(long id, long screen,
                                             int x, int y, int spanX, int spanY) {
        return new HomeGridItemPosition(id, screen, x, y, spanX, spanY);
    }

    private static void assertPosition(HomeGridItemPosition item,
                                       long id, long screen,
                                       int x, int y, int spanX, int spanY) {
        assertEquals(id, item.itemId());
        assertEquals(screen, item.screenId());
        assertEquals(x, item.cellX());
        assertEquals(y, item.cellY());
        assertEquals(spanX, item.spanX());
        assertEquals(spanY, item.spanY());
    }
}
