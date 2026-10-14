package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static HyperOS 4.50 material targets and destructive-API bans. Ownership is typed state-tested. */
public class LauncherGlassVendorMaterialSuppressionContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void standardWidgetTargetsOnlyTaggedDirectRemoteViewsRoot() throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));

        assertTrue(helper.contains("android.R.id.widget_frame"));
        assertTrue(helper.contains("resolveRemoteViewsContent"));
        assertTrue(helper.contains("child.getTag(android.R.id.widget_frame)"));
        assertFalse(helper.contains("findViewById(android.R.id.widget_frame)"));
        assertFalse(helper.contains("setAlpha(0"));
        assertFalse(helper.contains("removeAllViews"));
        assertFalse(helper.contains("setVisibility(View.INVISIBLE"));
        assertFalse(helper.contains("setVisibility(View.GONE"));
    }

    @Test public void mamlUsesLaunchersBackgroundBlurVariableBoundary() throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));

        assertTrue(helper.contains("putVariableNumber"));
        assertTrue(helper.contains("enable_background_blur"));
        assertFalse(helper.contains("LauncherMamlBackgroundRuleExecutor"));
        assertFalse(helper.contains("WidgetBackgroundRule"));
        assertFalse(helper.contains("findElement"));
    }

    @Test public void mamlReleaseRestoresOwnedRootValueNotAssumedZero() throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));
        assertTrue(helper.contains("MAML_BACKGROUND_OWNERSHIP"));
        assertTrue(helper.contains("getMaMlBackgroundBlurVariable(host)"));
        assertTrue(helper.contains("rememberMamlBackground(host, readMamlRoot(host))"));
        assertTrue(helper.contains("claimLoadedMamlRoot(View host, Object root)"));
        assertTrue(helper.contains("currentRoot == claim.rootRef.get()"));
        assertTrue(helper.contains("claim.value.release(owned)"));
        assertTrue(helper.contains("decision.restoreOriginal"));
        assertTrue(helper.contains("putMaMlBackgroundBlurVariable(host, decision.originalValue)"));
        assertFalse(helper.contains("if (isMaMlHost(host)) putMaMlBackgroundBlurVariable(host, 0.0d)"));
    }

    @Test public void widgetClaimBansDestructiveNativeMaterialApis()
            throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));
        // A static API boundary only: no source slicing or inferred runtime call ordering.
        // The folder path still has its own material contract, hence the explicit host scope.
        assertTrue(helper.contains("suppressPassWindowWithoutClearingVendorMaterial(host)"));
        assertFalse(helper.contains("MiBlurBridge.clearContentBlur(host)"));
        assertFalse(helper.contains("MiBlurBridge.clearPassWindowBlur(host)"));
        assertFalse(helper.contains("invokeBoolean(host, \"setViewBlur\""));
        assertFalse(helper.contains("invokeBoolean(host, \"setBlurIfNeed\""));
        assertFalse(helper.contains("invokeInt(host, \"setBlurRadius\""));
        assertFalse(helper.contains("setMaMlBlurIfSupported(host, false)"));
        assertTrue(helper.contains("MiBlurBridge.getPassWindowBlurEnabled(host)"));
        assertTrue(helper.contains("MiBlurBridge.setPassWindowBlurEnabled(host, true)"));
    }

    @Test public void widgetGlassGatesActualLauncher450ViewBlurNotSelfBlur()
            throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));
        String gate = Files.readString(MAIN.resolve("LauncherWidgetNativeViewBlurGate.java"));
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        assertTrue(helper.contains("LauncherWidgetNativeViewBlurGate.claim(host)"));
        assertTrue(helper.contains("LauncherWidgetNativeViewBlurGate.release(host)"));
        assertTrue(hook.contains("LauncherWidgetNativeViewBlurGate.install(classLoader)"));
        assertTrue(gate.contains("View.class.getMethod(\"setMiViewBlurMode\", int.class)"));
        assertTrue(gate.contains("com.miui.home.launcher.common.BlurUtilities"));
        assertTrue(gate.contains("setWidgetBlurIfSupported"));
        assertTrue(gate.contains("setMaMlBlurIfSupported"));
        assertTrue(gate.contains("supportWidgetBackgroundBlur"));
        assertTrue(gate.contains("isWidgetBlurSupported"));
        assertFalse(gate.contains("lastVendorMode"));
        assertFalse(gate.contains("sawVendorMode"));
        assertTrue(gate.contains("chain.proceed(new Object[]{0})"));
        // A transient vendor mode=0 from Window detach must never be
        // replayed as the final advanced-material state.
        assertFalse(gate.contains("setNativeMode(host, vendorMode)"));
        assertTrue(gate.contains("if (!host.isAttachedToWindow() || !isSupportedWidget(host))"));
        assertTrue(gate.contains("boolean restored = restoreViaVendorUtilities(host)"));
        assertTrue(helper.contains("boolean vendorRebound = LauncherWidgetNativeViewBlurGate.release(host)"));
        assertTrue(helper.contains("restoreMamlBackground(host, vendorRebound)"));
        assertTrue(helper.contains("if (vendorRebound) {"));
        assertTrue(helper.contains("MAML blur variable handed to Launcher"));
        assertTrue(gate.contains("restoreViaVendorUtilities(host)"));
        assertFalse(gate.contains("MiBlurBridge.clearContentBlur("));
        assertFalse(gate.contains("MiBlurBridge.clearPassWindowBlur("));
        assertFalse(gate.contains("setMiSelfBlur("));
        assertFalse(gate.contains("setMiBackgroundBlurRadius("));
        assertFalse(gate.contains("setMiBackgroundBlendColors("));
    }

    @Test public void appToHomeRebindsOnlyPreviouslyOwnedReleasedWidgetMaterial()
            throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        String gate = Files.readString(MAIN.resolve("LauncherWidgetNativeViewBlurGate.java"));

        // HOME resume is distinct from glass disable: the native material can
        // disappear again without a new glass ownership transition.
        assertTrue(hook.contains("Launcher\", \"onResume\""));
        assertTrue(hook.contains("restoreReleasedHostsAfterHomeResume(workspace)"));
        assertTrue(gate.contains("PREVIOUSLY_OWNED_HOSTS.put(host, Boolean.TRUE)"));
        assertTrue(gate.contains("GlassRuntimeState.isWidgetEnabled()"));
        assertTrue(gate.contains("!host.isAttachedToWindow()"));
        assertTrue(gate.contains("host.getRootView() != workspaceRoot"));
        assertTrue(gate.contains("!LauncherGlassHierarchy.isWorkspace(host)"));
        assertTrue(gate.contains("if (STATES.containsKey(host)) continue"));
        assertTrue(gate.contains("if (!isSupportedWidget(host)) continue"));
        assertTrue(gate.contains("boolean rebound = restoreViaVendorUtilities(host)"));
        assertFalse(gate.contains("setNativeMode(host, vendorMode)"));
    }

    @Test public void folderHookTargetsConcreteLauncher450VariantsWithoutDrawableReplacement()
            throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(folder.contains("com.miui.home.launcher.folder.FolderIcon1x1"));
        assertTrue(folder.contains("com.miui.home.launcher.folder.FolderIcon2x2"));
        assertTrue(folder.contains("setIconImageView"));
        assertFalse(folder.contains(
                "ImageView.class.getDeclaredMethod(\"setImageDrawable\", Drawable.class)"));
        assertFalse(folder.contains("mPreviewContainer.setAlpha(0"));
        assertFalse(folder.contains("mPreviewContainer.setVisibility"));
    }

    @Test public void largeFolderDrawableUsesConcretePaintApiBecauseSetAlphaIsVendorNoOp()
            throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(folder.contains("FolderIcon4x4NormalBackgroundDrawable"));
        assertTrue(folder.contains("FolderIcon4x4DefaultBackgroundDrawable"));
        assertTrue(folder.contains("HookUtil.tryInvoke(drawable, \"getPaint\")"));
        assertTrue(folder.contains("paintResult.succeeded()"));
    }
}
