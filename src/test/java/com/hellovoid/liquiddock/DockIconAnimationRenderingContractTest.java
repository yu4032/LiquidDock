package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class DockIconAnimationRenderingContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void animationProgressDoesNotMutateDockMembershipRevision() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));
        String animationMethods = slice(registry,
                "static synchronized void observeLaunchAnimationFrame",
                "static synchronized float animationOpacity");

        assertFalse(animationMethods.contains("revision++"));
        assertTrue(animationMethods.contains("if (!ANIMATION.observeProxyFrame"));
    }

    @Test
    public void membershipRevisionRemainsOwnedByRegistryMembershipChanges() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));

        assertTrue(registry.contains("private static long membershipRevision;"));
        assertTrue(registry.contains("static synchronized long revision() { return membershipRevision; }"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) throw new AssertionError("source anchors unavailable");
        return source.substring(from, to);
    }
}
