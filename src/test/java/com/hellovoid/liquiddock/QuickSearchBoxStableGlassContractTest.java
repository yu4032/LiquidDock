package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the QuickSearchBox integration against reflection/R8/obfuscated-symbol coupling. */
public class QuickSearchBoxStableGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void quickSearchBoxIsScopedAndRoutedAsAnIsolatedProcess() throws Exception {
        List<String> scope = Files.readAllLines(
                Path.of("src/main/resources/META-INF/xposed/scope.list"));
        assertTrue(scope.contains("com.android.quicksearchbox"));

        String moduleMain = Files.readString(MAIN.resolve("ModuleMain.java"));
        assertTrue(moduleMain.contains("QuickSearchBoxProcessPolicy.PACKAGE"));
        assertTrue(moduleMain.contains("QuickSearchBoxGlassRuntimeState.initialize("));
        assertTrue(moduleMain.contains("QuickSearchBoxGlassHook.install("));
    }

    @Test
    public void hookUsesOnlyFrameworkActivityLifecycleAnchors() throws Exception {
        Path hookPath = MAIN.resolve("QuickSearchBoxGlassHook.java");
        assertTrue(Files.exists(hookPath));
        String hook = Files.readString(hookPath);

        assertTrue(hook.contains("HookUtil.hookMethod(Activity.class, \"onCreate\""));
        assertTrue(hook.contains("HookUtil.hookMethod(Activity.class, \"onResume\""));
        assertTrue(hook.contains("HookUtil.hookMethod(Activity.class, \"onNewIntent\""));
        assertTrue(hook.contains("HookUtil.hookMethod(Activity.class, \"onConfigurationChanged\""));
        assertTrue(hook.contains("HookUtil.hookMethod(Activity.class, \"onDestroy\""));
        assertTrue(hook.contains("com.android.quicksearchbox.SearchActivity"));

        assertFalse(hook.contains("Class.forName"));
        assertFalse(hook.contains("getDeclaredMethod"));
        assertFalse(hook.contains("getDeclaredField"));
        assertFalse(hook.contains("tryInvoke"));
        assertFalse(hook.contains("updateSearchActivityViewBlurBackground"));
        assertFalse(hook.contains("onBitmapBlur"));
        assertFalse(hook.contains("setSearchActivityViewLayoutParams"));
    }

    @Test
    public void controllerUsesSemanticResourcesAndNoVendorReflection() throws Exception {
        Path controllerPath = MAIN.resolve("QuickSearchBoxGlassController.java");
        assertTrue(Files.exists(controllerPath));
        String controller = Files.readString(controllerPath);

        assertTrue(controller.contains("search_activity_view"));
        assertTrue(controller.contains("search_activity_view_background"));
        assertTrue(controller.contains("getIdentifier"));
        assertTrue(controller.contains("OnPreDrawListener"));
        assertTrue(controller.contains("setBackground(null)"));

        assertFalse(controller.contains("Class.forName"));
        assertFalse(controller.contains("getDeclaredMethod"));
        assertFalse(controller.contains("getDeclaredField"));
        assertFalse(controller.contains("tryInvoke"));
        assertFalse(controller.contains("updateSearchActivityViewBlurBackground"));
        assertFalse(controller.contains("onBitmapBlur"));
    }

    @Test
    public void sessionUsesExistingPassBlurToPrismalPipeline() throws Exception {
        Path sessionPath = MAIN.resolve("QuickSearchBoxGlassSession.java");
        assertTrue(Files.exists(sessionPath));
        String session = Files.readString(sessionPath);

        assertTrue(session.contains("new RootPassBlurBackend("));
        assertTrue(session.contains("PassBlurBindRequest.quickSearchBox("));
        assertTrue(session.contains("prismalRenderer.prepareBackdrop("));
        assertTrue(session.contains("prismalRenderer.drawGlass("));
        assertTrue(session.contains("sourceBackend.shutdown()"));
    }
}