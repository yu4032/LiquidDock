package com.hellovoid.liquiddock;

import static org.junit.Assert.assertNotNull;

import android.graphics.Matrix;
import android.view.View;

import java.lang.reflect.Method;

import org.junit.Test;

/** Typed API guard for the shared-root Workspace geometry capture path. */
public class LauncherWorkspaceGeometryHotPathContractTest {
    @Test
    public void staticNodeExposesSharedRootTransformCapture() throws Exception {
        Method method = LauncherGlassStaticNode.class.getDeclaredMethod(
                "captureGeometry", View.class, Matrix.class, int.class, int.class);
        assertNotNull(method);
    }
}
