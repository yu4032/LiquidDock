package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure all-or-nothing migration from the legacy six-column desktop into Stage physical space. */
final class StageWorkspaceMigration {
    private static final int ROWS = 4;

    private StageWorkspaceMigration() {}

    static PlanResult plan(Collection<HomeGridItemPosition> items, boolean alreadyMapped) {
        if (items == null) return PlanResult.failure();

        List<HomeGridItemPosition> source = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (HomeGridItemPosition item : items) {
            if (item == null || !ids.add(item.itemId())) return PlanResult.failure();
            if (!validVertical(item)) return PlanResult.failure();
            if (alreadyMapped) {
                if (!StageWorkspacePolicy.isOrdinaryPhysicalPlacementLegal(
                        item.cellX(), item.spanX())) {
                    return PlanResult.failure();
                }
            } else {
                if (!validLegacyHorizontal(item)) return PlanResult.failure();
            }
            for (HomeGridItemPosition existing : source) {
                if (item.overlaps(existing)) return PlanResult.failure();
            }
            source.add(item);
        }

        if (alreadyMapped) {
            return PlanResult.success(source, false);
        }

        List<HomeGridItemPosition> mapped = new ArrayList<>(source.size());
        for (HomeGridItemPosition item : source) {
            mapped.add(new HomeGridItemPosition(
                    item.itemId(),
                    item.screenId(),
                    StageWorkspacePolicy.mapLogicalX(item.cellX(), item.spanX()),
                    item.cellY(),
                    item.spanX(),
                    item.spanY()));
        }
        return PlanResult.success(mapped, !mapped.isEmpty());
    }

    private static boolean validLegacyHorizontal(HomeGridItemPosition item) {
        return item.spanX() > 0
                && item.cellX() >= 0
                && (long) item.cellX() + item.spanX() <= StageWorkspacePolicy.DESKTOP_COLUMNS;
    }

    private static boolean validVertical(HomeGridItemPosition item) {
        return item.spanY() > 0
                && item.cellY() >= 0
                && (long) item.cellY() + item.spanY() <= ROWS;
    }

    static final class PlanResult {
        private final boolean success;
        private final boolean changed;
        private final List<HomeGridItemPosition> positions;

        private PlanResult(boolean success,
                           boolean changed,
                           List<HomeGridItemPosition> positions) {
            this.success = success;
            this.changed = changed;
            this.positions = positions;
        }

        static PlanResult failure() {
            return new PlanResult(false, false, Collections.emptyList());
        }

        static PlanResult success(List<HomeGridItemPosition> positions, boolean changed) {
            return new PlanResult(
                    true,
                    changed,
                    Collections.unmodifiableList(new ArrayList<>(positions)));
        }

        boolean success() { return success; }
        boolean changed() { return changed; }
        List<HomeGridItemPosition> positions() { return positions; }
    }
}
