package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static contract: mirror hiding must be implemented as a feature-registration gate. */
public class DockMirrorShortcutReflectionContractTest {
    @Test public void mirrorHideUsesSdkRegistrationAuthorityNotDockGeometry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockMirrorShortcutHook.java"));

        assertTrue(source.contains("com.xiaomi.mirror.synergy.MirrorDesktopHelper"));
        assertTrue(source.contains("com.xiaomi.mirror.synergy.MirrorDesktopCallback"));
        assertTrue(source.contains("\"registerMirrorDesktopCallback\""));
        assertTrue(source.contains("\"unRegisterMirrorDesktopCallback\""));
        assertTrue(source.contains("\"onDeviceListUpdate\""));
        assertTrue(source.contains("mirror desktop registration suppressed; handoff untouched"));

        // Never regress to late-stage Dock presentation/layout manipulation.
        assertFalse(source.contains("SystemSettingsUtils"));
        assertFalse(source.contains("pref_key_mirror_switch"));
        assertFalse(source.contains("HotSeatsList"));
        assertFalse(source.contains("onMirrorSeatUpdate"));
        assertFalse(source.contains("View.INVISIBLE"));
        assertFalse(source.contains("setTranslationX"));
        assertFalse(source.contains("LayoutParams"));
        assertFalse(source.contains("width = 0"));
    }

    @Test public void runtimeToggleUsesExplicitVendorInvocationResults() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockMirrorShortcutHook.java"));

        assertTrue(source.contains("HookUtil.InvocationResult<Object> clear"));
        assertTrue(source.contains("HookUtil.InvocationResult<Object> unregister"));
        assertTrue(source.contains("HookUtil.InvocationResult<Object> register"));
        assertTrue(source.contains("clear.succeeded()"));
        assertTrue(source.contains("unregister.succeeded()"));
        assertTrue(source.contains("register.succeeded()"));
    }
}
