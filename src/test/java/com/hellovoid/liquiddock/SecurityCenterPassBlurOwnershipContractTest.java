package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for the single-owner Security Center PassBlur producer. */
public class SecurityCenterPassBlurOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void claimedVendorBlurCannotReplaceTheModuleProducer() throws Exception {
        String state = Files.readString(
                MAIN.resolve("SecurityCenterVendorMaterialState.java"));
        String bridge = Files.readString(
                MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));
        String session = Files.readString(
                MAIN.resolve("SecurityCenterGlassSession.java"));

        assertTrue("claimed vendor calls must be suppressed from reaching ViewRootImpl",
                state.contains("successfulSuppressionResult(method)"));
        assertTrue("claim must clear the already-active native pass-window output",
                bridge.contains("MiBlurBridge.clearPassWindowBlur(target)"));
        assertTrue("a changed vendor claim must request a fresh module producer binding",
                coordinator.contains("requestSourceRebind("));
        assertTrue("the session must expose producer rebind to the coordinator",
                session.contains("requestSourceRebind("));
    }

    @Test
    public void securityCenterOwnsPassBlurSurfaceContinuouslyAcrossVendorTransactions()
            throws Exception {
        Path authorityPath = MAIN.resolve("SecurityCenterPassBlurContinuousAuthority.java");
        assertTrue("Security Center must keep a root-level native producer authority",
                Files.exists(authorityPath));

        String authority = Files.readString(authorityPath);
        String moduleMain = Files.readString(MAIN.resolve("ModuleMain.java"));
        String passBlur = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue("Security Center startup must install continuous PassBlur authority",
                moduleMain.contains("SecurityCenterPassBlurContinuousAuthority.install()"));
        assertTrue("vendor SetPassBlurSurface writes must be intercepted while our root is claimed",
                authority.contains("SetPassBlurSurface")
                        && authority.contains("args[1] = claim.surface"));
        assertTrue("vendor update flag/scale writes must preserve live LiquidDock updates",
                authority.contains("setUpdateTextureFlag")
                        && authority.contains("args[1] = Boolean.TRUE")
                        && authority.contains("args[2] = Float.valueOf(claim.scale)"));
        assertTrue("Security Center bind must claim the caller-owned producer Surface",
                passBlur.contains("SecurityCenterPassBlurContinuousAuthority.claim("));
        assertTrue("Security Center unbind must release that exact producer claim",
                passBlur.contains("SecurityCenterPassBlurContinuousAuthority.release("));
        assertTrue("Security Center resume must force the native update contract even when Java state is already true",
                passBlur.contains("boolean force = binding.domain == PassBlurDomain.SECURITY_CENTER")
                        && passBlur.contains("setUpdatesEnabled(binding, true, force)"));
    }

    @Test
    public void securityCenterUsesPassBlurAsPrismalInputAndReplacesVendorMaterial()
            throws Exception {
        String policy = Files.readString(
                MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));
        String bridge = Files.readString(
                MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String session = Files.readString(
                MAIN.resolve("SecurityCenterGlassSession.java"));
        String passBlur = Files.readString(
                MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue("Security Center must use LiquidDock's Prismal material path",
                policy.contains("return LiquidBlurMode.SHADER;"));
        assertTrue("Prismal must calculate the material from the PassBlur input",
                policy.contains("static boolean useShaderBlur() {\n        return true;")
                        && session.contains("prismalRenderer.prepareBackdrop(")
                        && session.contains("prismalRenderer.drawGlass("));
        assertTrue("the source must be bound through SurfaceFlinger PassBlur",
                session.contains("PassBlurBindRequest.securityCenter(root)")
                        && passBlur.contains("SetPassBlurSurface"));
        assertTrue("the coordinator must create the PassBlur/Prismal session",
                coordinator.contains("bindAttachedRoot(turboLayout)")
                        && !coordinator.contains("usesDirectNativeMaterial()"));
        assertTrue("Prismal output must replace the vendor material at handoff",
                bridge.contains("claimCustom(")
                        && coordinator.contains("bridge.claimCustom(turbo, dock, box, apps)"));
        assertTrue("Dock, upper toolbox, and All Apps vendor carriers must all be cleared",
                bridge.contains("dockLayout, boxMaterialView, allAppsLayout")
                        && bridge.contains("clearVendorTarget(boxMaterialView)")
                        && bridge.contains("clearVendorTarget(allAppsLayout)"));
        assertTrue("Security Center must not fall back to ordinary vendor background blur",
                !policy.contains("MiBlurBridge.applyPassWindowBlur(")
                        && !bridge.contains("configureAdvancedMaterial("));
    }

    @Test
    public void sidebarAndSyntheticTerminalLifecycleAreObservationOnly() throws Exception {
        String hook = Files.readString(MAIN.resolve("SecurityCenterGlassHook.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue("the stable AIDL binder show/hide methods remain hooked only as pass-through observation points",
                hook.contains("sidebarLifecycle.show()")
                        && hook.contains("sidebarLifecycle.hideImmediate()")
                        && hook.contains("sidebarLifecycle.hideAnimated()"));
        assertTrue("binder callbacks must remain no-op in the coordinator",
                coordinator.contains("void onSidebarShowRequested() {}")
                        && coordinator.contains("void onSidebarHideRequested(boolean animated) {}"));
        assertFalse("synthetic terminal hooks must not release a TurboLayout that the vendor can reuse",
                hook.contains("notifyVendorPanelClosing(chain.getArgs(), contract)")
                        || hook.contains("notifyVendorPanelTerminal(chain.getArgs(), contract)"));
        assertFalse("synthetic terminal cleanup must not remain a coordinator teardown authority",
                coordinator.contains("releasePanel(turboLayout, \"vendor terminal cleanup\")"));
        assertTrue("real Turbo/root detach remains the fail-safe teardown authority",
                coordinator.contains("panel detached fallback")
                        && coordinator.contains("releaseForRootDetach()"));
        assertFalse("teardown must not invent a fixed-delay terminal fallback",
                coordinator.contains("postDelayed("));
    }
}
