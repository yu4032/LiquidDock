package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalHighlightProfile;

final class LauncherHighlightProfilePolicy {
    private LauncherHighlightProfilePolicy() {}

    static PrismalHighlightProfile select(LauncherGlassNodeKind kind,
            PrismalHighlightProfile compact, PrismalHighlightProfile large) {
        return kind == LauncherGlassNodeKind.WIDGET || kind == LauncherGlassNodeKind.LARGE_FOLDER
                ? large : compact;
    }
}
