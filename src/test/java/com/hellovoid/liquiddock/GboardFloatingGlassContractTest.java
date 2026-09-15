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
        String resolver = read(MAIN.resolve("GboardFloatingTargetResolver.java"));

        assertTrue(hook.contains("GboardFloatingTargetResolver.resolve(classLoader)"));
        assertTrue(resolver.contains("RUNTIME_PROVIDER = \"pev\""));
        assertFalse(resolver.contains("defpackage.pev"));
        assertTrue(resolver.contains("\"b\""));
        assertTrue(resolver.contains("\"a\""));
        assertTrue(resolver.contains("View.class.isAssignableFrom"));
        assertTrue(hook.contains("GboardFloatingGlassCoordinator.onShown"));
        assertTrue(hook.contains("GboardFloatingGlassCoordinator.onHidden"));
        assertFalse(hook.contains("LatinIME"));
        assertFalse(hook.contains("onCreateInputView"));
    }

    @Test public void hookFailureIsDiagnosableThroughTheFilteredTagLine() throws Exception {
        String hook = read(MAIN.resolve("GboardFloatingGlassHook.java"));

        assertTrue(hook.contains("failureSummary(error)"));
        assertTrue(hook.contains("error.getClass().getName()"));
        assertTrue(hook.contains("error.getMessage()"));
    }

    @Test public void sessionFailureIsDiagnosableThroughTheFilteredTagLine() throws Exception {
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));

        assertTrue(session.contains("notifyFailure(\"output-attach\""));
        assertTrue(session.contains("notifyFailure(\"fresh-frame\""));
        assertTrue(session.contains("notifyFailure(\"source-terminal\""));
        assertTrue(session.contains("notifyFailure(\"render\""));
        assertTrue(session.contains("failureSummary(error)"));
        assertTrue(session.contains("session failure stage="));
    }

    @Test public void gboardUsesSidebarStyleContinuousPassBlurOwnership() throws Exception {
        String module = read(MAIN.resolve("ModuleMain.java"));
        String authority = read(MAIN.resolve("GboardPassBlurContinuousAuthority.java"));
        String bridge = read(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue(module.contains("GboardPassBlurContinuousAuthority.install()"));
        assertTrue(authority.contains("SetPassBlurSurface"));
        assertTrue(authority.contains("setUpdateTextureFlag"));
        assertTrue(authority.contains("args[1] = claim.surface"));
        assertTrue(authority.contains("args[1] = Boolean.TRUE"));
        assertTrue(bridge.contains("GboardPassBlurContinuousAuthority.claim("));
        assertTrue(bridge.contains("GboardPassBlurContinuousAuthority.release("));
        assertTrue(bridge.contains("binding.domain == PassBlurDomain.GBOARD_FLOATING"));
    }

    @Test public void replacementUsesTheDecompiledKeyboardAreaAndBackgroundFrame() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));

        assertTrue(coordinator.contains("0x7f0b0617"));
        assertTrue(coordinator.contains("0x7f0b0618"));
        assertTrue(coordinator.contains("insertSinkAboveStockBackground"));
        assertTrue(coordinator.contains("backgroundFrame.setAlpha(0f)"));
        assertTrue(coordinator.contains("restoreStockBackground"));
        assertTrue(coordinator.contains("OnAttachStateChangeListener"));
    }

    @Test public void sinkUsesRealKeyboardBoundsInsteadOfGboardMatchParentSentinel() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));

        assertFalse(coordinator.contains("ViewGroup.LayoutParams.MATCH_PARENT,\n                    ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue(coordinator.contains("new ViewGroup.LayoutParams(1, 1)"));
        assertTrue(coordinator.contains("syncSinkBounds(state)"));
        assertTrue(coordinator.contains("state.keyboardArea.getWidth()"));
        assertTrue(coordinator.contains("state.keyboardArea.getHeight()"));
    }

    @Test public void sinkIsInsertedRelativeToTheStockBackgroundNotAtTreeBottom() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));

        assertFalse(coordinator.contains("addView(sink, 0"));
        assertTrue(coordinator.contains("backgroundFrame.getParent()"));
        assertTrue(coordinator.contains("indexOfChild(backgroundFrame)"));
        assertTrue(coordinator.contains("backgroundIndex + 1"));
        assertTrue(coordinator.contains("state.sinkHost"));
    }

    @Test public void presentedGlassOwnsAllFloatingStockFillsAndRestoresThem() throws Exception {
        String module = read(MAIN.resolve("ModuleMain.java"));
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String authority = read(MAIN.resolve("GboardStockVisualAuthority.java"));

        assertTrue(module.contains("GboardStockVisualAuthority.install(classLoader)"));
        assertTrue(coordinator.contains("0x7f0b061b"));
        assertTrue(coordinator.contains("GboardStockVisualAuthority.claim("));
        assertTrue(coordinator.contains("GboardStockVisualAuthority.release("));
        assertTrue(authority.contains("loadClass(\"pef\")"));
        assertTrue(authority.contains("getDeclaredMethod(\"j\", Integer.TYPE)"));
        assertTrue(authority.contains("getDeclaredMethod(\"e\", Integer.TYPE)"));
        assertTrue(authority.contains("baseArea.setBackground(null)"));
        assertTrue(authority.contains("baseArea.setElevation(0f)"));
        assertTrue(authority.contains("bottomFrame.setBackground(null)"));
        assertTrue(authority.contains("baseArea.setBackground(claim.baseBackground)"));
        assertTrue(authority.contains("baseArea.setElevation(claim.baseElevation)"));
        assertTrue(authority.contains("bottomFrame.setBackground(claim.bottomBackground)"));
    }

    @Test public void presentedGlassAlsoOwnsHeaderAndKeyboardContainerFills() throws Exception {
        String authority = read(MAIN.resolve("GboardStockVisualAuthority.java"));

        assertTrue(authority.contains("0x7f0b0643"));
        assertTrue(authority.contains("0x7f0b061a"));
        assertTrue(authority.contains("0x7f0b02f6"));
        assertTrue(authority.contains("0x7f0b0644"));
        assertTrue(authority.contains("containerBackgrounds"));
        assertTrue(authority.contains("view.setBackground(null)"));
        assertTrue(authority.contains("view.setBackground(saved)"));
    }

    @Test public void headerCurrentContentBackgroundIsAuthoritativeAcrossKeyboardViewRebinds() throws Exception {
        String authority = read(MAIN.resolve("GboardStockVisualAuthority.java"));

        assertTrue(authority.contains("KeyboardViewHolder"));
        assertTrue(authority.contains("findKeyboardViewBindMethod"));
        assertTrue(authority.contains("claimForHeaderHolder"));
        assertTrue(authority.contains("suppressHeaderContentBackground"));
        assertTrue(authority.contains("headerContentBackgrounds"));
        assertTrue(authority.contains("content.setBackground(null)"));
        assertTrue(authority.contains("content.setBackground(saved)"));
    }

    @Test public void runtimeProvenTopEdgeAndMainBodyBackgroundsAreOwned() throws Exception {
        String authority = read(MAIN.resolve("GboardStockVisualAuthority.java"));

        assertTrue(authority.contains("0x7f0b064f"));
        assertTrue(authority.contains("topEdgeBackground"));
        assertTrue(authority.contains("topEdge.setBackground(null)"));
        assertTrue(authority.contains("claim.topEdge.setBackground(claim.topEdgeBackground)"));
        assertTrue(authority.contains("MAIN_KEYBOARD_VIEW_HOLDER_ID"));
        assertTrue(authority.contains("BY_DYNAMIC_HOLDER"));
        assertTrue(authority.contains("claimForDynamicHolder"));
        assertTrue(authority.contains("suppressBoundContentBackground"));
        assertTrue(authority.contains("boundContentBackgrounds"));
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
