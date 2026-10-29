package com.hellovoid.liquiddock;

/** Selects exactly one backing material, irrespective of malformed imported profile keys. */
final class MaterialModePolicy {
    private MaterialModePolicy() {}

    static boolean liquidEnabled(boolean liquidRequested, boolean nativeRequested) {
        return liquidRequested && !nativeRequested;
    }
}
