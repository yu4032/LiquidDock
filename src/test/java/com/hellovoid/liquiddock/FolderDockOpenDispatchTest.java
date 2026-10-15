package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class FolderDockOpenDispatchTest {
    @Test public void normalNativeOpenIsNotSuppressed() {
        FolderDockOpenDispatch relay = new FolderDockOpenDispatch();
        assertFalse(relay.consumeLateNativeOpen());
        assertFalse(relay.hasPendingNativeOpen());
    }

    @Test public void earlyOpenConsumesExactlyOneLaterVendorEvent() {
        FolderDockOpenDispatch relay = new FolderDockOpenDispatch();
        relay.onEarlyDispatched();
        assertTrue(relay.hasPendingNativeOpen());
        assertTrue(relay.consumeLateNativeOpen());
        assertFalse(relay.hasPendingNativeOpen());
        assertFalse(relay.consumeLateNativeOpen());
    }

    @Test public void closeDiscardsPendingOpenWithoutSuppressingNextFolder() {
        FolderDockOpenDispatch relay = new FolderDockOpenDispatch();
        relay.onEarlyDispatched();
        relay.onClose();
        assertFalse(relay.consumeLateNativeOpen());
        relay.onEarlyDispatched();
        assertTrue(relay.consumeLateNativeOpen());
    }
}
