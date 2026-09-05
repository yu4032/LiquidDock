package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static HyperOS vendor-boundary contract for drag callbacks. Runtime suppression is state-tested. */
public class FolderDragOverlayContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void hooksConcreteLauncherDragContainerLifecycle() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherDragOverlayHook.java"));

        assertTrue(hook.contains("onViewAdded"));
        assertTrue(hook.contains("onViewRemoved"));
        assertTrue(hook.contains("contains(\"DragContainer\")"));
        assertTrue(hook.contains("onDragContainerBgAnimAlpha"));
        assertTrue(hook.contains("new Class<?>[]{Boolean.TYPE, Boolean.TYPE}"));
    }
}
