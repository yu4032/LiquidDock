package com.hellovoid.liquiddock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cross-ViewRoot glass ownership for Launcher-owned uninstall dialogs.
 *
 * <p>The custom UninstallDialogViewContainer is content only. HyperOS 4.50 places the visible
 * MIUIX window material on DialogParentPanel2 (@id/parentPanel). The Launcher Activity remains the
 * sole PassBlur source authority and the Dialog ViewRoot hosts output only.</p>
 */
final class LauncherDialogGlassCoordinator {
    private static final String TAG = "[DC][LauncherDialogGlass]";
    private static final String UNINSTALL_CONTENT =
            "com.miui.home.launcher.uninstall.UninstallDialogViewContainer";
    private static final String DIALOG_PARENT_PANEL =
            "miuix.appcompat.internal.widget.DialogParentPanel2";
    private static final String DIALOG_PARENT_PANEL_ID = "parentPanel";
    private static final int MAX_TREE_LOG_NODES = 32;
    private static final Map<Dialog, Binding> BINDINGS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LauncherDialogGlassCoordinator() {}

    /**
     * Observe one exact BaseUninstallDialog instance without taking over its vendor show/dismiss
     * listeners. Constructor callers may immediately invoke create()/show(), so rendezvous is
     * deferred to the main queue and completed from real attach/layout events.
     */
    static void watchUninstallDialog(
            Activity launcher, Dialog dialog, LiquidDockConfig.Glass glassConfig, String source) {
        if (launcher == null || dialog == null || glassConfig == null
                || !GlassRuntimeState.isEnabled()) {
            return;
        }
        MAIN.post(() -> watchOnMain(launcher, dialog, glassConfig, source));
    }

    private static synchronized void watchOnMain(
            Activity launcher, Dialog dialog, LiquidDockConfig.Glass glassConfig, String source) {
        if (!GlassRuntimeState.isEnabled()) return;

        Binding previous = BINDINGS.remove(dialog);
        if (previous != null) releaseBinding(previous, "watch-replace");

        Window window = dialog.getWindow();
        View decor = window != null ? window.getDecorView() : null;
        if (decor == null) {
            MainHook.log(TAG + " decor unavailable source=" + source
                    + " type=" + dialog.getClass().getName());
            return;
        }

        Binding binding = new Binding(launcher, dialog, decor, glassConfig, source);
        BINDINGS.put(dialog, binding);
        View.OnAttachStateChangeListener attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                MainHook.log(TAG + " decor attached source=" + binding.source
                        + " type=" + binding.dialogType);
                armLayoutRendezvous(binding);
            }

            @Override public void onViewDetachedFromWindow(View v) {
                MAIN.post(() -> releaseObserved(binding, "decor-detached"));
            }
        };
        binding.attachListener = attachListener;
        decor.addOnAttachStateChangeListener(attachListener);

        MainHook.log(TAG + " watching source=" + binding.source
                + " type=" + binding.dialogType
                + " attached=" + decor.isAttachedToWindow());
        if (decor.isAttachedToWindow()) armLayoutRendezvous(binding);
    }

    private static void armLayoutRendezvous(Binding binding) {
        if (binding == null || binding.released || binding.bound || binding.layoutListener != null) {
            return;
        }
        View decor = binding.decorRef.get();
        if (decor == null) return;
        ViewTreeObserver observer = decor.getViewTreeObserver();
        if (!observer.isAlive()) return;

        ViewTreeObserver.OnGlobalLayoutListener listener = () -> tryAttach(binding);
        binding.layoutObserver = observer;
        binding.layoutListener = listener;
        observer.addOnGlobalLayoutListener(listener);
        tryAttach(binding);
    }

    /**
     * Wait for MIUIX to install its own content hierarchy. No fixed delay is involved: async
     * inflation, create() and show() all converge through attach/global-layout.
     */
    private static synchronized void tryAttach(Binding binding) {
        if (binding == null || binding.released || binding.bound) return;
        View decor = binding.decorRef.get();
        Activity launcher = binding.launcherRef.get();
        Dialog dialog = binding.dialogRef.get();
        if (decor == null || launcher == null || dialog == null
                || !decor.isAttachedToWindow()) {
            return;
        }

        View uninstallContent = findExactClass(decor, UNINSTALL_CONTENT);
        View panel = findExactClass(decor, DIALOG_PARENT_PANEL);
        if (uninstallContent == null || panel == null) {
            logMissingTargetsOnce(binding, decor, uninstallContent, panel);
            return;
        }
        String panelId = resourceEntryName(panel);
        if (!DIALOG_PARENT_PANEL_ID.equals(panelId)) {
            MainHook.log(TAG + " panel identity mismatch source=" + binding.source
                    + " class=" + panel.getClass().getName() + " id=" + panelId);
            removeLayoutRendezvous(binding);
            return;
        }
        if (!(panel.getParent() instanceof ViewGroup)
                || panel.getWidth() <= 0 || panel.getHeight() <= 0) {
            return;
        }

        View authorityAnchor = launcher.findViewById(android.R.id.content);
        if (authorityAnchor == null || !authorityAnchor.isAttachedToWindow()) {
            if (!binding.authorityUnavailableLogged) {
                binding.authorityUnavailableLogged = true;
                MainHook.log(TAG + " launcher authority unavailable source=" + binding.source);
            }
            return;
        }
        LauncherGlassSession authority =
                LauncherGlassSessionRegistry.acquire(authorityAnchor, binding.glassConfig);
        if (authority == null || authority.isShutdown()) {
            if (!binding.authorityUnavailableLogged) {
                binding.authorityUnavailableLogged = true;
                MainHook.log(TAG + " shared launcher session unavailable source=" + binding.source);
            }
            return;
        }

        float radiusPx = resolveDialogCornerRadius(panel);
        LauncherGlassSinkView sink = LauncherGlassSinkView.attachToExternalMaterial(
                panel, authority, radiusPx, binding.glassConfig);
        if (sink == null) {
            if (!binding.sinkUnavailableLogged) {
                binding.sinkUnavailableLogged = true;
                MainHook.log(TAG + " output sink unavailable source=" + binding.source);
            }
            return;
        }

        binding.panelRef = new WeakReference<>(panel);
        binding.sink = sink;
        binding.bound = true;
        sink.setNodeKind(LauncherGlassNodeKind.LARGE_FOLDER);
        removeLayoutRendezvous(binding);

        sink.runWhenFirstFramePresented(() -> {
            Dialog owner = binding.dialogRef.get();
            if (owner != null) claimVendorMaterial(owner, binding);
            else releaseBinding(binding, "first-frame-orphan");
        });
        sink.requestLifecycleRefresh();
        View launcherRoot = authorityAnchor.getRootView();
        if (launcherRoot != null) LauncherGlassSceneController.requestFreshForRoot(launcherRoot);

        MainHook.log(TAG + " bound source=" + binding.source
                + " dialog=" + binding.dialogType
                + " panel=" + panel.getClass().getName()
                + " id=" + panelId
                + " background=" + className(panel.getBackground())
                + " size=" + panel.getWidth() + "x" + panel.getHeight()
                + " radiusPx=" + radiusPx);
    }

    private static synchronized void claimVendorMaterial(Dialog dialog, Binding expected) {
        Binding binding = BINDINGS.get(dialog);
        if (binding != expected || binding.released || binding.claimed) return;
        View panel = binding.panelRef.get();
        if (panel == null || !panel.isAttachedToWindow()) {
            releaseObserved(binding, "first-frame-without-panel");
            return;
        }

        binding.originalBackground = panel.getBackground();
        panel.setBackground(null);
        binding.claimed = true;
        MainHook.log(TAG + " first glass frame presented; MIUIX parentPanel background released"
                + " source=" + binding.source
                + " original=" + className(binding.originalBackground));
    }

    static synchronized void releaseAll() {
        ArrayList<Binding> snapshot = new ArrayList<>(BINDINGS.values());
        BINDINGS.clear();
        for (Binding binding : snapshot) releaseBinding(binding, "runtime-teardown");
    }

    private static synchronized void releaseObserved(Binding binding, String reason) {
        if (binding == null || binding.released) return;
        Dialog dialog = binding.dialogRef.get();
        if (dialog != null && BINDINGS.get(dialog) == binding) BINDINGS.remove(dialog);
        releaseBinding(binding, reason);
    }

    private static void releaseBinding(Binding binding, String reason) {
        if (binding == null || binding.released) return;
        binding.released = true;
        removeLayoutRendezvous(binding);

        View decor = binding.decorRef.get();
        if (decor != null && binding.attachListener != null) {
            try { decor.removeOnAttachStateChangeListener(binding.attachListener); }
            catch (Throwable ignored) {}
        }
        binding.attachListener = null;

        View panel = binding.panelRef.get();
        if (panel != null && binding.claimed) {
            panel.setBackground(binding.originalBackground);
        }
        LauncherGlassSinkView sink = binding.sink;
        binding.sink = null;
        if (sink != null) sink.dispose();

        MainHook.log(TAG + " released source=" + binding.source + " reason=" + reason
                + " claimed=" + binding.claimed);
    }

    private static void removeLayoutRendezvous(Binding binding) {
        ViewTreeObserver observer = binding != null ? binding.layoutObserver : null;
        ViewTreeObserver.OnGlobalLayoutListener listener =
                binding != null ? binding.layoutListener : null;
        if (binding != null) {
            binding.layoutObserver = null;
            binding.layoutListener = null;
        }
        if (observer != null && listener != null) {
            try {
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
            } catch (Throwable ignored) {}
        }
    }

    private static void logMissingTargetsOnce(
            Binding binding, View decor, View uninstallContent, View panel) {
        if (binding.targetsMissingLogged) return;
        binding.targetsMissingLogged = true;
        MainHook.log(TAG + " awaiting MIUIX hierarchy source=" + binding.source
                + " uninstallContent=" + className(uninstallContent)
                + " panel=" + className(panel));
        logTree(decor);
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

    private static float resolveDialogCornerRadius(View panel) {
        try {
            int id = panel.getResources().getIdentifier(
                    "miuix_appcompat_dialog_bg_corner_radius", "dimen", "com.miui.home");
            if (id != 0) return panel.getResources().getDimension(id);
            id = panel.getResources().getIdentifier(
                    "miuix_appcompat_window_dialog_radius", "dimen", "com.miui.home");
            if (id != 0) return panel.getResources().getDimension(id);
        } catch (Throwable ignored) {}
        return 28f * panel.getResources().getDisplayMetrics().density;
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
        final WeakReference<Activity> launcherRef;
        final WeakReference<Dialog> dialogRef;
        final WeakReference<View> decorRef;
        WeakReference<View> panelRef = new WeakReference<>(null);
        final LiquidDockConfig.Glass glassConfig;
        final String source;
        final String dialogType;
        LauncherGlassSinkView sink;
        View.OnAttachStateChangeListener attachListener;
        ViewTreeObserver layoutObserver;
        ViewTreeObserver.OnGlobalLayoutListener layoutListener;
        Drawable originalBackground;
        boolean targetsMissingLogged;
        boolean authorityUnavailableLogged;
        boolean sinkUnavailableLogged;
        boolean bound;
        boolean claimed;
        boolean released;

        Binding(
                Activity launcher,
                Dialog dialog,
                View decor,
                LiquidDockConfig.Glass glassConfig,
                String source) {
            launcherRef = new WeakReference<>(launcher);
            dialogRef = new WeakReference<>(dialog);
            decorRef = new WeakReference<>(decor);
            this.glassConfig = glassConfig;
            this.source = source != null ? source : "unknown";
            dialogType = dialog.getClass().getName();
        }
    }
}
