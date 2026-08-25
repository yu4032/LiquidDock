package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for live divider ownership release. */
public class DividerRuntimeOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void dividerCapturesExactVendorStateBeforeFirstMutation() throws Exception {
        String source = Files.readString(MAIN.resolve("DockDividerHook.java"));

        assertTrue(source.contains("WeakHashMap<View, DividerSnapshot>"));
        assertTrue(source.contains("line.getBackground()"));
        assertTrue(source.contains("lp.width"));
        assertTrue(source.contains("lp.height"));
        assertTrue(source.contains("lp.leftMargin"));
        assertTrue(source.contains("lp.topMargin"));
        assertTrue(source.contains("lp.rightMargin"));
        assertTrue(source.contains("lp.bottomMargin"));

        int snapshot = source.indexOf("captureOriginalState(line)");
        int mutate = source.indexOf("lp.width =", snapshot);
        assertTrue("vendor state must be captured before LiquidDock mutates layout", snapshot >= 0 && mutate > snapshot);
    }

    @Test
    public void backgroundSnapshotDoesNotAliasMutableVendorDrawable() throws Exception {
        String source = Files.readString(MAIN.resolve("DockDividerHook.java"));

        assertTrue("setBackgroundColor may mutate an existing ColorDrawable in place; snapshot must clone it",
                source.contains("cloneBackgroundDrawable("));
        assertTrue("prefer Drawable.ConstantState for an independent restore copy",
                source.contains("getConstantState()"));
        assertTrue(source.contains("newDrawable(line.getResources())"));
    }

    @Test
    public void disablingDividerRestoresLayoutBackgroundAndCancelsDeferredWork() throws Exception {
        String source = Files.readString(MAIN.resolve("DockDividerHook.java"));

        assertTrue(source.contains("static void onRuntimeDividerDisabled()"));
        assertTrue(source.contains("releaseDivider(line)"));
        assertTrue(source.contains("line.setBackground(snapshot.background)"));
        assertTrue(source.contains("lp.width = snapshot.width"));
        assertTrue(source.contains("lp.height = snapshot.height"));
        assertTrue(source.contains("lp.leftMargin = snapshot.leftMargin"));
        assertTrue(source.contains("lp.topMargin = snapshot.topMargin"));
        assertTrue(source.contains("lp.rightMargin = snapshot.rightMargin"));
        assertTrue(source.contains("lp.bottomMargin = snapshot.bottomMargin"));
        assertTrue(source.contains("removeOnLayoutChangeListener"));
        assertTrue(source.contains("pendingGeometry.clear()"));
    }

    @Test
    public void bindAndDeferredCallbacksBecomeInertWhenLiveDisabled() throws Exception {
        String source = Files.readString(MAIN.resolve("DockDividerHook.java"));

        assertTrue(source.contains("VisualRuntimeState.isDividerEnabled()"));
        assertTrue(source.contains("if (!VisualRuntimeState.isDividerEnabled())"));

        String deferred = methodSlice(source,
                "private static void finishDeferredGeometry(",
                "static void onRuntimeDividerDisabled()");
        assertTrue("queued layout callback must release instead of reapplying after disable",
                deferred.contains("if (!VisualRuntimeState.isDividerEnabled())"));
        assertTrue(deferred.contains("releaseDivider(line)"));
    }

    private static String methodSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + 1);
        if (start < 0 || end < 0 || end <= start) return "";
        return source.substring(start, end);
    }
}
