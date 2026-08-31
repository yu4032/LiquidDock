package com.hellovoid.liquiddock;

import android.view.View;

/** One semantic entry point for widget background/material ownership. */
final class LauncherWidgetBackgroundController {
    private LauncherWidgetBackgroundController() {}

    static void claim(View host) {
        if (host == null) return;
        // Discovery and explicit user hide rules are independent from LiquidDock glass. Every
        // widget remains selectable in the existing component picker even when its glass plate is
        // disabled by default.
        LauncherWidgetComponentDiscovery.scan(host);

        boolean glassEnabled = LauncherWidgetGlassSelection.isEnabled(host);
        if (glassEnabled) {
            LauncherGlassVendorMaterialSuppressor.claimWidgetMaterial(host);
            if (isMamlHost(host)) {
                LauncherMamlBackgroundRuleExecutor.claim(host);
            }
        } else {
            if (isMamlHost(host)) {
                LauncherMamlBackgroundRuleExecutor.release(host);
            }
            LauncherGlassVendorMaterialSuppressor.releaseWidgetMaterial(host);
        }

        LauncherWidgetComponentSelectionExecutor.claim(host);

        // The legacy static-glass binder still reaches this controller after creating a widget
        // node. For an unselected type, tear that node down synchronously before it can become a
        // persistent Workspace render participant. This keeps hidden-component behavior intact
        // without changing Dock or the shared static compositor lifecycle.
        if (!glassEnabled) disposeWidgetGlassNode(host);
    }

    static void claimLoadedMamlRoot(View host, Object root) {
        if (host == null || root == null || !isMamlHost(host)) return;
        boolean glassEnabled = LauncherWidgetGlassSelection.isEnabled(host);
        if (glassEnabled) {
            LauncherMamlBackgroundRuleExecutor.claimLoadedRoot(host, root);
        } else {
            LauncherMamlBackgroundRuleExecutor.release(host);
            LauncherGlassVendorMaterialSuppressor.releaseWidgetMaterial(host);
        }
        LauncherWidgetComponentSelectionExecutor.claimLoadedMamlRoot(host, root);
        if (!glassEnabled) disposeWidgetGlassNode(host);
    }

    static void release(View host) {
        if (host == null) return;
        // User MAML claims observe the state after bundled compatibility rules have run. Restore
        // them first, then let the bundled rule executor restore the provider's real mShow value.
        LauncherWidgetComponentSelectionExecutor.release(host);
        if (isMamlHost(host)) {
            LauncherMamlBackgroundRuleExecutor.release(host);
        }
        LauncherGlassVendorMaterialSuppressor.releaseWidgetMaterial(host);
    }

    private static void disposeWidgetGlassNode(View host) {
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
        if (node != null && node.kind() == LauncherGlassDragState.Kind.WIDGET) {
            node.dispose();
        }
        DockGlassItemRegistry.unregister(host);
    }

    private static boolean isMamlHost(View host) {
        String name = host.getClass().getName();
        return name.endsWith(".MaMlHostView") || name.contains(".maml.");
    }
}
