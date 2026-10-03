package com.hellovoid.liquiddock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Identity-based bounded graph traversal used by free-grid squeeze waves. */
final class HomeGridSqueezeIdentityTraversal {
    interface NeighborCollector<T> {
        void collect(T current, ArrayList<T> out);
    }

    private HomeGridSqueezeIdentityTraversal() {}

    static <T> ArrayList<T> collect(
            T root,
            int maxNodes,
            NeighborCollector<T> collector) {
        if (root == null || collector == null || maxNodes <= 0) return null;

        ArrayDeque<T> pending = new ArrayDeque<>();
        IdentityHashMap<T, Boolean> scheduled = new IdentityHashMap<>();
        ArrayList<T> result = new ArrayList<>();

        scheduled.put(root, Boolean.TRUE);
        pending.add(root);

        while (!pending.isEmpty()) {
            T current = pending.removeFirst();
            result.add(current);

            ArrayList<T> discovered = new ArrayList<>();
            collector.collect(current, discovered);
            for (T next : discovered) {
                if (next == null || scheduled.containsKey(next)) continue;
                scheduled.put(next, Boolean.TRUE);
                if (scheduled.size() > maxNodes) return null;
                pending.addLast(next);
            }
        }
        return result;
    }
}
