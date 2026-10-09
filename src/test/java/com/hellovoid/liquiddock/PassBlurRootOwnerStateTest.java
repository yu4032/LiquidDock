package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class PassBlurRootOwnerStateTest {
    @Test public void recentsAndWorkspaceCanReclaimOneRootWithoutStaleUnbind() {
        PassBlurRootOwnerState state = new PassBlurRootOwnerState();
        Object workspace = new Object();
        Object recents = new Object();

        state.claim(33257, workspace);
        assertTrue(state.isOwner(33257, workspace));
        state.claim(33257, recents);
        assertSame(recents, state.owner(33257));
        assertFalse(state.release(33257, workspace));

        state.claim(33257, workspace);
        assertFalse(state.release(33257, recents));
        assertSame(workspace, state.owner(33257));
        assertTrue(state.release(33257, workspace));
        assertNull(state.owner(33257));
    }

    @Test public void distinctRootLayersAndUnknownIdsStayIsolated() {
        PassBlurRootOwnerState state = new PassBlurRootOwnerState();
        Object first = new Object();
        Object second = new Object();
        state.claim(5, first);
        state.claim(6, second);
        state.claim(-1, first);
        assertNull(state.owner(-1));
        assertSame(first, state.owner(5));
        assertSame(second, state.owner(6));
        assertTrue(state.release(5, first));
        assertSame(second, state.owner(6));
    }

    @Test public void bridgeRestoresProducerAndExclusionsBeforeEnablingUpdates()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java"));
        assertTrue(source.contains("LAUNCHER_ROOT_OWNER.claim"));
        assertTrue(source.contains("currentOwner != binding"));
        assertTrue(source.contains("binding.setPassBlurSurface.invoke("));
        assertTrue(source.contains("(Object) binding.exclusions"));
        assertTrue(source.contains("binding.setUpdateTextureFlag.invoke("));
        assertTrue(source.contains("inactive launcher root producer retired"));
        assertFalse(source.contains("PixelCopy"));
        assertFalse(source.contains("ScreenCapture"));
        assertFalse(source.contains("glReadPixels"));
    }
}
