package com.hellovoid.liquiddock;

import android.graphics.SurfaceTexture;
import android.os.Process;
import android.os.SystemClock;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Temporary PR #392 field diagnostics. Deliberately log lifecycle edges and
 * eight frame checkpoints, NOT every Choreographer frame or every GL frame.
 * This must remain usable when the GUI debug-log switch is disabled.
 */
final class FolderDiagnosticTrace {
    private static final String TAG = "[FolderTrace]";
    private static final int[] CHECKPOINTS = {0, 1, 2, 4, 8, 16, 32, 64};
    private static final Set<String> MISSING_FIELDS = new HashSet<>();
    private static WeakReference<Object> launcherRef = new WeakReference<>(null);
    private static int serial;
    private static long startedMs;

    private FolderDiagnosticTrace() {}

    static void processBoot(String stage, String config) {
        MainHook.diag(TAG + " BOOT stage=" + stage + " source=" + BuildConfig.DIAG_SOURCE_SHA
                + " ciRun=" + BuildConfig.DIAG_CI_RUN
                + " app=" + BuildConfig.APPLICATION_ID
                + " version=" + BuildConfig.VERSION_NAME + "(" + BuildConfig.VERSION_CODE + ")"
                + " pid=" + Process.myPid() + " process="
                + android.app.Application.getProcessName() + " " + config);
    }

    static void begin(Object launcher) {
        if (launcher != null) launcherRef = new WeakReference<>(launcher);
        serial++;
        startedMs = SystemClock.uptimeMillis();
        event("openFolder-enter");
        schedule("open");
    }

    static void event(String what) {
        MainHook.diag(TAG + " event=" + what + " serial=" + serial
                + " ms=" + (SystemClock.uptimeMillis() - startedMs)
                + " " + snapshot());
    }

    static void closeStarted() {
        event("FolderCling.close-begin");
        schedule("close");
    }

    static void finished() {
        event("folder-output-release");
    }

    private static void schedule(String phase) {
        Object launcher = launcherRef.get();
        View workspace = fieldView(launcher, "mWorkspace");
        View root = workspace != null ? workspace.getRootView() : null;
        if (root == null || !root.isAttachedToWindow()) {
            MainHook.diag(TAG + " no attached Workspace root to schedule " + phase);
            return;
        }
        final int session = serial;
        root.postOnAnimation(new Runnable() {
            int frame;
            int nextIndex;
            @Override public void run() {
                if (session != serial || !root.isAttachedToWindow()) return;
                if (frame == CHECKPOINTS[nextIndex]) {
                    MainHook.diag(TAG + " phase=" + phase + " frame=" + frame
                            + " serial=" + session + " ms="
                            + (SystemClock.uptimeMillis() - startedMs) + " " + snapshot());
                    nextIndex++;
                    if (nextIndex == CHECKPOINTS.length) return;
                }
                frame++;
                root.postOnAnimation(this);
            }
        });
    }

    private static String snapshot() {
        Object launcher = launcherRef.get();
        View workspace = fieldView(launcher, "mWorkspace");
        View search = fieldView(launcher, "mSearchBar");
        View content = fieldView(launcher, "mScreenContent");
        Object controller = field(launcher, "mDockController");
        View seats = fieldView(controller, "mHotSeatsView");
        View root = workspace != null ? workspace.getRootView() : null;
        LauncherGlassStaticLayer layer = root != null ? LauncherGlassStaticLayer.find(root) : null;
        return "launcher=" + identity(launcher)
                + " workspace=" + view(workspace)
                + " search=" + view(search)
                + " screenContent=" + view(content)
                + " hotSeats=" + view(seats)
                + " workspaceGlass=" + view(layer)
                + " workspaceST=" + texture(layer)
                + " dock=" + Miuix307ZeroCopyRenderer.diagnosticState()
                + " frozen=" + Miuix307ZeroCopyRenderer.isFolderFrozen()
                + " folderGlassFrozen=" + (root != null
                   && LauncherGlassSceneController.isFolderBackdropFrozenForRoot(root));
    }

    private static String texture(TextureView view) {
        if (view == null) return "null";
        SurfaceTexture texture = view.getSurfaceTexture();
        return texture == null ? "none" : "stamp=" + texture.getTimestamp();
    }

    private static String identity(Object obj) {
        return obj == null ? "null" : obj.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(obj));
    }

    private static String view(View v) {
        if (v == null) return "null";
        StringBuilder sb = new StringBuilder(identity(v));
        sb.append("{v=").append(v.getVisibility()).append(",a=").append(v.getAlpha())
                .append(",shown=").append(v.isShown())
                .append(",attached=").append(v.isAttachedToWindow())
                .append(",wh=").append(v.getWidth()).append("x").append(v.getHeight())
                .append(",xy=").append(v.getX()).append(",").append(v.getY());
        ViewParent p = v.getParent();
        for (int i = 0; i < 3 && p instanceof View; i++) {
            View parent = (View) p;
            sb.append(",p").append(i).append("=").append(identity(parent))
                    .append(":").append(parent.getVisibility())
                    .append("/").append(parent.getAlpha());
            p = parent.getParent();
        }
        return sb.append("}").toString();
    }

    private static View fieldView(Object owner, String name) {
        Object value = field(owner, name);
        return value instanceof View ? (View) value : null;
    }

    private static Object field(Object owner, String name) {
        if (owner == null) return null;
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (Throwable error) {
            String missing = owner.getClass().getName() + "#" + name;
            if (MISSING_FIELDS.add(missing)) {
                MainHook.diag(TAG + " field unavailable " + missing + " " + error);
            }
            return null;
        }
    }
}
