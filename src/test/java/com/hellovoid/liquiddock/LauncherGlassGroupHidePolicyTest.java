package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LauncherGlassGroupHidePolicyTest {
    @Test public void dockBackgroundHideHasSeparateOptInAndNeverAffectsAnimatedIcon() {
        assertEquals(0f, LauncherGlassGroupHidePolicy.dockBackground(true,true,false,1f),0f);
        assertEquals(0f, LauncherGlassGroupHidePolicy.dockBackground(true,false,true,1f),0f);
        assertEquals(.7f, LauncherGlassGroupHidePolicy.dockBackground(true,false,false,.7f),0f);
        assertEquals(1f, LauncherGlassGroupHidePolicy.dockBackground(false,true,true,0f),0f);
        assertEquals(.6f, DockGlassIconOpacity.resolve(.6f,0f,true,0f),0f);
    }

    @Test public void workspaceProxyRemainsDrawableWhenItsSiblingsAreHidden() {
        assertFalse(LauncherGlassGroupHidePolicy.hideWholeWorkspaceOutput(true,true));
        assertTrue(LauncherGlassGroupHidePolicy.hideWholeWorkspaceOutput(true,false));
        assertFalse(LauncherGlassGroupHidePolicy.hideWholeWorkspaceOutput(false,false));
        assertEquals(0f, LauncherGlassGroupHidePolicy.workspaceBackground(true,true,true,1f),0f);
        assertEquals(.5f, LauncherGlassGroupHidePolicy.workspaceBackground(true,true,false,.5f),0f);
        assertEquals(1f, LauncherGlassGroupHidePolicy.workspaceBackground(false,true,true,0f),0f);
        assertEquals(1f, LauncherGlassGroupHidePolicy.workspaceBackground(true,false,true,0f),0f);
        assertTrue(LauncherGlassGroupHidePolicy.honorWorkspaceAncestorAlpha(true,true));
        assertFalse(LauncherGlassGroupHidePolicy.honorWorkspaceAncestorAlpha(false,true));
        assertFalse(LauncherGlassGroupHidePolicy.honorWorkspaceAncestorAlpha(true,false));
    }

    @Test public void opacityPolicyGuardsInvalidSamples() {
        assertEquals(0f, LauncherGlassGroupHidePolicy.dockBackground(true,false,false,Float.NaN),0f);
        assertEquals(0f, LauncherGlassGroupHidePolicy.workspaceBackground(true,true,false,Float.NaN),0f);
    }
}
