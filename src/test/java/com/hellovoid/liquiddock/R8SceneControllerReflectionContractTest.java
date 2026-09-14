package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** R8 contract: project-owned scene state must use typed APIs, never member-name reflection. */
public class R8SceneControllerReflectionContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void widgetTransitionReadsSceneThroughTypedControllerApi() throws Exception {
        String coordinator = Files.readString(MAIN.resolve("LauncherWidgetTransitionCoordinator.java"));
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));

        assertFalse(coordinator.contains("HookUtil.getField(controller, \"state\")"));
        assertFalse(coordinator.contains("HookUtil.tryInvoke(stateMachine, \"generation\")"));
        assertFalse(coordinator.contains("HookUtil.tryInvoke(stateMachine, \"state\")"));
        assertFalse(coordinator.contains("HookUtil.getBooleanField(controller, \"homeTransitionPending\")"));

        assertTrue(coordinator.contains("controller.widgetTransitionSceneGeneration()"));
        assertTrue(coordinator.contains("controller.widgetTransitionSceneState()"));
        assertTrue(coordinator.contains("controller.isHomeTransitionPending()"));

        assertTrue(controller.contains("long widgetTransitionSceneGeneration()"));
        assertTrue(controller.contains("State widgetTransitionSceneState()"));
        assertTrue(controller.contains("boolean isHomeTransitionPending()"));
    }
}
