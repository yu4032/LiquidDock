package com.hellovoid.liquiddock;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Read-only Launcher 4.50 folder layout audit.
 *
 * The expanded folder's native content rectangle, not the separate glass
 * TextureView, clips its lowest app row on some tablet configurations.
 * Only a real OEM layout can establish whether this is measurement, spacing,
 * or a child clip. No animation, size or alpha is changed by this probe.
 */
final class ExpandedFolderGridProbe {
    private static final String TAG = "LiquidDock-Grid";
    private static boolean installed;

    private ExpandedFolderGridProbe() {}

    static void install(ClassLoader loader) {
        if (installed || loader == null) return;
        try {
            Class<?> cling = Class.forName("com.miui.home.launcher.FolderCling", false, loader);
            HookUtil.hook(HookUtil.findMethodExact(
                    cling, "onFolderOpenAnimationFinish", new Class<?>[0]), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (MainHook.debugLogging && chain.getThisObject() instanceof View) {
                    View owner = (View) chain.getThisObject();
                    owner.postOnAnimation(() -> snapshot(owner));
                }
                return result;
            });
            installed = true;
        } catch (Throwable error) {
            if (MainHook.debugLogging) Log.i(TAG, "hook unavailable: " + error);
        }
    }

    private static void snapshot(View cling) {
        if (!MainHook.debugLogging || cling == null || !cling.isAttachedToWindow()) return;
        View folder = asView(invoke(cling, "getFolder"));
        View grid = asView(invoke(folder, "getContent"));
        if (grid == null) {
            Log.i(TAG, "native getContent() unavailable cling=" + frame(cling)
                    + " folder=" + frame(folder));
            return;
        }

        // All reported rectangles are in actual screenshot/screen pixels.
        StringBuilder hierarchy = new StringBuilder();
        View cursor = grid;
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (i > 0) hierarchy.append(" <- ");
            hierarchy.append(frame(cursor));
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        Log.i(TAG, "layout path: " + hierarchy);
        Log.i(TAG, "content: " + frame(grid)
                + " folder=" + frame(folder)
                + " cling=" + frame(cling));

        ArrayList<View> icons = new ArrayList<>();
        ArrayList<TextView> labels = new ArrayList<>();
        ArrayDeque<View> pending = new ArrayDeque<>();
        pending.add(grid);
        int inspected = 0;
        while (!pending.isEmpty() && inspected++ < 240) {
            View v = pending.removeFirst();
            String name = v.getClass().getSimpleName();
            if (name.contains("ShortcutIcon") || name.contains("ProgressShortcutIcon")) {
                icons.add(v);
            }
            if (v instanceof TextView && ((TextView) v).getText().length() > 0) {
                labels.add((TextView) v);
            }
            if (v instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) v;
                for (int i = 0, n = parent.getChildCount(); i < n; i++) {
                    pending.addLast(parent.getChildAt(i));
                }
            }
        }
        icons.sort(Comparator.comparingInt(ExpandedFolderGridProbe::bottom).reversed());
        labels.sort(Comparator.comparingInt(ExpandedFolderGridProbe::bottom).reversed());
        Log.i(TAG, "node count=" + inspected + " icons=" + icons.size()
                + " labels=" + labels.size() + " gridBottom=" + bottom(grid)
                + " folderBottom=" + bottom(folder));
        for (int i = 0; i < Math.min(5, icons.size()); i++) {
            Log.i(TAG, "bottom icon " + i + ": " + frame(icons.get(i)));
        }
        for (int i = 0; i < Math.min(5, labels.size()); i++) {
            TextView label = labels.get(i);
            Log.i(TAG, "bottom title " + i + ": " + frame(label)
                    + " text=" + label.getText());
        }
    }

    private static int bottom(View v) {
        if (v == null) return Integer.MIN_VALUE;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return loc[1] + v.getHeight();
    }

    private static String frame(View view) {
        if (view == null) return "null";
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        String result = view.getClass().getSimpleName()
                + "[" + loc[0] + "," + loc[1] + " "
                + view.getWidth() + "x" + view.getHeight() + "]"
                + " measured=" + view.getMeasuredWidth() + "x" + view.getMeasuredHeight()
                + " visible=" + view.getVisibility()
                + " alpha=" + view.getAlpha();
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            result += " clip=" + g.getClipChildren() + "/" + g.getClipToPadding()
                    + " padding=" + g.getPaddingTop() + "/" + g.getPaddingBottom();
        }
        return result;
    }

    private static Object invoke(Object owner, String method) {
        if (owner == null) return null;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(owner, method);
        return result.succeeded() ? result.value() : null;
    }

    private static View asView(Object value) {
        return value instanceof View ? (View) value : null;
    }
}
