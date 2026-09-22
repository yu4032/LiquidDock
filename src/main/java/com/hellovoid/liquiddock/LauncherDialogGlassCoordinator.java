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
 * Dialog-root glass ownership for Launcher uninstall/remove confirmation windows.
 *
 * <p>The custom UninstallDialogViewContainer is content only. HyperOS places the visible MIUIX
 * material on DialogParentPanel2 (@id/parentPanel). The Dialog ViewRoot is the PassBlur authority:
 * excluding that root surface captures the real Launcher window below the dialog instead of the
 * Launcher window's own behind-content (normally the wallpaper). Keeping source, geometry sync and
 * output in the same ViewRoot also makes the glass follow the dialog's real frame animation.</p>
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
     * Observe one exact BaseUninstallDialog instance without replacing vendor show/dismiss
     * listeners. Constructor callers may immediately invoke create()/show(), so rendezvous is
     * deferred to attach/layout authority rather than a fixed delay.
     */
    static void watchUninstallDialog(
            Activity launcher, Dialog dialog, LiquidDockConfig.Glass glassConfig, String source) {
        if (launcher == null || dialog == null || glassConfig == null
                || !GlassRuntimeState.isEnabled()) {
            return;
        }
        MAIN.post(() -> watchOnMain(dialog, glassConfig, source));
    }

    private static synchronized void watchOnMain(
            Dialog dialog, LiquidDockConfig.Glass glassConfig, String source) {
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

        Binding binding = new Binding(dialog, decor, glassConfig, source);
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
     * Wait for MIUIX to install the exact uninstall hierarchy, then bind PassBlur to the dialog
     * ViewRoot. No Launcher-root producer and no fixed timing heuristic participate.
     */
    private static synchronized void tryAttach(Binding binding) {
        if (binding == null || binding.released || binding.bound) return;
        View decor = binding.decorRef.get();
        Dialog dialog = binding.dialogRef.get();
        if (decor == null || dialog == null || !decor.isAttachedToWindow()) return;

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

        View dialogRoot = decor.getRootView();
        if (dialogRoot == null || !dialogRoot.isAttachedToWindow()
                || dialogRoot.getWidth() <= 0 || dialogRoot.getHeight() <= 0
                || dialogRoot.getWindowToken() == null) {
            if (!binding.authorityUnavailableLogged) {
                binding.authorityUnavailableLogged = true;
                MainHook.log(TAG + " dialog ViewRoot unavailable source=" + binding.source);
            }
            return;
        }

        LauncherGlassSession authority = binding.dialogSession;
        if (authority == null || authority.isShutdown() || !authority.ownsRoot(dialogRoot)) {
            if (authority != null) authority.shutdown();
            try {
                authority = new LauncherGlassSession(
                        dialogRoot,
                        binding.glassConfig,
                        PassBlurBindRequest.launcherDialog(dialogRoot));
                binding.dialogSession = authority;
                LauncherGlassSession observed = authority;
                observed.setTerminalFailureListener(
                        () -> releaseObserved(binding, "source-terminal-failure"));
                MainHook.log(TAG + " dialog source created source=" + binding.source
                        + " root=" + dialogRoot.getClass().getName()
                        + " size=" + dialogRoot.getWidth() + "x" + dialogRoot.getHeight());
            } catch (Throwable error) {
                if (!binding.authorityUnavailableLogged) {
                    binding.authorityUnavailableLogged = true;
                    MainHook.log(TAG + " dialog source unavailable source=" + binding.source
                            + " error=" + error);
                }
                return;
            }
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
        sink.runWhenOutputLost(() -> releaseObserved(binding, "output-surface-lost"));
        removeLayoutRendezvous(binding);

        sink.runWhenFirstFramePresented(() -> {
            Dialog owner = binding.dialogRef.get();
            if (owner != null) claimVendorMaterial(owner, binding);
            else releaseBinding(binding, "first-frame-orphan");
        });
        sink.requestLifecycleRefresh();
        authority.requestFreshBackdrop();

        MainHook.log(TAG + " bound source=" + binding.source
                + " dialog=" + binding.dialogType
                + " authority=DIALOG_VIEW_ROOT"
                + " panel=" + panel.getClass().getName()
                + " id=" + panelId
                + " background=" + className(panel.getBackground())
                + " panelSize=" + panel.getWidth() + "x" + panel.getHeight()
                + " rootSize=" + dialogRoot.getWidth() + "x" + dialogRoot.getHeight()
                + " radiusPx=" + radiusPx);
    }

    /**
     * Hand off material ownership only after the first replacement frame has reached the dialog.
     * The original Drawable is never alpha-mutated: a transparent clone is installed only when
     * the vendor fallback is currently opaque, so release can restore the exact vendor object.
     */
    private static synchronized void claimVendorMaterial(Dialog dialog, Binding expected) {
        Binding binding = BINDINGS.get(dialog);
        if (binding != expected || binding.released || binding.claimed) return;
        View panel = binding.panelRef.get();
        if (panel == null || !panel.isAttachedToWindow()) {
            releaseObserved(binding, "first-frame-without-panel");
            return;
        }

        Boolean vendorPassBlur = MiBlurBridge.getPassWindowBlurEnabled(panel);
        if (vendorPassBlur == null) {
            MainHook.log(TAG + " pass-window state unavailable; stock material retained source="
                    + binding.source);
            releaseObserved(binding, "pass-window-state-unavailable");
            return;
        }

        Drawable background = panel.getBackground();
        int backgroundAlpha = background != null ? background.getAlpha() : -1;
        Drawable transparentBackground = null;
        if (background != null && backgroundAlpha > 0) {
            transparentBackground = cloneTransparent(background, panel);
            if (transparentBackground == null) {
                MainHook.log(TAG + " background clone unavailable; stock material retained source="
                        + binding.source + " background=" + className(background));
                releaseObserved(binding, "background-clone-unavailable");
                return;
            }
        }

        binding.originalBackground = background;
        binding.vendorPassBlurEnabled = vendorPassBlur;
        if (vendorPassBlur) {
            if (!MiBlurBridge.setPassWindowBlurEnabled(panel, false)) {
                MainHook.log(TAG
                        + " vendor pass-window gate could not be paused; stock material retained"
                        + " source=" + binding.source);
                releaseObserved(binding, "pass-window-pause-failed");
                return;
            }
            binding.vendorGatePaused = true;
        }

        if (transparentBackground != null) {
            try {
                panel.setBackground(transparentBackground);
                binding.transparentBackground = transparentBackground;
                binding.backgroundReplaced = true;
            } catch (Throwable error) {
                MainHook.log(TAG + " transparent background install failed source="
                        + binding.source + " error=" + error);
                releaseObserved(binding, "background-install-failed");
                return;
            }
        }

        binding.claimed = true;
        installMaterialGuard(binding);
        MainHook.log(TAG + " first glass frame presented; MIUIX material paused source="
                + binding.source
                + " vendorPassBlur=" + vendorPassBlur
                + " originalBackground=" + className(background)
                + " originalBackgroundAlpha=" + backgroundAlpha
                + " backgroundReplaced=" + binding.backgroundReplaced);
    }

    /**
     * MIUIX can touch the pass-window gate again while its show/dismiss animator is active. Keep
     * our claimed gate suppressed from the dialog's own pre-draw signal; a failed reassertion
     * releases immediately back to the stock material.
     */
    private static void installMaterialGuard(Binding binding) {
        if (binding == null || binding.released || binding.materialGuard != null) return;
        View decor = binding.decorRef.get();
        if (decor == null) return;
        ViewTreeObserver observer = decor.getViewTreeObserver();
        if (!observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            if (binding.released || !binding.claimed) return true;
            View panel = binding.panelRef.get();
            if (panel == null || !panel.isAttachedToWindow()) return true;
            Boolean enabled = MiBlurBridge.getPassWindowBlurEnabled(panel);
            if (Boolean.TRUE.equals(enabled)) {
                boolean suppressed = MiBlurBridge.setPassWindowBlurEnabled(panel, false);
                MainHook.log(TAG + " vendor pass-window gate reassert source=" + binding.source
                        + " result=" + suppressed);
                if (!suppressed) {
                    MAIN.post(() -> releaseObserved(binding, "pass-window-reassert-failed"));
                }
            }
            return true;
        };
        binding.materialGuardObserver = observer;
        binding.materialGuard = listener;
        observer.addOnPreDrawListener(listener);
    }

    private static void removeMaterialGuard(Binding binding) {
        ViewTreeObserver observer = binding != null ? binding.materialGuardObserver : null;
        ViewTreeObserver.OnPreDrawListener listener =
                binding != null ? binding.materialGuard : null;
        if (binding != null) {
            binding.materialGuardObserver = null;
            binding.materialGuard = null;
        }
        if (observer != null && listener != null) {
            try {
                if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
            } catch (Throwable ignored) {}
        }
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
        removeMaterialGuard(binding);

        View decor = binding.decorRef.get();
        if (decor != null && binding.attachListener != null) {
            try { decor.removeOnAttachStateChangeListener(binding.attachListener); }
            catch (Throwable ignored) {}
        }
        binding.attachListener = null;

        View panel = binding.panelRef.get();
        if (panel != null && binding.backgroundReplaced) {
            try {
                panel.setBackground(binding.originalBackground);
            } catch (Throwable error) {
                MainHook.log(TAG + " vendor background restore failed source=" + binding.source
                        + " error=" + error);
            }
        }
        if (panel != null && binding.vendorGatePaused && binding.vendorPassBlurEnabled) {
            boolean restored = MiBlurBridge.setPassWindowBlurEnabled(panel, true);
            MainHook.log(TAG + " vendor pass-window gate restore source=" + binding.source
                    + " result=" + restored);
        }

        LauncherGlassSinkView sink = binding.sink;
        binding.sink = null;
        if (sink != null) sink.dispose();

        LauncherGlassSession authority = binding.dialogSession;
        binding.dialogSession = null;
        if (authority != null) {
            authority.setTerminalFailureListener(null);
            authority.shutdown();
        }

        MainHook.log(TAG + " released source=" + binding.source + " reason=" + reason
                + " claimed=" + binding.claimed
                + " authority=DIALOG_VIEW_ROOT");
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

    private static Drawable cloneTransparent(Drawable source, View owner) {
        if (source == null || owner == null) return null;
        try {
            Drawable.ConstantState state = source.getConstantState();
            if (state == null) return null;
            Drawable clone = state.newDrawable(
                    owner.getResources(), owner.getContext().getTheme());
            if (clone == null) return null;
            clone = clone.mutate();
            clone.setAlpha(0);
            return clone;
        } catch (Throwable error) {
            MainHook.log(TAG + " transparent background clone failed: " + error);
            return null;
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
        final WeakReference<Dialog> dialogRef;
        final WeakReference<View> decorRef;
        WeakReference<View> panelRef = new WeakReference<>(null);
        final LiquidDockConfig.Glass glassConfig;
        final String source;
        final String dialogType;
        LauncherGlassSession dialogSession;
        LauncherGlassSinkView sink;
        View.OnAttachStateChangeListener attachListener;
        ViewTreeObserver layoutObserver;
        ViewTreeObserver.OnGlobalLayoutListener layoutListener;
        ViewTreeObserver materialGuardObserver;
        ViewTreeObserver.OnPreDrawListener materialGuard;
        Drawable originalBackground;
        Drawable transparentBackground;
        boolean vendorPassBlurEnabled;
        boolean vendorGatePaused;
        boolean backgroundReplaced;
        boolean targetsMissingLogged;
        boolean authorityUnavailableLogged;
        boolean sinkUnavailableLogged;
        boolean bound;
        boolean claimed;
        boolean released;

        Binding(
                Dialog dialog,
                View decor,
                LiquidDockConfig.Glass glassConfig,
                String source) {
            dialogRef = new WeakReference<>(dialog);
            decorRef = new WeakReference<>(decor);
            this.glassConfig = glassConfig;
            this.source = source != null ? source : "unknown";
            dialogType = dialog.getClass().getName();
        }
    }
}
