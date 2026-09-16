package com.hellovoid.liquiddock;

/** Typed authority for preferred Gboard glass output and fail-closed fallback. */
final class GboardFloatingOutputSelection {
    enum Mode { SURFACE_CONTROL, TEXTURE_VIEW, STOCK }

    private Mode mode = Mode.STOCK;

    synchronized Mode onSurfaceControlReady() {
        mode = Mode.SURFACE_CONTROL;
        return mode;
    }

    synchronized Mode onSurfaceControlFailed(boolean textureViewAvailable) {
        mode = textureViewAvailable ? Mode.TEXTURE_VIEW : Mode.STOCK;
        return mode;
    }

    synchronized Mode mode() {
        return mode;
    }
}
