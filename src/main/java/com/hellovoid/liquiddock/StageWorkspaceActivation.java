package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Atomic activation coordinator for one-way Stage workspace mapping. */
final class StageWorkspaceActivation {
    interface MappingStore {
        Collection<HomeGridItemPosition> loadTarget();
        boolean saveTarget(Collection<HomeGridItemPosition> positions);
    }

    interface LayoutApplier {
        boolean apply(Collection<HomeGridItemPosition> positions);
    }

    static final class Result {
        private final boolean active;
        private final boolean changed;

        private Result(boolean active, boolean changed) {
            this.active = active;
            this.changed = changed;
        }

        static Result inactive() { return new Result(false, false); }
        static Result active(boolean changed) { return new Result(true, changed); }

        boolean active() { return active; }
        boolean changed() { return changed; }
    }

    private final MappingStore store;
    private final LayoutApplier applier;

    StageWorkspaceActivation(MappingStore store, LayoutApplier applier) {
        if (store == null) throw new IllegalArgumentException("store == null");
        if (applier == null) throw new IllegalArgumentException("applier == null");
        this.store = store;
        this.applier = applier;
    }

    Result ensureMapped(Collection<HomeGridItemPosition> current) {
        Collection<HomeGridItemPosition> persistedTarget = store.loadTarget();
        if (persistedTarget != null) {
            StageWorkspaceMigration.PlanResult targetValidation =
                    StageWorkspaceMigration.plan(persistedTarget, true);
            if (!targetValidation.success()) return Result.inactive();
            List<HomeGridItemPosition> target = targetValidation.positions();
            if (matches(current, target)) return Result.active(false);
            if (!applier.apply(target)) return Result.inactive();
            return Result.active(true);
        }

        StageWorkspaceMigration.PlanResult plan = StageWorkspaceMigration.plan(current, false);
        if (!plan.success()) return Result.inactive();
        if (!store.saveTarget(plan.positions())) return Result.inactive();
        if (!applier.apply(plan.positions())) return Result.inactive();
        return Result.active(plan.changed());
    }

    private static boolean matches(Collection<HomeGridItemPosition> current,
                                   Collection<HomeGridItemPosition> target) {
        if (current == null || target == null || current.size() != target.size()) return false;
        Map<Long, HomeGridItemPosition> byId = new HashMap<>();
        for (HomeGridItemPosition item : current) {
            if (item == null || byId.put(item.itemId(), item) != null) return false;
        }
        for (HomeGridItemPosition expected : target) {
            if (expected == null) return false;
            HomeGridItemPosition actual = byId.get(expected.itemId());
            if (!samePosition(actual, expected)) return false;
        }
        return true;
    }

    private static boolean samePosition(HomeGridItemPosition left,
                                        HomeGridItemPosition right) {
        return left != null
                && left.itemId() == right.itemId()
                && left.screenId() == right.screenId()
                && left.cellX() == right.cellX()
                && left.cellY() == right.cellY()
                && left.spanX() == right.spanX()
                && left.spanY() == right.spanY();
    }
}
