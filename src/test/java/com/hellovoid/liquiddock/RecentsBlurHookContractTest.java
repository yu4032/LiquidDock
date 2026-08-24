package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class RecentsBlurHookContractTest {
    @Test
    public void hookScopesCustomizationToLauncherRecentsWrappers() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/RecentsBackgroundBlurHook.java"));
        assertTrue(hook.contains("fastBlurWhenEnterRecents"));
        assertTrue(hook.contains("fastBlurWhenGestureResetTaskView"));
        assertTrue(hook.contains("fastBlurWhenDontUseNoBlurTypeWhenRecents"));
        assertTrue(hook.contains("fastBlurWhenUseCompleteRecentsBlur"));
        assertFalse(hook.contains("WindowBlurUtils"));
    }
}
