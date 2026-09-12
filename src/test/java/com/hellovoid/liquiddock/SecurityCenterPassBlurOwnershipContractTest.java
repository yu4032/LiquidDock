package com.hellovoid.liquiddock;

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
    public void realSidebarBinderLifecycleRevokesStaleCustomPresentation() throws Exception {
        String hook = Files.readString(MAIN.resolve("SecurityCenterGlassHook.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue("the stable AIDL binder show/hide lifecycle must be hooked",
                hook.contains("sidebarLifecycle.show()")
                        && hook.contains("sidebarLifecycle.hideImmediate()")
                        && hook.contains("sidebarLifecycle.hideAnimated()"));
        assertTrue("an immediate vendor hide must terminate the custom session on the next loop",
                coordinator.contains("immediate sidebar hide terminal")
                        && coordinator.contains("mainHandler.post(terminalFallback)"));
        assertTrue("a missed animated terminal callback must have a bounded cleanup fallback",
                coordinator.contains("ANIMATED_HIDE_TERMINAL_FALLBACK_MS")
                        && coordinator.contains("mainHandler.postDelayed(terminalFallback"));
        assertTrue("a new show must revoke a stale previous presentation before rebinding",
                coordinator.contains("sidebar show revoked stale presentation"));
    }
}
