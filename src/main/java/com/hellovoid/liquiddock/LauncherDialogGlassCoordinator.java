package com.hellovoid.liquiddock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cross-ViewRoot glass ownership for Launcher-owned dialogs.
 *
 * <p>The Launcher Activity remains the sole PassBlur source authority. Dialog windows only host
 * output sinks, so they never sample themselves and can fail closed to the vendor material.</p>
 */
final class LauncherDialogGlassCoordinator {
    private static final String TAG = "[DC][LauncherDialogGlass]";
    private static final String UNINSTALL_CONTAINER =
            "com.miui.home.launcher.uninstall.UninstallDialogViewContainer";
    private static final int MAX_TREE_LOG_NODES = 24;
    private static final Map<Dialog, Binding> BINDINGS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LauncherDialogGlassCoordinator() {}

    static synchronized boolean attachUninstallDialog(
            Activity launcher, Dialog dialog, LiquidDockConfig.Glass glassConfig, String source) {
        if (launcher == null || dialog == null || glassConfig == null
                || !GlassRuntimeState.isEnabled() || !dialog.isShowing()) {
            return false;
        }

        Binding previous = BINDINGS.remove(dialog);
        if (previous != null) releaseBinding(previous, "replace");

        Window dialogWindow = dialog.getWindow();
        View decor = dialogWindow != null ? dialogWindow.getDecorView() : null;
        View material = findExactClass(decor, UNINSTALL_CONTAINER);
        if (material == null || !(material.getParent() instanceof ViewGroup)) {
            MainHook.log(TAG + " target unavailable source=" + source
                    + " decor=" + className(decor));
            logTree(decor);
            return false;
        }

        View authorityAnchor = launcher.findViewById(android.R.id.content);
        if (authorityAnchor == null || !authorityAnchor.isAttachedToWindow()) {
            MainHook.log(TAG + " launcher authority unavailable source=" + source);
            return false;
        }
        LauncherGlassSession authority =
                LauncherGlassSessionRegistry.acquire(authorityAnchor, glassConfig);
        if (authority == null || authority.isShutdown()) {
            MainHook.log(TAG + " shared launcher session unavailable source=" + source);
            return false;
        }

        float radiusPx = resolveDialogCornerRadius(material);
        LauncherGlassSinkView sink = LauncherGlassSinkView.attachToExternalMaterial(
                material, authority, radiusPx, glassConfig);
        if (sink == null) {
            MainHook.log(TAG + " output sink unavailable source=" + source);
            return false;
        }
        sink.setNodeKind(LauncherGlassNodeKind.LARGE_FOLDER);

        Binding binding = new Binding(dialog, material, sink, source);
        View.OnAttachStateChangeListener detachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                MAIN.post(() -> {
                    Dialog owner = binding.dialogRef.get();
                    if (owner != null) release(owner, binding, "material-detached");
                    else releaseBinding(binding, "material-detached-orphan");
                });
            }
        };
        binding.detachListener = detachListener;
        material.addOnAttachStateChangeListener(detachListener);
        BINDINGS.put(dialog, binding);

        sink.runWhenFirstFramePresented(() -> claimVendorMaterial(dialog, binding));
        sink.requestLifecycleRefresh();
        View launcherRoot = authorityAnchor.getRootView();
        if (launcherRoot != null) LauncherGlassSceneController.requestFreshForRoot(launcherRoot);

        MainHook.log(TAG + " bound source=" + source
                + " target=" + material.getClass().getName()
                + " parent=" + className(material.getParent())
                + " background=" + className(material.getBackground())
                + " radiusPx=" + radiusPx);
        return true;
    }

    private static synchronized void claimVendorMaterial(Dialog dialog, Binding expected) {
        Binding binding = BINDINGS.get(dialog);
        if (binding != expected || binding.released || binding.claimed) return;
        View material = binding.materialRef.get();
        if (material == null || !material.isAttachedToWindow()) {
            release(dialog, binding, "first-frame-without-material");
            return;
        }

        binding.originalBackground = material.getBackground();
        material.setBackground(null);
        binding.claimed = true;
        MainHook.log(TAG + " first glass frame presented; vendor background released source="
                + binding.source);
    }

    static synchronized void releaseAll() {
        ArrayList<Map.Entry<Dialog, Binding>> snapshot =
                new ArrayList<>(BINDINGS.entrySet());
        BINDINGS.clear();
        for (Map.Entry<Dialog, Binding> entry : snapshot) {
            releaseBinding(entry.getValue(), "runtime-teardown");
        }
    }

    private static synchronized void release(Dialog dialog, Binding expected, String reason) {
        Binding binding = BINDINGS.get(dialog);
        if (binding != expected) return;
        BINDINGS.remove(dialog);
        releaseBinding(binding, reason);
    }

    private static void releaseBinding(Binding binding, String reason) {
        if (binding == null || binding.released) return;
        binding.released = true;
        View material = binding.materialRef.get();
        if (material != null) {
            if (binding.detachListener != null) {
                try { material.removeOnAttachStateChangeListener(binding.detachListener); }
                catch (Throwable ignored) {}
            }
            if (binding.claimed) {
                material.setBackground(binding.originalBackground);
            }
        }
        binding.detachListener = null;
        LauncherGlassSinkView sink = binding.sink;
        binding.sink = null;
        if (sink != null) sink.dispose();
        MainHook.log(TAG + " released source=" + binding.source + " reason=" + reason);
    }

    private static View findExactClass(View root, String className) {
        if (root == null || className == null) return null;
        if (className.equals(root.getClass().getName())) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View match = findExactClass(group.getChildAt(i), className);
            if (match != null) return match;
        }
        return null;
    }

    private static float resolveDialogCornerRadius(View material) {
        try {
            int id = material.getResources().getIdentifier(
                    "miuix_appcompat_dialog_bg_corner_radius", "dimen", "com.miui.home");
            if (id != 0) return material.getResources().getDimension(id);
            id = material.getResources().getIdentifier(
                    "miuix_appcompat_window_dialog_radius", "dimen", "com.miui.home");
            if (id != 0) return material.getResources().getDimension(id);
        } catch (Throwable ignored) {}
        return 28f * material.getResources().getDisplayMetrics().density;
    }

    private static void logTree(View root) {
        if (root == null) return;
        int[] remaining = {MAX_TREE_LOG_NODES};
        logTree(root, 0, remaining);
    }

    private static void logTree(View view, int depth, int[] remaining) {
        if (view == null || remaining[0]-- <= 0) return;
        MainHook.log(TAG + " tree depth=" + depth
                + " class=" + view.getClass().getName()
                + " id=" + resourceEntryName(view)
                + " bg=" + className(view.getBackground())
                + " size=" + view.getWidth() + "x" + view.getHeight());
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount() && remaining[0] > 0; i++) {
            logTree(group.getChildAt(i), depth + 1, remaining);
        }
    }

    private static String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return "<none>";
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (Throwable ignored) { return Integer.toHexString(view.getId()); }
    }

    private static String className(Object value) {
        return value != null ? value.getClass().getName() : "<null>";
    }

    private static final class Binding {
        final WeakReference<Dialog> dialogRef;
        final WeakReference<View> materialRef;
        final String source;
        LauncherGlassSinkView sink;
        View.OnAttachStateChangeListener detachListener;
        Drawable originalBackground;
        boolean claimed;
        boolean released;

        Binding(Dialog dialog, View material, LauncherGlassSinkView sink, String source) {
            dialogRef = new WeakReference<>(dialog);
            materialRef = new WeakReference<>(material);
            this.sink = sink;
            this.source = source != null ? source : "unknown";
        }
    }
}
