package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete validated placement set for one dynamic grid size and orientation. */
final class HomeGridLayoutSnapshot {
    private final HomeGridDimensions dimensions;
    private final HomeGridOrientation orientation;
    private final Map<Long, HomeGridItemPosition> positions;

    private HomeGridLayoutSnapshot(HomeGridDimensions dimensions,
                                   HomeGridOrientation orientation,
                                   Map<Long, HomeGridItemPosition> positions) {
        this.dimensions = dimensions;
        this.orientation = orientation;
        this.positions = Collections.unmodifiableMap(positions);
    }

    static HomeGridLayoutSnapshot create(HomeGridDimensions dimensions,
                                         HomeGridOrientation orientation,
                                         Collection<HomeGridItemPosition> positions) {
        if (dimensions == null || orientation == null || positions == null) return null;

        int columns = dimensions.columns(orientation);
        int rows = dimensions.rows(orientation);
        LinkedHashMap<Long, HomeGridItemPosition> accepted = new LinkedHashMap<>();

        for (HomeGridItemPosition candidate : positions) {
            if (candidate == null || !candidate.fitsWithin(columns, rows)
                    || accepted.containsKey(candidate.itemId())) {
                return null;
            }
            for (HomeGridItemPosition existing : accepted.values()) {
                if (candidate.overlaps(existing)) return null;
            }
            accepted.put(candidate.itemId(), candidate);
        }

        return new HomeGridLayoutSnapshot(dimensions, orientation, accepted);
    }

    HomeGridDimensions dimensions() { return dimensions; }
    HomeGridOrientation orientation() { return orientation; }
    int size() { return positions.size(); }
    HomeGridItemPosition get(long itemId) { return positions.get(itemId); }
    Collection<HomeGridItemPosition> positions() { return positions.values(); }
}
