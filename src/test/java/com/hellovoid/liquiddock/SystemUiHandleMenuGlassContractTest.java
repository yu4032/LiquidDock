package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static ownership contract for the HyperOS SystemUI caption Handle Menu integration. */
public class SystemUiHandleMenuGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void hookUsesRealMiuiCaptionWindowBoundaryAndNativeBackdrop() throws Exception {
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

        assertFalse(hook.contains("PassBlurBindRequest.systemUiHandleMenu"));
        assertFalse(hook.contains("new RootPassBlurBackend"));
        assertFalse(hook.contains("new SystemUiHandleMenuGlassSession"));
        assertFalse(hook.contains("PixelCopy"));
        assertFalse(hook.contains("Bitmap"));
        assertFalse(hook.contains("postDelayed"));
    }

    @Test
    public void miuiBlurFadesUsingNativeSurfaceAlphaInsteadOfScalingBackdropTexture()
            throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String probe = Files.readString(MAIN.resolve("SystemUiHandleMenuSurfaceProbe.java"));
        String blur = Files.readString(MAIN.resolve("MiBlurBridge.java"));

        assertTrue(hook.contains(
                "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.handlemenu.MiuiWindowController"));
        assertTrue(hook.contains("\"releaseViewWithAnim\""));
        assertTrue(hook.contains("SystemUiHandleMenuSurfaceProbe.trackController(result)"));
        assertTrue(hook.contains("registerAlphaListener(menuSurface, alphaListener)"));
        assertTrue(hook.contains("applyReplacementBlur();"));
        assertTrue(hook.contains("pendingSurfaceAlpha = trackNativeSurfaceAnimation ? 0f : 1f"));
        assertTrue(hook.contains("float t = (surfaceAlpha - 0.80f) / 0.20f"));
        assertTrue(hook.contains("return t * t * (3f - (2f * t))"));
        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurRadius(target, radius)"));
        assertTrue(hook.contains("glass material retained through surface scale-out"));
        assertTrue(hook.contains("initialRadius = trackNativeSurfaceAnimation ? 0 : nativeBlurRadiusPx"));
        assertTrue(hook.contains("fadeStartAlpha=0.80"));
        assertTrue(hook.contains("customPassBlurOwned"));

        assertFalse(hook.contains("pendingTextureScale"));
        assertFalse(hook.contains("setPassTextureScale(target"));
        assertFalse(hook.contains("stock background restored before surface scale-out"));
        assertFalse(blur.contains("static boolean setPassTextureScale(View view, float textureScale)"));

        assertTrue(probe.contains("registerAlphaListener"));
        assertTrue(probe.contains("dispatchAlpha"));
        assertTrue(probe.contains("Map<Integer, AlphaListener>"));
        assertTrue(probe.contains("stableLayerId(surface)"));
    }

    @Test
    public void fullPrismalExportsTheSameCaptionViewRootBackdropAsNativeBlur()
            throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String session = Files.readString(MAIN.resolve("SystemUiHandleMenuPrismalSession.java"));
        String output = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassOutputView.java"));

        assertTrue(hook.contains("new SystemUiHandleMenuPrismalSession("));
        assertTrue(hook.contains("target,"));
        assertTrue(hook.contains("sourceRoot,"));
        assertTrue(hook.contains("SystemUiHandleMenuGlassOutputView.attachInsideTarget"));
        assertTrue(hook.contains("full Prismal glass presented; native sampler retained at blur=0"));
        assertTrue(hook.contains("Prismal fallback to native blur"));
        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurEnabled(target, true)"));
        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurRadius(target, 0)"));

        assertTrue(session.contains("RootPassBlurEndpointBridge.inspect(sourceRoot)"));
        assertTrue(session.contains("PassBlurBindRequest.systemUiHandleMenu(sourceRoot)"));
        assertTrue(session.contains("Miuix307PassBlurBridge.bind("));
        assertTrue(session.contains("RootPassBlurEndpointBridge.sameGeneration"));
        assertTrue(session.contains("sourceEndpoint.bufferWidth"));
        assertTrue(session.contains("sourceEndpoint.bufferHeight"));
        assertTrue(session.contains("sourceEndpoint.rotation"));
        assertTrue(session.contains("RootPassBlurContentRect.resolve("));
        assertTrue(session.contains("sourceContentRect.left"));
        assertTrue(session.contains("sourceContentRect.bottom"));
        assertTrue(session.contains("sourceContentRect.width"));
        assertTrue(session.contains("sourceContentRect.height"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(session.contains("PrismalGeometry"));
        assertTrue(session.contains("PrismalHighlightProfile"));
        assertTrue(session.contains("Miuix307PrismalAdapter.toPortable"));
        assertTrue(session.contains("Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT"));
        assertTrue(session.contains("Miuix307PrismalCompositeShaders.FRAGMENT"));
        assertTrue(session.contains("first Prismal frame presented"));

        assertFalse(session.contains("MiuiWindowController#getWindowSurface()"));
        assertFalse(session.contains("new RootPassBlurBackend"));
        assertFalse(session.contains("PixelCopy"));
        assertFalse(session.contains("Bitmap"));

        assertTrue(output.contains("extends TextureView"));
        assertTrue(output.contains("group.addView(output, 0"));
        assertTrue(output.contains("new ViewGroup.LayoutParams(0, 0)"));
        assertTrue(output.contains("setMaterialAlpha"));
    }

    @Test
    public void prismalKeepsNativeSamplerAliveAndNormalizesInLocalViewRootDomain()
            throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String session = Files.readString(MAIN.resolve("SystemUiHandleMenuPrismalSession.java"));

        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurEnabled(target, true)"));
        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurRadius(target, 0)"));
        assertTrue(hook.contains("native sampler retained at blur=0"));
        assertFalse(hook.contains("native blur fallback released"));

        assertTrue(session.contains("RootPassBlurContentRect.resolve("));
        assertTrue(session.contains("localDomain="));
        assertTrue(session.contains("Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT"));
        assertTrue(session.contains("sourceContentRect.left"));
        assertTrue(session.contains("sourceContentRect.bottom"));
        assertTrue(session.contains("sourceContentRect.width"));
        assertTrue(session.contains("sourceContentRect.height"));
        assertFalse(session.contains("Miuix307BackdropMapping.compute("));
        assertFalse(session.contains("nativePlacement="));
        assertFalse(session.contains("backdropRect="));
        assertFalse(session.contains("HANDLE_MENU_OES_NORMALIZE_FRAGMENT"));
        assertFalse(session.contains("runContinuousSourceRefresh"));
        assertTrue(session.contains("continuous ViewRoot source confirmed timestamp="));
        assertTrue(session.contains("sourceFrameReady = true"));
        assertTrue(session.contains("if (!sourceFrameReady || normalizedTexture == 0"));
        assertFalse(session.contains("postDelayed"));
    }

    @Test
    public void prismalStartupCannotMakeCurrentBeforeEglInitialization() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String session = Files.readString(MAIN.resolve("SystemUiHandleMenuPrismalSession.java"));

        assertTrue(hook.contains("session.start(target.getWidth(), target.getHeight())"));
        assertTrue(hook.contains("SystemUiHandleMenuGlassOutputView.attachInsideTarget"));

        assertTrue(session.contains("if (!isEglReady()) return;"));
        assertTrue(session.contains("private boolean isEglReady()"));
        assertTrue(session.contains("attachInsideTarget() can publish the first visual size before start()"));
    }

    @Test
    public void animationProbeIsStrictlyReadOnlyAndOwnedByBindingLifetime() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String probe = Files.readString(MAIN.resolve("SystemUiHandleMenuAnimationProbe.java"));

        assertTrue(hook.contains("animationProbe.start()"));
        assertTrue(hook.contains("animationProbe.stop()"));
        assertTrue(probe.contains("OnPreDrawListener"));
        assertTrue(probe.contains("getScaleX()"));
        assertTrue(probe.contains("getScaleY()"));
        assertTrue(probe.contains("getTranslationX()"));
        assertTrue(probe.contains("getTranslationY()"));
        assertTrue(probe.contains("getPivotX()"));
        assertTrue(probe.contains("getPivotY()"));
        assertTrue(probe.contains("getGlobalVisibleRect"));
        assertTrue(probe.contains("RootPassBlurEndpointBridge.inspect(sourceRoot)"));
        assertTrue(probe.contains("[DC][SystemUiHandleMenuAnim]"));

        assertFalse(probe.contains("setScaleX("));
        assertFalse(probe.contains("setScaleY("));
        assertFalse(probe.contains("setTranslationX("));
        assertFalse(probe.contains("setTranslationY("));
        assertFalse(probe.contains("setAlpha("));
        assertFalse(probe.contains("SurfaceControl.Transaction"));
        assertFalse(probe.contains("PassBlurBindRequest"));
        assertFalse(probe.contains("MiBlurBridge."));
        assertFalse(probe.contains("postDelayed"));
    }


    @Test
    public void surfaceProbeObservesOnlyCaptionMenuTransactionsWithoutMutatingThem()
            throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String probe = Files.readString(MAIN.resolve("SystemUiHandleMenuSurfaceProbe.java"));

        assertTrue(module.contains("SystemUiHandleMenuSurfaceProbe.install()"));
        assertTrue(probe.contains("SurfaceControl.Transaction.class"));
        assertTrue(probe.contains("\"setMatrix\""));
        assertTrue(probe.contains("\"setScale\""));
        assertTrue(probe.contains("\"setPosition\""));
        assertTrue(probe.contains("\"setAlpha\""));
        assertTrue(probe.contains("\"reparent\""));
        assertTrue(probe.contains("\"Caption Menu\""));
        assertTrue(probe.contains("[DC][SystemUiHandleMenuSurface]"));

        assertFalse(probe.contains("new SurfaceControl.Transaction"));
        assertFalse(probe.contains(".apply()"));
        assertFalse(probe.contains(".setMatrix("));
        assertFalse(probe.contains(".setScale("));
        assertFalse(probe.contains(".setPosition("));
        assertFalse(probe.contains(".setAlpha("));
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
