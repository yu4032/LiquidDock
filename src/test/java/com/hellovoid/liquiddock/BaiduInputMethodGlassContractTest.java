package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static contract for safe Baidu Input Method Prismal replacement. */
public class BaiduInputMethodGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void packageUsesStableImeServiceHooksAndDedicatedPassBlurDomain() throws Exception {
        String scope = read(Path.of("src/main/resources/META-INF/xposed/scope.list"));
        String module = read(MAIN.resolve("ModuleMain.java"));
        String hook = read(MAIN.resolve("BaiduInputMethodGlassHook.java"));
        String domain = read(MAIN.resolve("PassBlurDomain.java"));
        String request = read(MAIN.resolve("PassBlurBindRequest.java"));

        assertTrue(scope.contains("com.baidu.input_mi"));
        assertTrue(module.contains("BAIDU_INPUTMETHOD_PACKAGE = \"com.baidu.input_mi\""));
        assertTrue(module.contains("BaiduInputMethodPassBlurContinuousAuthority.install()"));
        assertTrue(module.contains("BaiduInputMethodGlassHook.install(classLoader)"));
        assertTrue(hook.contains("com.content.input_mi.ImeService"));
        assertTrue(hook.contains("\"setInputView\""));
        assertTrue(hook.contains("\"onWindowShown\""));
        assertTrue(domain.contains("BAIDU_INPUTMETHOD"));
        assertTrue(request.contains("static PassBlurBindRequest baiduInputMethod(View authoritativeRoot)"));
    }

    @Test public void resolverUsesStableViewTypesAndRuntimeTopologyOnly() throws Exception {
        String resolver = read(MAIN.resolve("BaiduInputMethodStructureResolver.java"));
        assertTrue(resolver.contains("com.content.simeji.inputview.KeyboardRegion"));
        assertTrue(resolver.contains("com.content.simeji.inputview.KeyboardContainer"));
        assertTrue(resolver.contains("getParent()"));
        assertTrue(resolver.contains("getChildCount()"));
        assertTrue(resolver.contains("indexOfChild"));
        assertTrue(resolver.contains("getLocationInWindow"));
        assertFalse(resolver.contains("findViewById"));
        assertFalse(resolver.contains("0x7f"));
        assertFalse(resolver.contains("xb9"));
        assertFalse(resolver.contains("ch9"));
    }

    @Test public void coordinatorTracksGeometryWithoutFixedDelayAndFailsClosed() throws Exception {
        String coordinator = read(MAIN.resolve("BaiduInputMethodGlassCoordinator.java"));
        assertTrue(coordinator.contains("ViewTreeObserver.OnPreDrawListener"));
        assertTrue(coordinator.contains("addOnPreDrawListener"));
        assertTrue(coordinator.contains("removeOnPreDrawListener"));
        assertTrue(coordinator.contains("restoreStockBackground"));
        assertTrue(coordinator.contains("backgroundFrame.setAlpha(0f)"));
        assertFalse(coordinator.contains("postDelayed"));
    }

    @Test public void sessionIsZeroCopyAndStockWaitsForPresentedTexture() throws Exception {
        String session = read(MAIN.resolve("BaiduInputMethodGlassSession.java"));
        String sink = read(MAIN.resolve("BaiduInputMethodGlassView.java"));
        assertTrue(session.contains("RootPassBlurBackend"));
        assertTrue(session.contains("PassBlurBindRequest.baiduInputMethod(root)"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(session.contains("prepareBackdrop"));
        assertTrue(session.contains("swapBuffers"));
        assertTrue(session.contains("onOutputPresented"));
        assertTrue(sink.contains("TextureView"));
        assertTrue(sink.contains("onSurfaceTextureUpdated"));
        assertTrue(sink.contains("session.onOutputPresented()"));
        assertFalse(session.contains("PixelCopy"));
        assertFalse(session.contains("ScreenCapture"));
        assertFalse(session.contains("Bitmap.createBitmap"));
    }
}
