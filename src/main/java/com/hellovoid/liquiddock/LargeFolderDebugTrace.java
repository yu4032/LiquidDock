package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * TEMPORARY PR #376 device diagnostics. Obeys the existing user debug
 * switch through Api101Bridge, as required by LoggingGateContractTest.
 * Strict per-category budgets; no folder titles, packages or app names logged.
 * Remove the entire file and its call sites after the diagnosis.
 */
final class LargeFolderDebugTrace {
    private static final Map<String, Integer> COUNTS = new HashMap<>();
    private static WeakReference<View> lastDropped = new WeakReference<>(null);
    private static final int MAX_PER_CATEGORY = 24;

    private LargeFolderDebugTrace() {}

    static synchronized void log(String category, String message) {
        if (!Api101Bridge.isDebugLoggingEnabled()) return;
        int n = COUNTS.containsKey(category) ? COUNTS.get(category) : 0;
        if (n >= MAX_PER_CATEGORY) return;
        COUNTS.put(category, n + 1);
        Api101Bridge.log("[DC][FolderDiag] " + category + " #" + (n + 1) + " " + message);
    }

    static String shortError(Throwable e) {
        if (e == null) return "null";
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + ":" +
                (msg == null ? "" : msg.substring(0, Math.min(100, msg.length())));
    }

    private static String describe(View v) {
        if (v == null) return "null";
        return v.getClass().getSimpleName() + "@" + Integer.toHexString(
                System.identityHashCode(v)) + "{vis=" + v.getVisibility()
                + ",alpha=" + v.getAlpha() + ",measured="
                + v.getMeasuredWidth() + "x" + v.getMeasuredHeight()
                + ",bounds=" + v.getWidth() + "x" + v.getHeight() + "}";
    }

    private static String invoke(Object target, String method) {
        if (target == null) return "n/a";
        HookUtil.InvocationResult<Object> r = HookUtil.tryInvoke(target, method);
        return r.succeeded() ? String.valueOf(r.value()) : "unsupported";
    }

    private static String fieldBool(View folder, String field) {
        ViewGroup.LayoutParams params = folder.getLayoutParams();
        if (params == null) return "noLP";
        try { return String.valueOf(HookUtil.getBooleanField(params, field)); }
        catch (Throwable ignored) { return "unsupported"; }
    }

    static void snapshot(String event, View folder) {
        if (folder == null) { log("title", event + " null folder"); return; }
        try {
            Object info = folder.getTag();
            ViewParent parent = folder.getParent();
            Object container = HookUtil.tryInvoke(folder, "getTitleContainer").value();
            Object text = HookUtil.tryInvoke(folder, "getTitleView").value();
            String type = "?";
            String span = "?";
            String marked = "?";
            String pref = "?";
            if (info != null) {
                try {
                    type = String.valueOf(HookUtil.getIntField(info, "itemType"));
                    span = HookUtil.getIntField(info, "spanX") + "x"
                            + HookUtil.getIntField(info, "spanY");
                    marked = String.valueOf(LauncherLargeFolderPickerHook.isMarked(
                            folder.getContext(), info));
                    pref = String.valueOf(LauncherLargeFolderPickerHook.shouldShowTitle(
                            folder.getContext(), info));
                } catch (Throwable ignored) {}
            }
            String channel = event.startsWith("restore/") ? "title-restore"
                    : event.startsWith("queue") ? "title-queue"
                    : event.startsWith("ItemIcon") ? "title-text-alpha"
                    : event.startsWith("shortcutMenu") ? "title-menu" : "title-drop";
            log(channel, event + " folder=" + describe(folder)
                    + " parent=" + (parent == null ? "null" : parent.getClass().getSimpleName())
                    + " attached=" + folder.isAttachedToWindow()
                    + " model=" + type + "/" + span
                    + " marked=" + marked + " titlePref=" + pref
                    + " dragging=" + fieldBool(folder, "isDragging")
                    + " modelHide=" + invoke(info, "needHideTitleByFlags")
                    + " hide=" + invoke(folder, "getIsHideTitle")
                    + " outline=" + invoke(folder, "isDrawOutline")
                    + " titleContainer=" + describe(container instanceof View ? (View) container : null)
                    + " titleText=" + describe(text instanceof View ? (View) text : null));
        } catch (Throwable e) {
            log("error", event + " snapshot " + shortError(e));
        }
    }

    static void drop(String event, View folder) {
        if (folder == null) { log("drop", event + " no-view"); return; }
        lastDropped = new WeakReference<>(folder);
        log("drop", event + " " + folder.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(folder)));
        snapshot(event, folder);
        material(event, folder);
        folder.postOnAnimation(() -> {
            snapshot(event + "/nextFrame", folder);
            material(event + "/nextFrame", folder);
        });
    }

    static void menu(String event) {
        View folder = lastDropped.get();
        if (folder == null) { log("menu", event + " no previous drop"); return; }
        log("menu", event);
        snapshot(event, folder);
        material(event, folder);
        folder.postOnAnimation(() -> snapshot(event + "/nextFrame", folder));
    }

    private static String drawable(Object value) {
        if (value == null) return "null";
        if (value instanceof View) {
            View v = (View) value;
            if (v instanceof ImageView) {
                return describe(v) + " drawable=" + drawable(((ImageView) v).getDrawable())
                        + " background=" + drawable(v.getBackground());
            }
            return describe(v) + " background=" + drawable(v.getBackground());
        }
        if (!(value instanceof Drawable)) return value.getClass().getSimpleName();
        Drawable d = (Drawable) value;
        String out = d.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(d))
                + " bounds=" + d.getBounds().toShortString();
        try {
            Object paint = HookUtil.getField(d, "mStrokePaint");
            if (paint instanceof android.graphics.Paint) {
                android.graphics.Paint p = (android.graphics.Paint) paint;
                out += " stroke={alpha=" + p.getAlpha() + ",color="
                        + Integer.toHexString(p.getColor()) + ",width="
                        + p.getStrokeWidth() + "}";
            }
        } catch (Throwable ignored) {}
        return out;
    }

    static void material(String event, View owner) {
        if (owner == null) return;
        StringBuilder result = new StringBuilder(event)
                .append(" owner=").append(describe(owner))
                .append(" rootBg=").append(drawable(owner.getBackground()));
        for (String field : new String[]{"mImageView", "mFolderCover",
                "mDefaultFolderBackground", "mFolderBackground"}) {
            try {
                result.append(" ").append(field).append("=")
                        .append(drawable(HookUtil.getField(owner, field)));
            } catch (Throwable ignored) {
                result.append(" ").append(field).append("=missing");
            }
        }
        String channel = event.startsWith("drawChild") ? "material-draw"
                : event.startsWith("toggle") ? "material-toggle"
                : event.startsWith("shortcutMenu") ? "material-menu" : "material-drop";
        log(channel, result.toString());
    }

    static void observeTrackedFolders(String event) {
        View v = lastDropped.get();
        if (v != null) {
            material(event, v);
            snapshot(event, v);
        }
    }
}
