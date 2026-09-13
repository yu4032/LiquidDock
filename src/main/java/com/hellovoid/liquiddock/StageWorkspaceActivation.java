package com.hellovoid.liquiddock;

import java.util.Collection;

/** Atomic activation coordinator for one-way Stage workspace mapping. */
final class StageWorkspaceActivation {
    interface MappingStore {
        boolean isMapped();
        boolean markMapped();
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
        boolean alreadyMapped = store.isMapped();
        StageWorkspaceMigration.PlanResult plan =
                StageWorkspaceMigration.plan(current, alreadyMapped);
        if (!plan.success()) return Result.inactive();
        if (alreadyMapped) return Result.active(false);

        if (!applier.apply(plan.positions())) return Result.inactive();
        if (!store.markMapped()) return Result.inactive();
        return Result.active(plan.changed());
    }
}
