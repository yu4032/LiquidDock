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

    @Test public void widgetDoesNotMutateVendorOwnedMaterialConfiguration()
            throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));
        String controller = Files.readString(MAIN.resolve("LauncherWidgetBackgroundController.java"));
        String launcher = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        // Source of truth: Launcher 4.50 writes the current native material across
        // attach/detach, App -> HOME, updateColor and asynchronous MAML root loads.
        // Glass only gates the *output* mode and claims the RemoteViews drawable.
        assertTrue(helper.contains("LauncherWidgetNativeViewBlurGate.claim(host)"));
        assertTrue(helper.contains("LauncherWidgetNativeViewBlurGate.release(host)"));
        assertTrue(helper.contains("WIDGET_BACKGROUND_OWNERSHIP"));
        assertFalse(helper.contains("MAML_BACKGROUND_OWNERSHIP"));
        assertFalse(helper.contains("PASS_WINDOW_GATE_OWNERSHIP"));
        assertFalse(helper.contains("getPassWindowBlurEnabled(host)"));
        assertFalse(helper.contains("setPassWindowBlurEnabled(host"));
        assertFalse(helper.contains("putMaMlBackgroundBlurVariable"));
        assertFalse(helper.contains("getMaMlBackgroundBlurVariable"));
        assertFalse(helper.contains("enable_background_blur\""));
        assertFalse(controller.contains("LauncherGlassVendorMaterialSuppressor.claimLoadedMamlRoot"));
        assertTrue(controller.contains("LauncherMamlBackgroundRuleExecutor.claimLoadedRoot"));
        assertTrue(launcher.contains("installMamlBackgroundOwnershipHooks"));
    }

    @Test public void widgetClaimBansDestructiveNativeMaterialApis()
            throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));
        assertFalse(helper.contains("MiBlurBridge.clearContentBlur(host)"));
        assertFalse(helper.contains("MiBlurBridge.clearPassWindowBlur(host)"));
        assertFalse(helper.contains("invokeBoolean(host,"));
        assertFalse(helper.contains("setMaMlBlurIfSupported(host, false)"));
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
        assertFalse(helper.contains("restoreMamlBackground(host"));
        assertFalse(helper.contains("restorePassWindowGate(host"));
        assertTrue(gate.contains("restoreViaVendorUtilities(host)"));
        assertFalse(gate.contains("MiBlurBridge.clearContentBlur("));
        assertFalse(gate.contains("MiBlurBridge.clearPassWindowBlur("));
        assertFalse(gate.contains("setMiSelfBlur("));
        assertFalse(gate.contains("setMiBackgroundBlurRadius("));
        assertFalse(gate.contains("setMiBackgroundBlendColors("));
    }

    @Test public void darkContentDoesNotRestoreAnOldMamlRootOrOverwriteProviderColors()
            throws Exception {
        String dark = Files.readString(MAIN.resolve("LauncherWidgetDarkContentAdapter.java"));
        String controller = Files.readString(MAIN.resolve("LauncherWidgetBackgroundController.java"));

        assertTrue(dark.contains("WeakReference<Object> root"));
        assertTrue(dark.contains("claim.root.get()"));
        assertTrue(dark.contains("root == claim.root.get()"));
        assertTrue(dark.contains("Double.isFinite(original)"));
        assertTrue(dark.contains("text.getCurrentTextColor() == Color.WHITE"));
        assertFalse(dark.contains("((Number) value).doubleValue() : 0.0d"));
        assertTrue(controller.contains("LauncherWidgetDarkContentAdapter.apply(host)"));
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
