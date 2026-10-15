package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class FolderDockAppLifecyclePolicyTest {
    @Test public void enterOnlyFromNativeHomeState() {
        assertTrue(FolderDockAppLifecyclePolicy.shouldEnter("HOME_STATE"));
        assertFalse(FolderDockAppLifecyclePolicy.shouldEnter("RECENT_STATE"));
        assertFalse(FolderDockAppLifecyclePolicy.shouldEnter("APP_STATE"));
        assertFalse(FolderDockAppLifecyclePolicy.shouldEnter(null));
    }

    @Test public void pairedRestoreDoesNotInterruptRealAppLaunchOrBackgroundLauncher() {
        assertTrue(FolderDockAppLifecyclePolicy.shouldRestore("APP_STATE", false, false));
        assertFalse(FolderDockAppLifecyclePolicy.shouldRestore("APP_STATE", true, false));
        assertFalse(FolderDockAppLifecyclePolicy.shouldRestore("APP_STATE", false, true));
        assertFalse(FolderDockAppLifecyclePolicy.shouldRestore("RECENT_STATE", false, false));
        assertFalse(FolderDockAppLifecyclePolicy.shouldRestore("HOME_STATE", false, false));
    }
}
