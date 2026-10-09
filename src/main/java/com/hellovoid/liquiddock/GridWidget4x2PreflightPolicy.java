package com.hellovoid.liquiddock;

/** Fail-closed policy for decreasing the HOME grid below four cells on an axis. */
public final class GridWidget4x2PreflightPolicy {
    private static final int DESKTOP_CONTAINER = -100;

    private GridWidget4x2PreflightPolicy() {}

    public static boolean needsCheck(int current, int target) {
        return target < 4 && target < current;
    }

    /** Rotation can transpose a saved 4x2 widget into 2x4. */
    public static boolean matches(int container, int spanX, int spanY) {
        return container == DESKTOP_CONTAINER
                && ((spanX == 4 && spanY == 2) || (spanX == 2 && spanY == 4));
    }
}
