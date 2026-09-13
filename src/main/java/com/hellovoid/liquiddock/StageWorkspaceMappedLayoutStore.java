package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Versioned sidecar authority for the complete Stage-mapped ordinary workspace target. */
final class StageWorkspaceMappedLayoutStore implements StageWorkspaceActivation.MappingStore {
    static final String KEY = "liquiddock_stage_workspace_target_v1";
    private static final String VERSION = "v1";

    private final HomeGridOrientationMemoryStore store;

    StageWorkspaceMappedLayoutStore(HomeGridOrientationMemoryStore store) {
        if (store == null) throw new IllegalArgumentException("store == null");
        this.store = store;
    }

    boolean save(Collection<HomeGridItemPosition> positions) {
        StageWorkspaceMigration.PlanResult validation =
                StageWorkspaceMigration.plan(positions, true);
        if (!validation.success()) return false;

        List<HomeGridItemPosition> ordered = new ArrayList<>(validation.positions());
        ordered.sort(Comparator.comparingLong(HomeGridItemPosition::itemId));
        StringBuilder payload = new StringBuilder(VERSION);
        for (HomeGridItemPosition position : ordered) {
            payload.append('\n')
                    .append(position.itemId()).append(',')
                    .append(position.screenId()).append(',')
                    .append(position.cellX()).append(',')
                    .append(position.cellY()).append(',')
                    .append(position.spanX()).append(',')
                    .append(position.spanY());
        }
        store.write(KEY, payload.toString());
        return true;
    }

    Snapshot load() {
        String payload = store.read(KEY);
        if (payload == null || payload.isEmpty()) return null;
        try {
            String[] lines = payload.split("\\n", -1);
            if (lines.length == 0 || !VERSION.equals(lines[0])) return null;

            List<HomeGridItemPosition> positions = new ArrayList<>();
            for (int index = 1; index < lines.length; index++) {
                if (lines[index].isEmpty()) return null;
                String[] fields = lines[index].split(",", -1);
                if (fields.length != 6) return null;
                positions.add(new HomeGridItemPosition(
                        Long.parseLong(fields[0]),
                        Long.parseLong(fields[1]),
                        Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]),
                        Integer.parseInt(fields[4]),
                        Integer.parseInt(fields[5])));
            }

            StageWorkspaceMigration.PlanResult validation =
                    StageWorkspaceMigration.plan(positions, true);
            if (!validation.success()) return null;
            return new Snapshot(validation.positions());
        } catch (RuntimeException error) {
            return null;
        }
    }

    @Override
    public Collection<HomeGridItemPosition> loadTarget() {
        Snapshot snapshot = load();
        return snapshot == null ? null : snapshot.positions();
    }

    @Override
    public boolean saveTarget(Collection<HomeGridItemPosition> positions) {
        return save(positions);
    }

    static final class Snapshot {
        private final List<HomeGridItemPosition> positions;

        private Snapshot(Collection<HomeGridItemPosition> positions) {
            this.positions = java.util.Collections.unmodifiableList(
                    new ArrayList<>(positions));
        }

        List<HomeGridItemPosition> positions() { return positions; }
    }
}
