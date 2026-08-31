package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for semantic Launcher scene transitions. */
public class LauncherGlassSceneTransitionContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void recentsAndHomePresentationHideStaticLayerImmediately() throws Exception {
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        String visibility = methodSlice(controller,
                "private void applyLayerVisibility()",
                "/** Startup barrier");

        assertTrue("Recents coverage must use the StaticLayer immediate-hide path",
                visibility.contains("folderCovered || recentsCovered"));
        assertTrue("App->HOME must reset the cached layer to alpha=0 before its reveal fade",
                visibility.contains("homeTransitionPending"));
    }

    @Test
    public void closeToHomeStartsRevealAfterVendorAnimationHasStarted() throws Exception {
        String hook = Files.readString(MAIN.resolve("LauncherGlassHomePresentationHook.java"));
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        String start = methodSlice(hook,
                "private static void hookHomeStart(ClassLoader classLoader)",
                "private static void hookHomeEnd(ClassLoader classLoader)");

        assertTrue("4.50 semantic HOME marker must stay WindowElement.animTo(CLOSE_TO_HOME)",
                start.contains("WINDOW_ELEMENT")
                        && start.contains("\"animTo\"")
                        && start.contains("containsHomeClose(args)"));
        int freeze = start.indexOf("setHomeTransitionPendingForAll(true)");
        int vendorStart = start.indexOf("chain.proceed(args)");
        int reveal = start.indexOf("beginHomeReturnRevealForAll()");
        assertTrue("capture must freeze before vendor HOME animation starts",
                freeze >= 0 && vendorStart > freeze);
        assertTrue("glass reveal must be armed only after vendor HOME animation starts",
                reveal > vendorStart);

        assertTrue("SceneController must own a HOME-return early-reveal entry point",
                controller.contains("static void beginHomeReturnRevealForAll()"));
        assertTrue("HOME return reveal must remain under the HOME capture barrier",
                controller.contains("homeTransitionPending"));
    }

    private static String methodSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + 1);
        if (start < 0 || end < 0 || end <= start) return "";
        return source.substring(start, end);
    }
}
