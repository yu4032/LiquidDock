package com.hellovoid.liquiddock;

import android.view.View;

import java.util.Set;

/** Resolves a Launcher widget host to the same type-level key used by the settings catalog. */
final class LauncherWidgetGlassSelection {
    private LauncherWidgetGlassSelection() {}

    static boolean isEnabled(View host) {
        String groupKey = groupKey(host);
        if (groupKey.isEmpty()) return false;
        Set<String> selected = ConfigReader.load().stringSet(WidgetGlassSelectionPolicy.SELECTION_KEY);
        return WidgetGlassSelectionPolicy.isEnabled(selected, groupKey);
    }

    static String groupKey(View host) {
        if (host == null) return "";
        if (LauncherWidgetComponentDiscovery.isMamlHost(host)) {
            Object itemInfo = HookUtil.invoke(host, "getItemInfo");
            Object productId = readField(itemInfo, "productId");
            return WidgetGlassSelectionPolicy.mamlGroupKey(
                    productId instanceof String ? (String) productId : "");
        }
        return WidgetGlassSelectionPolicy.remoteGroupKey(
                LauncherWidgetComponentDiscovery.providerIdentity(host));
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }
}
