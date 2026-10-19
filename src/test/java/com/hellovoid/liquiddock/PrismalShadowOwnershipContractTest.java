package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ownership and work-avoidance contracts for glass-only Prismal cast shadows. */
public final class PrismalShadowOwnershipContractTest {
    private static final Path SRC = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(SRC.resolve(name));
    }

    @Test public void dockAndOtherNodesShareTheSameGpuShadowMaterial() throws Exception {
        String effects=read("GlassEdgeEffects.java");
        String compositor=read("DockGlassCompositor.java");
        String session=read("LauncherGlassSession.java");
        assertFalse(effects.contains("withoutNativeShadows"));
        assertFalse(effects.contains("withoutNativeShadow(String scope)"));
        assertFalse(session.contains("GlassEdgeEffects.withoutNativeShadow("));
        assertTrue(compositor.contains(
                "renderer.setEdgeDecoration(GlassEdgeEffects.current(GlassEdgeEffects.DOCK)"));
        assertTrue(session.contains("GlassEdgeEffects.current(GlassEdgeEffects.ICON)"));
        assertTrue(session.contains("GlassEdgeEffects.current(GlassEdgeEffects.WIDGET)"));
        assertTrue(session.contains("GlassEdgeEffects.current(GlassEdgeEffects.FOLDER)"));
    }

    @Test public void nativeShadowSuppressionRequiresActiveGlassAndCanRestore() throws Exception {
        String bridge=read("DockNativeShadowBridge.java");
        String hook=read("MiuixGlassHook.java");
        assertTrue(bridge.contains("DockShadowRuntimePolicy.shouldSuppressNativeForPrismal("));
        assertTrue(bridge.contains("GlassRuntimeState.isEnabled()"));
        assertTrue(bridge.contains("MiuixGlassHook.isZeroCopyActive()"));
        assertTrue(bridge.contains("MIUIX_TARGET_CLASS.equals(targetClass)"));
        assertTrue(bridge.contains("args[1] = Color.TRANSPARENT;"));
        assertTrue(bridge.contains("args[4] = 0f;"));
        assertTrue(hook.contains("DockShadowOwnership.refreshVendorDockShadow();"));
        assertTrue(hook.contains("Miuix307ZeroCopyRenderer.clear();"));
    }

    @Test public void workspaceAvoidsRepeatedConfigLookupAndInvisibleNodeDraws() throws Exception {
        String session=read("LauncherGlassSession.java");
        int begin=session.indexOf("private void renderStaticScene(");
        int end=session.indexOf("private PrismalGeometry resolveStaticPrismalGeometry",begin);
        assertTrue(begin>=0 && end>begin);
        String render=session.substring(begin,end);
        int startLoop=render.indexOf("for (StaticNodeState state : snapshot)");
        assertTrue(startLoop>0);
        String frame=render.substring(0,startLoop);
        assertTrue(frame.contains("float density = root != null"));
        assertTrue(frame.contains("GlassEdgeEffects.current(GlassEdgeEffects.ICON)"));
        assertTrue(frame.contains("GlassEdgeEffects.current(GlassEdgeEffects.WIDGET)"));
        assertTrue(frame.contains("GlassEdgeEffects.current(GlassEdgeEffects.FOLDER)"));
        String loop=render.substring(startLoop);
        assertFalse(loop.contains("getDisplayMetrics()"));
        assertFalse(loop.contains("GlassEdgeEffects.current("));
        assertTrue(loop.contains("if (visibleAlpha <= 0f) continue;"));
        assertTrue(loop.contains("state.interaction, visibleAlpha"));
    }
}
