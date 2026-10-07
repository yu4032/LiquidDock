package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for Dock ownership of the shared Floating Dock PassBlur root. */
public class DockPassBlurOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void dockOwnsPassBlurSurfaceAcrossVendorSnapshotTransactions() throws Exception {
        Path authorityPath = MAIN.resolve("DockPassBlurContinuousAuthority.java");
        assertTrue("Dock must have a root-level PassBlur ownership authority",
                Files.exists(authorityPath));

        String authority = Files.readString(authorityPath);
        String moduleMain = Files.readString(MAIN.resolve("ModuleMain.java"));
        String passBlur = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue("Launcher startup must install Dock authority before glass binding",
                moduleMain.contains("DockPassBlurContinuousAuthority.install()"));
        assertTrue("vendor producer rebinds must be suppressed while LiquidDock owns Dock root",
                authority.contains("SetPassBlurSurface")
                        && authority.contains("requested != claim.surface")
                        && authority.contains("successfulSuppressionResult("));
        assertFalse("an already-parceled producer must not be substituted into vendor transactions",
                authority.contains("args[1] = claim.surface"));
        assertTrue("vendor update writes must preserve LiquidDock's current policy",
                authority.contains("setUpdateTextureFlag")
                        && authority.contains("claim.updatesEnabled")
                        && authority.contains("args[1] = Boolean.valueOf(replacementEnabled)")
                        && authority.contains("args[2] = Float.valueOf(claim.scale)"));
        assertTrue("Dock bind must claim the exact root/producer pair",
                passBlur.contains("DockPassBlurContinuousAuthority.claim("));
        assertTrue("Dock pause/resume must update authority state before native transaction",
                passBlur.contains("DockPassBlurContinuousAuthority.setUpdatesEnabled("));
        assertTrue("Dock unbind must release the claim before clearing the producer",
                passBlur.contains("DockPassBlurContinuousAuthority.release("));
    }

    @Test
    public void vendorSnapshotRemainsAPowerPolicyNotAProducerOwner() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue("vendor static-snapshot signal must still control idle producer policy",
                pipeline.contains("setMingouStaticDockSnapshotMode")
                        && pipeline.contains("setProducerUpdatesEnabled(!snapshotMode"));
        assertTrue("HOME animation override must dominate idle snapshot policy",
                renderer.contains("producerUpdatesPolicyEnabled || homeProducerOverride"));
    }
}
