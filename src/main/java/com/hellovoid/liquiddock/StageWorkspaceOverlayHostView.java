package com.hellovoid.liquiddock;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Top-level structural host for Stage content. Rendering is intentionally empty for now. */
final class StageWorkspaceOverlayHostView extends FrameLayout {
    StageWorkspaceOverlayHostView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
}
