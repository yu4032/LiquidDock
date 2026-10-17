package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

/** OEM Recents dim hooks are narrow but handle both known SurfaceControl types. */
public class RecentsDimLayerMethodPolicyTest {
    @Test public void onlyKnownSurfaceOwnersCanReceiveDimmingOverride() {
        assertTrue(RecentsDimLayerMethodPolicy.isKnownSurfaceType(
                "com.android.systemui.shared.recents.system.SurfaceControlCompat"));
        assertTrue(RecentsDimLayerMethodPolicy.isKnownSurfaceType("android.view.SurfaceControl"));
        assertFalse(RecentsDimLayerMethodPolicy.isKnownSurfaceType(
                "com.android.systemui.shared.recents.system.SurfaceControlTransaction"));
        assertFalse(RecentsDimLayerMethodPolicy.isKnownSurfaceType(
                "android.view.SurfaceControl$Transaction"));
    }

    @Test public void genericMethodsAndUnknownSignaturesAreRejected() {
        assertFalse(RecentsDimLayerMethodPolicy.isDimMethod(
                "dim", new Class<?>[]{float.class, String.class, boolean.class}));
        assertFalse(RecentsDimLayerMethodPolicy.isDimMethod(
                "dim", new Class<?>[]{float.class, Object.class, boolean.class}));
        assertFalse(RecentsDimLayerMethodPolicy.isDimMethod(
                "setAlpha", new Class<?>[]{float.class, Object.class, boolean.class}));
        assertFalse(RecentsDimLayerMethodPolicy.isDimMethod(
                "dim", new Class<?>[]{float.class, boolean.class}));
    }
}
