package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class MiuiSearchboxGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    @Test
    public void searchboxUsesStableSemanticAnchorsAndExistingGlassBackend() throws Exception {
        String module = read(MAIN.resolve("ModuleMain.java"));
        String hook = read(MAIN.resolve("MiuiSearchboxGlassHook.java"));
        String session = read(MAIN.resolve("MiuiSearchboxGlassSession.java"));
        String request = read(MAIN.resolve("PassBlurBindRequest.java"));
        String scope = Files.readString(Path.of("src/main/resources/META-INF/xposed/scope.list"));

        assertTrue(scope.contains("com.android.quicksearchbox"));
        assertTrue(module.contains("MIUI_SEARCHBOX_PACKAGE = \"com.android.quicksearchbox\""));
        assertTrue(module.contains("MiuiSearchboxGlassHook.install(classLoader)"));
        assertTrue(hook.contains("com.android.quicksearchbox.SearchActivity"));
        assertTrue(hook.contains("com.android.quicksearchbox.ui.SearchActivityBackground"));
        assertTrue(hook.contains("com.android.quicksearchbox.util.BlurTransition"));
        assertTrue(hook.contains("search_activity_view_background"));
        assertTrue(session.contains("implements RootPassBlurBackend.Consumer"));
        assertTrue(session.contains("PassBlurBindRequest.miuiSearchbox(root)"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(request.contains("MIUI_SEARCHBOX_EXTRA_EXCLUSIONS = {\"MiuiSearchboxGlassView\"}"));
        assertFalse(hook.contains("getDeclaredFields("));
        assertFalse(hook.contains("getDeclaredConstructors("));
        assertFalse(hook.contains("search_input_bg_hf"));
    }
}
