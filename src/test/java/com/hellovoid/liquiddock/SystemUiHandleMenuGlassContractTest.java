package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Production ownership contract for HyperOS SystemUI caption-menu glass. */
public class SystemUiHandleMenuGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void hookUsesSemanticCaptionMenuBoundariesAndKeepsFailOpenBlur() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));

        assertTrue(hook.contains(
                "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDot"));
        assertTrue(hook.contains("\"addWindow\""));
        assertTrue(hook.contains("\"caption_menu_container\""));
        assertTrue(hook.contains("\"desktop_mode_window_decor_handle_menu\""));
        assertTrue(hook.contains("\"windowing_pill\""));
        assertTrue(hook.contains("MiBlurBridge.applyPassWindowBlur(target, initialRadius)"));
        assertTrue(hook.contains("target.setBackground(null)"));
        assertTrue(hook.contains("restoreStockBackground()"));

        assertFalse(hook.contains("MIUI_WINDOW_CONTROLLER"));
        assertFalse(hook.contains("releaseViewWithAnim"));
        assertFalse(hook.contains("SystemUiHandleMenuAnimationProbe"));
        assertFalse(hook.contains("SystemUiHandleMenuSurfaceProbe"));
        assertFalse(hook.contains("menuX"));
        assertFalse(hook.contains("menuY"));
        assertFalse(hook.contains("postDelayed"));
        assertFalse(hook.contains("PixelCopy"));
        assertFalse(hook.contains("Bitmap"));
    }

    @Test
    public void surfaceAuthorityObservesOnlyNativeAlphaWithoutMutatingTransactions()
            throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String authority = Files.readString(
                MAIN.resolve("SystemUiHandleMenuSurfaceAnimationAuthority.java"));

        assertTrue(module.contains("SystemUiHandleMenuSurfaceAnimationAuthority.install()"));
        assertTrue(authority.contains("SurfaceControl.Transaction.class"));
        assertTrue(authority.contains("\"setAlpha\""));
        assertTrue(authority.contains("AlphaListener"));
        assertTrue(authority.contains("getWindowSurface"));
        assertTrue(authority.contains("listener.onAlpha"));
        assertTrue(authority.contains("Miuix307PassBlurBridge.surfaceLayerId(surface)"));

        assertFalse(authority.contains("\"setMatrix\""));
        assertFalse(authority.contains("\"setScale\""));
        assertFalse(authority.contains("\"setPosition\""));
        assertFalse(authority.contains("\"setCrop\""));
        assertFalse(authority.contains("\"setWindowCrop\""));
        assertFalse(authority.contains("\"setGeometry\""));
        assertFalse(authority.contains(".setAlpha("));
        assertFalse(authority.contains("new SurfaceControl.Transaction"));
        assertFalse(authority.contains(".apply()"));
    }

    @Test
    public void systemUiHookBoundariesAreCrashContained() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String authority = Files.readString(
                MAIN.resolve("SystemUiHandleMenuSurfaceAnimationAuthority.java"));
        String output = Files.readString(
                MAIN.resolve("SystemUiHandleMenuGlassOutputView.java"));
        String session = Files.readString(
                MAIN.resolve("SystemUiHandleMenuPrismalSession.java"));

        assertTrue(authority.contains("Object result = chain.proceed(args)"));
        assertTrue(authority.contains(
                "Surface alpha dispatch failed; original transaction preserved"));
        assertTrue(authority.contains("Surface alpha listener failed; listener removed"));
        assertTrue(authority.contains("ALPHA_LISTENERS.isEmpty()"));

        assertTrue(hook.contains(
                "MIUI captionMenu observation failed; original result preserved"));
        assertTrue(hook.contains(
                "AOSP HandleMenu observation failed; original result preserved"));
        assertTrue(hook.contains("material fade failed; preserving SystemUI animation"));
        assertTrue(hook.contains("callback failed; SystemUI preserved"));
        assertTrue(hook.contains("detach cleanup failed"));

        assertTrue(output.contains("SurfaceTexture attach failed"));
        assertTrue(output.contains("SurfaceTexture resize failed"));
        assertTrue(output.contains("SurfaceTexture destroy detach failed"));
        assertTrue(output.contains("output view removal failed"));

        assertTrue(session.contains("failureReported"));
        assertTrue(session.contains("failure listener failed"));
        assertTrue(session.contains("first-frame listener failed"));
        assertTrue(session.contains("render cleanup enqueue failed"));
    }

    @Test
    public void prismalExportsTheNativeCaptionViewRootBackdropInLocalDomain()
            throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String session = Files.readString(MAIN.resolve("SystemUiHandleMenuPrismalSession.java"));
        String output = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassOutputView.java"));

        assertTrue(hook.contains("new SystemUiHandleMenuPrismalSession("));
        assertTrue(hook.contains("sourceRoot,"));
        assertTrue(hook.contains("SystemUiHandleMenuGlassOutputView.attachInsideTarget"));

        assertTrue(session.contains("RootPassBlurEndpointBridge.inspect(sourceRoot)"));
        assertTrue(session.contains("PassBlurBindRequest.systemUiHandleMenu(sourceRoot)"));
        assertTrue(session.contains("Miuix307PassBlurBridge.bind("));
        assertTrue(session.contains("RootPassBlurEndpointBridge.sameGeneration"));
        assertTrue(session.contains("RootPassBlurContentRect.resolve("));
        assertTrue(session.contains("sourceEndpoint.bufferWidth"));
        assertTrue(session.contains("sourceEndpoint.bufferHeight"));
        assertTrue(session.contains("sourceEndpoint.rotation"));
        assertTrue(session.contains("Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT"));
        assertTrue(session.contains("sourceContentRect.left"));
        assertTrue(session.contains("sourceContentRect.bottom"));
        assertTrue(session.contains("sourceContentRect.width"));
        assertTrue(session.contains("sourceContentRect.height"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(session.contains("PrismalGeometry"));
        assertTrue(session.contains("PrismalHighlightProfile"));

        assertFalse(session.contains("Miuix307BackdropMapping.compute("));
        assertFalse(session.contains("nativePlacement="));
        assertFalse(session.contains("runContinuousSourceRefresh"));
        assertFalse(session.contains("postDelayed"));
        assertFalse(session.contains("PixelCopy"));
        assertFalse(session.contains("Bitmap"));

        assertTrue(output.contains("extends TextureView"));
        assertTrue(output.contains("group.addView(output, 0"));
        assertTrue(output.contains("new ViewGroup.LayoutParams(0, 0)"));
        assertTrue(output.contains("setMaterialAlpha"));
    }

    @Test
    public void prismalPresentationKeepsNativeSamplerAliveButInvisible() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String session = Files.readString(MAIN.resolve("SystemUiHandleMenuPrismalSession.java"));

        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurEnabled(target, true)"));
        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurRadius(target, 0)"));
        assertTrue(hook.contains("MiBlurBridge.clearPassWindowBlur(target)"));

        assertTrue(session.contains("sourceFrameReady = true"));
        assertTrue(session.contains("if (!sourceFrameReady || normalizedTexture == 0"));
        assertTrue(session.contains("normalizeBackdrop();"));
        assertTrue(session.contains("eglSwapBuffers"));
        assertTrue(session.contains("listener.onFirstFramePresented()"));
    }

    @Test
    public void prismalStartupGuardsEglInitializationOrdering() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String session = Files.readString(MAIN.resolve("SystemUiHandleMenuPrismalSession.java"));

        assertTrue(hook.contains("session.start(target.getWidth(), target.getHeight())"));
        assertTrue(hook.contains("SystemUiHandleMenuGlassOutputView.attachInsideTarget"));
        assertTrue(session.contains("if (!isEglReady()) return;"));
        assertTrue(session.contains("private boolean isEglReady()"));
    }

    @Test
    public void moduleAndSchemaKeepFeatureOptIn() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String config = Files.readString(MAIN.resolve("LiquidDockConfig.java"));
        String schema = Files.readString(
                Path.of("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));

        assertTrue(module.contains("runtimeConfig.glass.systemUiHandleMenuEnabled"));
        assertTrue(module.contains("SystemUiHandleMenuGlassHook.install(classLoader, runtimeConfig.glass)"));
        assertTrue(config.contains("systemUiHandleMenuEnabled"));
        assertTrue(schema.contains("SYSTEMUI_HANDLE_MENU_GLASS = bool("));
        assertTrue(schema.contains("\"liquid_systemui_handle_menu_glass\", false, false, false"));
    }
}
