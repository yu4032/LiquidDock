package com.hellovoid.liquiddock;

import android.view.View;

/** One semantic entry point for widget background/material ownership. */
final class LauncherWidgetBackgroundController {
    private LauncherWidgetBackgroundController() {}

    static void claim(View host) {
        if (host == null) return;
        LauncherGlassVendorMaterialSuppressor.claimWidgetMaterial(host);
        if (isMamlHost(host)) {
            LauncherRemoteViewsBackgroundRuleExecutor.release(host);
            LauncherMamlBackgroundRuleExecutor.claim(host);
        } else {
            LauncherMamlBackgroundRuleExecutor.release(host);
            LauncherRemoteViewsBackgroundRuleExecutor.claim(host);
        }
    }

    static void claimLoadedMamlRoot(View host, Object root) {
        if (host == null || root == null || !isMamlHost(host)) return;
        LauncherRemoteViewsBackgroundRuleExecutor.release(host);
        LauncherMamlBackgroundRuleExecutor.claimLoadedRoot(host, root);
    }

    static void release(View host) {
        if (host == null) return;
        LauncherMamlBackgroundRuleExecutor.release(host);
        LauncherRemoteViewsBackgroundRuleExecutor.release(host);
        LauncherGlassVendorMaterialSuppressor.releaseWidgetMaterial(host);
    }

    private static boolean isMamlHost(View host) {
        String name = host.getClass().getName();
        return name.endsWith(".MaMlHostView") || name.contains(".maml.");
    }
}
