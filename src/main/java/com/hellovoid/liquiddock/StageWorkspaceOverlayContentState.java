package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable current Recents snapshot owned by the Stage overlay lifecycle. */
final class StageWorkspaceOverlayContentState {
    private List<StageWorkspaceRecentsSource.Item> items = Collections.emptyList();

    List<StageWorkspaceRecentsSource.Item> items() {
        return items;
    }

    void apply(
            StageWorkspaceOverlayState.Action action,
            List<StageWorkspaceRecentsSource.Item> snapshot) {
        if (action == StageWorkspaceOverlayState.Action.SHOW) {
            items = snapshot == null || snapshot.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(snapshot));
        } else if (action == StageWorkspaceOverlayState.Action.HIDE) {
            items = Collections.emptyList();
        }
    }
}
