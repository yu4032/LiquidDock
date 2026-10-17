package com.hellovoid.liquiddock;

/** Complete a native folder close only when both independent animations end. */
final class FolderCloseCompletionGate {
    private long serial;
    private boolean active, contentDone, backdropDone;

    void begin(long serial, boolean hasBackdropSpring) {
        this.serial = serial;
        active = true;
        contentDone = false;
        backdropDone = !hasBackdropSpring;
    }

    boolean isActive() { return active; }

    boolean contentFinished(long serial) {
        if (!active || this.serial != serial) return false;
        contentDone = true;
        return finishIfReady();
    }

    boolean backdropFinished(long serial) {
        if (!active || this.serial != serial) return false;
        backdropDone = true;
        return finishIfReady();
    }

    void cancel() { active = false; }

    private boolean finishIfReady() {
        if (!contentDone || !backdropDone) return false;
        active = false;
        return true;
    }
}
