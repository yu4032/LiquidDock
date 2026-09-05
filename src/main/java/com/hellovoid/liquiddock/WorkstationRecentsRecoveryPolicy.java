package com.hellovoid.liquiddock;

/**
 * Pure authority/episode state for Workstation HOME -> Recents -> HOME producer recovery.
 * Vendor callback arrival is not authority by itself: a hide is consumable only while the
 * authoritative Launcher scene still reports Recents covered and no episode already owns it.
 */
final class WorkstationRecentsRecoveryPolicy {
    enum Phase { IDLE, COVERED, RECOVERING, FAILED_CLOSED }

    static final class Decision {
        final boolean authoritative;
        final boolean requestRollover;
        final boolean allowUncover;
        final long episode;

        Decision(boolean authoritative, boolean requestRollover,
                 boolean allowUncover, long episode) {
            this.authoritative = authoritative;
            this.requestRollover = requestRollover;
            this.allowUncover = allowUncover;
            this.episode = episode;
        }
    }

    static final class TerminalDecision {
        final boolean matched;
        final boolean allowUncover;
        final boolean failClosed;

        TerminalDecision(boolean matched, boolean allowUncover, boolean failClosed) {
            this.matched = matched;
            this.allowUncover = allowUncover;
            this.failClosed = failClosed;
        }
    }

    private Phase phase = Phase.IDLE;
    private long nextEpisode;
    private long activeEpisode = -1L;

    synchronized void onRecentViewShow() {
        phase = Phase.COVERED;
        activeEpisode = -1L;
    }

    synchronized Decision onRecentViewHide(boolean workstationMode, boolean recentsCovered) {
        if (!recentsCovered || phase == Phase.RECOVERING || phase == Phase.FAILED_CLOSED) {
            return new Decision(false, false, false, activeEpisode);
        }

        // The SceneController's vendorRecentsCovered flag is the content authority. Accept the
        // first covered hide even if this policy missed an earlier show during hook bootstrap.
        if (!workstationMode) {
            phase = Phase.IDLE;
            activeEpisode = -1L;
            return new Decision(true, false, true, -1L);
        }

        long episode = ++nextEpisode;
        activeEpisode = episode;
        phase = Phase.RECOVERING;
        return new Decision(true, true, false, episode);
    }

    synchronized TerminalDecision onRecoveryTerminal(
            long episode, LauncherGlassProducerRecoveryState.Result result) {
        if (phase != Phase.RECOVERING || episode <= 0L || episode != activeEpisode
                || result == null) {
            return new TerminalDecision(false, false, phase == Phase.FAILED_CLOSED);
        }

        activeEpisode = -1L;
        if (result == LauncherGlassProducerRecoveryState.Result.ACCEPTED) {
            phase = Phase.IDLE;
            return new TerminalDecision(true, true, false);
        }

        phase = Phase.FAILED_CLOSED;
        return new TerminalDecision(true, false, true);
    }

    synchronized Phase phase() { return phase; }
    synchronized long activeEpisode() { return activeEpisode; }
}
