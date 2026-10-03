package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class HomeGridSqueezeIdentityTraversalTest {
    @Test
    public void selfCycleIsVisitedOnlyOnce() {
        Node a = new Node("A");
        a.neighbors.add(a);

        ArrayList<Node> result = collect(a, 4);

        assertEquals(1, result.size());
        assertSame(a, result.get(0));
    }

    @Test
    public void bidirectionalCycleProcessesEachIdentityOnce() {
        Node a = new Node("A");
        Node b = new Node("B");
        a.neighbors.add(b);
        b.neighbors.add(a);

        ArrayList<Node> result = collect(a, 4);

        assertEquals(2, result.size());
        assertSame(a, result.get(0));
        assertSame(b, result.get(1));
    }

    @Test
    public void diamondGraphDoesNotDuplicateSharedNeighbor() {
        Node a = new Node("A");
        Node b = new Node("B");
        Node c = new Node("C");
        Node d = new Node("D");
        a.neighbors.add(b);
        a.neighbors.add(c);
        b.neighbors.add(d);
        c.neighbors.add(d);

        ArrayList<Node> result = collect(a, 8);
        Map<Node, Integer> counts = new IdentityHashMap<>();
        for (Node node : result) {
            counts.put(node, counts.getOrDefault(node, 0) + 1);
        }

        assertEquals(4, result.size());
        assertEquals(Integer.valueOf(1), counts.get(a));
        assertEquals(Integer.valueOf(1), counts.get(b));
        assertEquals(Integer.valueOf(1), counts.get(c));
        assertEquals(Integer.valueOf(1), counts.get(d));
    }

    @Test
    public void traversalFailsClosedWhenUniqueNodesExceedGridBound() {
        Node a = new Node("A");
        Node b = new Node("B");
        Node c = new Node("C");
        a.neighbors.add(b);
        b.neighbors.add(c);

        assertNull(HomeGridSqueezeIdentityTraversal.collect(
                a, 2, (current, out) -> out.addAll(current.neighbors)));
    }

    private static ArrayList<Node> collect(Node root, int maxNodes) {
        return HomeGridSqueezeIdentityTraversal.collect(
                root, maxNodes, (current, out) -> out.addAll(current.neighbors));
    }

    static final class Node {
        final String name;
        final List<Node> neighbors = new ArrayList<>();

        Node(String name) {
            this.name = name;
        }
    }
}
