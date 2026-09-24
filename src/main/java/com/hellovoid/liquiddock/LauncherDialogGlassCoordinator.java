package com.hellovoid.liquiddock;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;

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
    // Canonical MIUIX AlertController.installContent(): mDimBg = findViewById(dialog_dim_bg).
    // Immersive dialogs animate this View's alpha while Window.dimAmount is explicitly 0.
    private static final String DIALOG_DIM_BG_ID = "dialog_dim_bg";
    private static final Map<Dialog, Binding> BINDINGS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LauncherDialogGlassCoordinator() {}

    /**
     * Observe one exact BaseUninstallDialog instance without replacing vendor show/dismiss
     * listeners. Constructor callers may immediately invoke create()/show(), so rendezvous is
     * deferred to attach/layout authority rather than a fixed delay.
     */
    static void watchUninstallDialog(
            Dialog dialog, LiquidDockConfig.Glass glassConfig, String source) {
        if (dialog == null || glassConfig == null || !GlassRuntimeState.isEnabled()) {
            return;
        }
        MAIN.post(() -> watchOnMain(dialog, glassConfig, source));
    }

    private static synchronized void watchOnMain(
            Dialog dialog, LiquidDockConfig.Glass glassConfig, String source) {
        if (!GlassRuntimeState.isEnabled()) return;

        Binding previous = BINDINGS.remove(dialog);
        if (previous != null) releaseBinding(previous);

        Window window = dialog.getWindow();
        View decor = window != null ? window.getDecorView() : null;
        if (decor == null) {
            MainHook.log(TAG + " decor unavailable source=" + source
                    + " type=" + dialog.getClass().getName());
            return;
        }

        Binding binding = new Binding(dialog, decor, glassConfig, source);
        binding.appearance = LauncherDialogGlassPreferences.resolve(
                ConfigReader.load(), glassConfig);
        BINDINGS.put(dialog, binding);
        View.OnAttachStateChangeListener attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                armLayoutRendezvous(binding);
            }

            @Override public void onViewDetachedFromWindow(View v) {
                MAIN.post(() -> releaseObserved(binding));
            }
        };
        binding.attachListener = attachListener;
        decor.addOnAttachStateChangeListener(attachListener);

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
        if (uninstallContent == null || panel == null) return;
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

        float effectiveDim = syncDialogDim(binding, dialog, false);

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
                        () -> releaseObserved(binding));
            } catch (Throwable error) {
                if (!binding.authorityUnavailableLogged) {
                    binding.authorityUnavailableLogged = true;
                    MainHook.log(TAG + " dialog source unavailable source=" + binding.source
                            + " error=" + error);
                }
                return;
            }
        }

        applyDialogMaterial(binding, dialogRoot, effectiveDim);

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
        sink.runWhenOutputLost(() -> releaseObserved(binding));

        // Suppress MIUIX in the same layout pass that creates the replacement output. Any later
        // source/output failure still fails closed and restores the exact vendor material.
        claimVendorMaterial(dialog, binding);
        if (binding.released || !binding.claimed) return;

        removeLayoutRendezvous(binding);
        sink.requestLifecycleRefresh();
        authority.requestFreshBackdrop();

    }

    /**
     * Hand off material ownership as soon as the exact panel and replacement output exist, before
     * the dialog layout is drawn. The original Drawable is never alpha-mutated: a transparent clone
     * is installed only when the vendor fallback is currently opaque, so release can restore the
     * exact vendor object if the replacement source/output later fails.
     */
    private static synchronized void claimVendorMaterial(Dialog dialog, Binding expected) {
        Binding binding = BINDINGS.get(dialog);
        if (binding != expected || binding.released || binding.claimed) return;
        View panel = binding.panelRef.get();
        if (panel == null || !panel.isAttachedToWindow()) {
            releaseObserved(binding);
            return;
        }

        Boolean vendorPassBlur = MiBlurBridge.getPassWindowBlurEnabled(panel);
        if (vendorPassBlur == null) {
            MainHook.log(TAG + " pass-window state unavailable; stock material retained source="
                    + binding.source);
            releaseObserved(binding);
            return;
        }

        Drawable background = panel.getBackground();
        Drawable transparentBackground = null;
        if (background != null && background.getAlpha() > 0) {
            transparentBackground = cloneTransparent(background, panel);
            if (transparentBackground == null) {
                MainHook.log(TAG + " background clone unavailable; stock material retained source="
                        + binding.source + " background=" + className(background));
                releaseObserved(binding);
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
                releaseObserved(binding);
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
                releaseObserved(binding);
                return;
            }
        }

        binding.claimed = true;
        installMaterialGuard(binding);
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
            Dialog owner = binding.dialogRef.get();
            if (owner != null) syncDialogDim(binding, owner, true);
            if (binding.backgroundReplaced && binding.transparentBackground != null) {
                Drawable current = panel.getBackground();
                if (current != binding.transparentBackground) {
                    // MIUIX may write its fallback Drawable again while the window animator runs.
                    // Preserve the latest vendor object for fail-closed restoration, but keep the
                    // replacement slot transparent for as long as our glass owns the material.
                    binding.originalBackground = current;
                    try {
                        panel.setBackground(binding.transparentBackground);
                    } catch (Throwable error) {
                        MainHook.log(TAG + " transparent background reassert failed source="
                                + binding.source + " error=" + error);
                        MAIN.post(() -> releaseObserved(binding));
                        return true;
                    }
                }
                // MIUIX can animate background alpha through panel.getBackground(). Reset the
                // transparent clone every pre-draw so such writes cannot create a half-opaque
                // fallback layer over the live glass.
                if (binding.transparentBackground.getAlpha() != 0) {
                    binding.transparentBackground.setAlpha(0);
                }
            }

            Boolean enabled = MiBlurBridge.getPassWindowBlurEnabled(panel);
            if (Boolean.TRUE.equals(enabled)) {
                boolean suppressed = MiBlurBridge.setPassWindowBlurEnabled(panel, false);
                if (!suppressed) {
                    MAIN.post(() -> releaseObserved(binding));
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
        for (Binding binding : snapshot) releaseBinding(binding);
    }

    private static synchronized void releaseObserved(Binding binding) {
        if (binding == null || binding.released) return;
        Dialog dialog = binding.dialogRef.get();
        if (dialog != null && BINDINGS.get(dialog) == binding) BINDINGS.remove(dialog);
        releaseBinding(binding);
    }

    private static void releaseBinding(Binding binding) {
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
            MiBlurBridge.setPassWindowBlurEnabled(panel, true);
        }
        restoreDialogDim(binding);

        LauncherGlassSinkView sink = binding.sink;
        binding.sink = null;
        if (sink != null) sink.dispose();

        LauncherGlassSession authority = binding.dialogSession;
        binding.dialogSession = null;
        if (authority != null) {
            authority.setTerminalFailureListener(null);
            authority.shutdown();
        }

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

    private static float syncDialogDim(
            Binding binding, Dialog dialog, boolean updateMaterial) {
        if (binding == null || dialog == null) return 0f;

        LauncherDialogGlassPreferences.Appearance appearance = binding.appearance;
        boolean disableDimming = appearance != null && appearance.disableDimming;

        // MIUIX immersive AlertDialog does not use Window DIM_BEHIND as its visual authority.
        // AlertController.setupImmersiveWindow() sets window dimAmount to 0 and onStart() animates
        // @id/dialog_dim_bg directly. Observe that real View every pre-draw so glass attenuation
        // follows the exact vendor animation instead of a guessed/final WindowManager value.
        View dimBg = resolveDimBackground(binding);
        if (dimBg != null && dimBg.isAttachedToWindow()) {
            if (!binding.dimViewCaptured) {
                binding.dimViewCaptured = true;
                binding.originalDimViewVisibility = dimBg.getVisibility();
                binding.originalDimViewAlpha = dimBg.getAlpha();
                binding.lastVendorDimAlpha = binding.originalDimViewAlpha;
            }

            float animatedDim = dimBg.getVisibility() == View.VISIBLE
                    ? clamp01(dimBg.getAlpha()) : 0f;
            if (animatedDim > 0.001f) binding.lastVendorDimAlpha = animatedDim;
            if (disableDimming && dimBg.getVisibility() == View.VISIBLE) {
                // MIUIX installs the outside-tap cancel listener on this exact View. Keep it
                // VISIBLE/clickable and suppress only drawing; a fully transparent View still
                // participates in hit testing, so "tap outside to dismiss" continues to work.
                // Folme may write alpha again on every animation frame, hence the pre-draw guard.
                if (dimBg.getAlpha() != 0f) dimBg.setAlpha(0f);
                binding.dimViewSuppressed = true;
            }

            float effectiveDim = disableDimming ? 0f : animatedDim;
            updateDialogMaterialIfNeeded(binding, effectiveDim, updateMaterial);
            return effectiveDim;
        }

        // Non-immersive / unexpected MIUIX variants retain the standard WindowManager fallback.
        return syncWindowDimFallback(binding, dialog, disableDimming, updateMaterial);
    }

    private static float syncWindowDimFallback(
            Binding binding,
            Dialog dialog,
            boolean disableDimming,
            boolean updateMaterial) {
        Window window = dialog.getWindow();
        if (window == null) return 0f;
        WindowManager.LayoutParams attributes = window.getAttributes();
        if (!binding.windowDimCaptured) {
            binding.windowDimCaptured = true;
            binding.originalWindowFlags = attributes.flags;
            binding.originalDimAmount = attributes.dimAmount;
        }

        if (disableDimming
                && (attributes.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            attributes = window.getAttributes();
            binding.windowDimSuppressed = true;
        }

        float effectiveDim = !disableDimming
                && (attributes.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
                ? clamp01(attributes.dimAmount) : 0f;
        updateDialogMaterialIfNeeded(binding, effectiveDim, updateMaterial);
        return effectiveDim;
    }

    private static void updateDialogMaterialIfNeeded(
            Binding binding, float effectiveDim, boolean updateMaterial) {
        if (!updateMaterial || Math.abs(effectiveDim - binding.appliedDimAmount) <= 0.001f) return;
        View decor = binding.decorRef.get();
        View root = decor != null ? decor.getRootView() : null;
        if (root != null) applyDialogMaterial(binding, root, effectiveDim);
    }

    private static void applyDialogMaterial(Binding binding, View root, float effectiveDim) {
        if (binding == null || root == null) return;
        LauncherGlassSession session = binding.dialogSession;
        if (session == null || session.isShutdown()) return;
        LauncherDialogGlassPreferences.Appearance appearance = binding.appearance;
        if (appearance == null) {
            appearance = LauncherDialogGlassPreferences.resolve(
                    ConfigReader.load(), binding.glassConfig);
            binding.appearance = appearance;
        }
        session.setPrismalParams(LauncherDialogGlassPreferences.material(
                binding.glassConfig,
                appearance,
                root.getResources().getDisplayMetrics().density,
                effectiveDim));
        binding.appliedDimAmount = effectiveDim;
    }

    private static View resolveDimBackground(Binding binding) {
        if (binding == null) return null;
        View current = binding.dimBgRef.get();
        if (current != null) return current;
        View decor = binding.decorRef.get();
        View resolved = findByResourceEntryName(decor, DIALOG_DIM_BG_ID);
        if (resolved != null) binding.dimBgRef = new WeakReference<>(resolved);
        return resolved;
    }

    private static void restoreDialogDim(Binding binding) {
        if (binding == null) return;

        View dimBg = binding.dimBgRef.get();
        if (dimBg != null && binding.dimViewCaptured && binding.dimViewSuppressed) {
            try {
                dimBg.setVisibility(binding.originalDimViewVisibility);
                float restoreAlpha = Float.isFinite(binding.lastVendorDimAlpha)
                        ? binding.lastVendorDimAlpha : binding.originalDimViewAlpha;
                dimBg.setAlpha(clamp01(restoreAlpha));
            } catch (Throwable error) {
                MainHook.log(TAG + " MIUIX dim view restore failed source=" + binding.source
                        + " error=" + error);
            }
        }

        if (!binding.windowDimCaptured || !binding.windowDimSuppressed) return;
        Dialog dialog = binding.dialogRef.get();
        Window window = dialog != null ? dialog.getWindow() : null;
        if (window == null) return;
        try {
            window.setDimAmount(binding.originalDimAmount);
            if ((binding.originalWindowFlags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " Window dim restore failed source=" + binding.source
                    + " error=" + error);
        }
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
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

    private static View findByResourceEntryName(View root, String entryName) {
        if (root == null || entryName == null) return null;
        if (entryName.equals(resourceEntryName(root))) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View match = findByResourceEntryName(group.getChildAt(i), entryName);
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
        WeakReference<View> dimBgRef = new WeakReference<>(null);
        final LiquidDockConfig.Glass glassConfig;
        final String source;
        LauncherGlassSession dialogSession;
        LauncherGlassSinkView sink;
        View.OnAttachStateChangeListener attachListener;
        ViewTreeObserver layoutObserver;
        ViewTreeObserver.OnGlobalLayoutListener layoutListener;
        ViewTreeObserver materialGuardObserver;
        ViewTreeObserver.OnPreDrawListener materialGuard;
        Drawable originalBackground;
        Drawable transparentBackground;
        LauncherDialogGlassPreferences.Appearance appearance;
        int originalWindowFlags;
        int originalDimViewVisibility = View.VISIBLE;
        float originalDimViewAlpha = 1f;
        float lastVendorDimAlpha = Float.NaN;
        float originalDimAmount;
        float appliedDimAmount = Float.NaN;
        boolean dimViewCaptured;
        boolean dimViewSuppressed;
        boolean windowDimCaptured;
        boolean windowDimSuppressed;
        boolean vendorPassBlurEnabled;
        boolean vendorGatePaused;
        boolean backgroundReplaced;
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
        }
    }
}
