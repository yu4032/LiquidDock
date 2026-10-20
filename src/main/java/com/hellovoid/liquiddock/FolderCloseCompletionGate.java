package com.hellovoid.liquiddock;

/** Complete a folder close only when all participating animations end. */
final class FolderCloseCompletionGate {
    private long serial;
    private boolean active, contentDone, backdropDone, visualFadeDone;

    void begin(long serial, boolean hasBackdropSpring) {
        begin(serial, hasBackdropSpring, false);
    }

    void begin(long serial, boolean hasBackdropSpring, boolean hasVisualFade) {
        this.serial = serial;
        active = true;
        contentDone = false;
        backdropDone = !hasBackdropSpring;
        visualFadeDone = !hasVisualFade;
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

    boolean visualFadeFinished(long serial) {
        if (!active || this.serial != serial) return false;
        visualFadeDone = true;
        return finishIfReady();
    }

    void cancel() { active = false; }

    private boolean finishIfReady() {
        if (!contentDone || !backdropDone || !visualFadeDone) return false;
        active = false;
        return true;
    }
}
