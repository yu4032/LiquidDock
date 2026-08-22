package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks widget/icon support onto the existing one-surface static Launcher compositor. */
public class LauncherGlassGenericStaticNodeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        Path path = MAIN.resolve(name);
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test
    public void staticNodeCarriesFolderWidgetAndIconKindsWithoutOwningSurface() throws Exception {
        String node = read("LauncherGlassStaticNode.java");
        assertTrue(node.contains("LauncherGlassDragState.Kind"));
        assertTrue(node.contains("Kind.ICON") || node.contains("LauncherGlassDragState.Kind.ICON"));
        assertFalse(node.contains("extends TextureView"));
        assertFalse(node.contains("new Surface("));
        assertFalse(node.contains("android.opengl.EGLSurface"));
    }

    @Test
    public void concreteLauncherHostsJoinSharedStaticCompositorWithoutGlobalViewHook() throws Exception {
        String hook = read("MiuixLauncherStaticGlassHook.java");
        assertFalse("static widget/icon hook must exist", hook.isBlank());
        assertTrue(hook.contains("com.miui.home.launcher.ShortcutIcon"));
        assertTrue(hook.contains("com.miui.home.launcher.LauncherAppWidgetHostView"));
        assertTrue(hook.contains("com.miui.home.launcher.maml.MaMlHostView"));
        assertTrue(hook.contains("getDeclaredConstructors()"));
        assertTrue(hook.contains("LauncherGlassStaticNode.attachToMaterial"));
        assertFalse(hook.contains("View.class.getDeclaredMethod(\"onAttachedToWindow\""));
        assertFalse(hook.contains("TextureView"));
        assertFalse(hook.contains("new Surface("));
    }

    @Test
    public void iconPathUsesVisualSubrectAndLeavesOriginalIconDrawingUntouched() throws Exception {
        String node = read("LauncherGlassStaticNode.java");
        String geometry = read("LauncherGlassIconGeometry.java");
        String hook = read("MiuixLauncherStaticGlassHook.java");
        assertFalse("icon geometry helper must exist", geometry.isBlank());
        assertTrue(node.contains("LauncherGlassIconGeometry"));
        assertTrue(geometry.contains("TextView"));
        assertTrue(geometry.contains("getCompoundDrawables"));
        assertFalse("icons must stay MIUI-owned; glass belongs behind transparent pixels",
                hook.contains("setImageDrawable") || hook.contains("setBackgroundColor")
                        || hook.contains("Color.TRANSPARENT"));
    }

    @Test
    public void moduleInstallsStaticWidgetIconHook() throws Exception {
        String module = read("ModuleMain.java");
        assertTrue(module.contains("MiuixLauncherStaticGlassHook.install(classLoader, runtimeConfig);"));
    }
}
