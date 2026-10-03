package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Pure policy layer for capturing, restoring and preflighting per-orientation layouts. */
final class HomeGridOrientationRuntime {
    private final HomeGridDimensions dimensions;
    private final HomeGridOrientationMemory memory;

    HomeGridOrientationRuntime(HomeGridDimensions dimensions, HomeGridOrientationMemory memory) {
        if (dimensions == null) throw new IllegalArgumentException("dimensions == null");
        if (memory == null) throw new IllegalArgumentException("memory == null");
        this.dimensions = dimensions;
        this.memory = memory;
    }

    HomeGridLayoutSnapshot captureCurrent(HomeGridOrientation orientation,
                                          Collection<HomeGridItemPosition> positions) {
        // Workspace loading exposes a transient zero-item tree before CellLayouts are bound.
        // Never persist that intermediate state as a real orientation layout.
        if (orientation == null || positions == null || positions.isEmpty()) return null;
        HomeGridLayoutSnapshot snapshot = HomeGridLayoutSnapshot.create(
                dimensions, orientation, positions);
        if (snapshot != null) memory.save(snapshot);
        return snapshot;
    }

    HomeGridLayoutSnapshot rememberedTarget(HomeGridOrientation targetOrientation,
                                            Collection<HomeGridItemPosition> currentItems) {
        if (targetOrientation == null || currentItems == null) return null;
        HomeGridLayoutSnapshot remembered = memory.load(dimensions, targetOrientation);
        if (remembered != null && remembered.size() == 0) {
            memory.invalidate(dimensions, targetOrientation);
            remembered = null;
        }
        if (currentItems.isEmpty()) return null;
        if (remembered == null || remembered.size() != currentItems.size()) return null;

        int targetColumns = dimensions.columns(targetOrientation);
        int targetRows = dimensions.rows(targetOrientation);
        Set<Long> seen = new HashSet<>();
        for (HomeGridItemPosition item : currentItems) {
            if (item == null || !seen.add(item.itemId())) return null;
            HomeGridItemPosition saved = remembered.get(item.itemId());
            if (saved == null
                    || saved.screenId() != item.screenId()
                    || saved.spanX() != item.spanX()
                    || saved.spanY() != item.spanY()
                    || !HomeGridDropLegalityPolicy.isLegal(
                            targetColumns, targetRows,
                            saved.cellX(), saved.cellY(),
                            saved.spanX(), saved.spanY())) {
                return null;
            }
        }
        return remembered;
    }

    void invalidate(HomeGridOrientation orientation) {
        if (orientation != null) memory.invalidate(dimensions, orientation);
    }

    HomeGridLayoutSnapshot preflightOther(HomeGridOrientation currentOrientation,
                                          Collection<HomeGridItemPosition> currentPositions) {
        if (currentOrientation == null || currentPositions == null
                || currentPositions.isEmpty()) return null;
        HomeGridLayoutSnapshot current = captureCurrent(currentOrientation, currentPositions);
        if (current == null) return null;

        HomeGridOrientation other = currentOrientation.other();
        HomeGridLayoutSnapshot remembered = memory.load(dimensions, other);
        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                dimensions, other, current.positions(), remembered);
        if (!result.success()) {
            memory.invalidate(dimensions, other);
            return null;
        }
        memory.save(result.snapshot());
        return result.snapshot();
    }
}
