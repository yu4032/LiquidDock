package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LauncherIconAnimationClipPolicyTest {
    @Test public void onlyFloatingIconInitializationUsesCroppedContent() {
        assertTrue(LauncherIconAnimationClipPolicy.isFloatingIconInit(new StackTraceElement[]{
                frame("com.miui.home.recents.views.FloatingIconView2", "init")}));
        assertFalse(LauncherIconAnimationClipPolicy.isFloatingIconInit(new StackTraceElement[]{
                frame("com.miui.home.recents.views.FloatingIconLayer2", "init$lambda$3")}));
        assertFalse(LauncherIconAnimationClipPolicy.isFloatingIconInit(new StackTraceElement[]{
                frame("com.miui.home.launcher.anim.LaunchAppAndBackHomeAnimTarget", "showIcon")}));
        assertFalse(LauncherIconAnimationClipPolicy.isFloatingIconInit(new StackTraceElement[]{
                frame("com.miui.home.recents.views.FloatingIconView2", "notifyBackHome")}));
    }
    @Test public void oemVisibilityZeroIsDisplayedAndFourIsPlaceholder() {
        assertTrue(LauncherIconAnimationClipPolicy.isOemIconVisible(0));
        assertFalse(LauncherIconAnimationClipPolicy.isOemIconVisible(4));
    }
    @Test public void asynchronousFloatingIconRefreshUsesCroppedContent() {
        assertTrue(LauncherIconAnimationClipPolicy.isFloatingIconRefresh(new StackTraceElement[]{
                frame("com.miui.home.recents.views.FloatingIconView2", "notifyBackHome$lambda$3")}));
        assertTrue(LauncherIconAnimationClipPolicy.isFloatingIconRefresh(new StackTraceElement[]{
                frame("com.miui.home.recents.views.FloatingIconView2", "update$lambda$0")}));
        assertFalse(LauncherIconAnimationClipPolicy.isFloatingIconRefresh(new StackTraceElement[]{
                frame("com.miui.home.launcher.ShortcutIcon", "updateIconDrawable")}));
    }
    private static StackTraceElement frame(String cls, String method) {
        return new StackTraceElement(cls, method, "Launcher.kt", 1);
    }
}
