package com.hellovoid.liquiddock;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces SystemUI's MIUIX pairing-dialog material with the existing zero-copy SystemUI
 * PassBlur -> Prismal pipeline.
 *
 * <p>The hook lives on the framework Dialog lifecycle because SystemUIPlugin owns PairingDialog
 * from a separate ClassLoader. Target selection is still exact and semantic: only
 * miuix.appcompat.app.PairingDialog with its PairingParentPanel marker is accepted. Native
 * pass-window blur stays visible until Prismal presents a real frame, then remains enabled only
 * as the compositor sampler while its visible radius is reduced to zero.</p>
 */
final class SystemUiPairingDialogGlassHook {
    private static final String TAG = "[DC][SystemUiPairingDialogGlass]";
    private static final String PAIRING_DIALOG = "miuix.appcompat.app.PairingDialog";
    private static final String PAIRING_PARENT_PANEL =
            "miuix.appcompat.internal.widget.PairingParentPanel";
    private static final String DIALOG_PARENT_PANEL =
            "miuix.appcompat.internal.widget.DialogParentPanel2";

    private static final Map<Dialog, Binding> BINDINGS = new WeakHashMap<>();
    private static boolean installed;
    private static LiquidDockConfig.Glass glassConfig;

    private SystemUiPairingDialogGlassHook() {}

    static boolean install(LiquidDockConfig.Glass glass) {
        if (installed) return true;
        if (glass == null || !glass.enabled) return false;
        glassConfig = glass;
        try {
            HookUtil.hookMethod(
                    Dialog.class,
                    "show",
                    new Class<?>[0],
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Object owner = chain.getThisObject();
                        if (owner instanceof Dialog) {
                            try {
                                observeIfPairingDialog((Dialog) owner);
                            } catch (Throwable error) {
                                log("pairing dialog observation failed; stock retained: " + error);
                            }
                        }
                        return result;
                    });
            installed = true;
            return true;
        } catch (Throwable error) {
            glassConfig = null;
            log("Dialog.show hook unavailable: " + error);
            return false;
        }
    }

    private static synchronized void observeIfPairingDialog(Dialog dialog) {
        LiquidDockConfig.Glass glass = glassConfig;
        if (dialog == null || glass == null || !glass.enabled
                || !PAIRING_DIALOG.equals(dialog.getClass().getName())) {
            return;
        }

        Binding previous = BINDINGS.remove(dialog);
        if (previous != null) previous.release();

        Window window = dialog.getWindow();
        View decor = window != null ? window.getDecorView() : null;
        if (decor == null) {
            log("PairingDialog decor unavailable");
            return;
        }

        Binding binding = new Binding(dialog, decor, glass);
        BINDINGS.put(dialog, binding);
        binding.start();
    }

    private static synchronized void releaseObserved(Binding binding) {
        if (binding == null || binding.released) return;
        Dialog dialog = binding.dialogRef.get();
        if (dialog != null && BINDINGS.get(dialog) == binding) {
            BINDINGS.remove(dialog);
        }
        binding.release();
    }

    private static final class Binding implements View.OnAttachStateChangeListener,
            View.OnLayoutChangeListener {
        final WeakReference<Dialog> dialogRef;
        final View decor;
        final LiquidDockConfig.Glass glass;
        final int nativeBlurRadiusPx;

        View target;
        Drawable stockBackground;
        boolean replacementBlurApplied;
        boolean prismalPresented;
        boolean terminalFailure;
        boolean bound;
        boolean released;

        SystemUiHandleMenuPrismalSession prismalSession;
        SystemUiHandleMenuGlassOutputView prismalOutput;
        ViewTreeObserver materialGuardObserver;
        ViewTreeObserver.OnPreDrawListener materialGuard;

        Binding(Dialog dialog, View decor, LiquidDockConfig.Glass glass) {
            dialogRef = new WeakReference<>(dialog);
            this.decor = decor;
            this.glass = glass;
            nativeBlurRadiusPx = Math.max(1, Math.round(glass.blur));
        }

        void start() {
            decor.addOnAttachStateChangeListener(this);
            decor.addOnLayoutChangeListener(this);
            tryBind();
        }

        void tryBind() {
            if (released || terminalFailure || bound || !decor.isAttachedToWindow()) return;
            if (findExactClass(decor, PAIRING_PARENT_PANEL) == null) return;

            View panel = findExactClass(decor, DIALOG_PARENT_PANEL);
            if (!(panel instanceof ViewGroup) || panel.getWidth() <= 0 || panel.getHeight() <= 0) {
                return;
            }

            View sourceRoot = decor.getRootView();
            if (sourceRoot == null || !sourceRoot.isAttachedToWindow()
                    || sourceRoot.getWidth() <= 0 || sourceRoot.getHeight() <= 0) {
                return;
            }

            stockBackground = panel.getBackground();
            if (!MiBlurBridge.applyPassWindowBlur(panel, nativeBlurRadiusPx)) {
                terminalFailure = true;
                log("pairing panel pass-window fallback unavailable; stock retained");
                return;
            }
            replacementBlurApplied = true;
            panel.setBackground(null);
            panel.invalidate();

            float radiusPx = resolveDialogCornerRadius(panel);
            SystemUiHandleMenuPrismalSession session = null;
            SystemUiHandleMenuGlassOutputView output = null;
            try {
                session = new SystemUiHandleMenuPrismalSession(
                        panel,
                        sourceRoot,
                        glass,
                        radiusPx,
                        new SystemUiHandleMenuPrismalSession.Listener() {
                            @Override
                            public void onFirstFramePresented() {
                                postToDecor("Prismal presentation", Binding.this::onPrismalPresented);
                            }

                            @Override
                            public void onFailure(Throwable error) {
                                postToDecor(
                                        "Prismal failure fallback",
                                        () -> onPrismalFailure(error));
                            }
                        });
                session.start(panel.getWidth(), panel.getHeight());
                output = SystemUiHandleMenuGlassOutputView.attachInsideTarget(panel, session);
                if (output == null) {
                    session.shutdown();
                    terminalFailure = true;
                    restoreNativeFallback();
                    return;
                }
                prismalSession = session;
                prismalOutput = output;
                target = panel;
                bound = true;
                output.setMaterialAlpha(0f);
                installMaterialGuard();
                log("PairingDialog glass bound target=" + panel.getClass().getName()
                        + " size=" + panel.getWidth() + "x" + panel.getHeight()
                        + " radius=" + radiusPx);
            } catch (Throwable error) {
                if (output != null) {
                    try { output.dispose(); } catch (Throwable ignored) {}
                }
                if (session != null) {
                    try { session.shutdown(); } catch (Throwable ignored) {}
                }
                target = panel;
                onPrismalFailure(error);
            }
        }

        private void onPrismalPresented() {
            if (released || !bound || target == null
                    || prismalSession == null || prismalOutput == null) {
                return;
            }

            boolean samplerEnabled = MiBlurBridge.setPassWindowBlurEnabled(target, true);
            boolean fallbackHidden = MiBlurBridge.setPassWindowBlurRadius(target, 0);
            if (!samplerEnabled || !fallbackHidden) {
                onPrismalFailure(new IllegalStateException(
                        "pairing native blur handoff unavailable"));
                return;
            }

            prismalPresented = true;
            target.setBackground(null);
            prismalOutput.setMaterialAlpha(1f);
            installMaterialGuard();
            target.invalidate();
        }

        private void onPrismalFailure(Throwable error) {
            if (released) return;
            terminalFailure = true;
            log("Prismal fallback to native pairing blur: " + error);
            removeMaterialGuard();

            SystemUiHandleMenuGlassOutputView output = prismalOutput;
            prismalOutput = null;
            if (output != null) {
                try { output.dispose(); } catch (Throwable ignored) {}
            }

            SystemUiHandleMenuPrismalSession session = prismalSession;
            prismalSession = null;
            if (session != null) {
                try { session.shutdown(); } catch (Throwable ignored) {}
            }

            prismalPresented = false;
            bound = false;
            restoreNativeFallback();
        }

        private void restoreNativeFallback() {
            View panel = target;
            if (panel == null) {
                panel = findExactClass(decor, DIALOG_PARENT_PANEL);
                if (panel != null) target = panel;
            }
            if (panel == null || !panel.isAttachedToWindow()) return;

            if (MiBlurBridge.applyPassWindowBlur(panel, nativeBlurRadiusPx)) {
                replacementBlurApplied = true;
                panel.setBackground(null);
            } else {
                replacementBlurApplied = false;
                if (stockBackground != null) panel.setBackground(stockBackground);
            }
            panel.invalidate();
        }

        private void installMaterialGuard() {
            if (released || materialGuard != null || target == null) return;
            ViewTreeObserver observer = target.getViewTreeObserver();
            if (!observer.isAlive()) return;

            ViewTreeObserver.OnPreDrawListener listener = () -> {
                if (released || target == null) return true;
                SystemUiHandleMenuPrismalSession session = prismalSession;
                if (session != null) session.refreshHostMapping();
                if (!prismalPresented) return true;
                if (target.getBackground() != null) target.setBackground(null);

                Boolean enabled = MiBlurBridge.getPassWindowBlurEnabled(target);
                if (!Boolean.TRUE.equals(enabled)) {
                    if (!MiBlurBridge.setPassWindowBlurEnabled(target, true)) {
                        postToDecor(
                                "pairing sampler recovery",
                                () -> onPrismalFailure(new IllegalStateException(
                                        "pairing PassBlur sampler disabled")));
                        return true;
                    }
                }
                if (!MiBlurBridge.setPassWindowBlurRadius(target, 0)) {
                    postToDecor(
                            "pairing blur-radius recovery",
                            () -> onPrismalFailure(new IllegalStateException(
                                    "pairing fallback blur reappeared")));
                }
                return true;
            };
            materialGuardObserver = observer;
            materialGuard = listener;
            observer.addOnPreDrawListener(listener);
        }

        private void removeMaterialGuard() {
            ViewTreeObserver observer = materialGuardObserver;
            ViewTreeObserver.OnPreDrawListener listener = materialGuard;
            materialGuardObserver = null;
            materialGuard = null;
            if (observer != null && listener != null) {
                try {
                    if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
                } catch (Throwable ignored) {}
            }
        }

        private void postToDecor(String operation, Runnable action) {
            if (released || action == null) return;
            try {
                if (!decor.post(() -> {
                    try {
                        if (!released) action.run();
                    } catch (Throwable error) {
                        log(operation + " callback failed: " + error);
                    }
                })) {
                    log(operation + " callback rejected because dialog decor is inactive");
                }
            } catch (Throwable error) {
                log(operation + " callback enqueue failed: " + error);
            }
        }

        void release() {
            if (released) return;
            released = true;
            removeMaterialGuard();

            try { decor.removeOnAttachStateChangeListener(this); } catch (Throwable ignored) {}
            try { decor.removeOnLayoutChangeListener(this); } catch (Throwable ignored) {}

            SystemUiHandleMenuGlassOutputView output = prismalOutput;
            prismalOutput = null;
            if (output != null) {
                try { output.dispose(); } catch (Throwable ignored) {}
            }

            SystemUiHandleMenuPrismalSession session = prismalSession;
            prismalSession = null;
            if (session != null) {
                try { session.shutdown(); } catch (Throwable ignored) {}
            }

            View panel = target;
            target = null;
            if (panel != null) {
                if (replacementBlurApplied) {
                    try { MiBlurBridge.clearPassWindowBlur(panel); } catch (Throwable ignored) {}
                }
                try {
                    if (panel.getBackground() == null && stockBackground != null) {
                        panel.setBackground(stockBackground);
                    }
                } catch (Throwable ignored) {}
            }
            replacementBlurApplied = false;
            prismalPresented = false;
            bound = false;
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            tryBind();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            releaseObserved(this);
        }

        @Override
        public void onLayoutChange(
                View view,
                int left,
                int top,
                int right,
                int bottom,
                int oldLeft,
                int oldTop,
                int oldRight,
                int oldBottom) {
            tryBind();
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

    private static float resolveDialogCornerRadius(View panel) {
        if (panel == null) return 1f;
        String resourcePackage = null;
        try {
            if (panel.getId() != View.NO_ID) {
                resourcePackage = panel.getResources().getResourcePackageName(panel.getId());
            }
        } catch (Throwable ignored) {}

        String contextPackage = null;
        try { contextPackage = panel.getContext().getPackageName(); }
        catch (Throwable ignored) {}

        String[] packages = new String[]{resourcePackage, contextPackage, "com.android.systemui"};
        String[] names = new String[]{
                "miuix_appcompat_dialog_bg_corner_radius",
                "miuix_appcompat_window_dialog_radius"
        };
        for (String name : names) {
            for (String packageName : packages) {
                if (packageName == null || packageName.isEmpty()) continue;
                try {
                    int id = panel.getResources().getIdentifier(name, "dimen", packageName);
                    if (id != 0) return panel.getResources().getDimension(id);
                } catch (Throwable ignored) {}
            }
        }
        return 28f * panel.getResources().getDisplayMetrics().density;
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
