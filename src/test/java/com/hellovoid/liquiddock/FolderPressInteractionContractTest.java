package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static HyperOS input/API bans. Press state and reversible animation are typed state-tested. */
public class FolderPressInteractionContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String folderHook() throws Exception {
        return Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
    }

    @Test public void usesFolderIconsConcreteDispatchWithoutInstallingCompetingTouchApis()
            throws Exception {
        String hook = folderHook();

        assertTrue(hook.contains("folderIcon.getDeclaredMethod(\"dispatchTouchEvent\", MotionEvent.class)"));
        assertFalse(hook.contains("View.class.getDeclaredMethod(\"dispatchTouchEvent\", MotionEvent.class)"));
        assertFalse(hook.contains("setOnTouchListener"));
        assertFalse(hook.contains("folmeDown") || hook.contains("folmeUp"));
    }

    @Test public void folderMaterialSuppressionNeverRewritesVendorDrawableOrViewAlpha()
            throws Exception {
        String hook = folderHook();

        assertFalse(hook.contains(
                "ImageView.class.getDeclaredMethod(\"setImageDrawable\", Drawable.class)"));
        assertFalse(hook.contains("HookUtil.hook(setImageDrawable"));
        assertFalse(hook.contains("image.setAlpha(0") || hook.contains("material.setAlpha(0"));
    }
}
