package com.hellovoid.liquiddock;

/** One-shot desktop frame admission with a native-opening publication fence. */
final class FolderDesktopCaptureGate {
    private boolean sealed;
    private boolean committed;

    synchronized boolean mayPrepare() { return !sealed && !committed; }

    synchronized boolean commit(Runnable publish) {
        if (sealed || committed) return false;
        publish.run();
        committed = true;
        return true;
    }

    synchronized boolean seal() {
        sealed = true;
        return committed;
    }
}
