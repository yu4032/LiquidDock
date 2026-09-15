package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static contract for the version-scoped Gboard floating-keyboard Prismal replacement. */
public class GboardFloatingGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void gboardPackageIsScopedAndRoutedWithoutLauncherInitialization() throws Exception {
        String scope = read(Path.of("src/main/resources/META-INF/xposed/scope.list"));
        String module = read(MAIN.resolve("ModuleMain.java"));

        assertTrue(scope.contains("com.google.android.inputmethod.latin"));
        assertTrue(module.contains("GBOARD_PACKAGE = \"com.google.android.inputmethod.latin\""));
        assertTrue(module.contains("GboardFloatingGlassHook.install(classLoader, runtimeConfig)"));
        assertTrue(module.contains("if (GBOARD_PACKAGE.equals(packageName))"));
    }

    @Test public void hookTargetsOnlyThePopupFloatingKeyboardLifecycle() throws Exception {
        String hook = read(MAIN.resolve("GboardFloatingGlassHook.java"));

        assertTrue(hook.contains("defpackage.pev"));
        assertTrue(hook.contains("\"b\""));
        assertTrue(hook.contains("\"a\""));
        assertTrue(hook.contains("HookUtil.getField(owner, \"b\")"));
        assertTrue(hook.contains("GboardFloatingGlassCoordinator.onShown"));
        assertTrue(hook.contains("GboardFloatingGlassCoordinator.onHidden"));
        assertFalse(hook.contains("LatinIME"));
        assertFalse(hook.contains("onCreateInputView"));
    }

    @Test public void replacementUsesTheDecompiledKeyboardAreaAndBackgroundFrame() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));

        assertTrue(coordinator.contains("0x7f0b0617"));
        assertTrue(coordinator.contains("0x7f0b0618"));
        assertTrue(coordinator.contains("addView(sink, 0"));
        assertTrue(coordinator.contains("backgroundFrame.setAlpha(0f)"));
        assertTrue(coordinator.contains("restoreStockBackground"));
        assertTrue(coordinator.contains("OnAttachStateChangeListener"));
    }

    @Test public void floatingGlassStaysZeroCopyContinuousAndFeedbackSafe() throws Exception {
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));
        String request = read(MAIN.resolve("PassBlurBindRequest.java"));
        String domain = read(MAIN.resolve("PassBlurDomain.java"));

        assertTrue(domain.contains("GBOARD_FLOATING"));
        assertTrue(request.contains("static PassBlurBindRequest gboardFloating(View authoritativeRoot)"));
        assertTrue(request.contains("PassBlurDomain.GBOARD_FLOATING"));
        assertTrue(session.contains("RootPassBlurBackend"));
        assertTrue(session.contains("PassBlurBindRequest.gboardFloating(root)"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(session.contains("prepareBackdrop"));
        assertTrue(session.contains("requestFresh(GENERATION)"));
        assertFalse(session.contains("setUpdatesEnabled(false"));
        assertFalse(session.contains("ScreenCapture"));
        assertFalse(session.contains("PixelCopy"));
        assertFalse(session.contains("Bitmap.createBitmap"));
    }

    @Test public void stockBackgroundIsOnlyHiddenAfterARealPresentedFrame() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));

        assertTrue(coordinator.contains("onPresented"));
        assertTrue(coordinator.contains("backgroundFrame.setAlpha(0f)"));
        assertTrue(coordinator.contains("restoreStockBackground"));
        assertTrue(session.contains("eglSwapBuffers") || session.contains("swapBuffers"));
        assertTrue(session.contains("listener.onPresented()"));
    }
}
