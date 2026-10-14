package com.hellovoid.liquiddock;

import android.view.View;

/** One semantic entry point for widget background/material ownership. */
final class LauncherWidgetBackgroundController {
    private LauncherWidgetBackgroundController() {}

    static void claim(View host) {
        if (host == null) return;
        // Release color claims before any provider material/background changes.
        LauncherWidgetWhiteningExecutor.release(host);
        LauncherManualDiscoveryBridge.ensureRegistered(host.getContext());
        // Discovery must observe provider-owned properties before LiquidDock clears the vendor
        // material. In particular Calendar's direct RemoteViews root owns the background that the
        // material suppressor intentionally removes immediately afterwards.
        LauncherWidgetComponentDiscovery.scan(host);
        LauncherGlassVendorMaterialSuppressor.claimWidgetMaterial(host);
        if (isMamlHost(host)) {
            LauncherMamlBackgroundRuleExecutor.claim(host);
        }
        LauncherWidgetComponentSelectionExecutor.claim(host);
    }

    static void claimLoadedMamlRoot(View host, Object root) {
        if (host == null || root == null || !isMamlHost(host)) return;
        // A root-loaded callback can precede the Workspace bind. Do not hide MAML elements
        // or claim its vendor material unless a real widget-glass node owns this host.
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
        if (node == null || node.kind() != LauncherGlassDragState.Kind.WIDGET
                || !GlassRuntimeState.isWidgetEnabled()
                || !LauncherGlassHierarchy.isWorkspace(host)) return;
        LauncherManualDiscoveryBridge.ensureRegistered(host.getContext());
        // Native MAML enable_background_blur belongs to Launcher and is not a
        // LiquidDock claim; the loaded root only needs visual component rules.
        LauncherMamlBackgroundRuleExecutor.claimLoadedRoot(host, root);
        LauncherWidgetComponentSelectionExecutor.claimLoadedMamlRoot(host, root);
    }

    static void release(View host) {
        if (host == null) return;
        // User MAML claims observe the state after bundled compatibility rules have run. Restore
        // them first, then let the bundled rule executor restore the provider's real mShow value.
        LauncherWidgetWhiteningExecutor.release(host);
        LauncherWidgetComponentSelectionExecutor.release(host);
        if (isMamlHost(host)) {
            LauncherMamlBackgroundRuleExecutor.release(host);
        }
        LauncherGlassVendorMaterialSuppressor.releaseWidgetMaterial(host);
        // MAML draws from ScreenElementRoot state, not Android View backgrounds. Request a
        // normal vendor redraw only after restored mShow and variable state are both committed.
        if (isMamlHost(host) && host.isAttachedToWindow()) {
            HookUtil.tryInvoke(host, "requestUpdate");
        }
    }

    private static boolean isMamlHost(View host) {
        String name = host.getClass().getName();
        return name.endsWith(".MaMlHostView") || name.contains(".maml.");
    }
}
