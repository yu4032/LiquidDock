package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static API contract for the Dock mirror presentation-only hook. */
public class DockMirrorShortcutReflectionContractTest {
    @Test public void mirrorHideReclaimsGeometryWithoutRestoringSystemSwitchHack() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockMirrorShortcutHook.java"));

        assertTrue(source.contains("HotSeatsListContentAdapter"));
        assertTrue(source.contains("onBindViewHolder"));
        assertTrue(source.contains("View.INVISIBLE"));
        assertTrue(source.contains("rawParams.width = 0"));
        assertTrue(source.contains("setMinWidth"));
        assertTrue(source.contains("setMaxWidth"));
        assertTrue(source.contains("out.left = 0"));
        assertTrue(source.contains("out.right = 0"));
        assertTrue(source.contains("applyCenteringCompensation"));
        assertTrue(source.contains("mContentBackground"));

        assertTrue(source.contains("HookUtil.InvocationResult<Object> refresh"));
        assertTrue(source.contains("HookUtil.tryInvoke(adapter, \"notifyDataSetChanged\")"));
        assertTrue(source.contains("refresh.succeeded()"));
        assertTrue(source.contains("refresh.failure()"));

        // The old switch spoof removed adapter items globally and caused center-justify drift.
        assertFalse(source.contains("com.xiaomi.mirror.SystemSettingsUtils"));
        assertFalse(source.contains("pref_key_mirror_switch"));
        assertFalse(source.contains("onMirrorSeatUpdate"));
    }
}
