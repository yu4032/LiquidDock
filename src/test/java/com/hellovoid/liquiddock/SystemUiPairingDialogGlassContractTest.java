package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class SystemUiPairingDialogGlassContractTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void systemUiInstallsPairingDialogGlassFromTheGlobalGlassGate() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));

        assertTrue(module.contains(
                "SystemUiPairingDialogGlassHook.install(runtimeConfig.glass)"));
        assertTrue(module.contains("runtimeConfig.enabled && runtimeConfig.glass.enabled"));
    }

    @Test
    public void pairingHookUsesStableMiuixSemanticsAcrossPluginClassLoaders() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiPairingDialogGlassHook.java"));

        assertTrue(hook.contains(
                "\"miuix.appcompat.app.PairingDialog\""));
        assertTrue(hook.contains(
                "\"miuix.appcompat.internal.widget.PairingParentPanel\""));
        assertTrue(hook.contains(
                "\"miuix.appcompat.internal.widget.DialogParentPanel2\""));
        assertTrue(hook.contains("HookUtil.hookMethod("));
        assertTrue(hook.contains("Dialog.class"));
        assertTrue(hook.contains("\"show\""));
        assertFalse(hook.contains("Class.forName(PAIRING_DIALOG"));
        assertFalse(hook.contains("postDelayed("));
    }

    @Test
    public void pairingDialogReusesTheExistingSystemUiPrismalPipeline() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiPairingDialogGlassHook.java"));
        String session = Files.readString(
                MAIN.resolve("SystemUiHandleMenuPrismalSession.java"));

        assertTrue(hook.contains("new SystemUiHandleMenuPrismalSession("));
        assertTrue(hook.contains(
                "SystemUiHandleMenuGlassOutputView.attachInsideTarget"));
        assertTrue(hook.contains("MiBlurBridge.applyPassWindowBlur("));
        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurRadius(target, 0)"));
        assertTrue(hook.contains("onFirstFramePresented()"));
        assertTrue(hook.contains("restoreNativeFallback()"));

        assertTrue(session.contains("float requestedCornerRadiusPx;"));
        assertTrue(session.contains("Float.isFinite(requestedCornerRadiusPx)"));
        assertTrue(session.contains("this(host, sourceRoot, glassConfig, Float.NaN, listener)"));
        assertTrue(session.contains("host.getLocationInWindow(hostLocation)"));
        assertTrue(session.contains("sourceRoot.getLocationInWindow(rootLocation)"));
        assertTrue(session.contains("sourceContentRect.subRect("));
        assertTrue(session.contains("RootPassBlurContentRect backdropRect = targetContentRect"));
        assertTrue(hook.contains("session.refreshHostMapping()"));
    }
}
