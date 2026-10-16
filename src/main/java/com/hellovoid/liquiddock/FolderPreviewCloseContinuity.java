package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;

/**
 * Native folder preview and its spring proxy have independent alpha owners.
 * During the terminal handoff, the source PreviewContainer can temporarily
 * become transparent for one frame while the Launcher Workspace itself is
 * fully visible. Keep only the source miniature opaque, allowing its parent
 * Workspace to own the real icon reveal animation.
 *
 * No changes to children, drawables, backgrounds, transforms, or OEM
 * FolderAnimController timing. Fail closed if the native preview type differs.
 */
final class FolderPreviewCloseContinuity {
    private static final String PREVIEW_SMALL =
            "com.miui.home.launcher.folder.FolderIconPreviewContainer1X1";
    private static final String PREVIEW_FOUR =
            "com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_4";
    private static final String PREVIEW_NINE =
            "com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_9";

    private static WeakReference<View> ownerRef = new WeakReference<>(null);
    private static WeakReference<View> rootRef = new WeakReference<>(null);
    private static WeakReference<View> previewRef = new WeakReference<>(null);
    private static ViewTreeObserver observer;
    private static ViewTreeObserver.OnPreDrawListener listener;
    private static long ownerSerial;
    private static boolean terminalRelease;
    private static int releaseDraws;
    private static int correctedFrames;

    private FolderPreviewCloseContinuity() {}

    static void begin(ViewGroup owner, long serial) {
        if (owner == null || serial == 0L) return;
        if (ownerRef.get() == owner && ownerSerial == serial && listener != null) return;
        cancel();
        View preview = findPreview(owner);
        View root = owner.getRootView();
        if (preview == null || root == null || !root.isAttachedToWindow()) {
            MainHook.diag("[FolderTrace] preview continuity unavailable type="
                    + owner.getClass().getName() + " serial=" + serial);
            return;
        }
        ViewTreeObserver watch = root.getViewTreeObserver();
        if (!watch.isAlive()) return;
        ownerRef = new WeakReference<>(owner);
        rootRef = new WeakReference<>(root);
        previewRef = new WeakReference<>(preview);
        ownerSerial = serial;
        terminalRelease = false;
        correctedFrames = 0;
        listener = () -> {
            View liveRoot = rootRef.get();
            View liveOwner = ownerRef.get();
            View livePreview = previewRef.get();
            if (liveRoot == null || liveOwner == null || livePreview == null
                    || !liveRoot.isAttachedToWindow() || !liveOwner.isAttachedToWindow()
                    || !livePreview.isAttachedToWindow()
                    || !LauncherGlassHierarchy.isWorkspace(liveOwner)) {
                cancel();
                return true;
            }
            boolean nativeWorkspaceVisible = LauncherGlassVisibility.isVisible(liveOwner, liveRoot);
            if (FolderPreviewClosePolicy.shouldRestore(nativeWorkspaceVisible,
                    livePreview.getVisibility() == View.VISIBLE, livePreview.getAlpha())) {
                livePreview.setAlpha(1f);
                if (correctedFrames++ < 4) {
                    MainHook.diag("[FolderTrace] source preview alpha recovered serial="
                            + ownerSerial + " preview=" + livePreview.getClass().getSimpleName());
                }
            }
            if (terminalRelease && --releaseDraws <= 0) cancel();
            return true;
        };
        observer = watch;
        watch.addOnPreDrawListener(listener);
        MainHook.diag("[FolderTrace] native preview continuity armed type="
                + preview.getClass().getSimpleName() + " serial=" + serial);
    }

    static void releaseAfterTwoDraws() {
        if (listener == null) return;
        terminalRelease = true;
        releaseDraws = 2;
    }

    static void cancel() {
        ViewTreeObserver old = observer;
        ViewTreeObserver.OnPreDrawListener oldListener = listener;
        observer = null;
        listener = null;
        if (old != null && oldListener != null && old.isAlive()) {
            old.removeOnPreDrawListener(oldListener);
        }
        ownerRef = new WeakReference<>(null);
        previewRef = new WeakReference<>(null);
        rootRef = new WeakReference<>(null);
        ownerSerial = 0L;
        terminalRelease = false;
        releaseDraws = 0;
    }

    private static View findPreview(View root) {
        if (root == null) return null;
        String type = root.getClass().getName();
        if (PREVIEW_SMALL.equals(type) || PREVIEW_FOUR.equals(type)
                || PREVIEW_NINE.equals(type)) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0, count = group.getChildCount(); i < count; i++) {
            View match = findPreview(group.getChildAt(i));
            if (match != null) return match;
        }
        return null;
    }
}
