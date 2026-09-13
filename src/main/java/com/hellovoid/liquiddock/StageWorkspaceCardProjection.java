package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable presentation projection of the current Recents snapshot. */
final class StageWorkspaceCardProjection {
    private StageWorkspaceCardProjection() {}

    static final class Card {
        private final int slotIndex;
        private final Object vendorTask;
        private final StageWorkspaceTaskModel.TaskEntry entry;

        Card(
                int slotIndex,
                Object vendorTask,
                StageWorkspaceTaskModel.TaskEntry entry) {
            this.slotIndex = slotIndex;
            this.vendorTask = vendorTask;
            this.entry = entry;
        }

        int slotIndex() {
            return slotIndex;
        }

        Object vendorTask() {
            return vendorTask;
        }

        StageWorkspaceTaskModel.TaskEntry entry() {
            return entry;
        }
    }

    static List<Card> project(List<StageWorkspaceRecentsSource.Item> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Card> cards = new ArrayList<>(items.size());
        for (StageWorkspaceRecentsSource.Item item : items) {
            if (item == null || item.entry() == null || item.vendorTask() == null) continue;
            cards.add(new Card(cards.size(), item.vendorTask(), item.entry()));
        }
        if (cards.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(cards);
    }
}
