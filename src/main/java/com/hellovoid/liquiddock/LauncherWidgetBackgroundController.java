package com.hellovoid.liquiddock;

import android.view.View;

/** One semantic entry point for widget background/material ownership. */
final class LauncherWidgetBackgroundController {
    private LauncherWidgetBackgroundController() {}

    static void claim(View host) {
        if (host == null) return;
        LauncherGlassVendorMaterialSuppressor.claimWidgetMaterial(host);
        LauncherWidgetComponentDiscovery.scan(host);
        if (isMamlHost(host)) {
            LauncherMamlBackgroundRuleExecutor.claim(host);
        }
        LauncherWidgetComponentSelectionExecutor.claim(host);
    }

    static void claimLoadedMamlRoot(View host, Object root) {
        if (host == null || root == null || !isMamlHost(host)) return;
        LauncherMamlBackgroundRuleExecutor.claimLoadedRoot(host, root);
        LauncherWidgetComponentSelectionExecutor.claimLoadedMamlRoot(host, root);
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

    private static boolean isMamlHost(View host) {
        String name = host.getClass().getName();
        return name.endsWith(".MaMlHostView") || name.contains(".maml.");
    }
}
