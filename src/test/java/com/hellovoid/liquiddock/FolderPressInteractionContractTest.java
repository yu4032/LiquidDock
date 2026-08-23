package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts FolderIcon press input without consuming MIUI's own touch/drag behavior. */
public class FolderPressInteractionContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test
    public void folderPressUsesFolderIconsOwnDispatchAndPreservesSystemTouchHandling()
            throws Exception {
        String hook = read("MiuixFolderGlassHook.java");

        assertTrue("FolderIcon's own dispatchTouchEvent is sufficient on this HyperOS build",
                hook.contains("folderIcon.getDeclaredMethod(\"dispatchTouchEvent\", MotionEvent.class)"));
        assertTrue("the original Launcher touch path must run before LiquidDock observes pressed state",
                hook.indexOf("chain.proceed") >= 0
                        && hook.indexOf("updateFolderPressAfterDispatch") > hook.indexOf("chain.proceed"));
        assertTrue("pressed state must come from the real FolderIcon after dispatch",
                hook.contains("owner.isPressed()"));
        assertFalse("do not install a global View dispatch hook when FolderIcon input already works",
                hook.contains("View.class.getDeclaredMethod(\"dispatchTouchEvent\", MotionEvent.class)"));
        assertFalse("do not install an OnTouchListener that could consume click/long-press/drag",
                hook.contains("setOnTouchListener"));
        assertFalse("do not resurrect MIUI's intentionally disabled FolderIcon Folme scaling",
                hook.contains("folmeDown") || hook.contains("folmeUp"));
    }

    @Test
    public void claimedFolderMaterialStaysHiddenWithoutRewritingSystemDrawables() throws Exception {
        String hook = read("MiuixFolderGlassHook.java");

        assertFalse("vendor drag/refresh must retain its concrete Drawable types",
                hook.contains("ImageView.class.getDeclaredMethod(\"setImageDrawable\", Drawable.class)"));
        assertTrue("claimed ImageView material must be hidden independently of drawable replacement",
                hook.contains("image.setImageAlpha(0)"));
        assertTrue("runtime disable must restore the pre-claim image alpha",
                hook.contains("ORIGINAL_IMAGE_ALPHA") && hook.contains("setImageAlpha(originalAlpha)"));
        assertFalse("do not hide the View itself because shared glass geometry tracks View alpha",
                hook.contains("image.setAlpha(0") || hook.contains("material.setAlpha(0"));
    }

    @Test
    public void touchLocationDrivesOnlyTheOwningFolderAndLifecycleEdgesResetIt() throws Exception {
        String hook = read("MiuixFolderGlassHook.java");
        String sink = read("LauncherGlassStaticNode.java");

        assertTrue("raw touch coordinates must be mapped to the material glass bounds",
                hook.contains("event.getRawX()") && hook.contains("event.getRawY()")
                        && hook.contains("material.getLocationOnScreen"));
        assertTrue("the owner-resolved sink alone receives the press update",
                hook.contains("resolveOwnerSink(owner)") && hook.contains("sink.setPressInteraction"));
        assertTrue("FolderIcon open must immediately clear press state before/while suppressing glass",
                hook.contains("sink.resetPressInteraction(false)"));
        assertTrue("static node detach must immediately clear stale press state",
                sink.contains("resetPressInteraction(false)")
                        && sink.contains("onViewDetachedFromWindow"));
        assertTrue("press transitions should be animated rather than snapping during normal touch",
                sink.contains("ValueAnimator") && sink.contains("PRESS_IN_DURATION_MS")
                        && sink.contains("PRESS_OUT_DURATION_MS"));
    }

    @Test
    public void launcherSessionStoresInteractionPerNodeNotGlobally() throws Exception {
        String session = read("LauncherGlassSession.java");

        assertTrue("each launcher glass node must own its interaction state",
                session.contains("volatile PrismalInteractionState interaction"));
        assertTrue("sink updates must resolve that sink's NodeState",
                session.contains("updateInteraction(LauncherGlassSinkView sink")
                        && session.contains("NodeState node = nodes.get(sink)"));
        assertTrue("each draw must pass the node-specific interaction state",
                session.contains("node.interaction"));
        assertFalse("interaction must not be a session-global mutable field",
                session.contains("private volatile PrismalInteractionState interaction"));
    }
}
