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
    private static int writerBudget;
    private static int noWordBudget;
    private static WeakReference<View> titleFocus = new WeakReference<>(null);

    private LargeFolderRenderProbe() {}

    static void installMenuProbe(ClassLoader loader) {
        if (!Api101Bridge.isDebugLoggingEnabled()) return;
        installVisibilityWriterTrace();
        installNoWordDrawTrace(loader);
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
        writerBudget = 12;
        noWordBudget = 8;
        Object title = HookUtil.tryInvoke(folder, "getTitleContainer").value();
        titleFocus = new WeakReference<>(title instanceof View ? (View) title : null);
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

    /**
     * TEMPORARY SOURCE TRACE: hook the actual Android setter only when the
     * Launcher diagnostics switch is enabled. All unrelated views fall
     * through without reflection, allocations or logging.
     */
    private static void installVisibilityWriterTrace() {
        try {
            HookUtil.hook(View.class.getDeclaredMethod("setVisibility", int.class), chain -> {
                Object owner = chain.getThisObject();
                View watched = titleFocus.get();
                if (watched == null || owner != watched || writerBudget <= 0) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                int requested = (Integer) chain.getArg(0);
                int before = watched.getVisibility();
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                int after = watched.getVisibility();
                if (before != after || requested != before) {
                    writerBudget--;
                    StringBuilder source = new StringBuilder();
                    StackTraceElement[] frames = Thread.currentThread().getStackTrace();
                    for (int i = 0, found = 0; i < frames.length && found < 8; i++) {
                        String name = frames[i].getClassName();
                        if (!name.startsWith("com.miui.home.")
                                && !name.startsWith("miuix.")
                                && !name.startsWith("com.hellovoid.liquiddock.")) continue;
                        if (source.length() > 0) source.append(" <- ");
                        source.append(frames[i].getClassName()).append(".")
                                .append(frames[i].getMethodName())
                                .append(":").append(frames[i].getLineNumber());
                        found++;
                    }
                    note("title/setVisibility " + before + " -> " + after
                            + " requested=" + requested + " source=" + source);
                }
                return result;
            });
            note("title visibility writer hook installed");
        } catch (Throwable e) {
            note("title visibility writer hook unavailable: " + e.getClass().getSimpleName());
        }
    }

    private static void installNoWordDrawTrace(ClassLoader loader) {
        try {
            Class<?> adapter = Class.forName(
                    "com.miui.home.launcher.util.noword.NoWordLauncherElementAdapter",
                    false, loader);
            HookUtil.hook(adapter.getDeclaredMethod("preDrawChild", View.class), chain -> {
                View child = chain.getArg(0) instanceof View ? (View) chain.getArg(0) : null;
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (child != null && child == titleFocus.get() && noWordBudget > 0) {
                    noWordBudget--;
                    note("title/no-word preDrawChild=" + result + " vis="
                            + child.getVisibility());
                }
                return result;
            });
            note("no-word draw probe installed");
        } catch (Throwable e) {
            note("no-word draw probe unavailable: " + e.getClass().getSimpleName());
        }
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
