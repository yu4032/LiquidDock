package com.hellovoid.liquiddock;

/** Bounded, generation-aware recovery for a PassBlur binding that never emits its first frame. */
final class PassBlurFirstFrameRecoveryState {
    static final class Ticket {
        final long serial;
        final long generation;
        final boolean armed;

        private Ticket(long serial, long generation, boolean armed) {
            this.serial = serial;
            this.generation = generation;
            this.armed = armed;
        }
    }

    static final class Decision {
        final boolean rebind;
        final boolean terminalFailure;

        private Decision(boolean rebind, boolean terminalFailure) {
            this.rebind = rebind;
            this.terminalFailure = terminalFailure;
        }

        static Decision none() { return new Decision(false, false); }
        static Decision rebind() { return new Decision(true, false); }
        static Decision terminal() { return new Decision(false, true); }
    }

    private final int maxRebinds;
    private long generation = -1L;
    private long serial;
    private int rebinds;
    private boolean fresh;

    PassBlurFirstFrameRecoveryState(int maxRebinds) {
        this.maxRebinds = Math.max(0, maxRebinds);
    }

    synchronized Ticket arm(long nextGeneration) {
        if (nextGeneration < 0L || nextGeneration < generation) {
            return new Ticket(serial, nextGeneration, false);
        }
        if (nextGeneration != generation) {
            generation = nextGeneration;
            rebinds = 0;
            fresh = false;
        }
        if (fresh) return new Ticket(serial, nextGeneration, false);
        return new Ticket(++serial, generation, true);
    }

    synchronized void onFreshFrame(long frameGeneration) {
        if (frameGeneration != generation) return;
        fresh = true;
        serial++;
    }

    synchronized Decision onTimeout(Ticket ticket) {
        if (ticket == null || !ticket.armed || fresh
                || ticket.generation != generation || ticket.serial != serial) {
            return Decision.none();
        }
        serial++;
        if (rebinds >= maxRebinds) return Decision.terminal();
        rebinds++;
        return Decision.rebind();
    }

    synchronized void cancel() {
        fresh = true;
        serial++;
    }
}
