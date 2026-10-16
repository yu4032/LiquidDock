package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenFolderGlassStateTest {
    @Test public void firstGlassFrameArrivingDuringRecentsRemainsReplayable() {
        OpenFolderGlassState state = new OpenFolderGlassState();
        long serial = state.prepare();
        assertTrue(state.beginOpen(serial));
        state.onRecentsShown(serial);
        assertTrue(state.onBackdropReady(serial));
        assertTrue("first frame can commit while Recents owns presentation",
                state.onPresented(serial));
        assertTrue("cached lens remains valid while temporarily covered",
                state.maySuppressNative());
        state.onRecentsHidden(serial);
        assertTrue(state.shouldReplayAfterRecents(serial, true));
        assertTrue(state.completeRecentsReturn(serial));
        assertTrue("native close still owns final release",
                state.beginClose(serial));
        assertTrue(state.finishClose(serial));
        assertFalse(state.shouldReplayAfterRecents(serial, true));
    }

    @Test public void recentsCannotDestroyOpenFolderOrReviveStaleOwnership() {
        OpenFolderGlassState state = new OpenFolderGlassState();
        long serial = state.prepare();
        state.beginOpen(serial);
        state.onBackdropReady(serial);
        state.onPresented(serial);
        state.onRecentsShown(serial);
        assertTrue("entering Recents must not invalidate an already-presented lens",
                state.maySuppressNative());
        assertFalse(state.shouldReplayAfterRecents(serial, true));
        assertTrue(state.isRecentsCovered(serial));
        assertFalse(state.isRecentsReturnPending(serial));
        assertTrue(state.keepHiddenFolder(serial, false));
        assertFalse(state.completeRecentsReturn(serial));
        state.onRecentsHidden(serial);
        assertFalse(state.isRecentsCovered(serial));
        assertTrue(state.isRecentsReturnPending(serial));
        assertTrue("temporary non-identity HOME transform cannot destroy glass",
                state.deferTransientRecentsTransform(serial));
        assertTrue("cached glass may restore before FolderCling becomes VISIBLE",
                state.shouldReplayAfterRecents(serial, true));
        assertFalse("native actual close forbids replay",
                state.shouldReplayAfterRecents(serial, false));
        assertTrue("temporarily hidden native folder remains valid",
                state.keepHiddenFolder(serial, true));
        assertFalse("truly closed native folder is not retained",
                state.keepHiddenFolder(serial, false));
        assertTrue(state.completeRecentsReturn(serial));
        assertFalse(state.deferTransientRecentsTransform(serial));
        assertFalse(state.isRecentsReturnPending(serial));
        assertFalse(state.shouldReplayAfterRecents(serial, true));
        assertFalse(state.completeRecentsReturn(serial));
        assertFalse(state.keepHiddenFolder(serial, true));

        state.onRecentsShown(serial);
        long replacement = state.prepare();
        state.onRecentsHidden(serial);
        assertFalse(state.shouldReplayAfterRecents(serial, true));
        assertFalse(state.keepHiddenFolder(serial, true));
        assertFalse(state.isRecentsCovered(replacement));
        assertFalse(state.deferTransientRecentsTransform(replacement));
        assertTrue(state.owns(replacement));
    }

    @Test public void capturedCloseCompletionCannotReleaseTheNextOpeningOfTheSameFolder() throws Exception {
        OpenFolderGlassState state = new OpenFolderGlassState();
        long old = state.prepare();
        state.onBackdropReady(old); state.beginOpen(old); state.onPresented(old); state.beginClose(old);
        Runnable close = (Runnable) state.getClass().getDeclaredMethod("guardCompletion", long.class, Runnable.class)
                .invoke(state, old, (Runnable) state::release);
        long current = state.prepare();
        state.onBackdropReady(current); state.beginOpen(current); state.onPresented(current);
        close.run();
        assertTrue(state.owns(current));
        assertTrue(state.maySuppressNative());
    }
    @Test public void nativeMaterialStaysUntilSafeBackdropAndFirstGlassFrame() throws Exception {
        Object state = state();
        long serial = (Long) call(state, "prepare");
        assertFalse((Boolean) call(state, "maySuppressNative"));
        assertTrue((Boolean) call(state, "onBackdropReady", serial));
        assertTrue((Boolean) call(state, "beginOpen", serial));
        assertTrue("native material cannot flash before the first glass frame",
                (Boolean) call(state, "maySuppressNative"));
        assertTrue((Boolean) call(state, "onPresented", serial));
        assertTrue((Boolean) call(state, "maySuppressNative"));
        assertTrue((Boolean) call(state, "beginClose", serial));
        assertTrue((Boolean) call(state, "maySuppressNative"));
        assertTrue((Boolean) call(state, "finishClose", serial));
        assertFalse((Boolean) call(state, "maySuppressNative"));
    }

    @Test public void wallpaperOnlyCaptureMayFinishAfterNativeOpeningStarted() throws Exception {
        Object state = state();
        long serial = (Long) call(state, "prepare");
        assertTrue((Boolean) call(state, "beginOpen", serial));
        assertFalse((Boolean) call(state, "onPresented", serial));
        assertTrue("opening suppresses native before async capture",
                (Boolean) call(state, "maySuppressNative"));
        assertTrue((Boolean) call(state, "onBackdropReady", serial));
        assertTrue((Boolean) call(state, "maySuppressNative"));
        assertTrue((Boolean) call(state, "onPresented", serial));
        assertTrue((Boolean) call(state, "maySuppressNative"));
    }

    @Test public void tooLateWallpaperFrameMustNotAppearDuringClose() throws Exception {
        Object state = state();
        long serial = (Long) call(state, "prepare");
        assertTrue((Boolean) call(state, "beginOpen", serial));
        assertTrue((Boolean) call(state, "beginClose", serial));
        assertTrue((Boolean) call(state, "onBackdropReady", serial));
        assertFalse((Boolean) call(state, "onPresented", serial));
        assertTrue("an in-flight native close retains the material suppression",
                (Boolean) call(state, "maySuppressNative"));
        assertTrue((Boolean) call(state, "finishClose", serial));
    }

    @Test public void callbacksFromPreviousFolderCannotHideOrReleaseTheCurrentOne() throws Exception {
        Object state = state();
        long old = (Long) call(state, "prepare");
        long current = (Long) call(state, "prepare");
        assertNotEquals(old, current);
        assertFalse((Boolean) call(state, "onBackdropReady", old));
        assertFalse((Boolean) call(state, "onPresented", old));
        assertFalse((Boolean) call(state, "finishClose", old));
        assertTrue((Boolean) call(state, "owns", current));
        assertTrue((Boolean) call(state, "onBackdropReady", current));
        assertTrue((Boolean) call(state, "beginOpen", current));
        assertTrue((Boolean) call(state, "onPresented", current));
        assertFalse((Boolean) call(state, "beginClose", old));
        assertTrue((Boolean) call(state, "maySuppressNative"));
    }

    @Test public void cancellationImmediateCloseAndReplacementReleaseTheirPresentation() throws Exception {
        Object state = state();
        long serial = (Long) call(state, "prepare");
        assertTrue((Boolean) call(state, "finishClose", serial));
        assertFalse((Boolean) call(state, "finishClose", serial));
        serial = (Long) call(state, "prepare");
        call(state, "onBackdropReady", serial);
        call(state, "beginOpen", serial);
        call(state, "onPresented", serial);
        call(state, "beginClose", serial);
        long replacement = (Long) call(state, "prepare");
        assertFalse((Boolean) call(state, "maySuppressNative"));
        call(state, "release");
        assertFalse((Boolean) call(state, "owns", replacement));
        assertFalse((Boolean) call(state, "onPresented", replacement));
    }

    private static Object state() throws Exception {
        try {
            return Class.forName("com.hellovoid.liquiddock.OpenFolderGlassState")
                    .getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing open-folder lifecycle ownership", missing);
        }
    }

    private static Object call(Object state, String name, long... serial) throws Exception {
        return serial.length == 0 ? state.getClass().getDeclaredMethod(name).invoke(state)
                : state.getClass().getDeclaredMethod(name, long.class).invoke(state, serial[0]);
    }
}
