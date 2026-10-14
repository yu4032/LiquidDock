package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;

/**
 * TEMPORARY. Follow one real folder through OEM drop, root restoration,
 * drawing and shortcut-menu dismissal. No continuous workspace traversal.
 * Remove this file and its call sites after the next device trace.
 */
final class LargeFolderRenderProbe {
    private static WeakReference<View> focus = new WeakReference<>(null);
    private static int generation;
    private static int drawBudget;

    private LargeFolderRenderProbe() {}

    static void installMenuProbe(ClassLoader loader) {
        if (!Api101Bridge.isDebugLoggingEnabled()) return;
        try {
            Class<?> menu = Class.forName(
                    "com.miui.home.launcher.ShortcutMenuLayer", false, loader);
            Class<?> reason = Class.forName(
                    "com.miui.home.launcher.EditStateChangeReason", false, loader);
            HookUtil.hook(menu.getDeclaredMethod("hideShortcutMenu", reason), chain -> {
                View tracked = focus.get();
                if (tracked != null) snapshot("menu/hide-before", tracked);
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (tracked != null) {
                    snapshot("menu/hide-after", tracked);
                    scheduleFrames(tracked, 3);
                }
                return result;
            });
            note("menu listener installed");
        } catch (Throwable e) {
            note("menu listener unavailable: " + e.getClass().getSimpleName());
        }
    }

    static void drop(View folder) {
        if (!Api101Bridge.isDebugLoggingEnabled()) return;
        focus = new WeakReference<>(folder);
        generation++;
        drawBudget = 8;
        snapshot("drop/completed", folder);
        scheduleFrames(folder, 6);
    }

    static void restored(View folder, String stage) {
        if (focus.get() == folder) snapshot("root/" + stage, folder);
    }

    static void childDrawn(View folder, View child, Object result) {
        if (focus.get() != folder || drawBudget <= 0) return;
        Object title = HookUtil.tryInvoke(folder, "getTitleContainer").value();
        if (child != title) return;
        drawBudget--;
        snapshot("title/drawChild-result=" + String.valueOf(result), folder);
    }

    private static void scheduleFrames(View folder, int count) {
        if (folder == null || count <= 0) return;
        int token = generation;
        ViewParent parent = folder.getParent();
        View host = parent instanceof View ? (View) parent : folder;
        scheduleNext(host, folder, count, token);
    }

    private static void scheduleNext(View host, View folder, int frames, int token) {
        if (!host.isAttachedToWindow()) return;
        host.postOnAnimation(() -> {
            if (token != generation) return;
            snapshot("pre-draw-frame/" + frames, folder);
            if (frames > 1) scheduleNext(host, folder, frames - 1, token);
        });
    }

    private static void snapshot(String stage, View folder) {
        if (!Api101Bridge.isDebugLoggingEnabled()) return;
        try {
            Object title = HookUtil.tryInvoke(folder, "getTitleContainer").value();
            Object text = HookUtil.tryInvoke(folder, "getTitleView").value();
            ViewParent parent = folder.getParent();
            String parentClass = parent == null ? "null" : parent.getClass().getSimpleName();
            String parentClip = parent instanceof ViewGroup
                    ? (((ViewGroup) parent).getClipChildren() + "/"
                    + ((ViewGroup) parent).getClipToPadding()) : "n/a";
            note(stage + " folder=" + Integer.toHexString(System.identityHashCode(folder))
                    + " root=" + viewState(folder)
                    + " parent=" + parentClass + " clipChildren/padding=" + parentClip
                    + " title=" + viewState(title)
                    + " titleParent=" + (title instanceof View
                    && ((View) title).getParent() != null
                    ? ((View) title).getParent().getClass().getSimpleName() : "null")
                    + " text=" + viewState(text)
                    + " thumbnail=" + bool(folder, "isDrawingInThumbnailView")
                    + " preview=" + bool(folder, "isDrawingInScreenPreview")
                    + " nativeHidden=" + bool(folder, "getIsHideTitle")
                    + " modelHidden=" + bool(folder.getTag(), "needHideTitleByFlags"));
        } catch (Throwable e) {
            note(stage + " snapshot failed: " + e.getClass().getSimpleName());
        }
    }

    private static String bool(Object owner, String name) {
        if (owner == null) return "n/a";
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(owner, name);
        return result.succeeded() ? String.valueOf(result.value()) : "unsupported";
    }

    private static String viewState(Object o) {
        if (!(o instanceof View)) return "missing";
        View v = (View) o;
        Rect visible = new Rect();
        boolean intersect = v.getGlobalVisibleRect(visible);
        return v.getClass().getSimpleName() + "{visibility=" + v.getVisibility()
                + ",shown=" + v.isShown() + ",attached=" + v.isAttachedToWindow()
                + ",alpha=" + v.getAlpha()
                + ",bounds=" + v.getLeft() + "," + v.getTop() + "-"
                + v.getRight() + "," + v.getBottom()
                + ",translation=" + v.getTranslationX() + "," + v.getTranslationY()
                + ",globalVisible=" + intersect
                + ",visibleRect=" + visible.toShortString() + "}";
    }

    private static void note(String msg) {
        if (Api101Bridge.isDebugLoggingEnabled()) {
            Api101Bridge.log("[DC][FolderRender] " + msg);
        }
    }
}
