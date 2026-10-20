package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SecurityCenterHookSpecTest {
    @Test public void exposesOnlyStableEntryGetterAndResourceAnchors() {
        assertEquals("com.miui.gamebooster.service.DockWindowManagerService",
                SecurityCenterHookSpec.BOOTSTRAP_SERVICE_CLASS);
        assertEquals("com.miui.gamebooster.windowmanager.newbox.TurboLayout",
                SecurityCenterHookSpec.TURBO_LAYOUT_CLASS);
        assertEquals("getDockLayout", SecurityCenterHookSpec.DOCK_LAYOUT_GETTER);
        assertEquals("getAbstractDockLayout", SecurityCenterHookSpec.ABSTRACT_DOCK_LAYOUT_GETTER);
        assertEquals("getAppsLayout", SecurityCenterHookSpec.APPS_LAYOUT_GETTER);
        assertEquals("getBoxView", SecurityCenterHookSpec.BOX_VIEW_GETTER);
        assertEquals("getGameTurboLayout", SecurityCenterHookSpec.GAME_BOX_GETTER);
        assertEquals("getMainView", SecurityCenterHookSpec.GAME_MATERIAL_GETTER);
        assertEquals("getVideoBoxViewAdapter", SecurityCenterHookSpec.VIDEO_ADAPTER_GETTER);
        assertEquals("game_toolbox_background_radius", SecurityCenterHookSpec.GAME_RADIUS_RESOURCE);
        assertEquals("main_content", SecurityCenterHookSpec.VIDEO_CONTENT_RESOURCE);
        assertEquals("dp_24", SecurityCenterHookSpec.ALL_APPS_RADIUS_RESOURCE);
    }




}
