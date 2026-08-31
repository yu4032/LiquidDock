package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Applies user-selected widget component suppression and restores original state on release. */
final class LauncherWidgetComponentSelectionExecutor {
    private static final Map<View, Claim> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherWidgetComponentSelectionExecutor() {}

    static void claim(View host) {
        if (host == null) return;
        if (LauncherWidgetComponentDiscovery.isMamlHost(host)) {
            Object root = readField(host, "mRoot");
            if (root != null) claimLoadedMamlRoot(host, root);
            return;
        }
        release(host);
        Set<String> encoded = ConfigReader.load().stringSet(WidgetComponentStore.SELECTION_KEY);
        if (encoded.isEmpty()) return;
        String provider = LauncherWidgetComponentDiscovery.providerIdentity(host);
        List<WidgetComponentStore.Descriptor> selectors = selectors(encoded, true, provider);
        if (selectors.isEmpty()) return;
        View content = LauncherWidgetComponentDiscovery.resolveRemoteViewsContent(host);
        if (!(content instanceof ViewGroup)) return;
        ArrayList<ViewClaim> claims = new ArrayList<>();
        ViewGroup group = (ViewGroup) content;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectRemote(group.getChildAt(i), selectors, claims);
        }
        if (!claims.isEmpty()) CLAIMS.put(host, new Claim(claims, List.of()));
    }

    static void claimLoadedMamlRoot(View host, Object root) {
        if (host == null || root == null) return;
        release(host);
        WidgetBackgroundIdentity identity = identity(host);
        if (identity == null || identity.productId == null) return;
        Set<String> encoded = ConfigReader.load().stringSet(WidgetComponentStore.SELECTION_KEY);
        List<WidgetComponentStore.Descriptor> selectors =
                selectors(encoded, false, identity.productId);
        if (selectors.isEmpty()) return;
        ArrayList<MamlClaim> claims = new ArrayList<>();
        for (WidgetComponentStore.Descriptor selector : selectors) {
            Object target = HookUtil.invoke(root, "findElement", selector.name);
            if (target == null || !selector.className.equals(target.getClass().getName())) continue;
            boolean originalShow = readBooleanField(target, "mShow", true);
            claims.add(new MamlClaim(target, originalShow));
            HookUtil.invoke(target, "show", false);
        }
        if (!claims.isEmpty()) CLAIMS.put(host, new Claim(List.of(), claims));
    }

    static void release(View host) {
        if (host == null) return;
        Claim claim = CLAIMS.remove(host);
        if (claim == null) return;
        for (ViewClaim item : claim.views) {
            if (item.view != null) item.view.setVisibility(item.originalVisibility);
        }
        for (MamlClaim item : claim.maml) {
            HookUtil.invoke(item.element, "show", item.originalShow);
        }
    }

    private static void collectRemote(
            View view, List<WidgetComponentStore.Descriptor> selectors, List<ViewClaim> claims) {
        if (view == null) return;
        String resource = LauncherWidgetComponentDiscovery.resourceEntryName(view);
        String className = view.getClass().getName();
        for (WidgetComponentStore.Descriptor selector : selectors) {
            if (selector.name.equals(resource) && selector.className.equals(className)) {
                claims.add(new ViewClaim(view, view.getVisibility()));
                view.setVisibility(View.INVISIBLE);
                break;
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectRemote(group.getChildAt(i), selectors, claims);
        }
    }

    private static List<WidgetComponentStore.Descriptor> selectors(
            Set<String> encoded, boolean remote, String owner) {
        if (encoded == null || encoded.isEmpty() || owner == null) return List.of();
        ArrayList<WidgetComponentStore.Descriptor> result = new ArrayList<>();
        for (String value : encoded) {
            WidgetComponentStore.Descriptor descriptor = WidgetComponentStore.parseSelector(value);
            if (descriptor == null || descriptor.isRemoteViews() != remote
                    || !owner.equals(descriptor.owner)) continue;
            result.add(descriptor);
        }
        return result;
    }

    private static WidgetBackgroundIdentity identity(View host) {
        Object itemInfo = HookUtil.invoke(host, "getItemInfo");
        if (itemInfo == null) return null;
        return new WidgetBackgroundIdentity(
                "maml",
                stringField(itemInfo, "productId"),
                stringField(itemInfo, "appPackage"),
                intField(itemInfo, "spanX", -1),
                intField(itemInfo, "spanY", -1),
                intField(itemInfo, "configSpanX", -1),
                intField(itemInfo, "configSpanY", -1));
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }

    private static String stringField(Object target, String name) {
        Object value = readField(target, name);
        return value instanceof String ? (String) value : null;
    }

    private static int intField(Object target, String name, int fallback) {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean readBooleanField(Object target, String name, boolean fallback) {
        try { return HookUtil.getBooleanField(target, name); }
        catch (Throwable ignored) { return fallback; }
    }

    private static final class Claim {
        final List<ViewClaim> views;
        final List<MamlClaim> maml;
        Claim(List<ViewClaim> views, List<MamlClaim> maml) {
            this.views = List.copyOf(views);
            this.maml = List.copyOf(maml);
        }
    }

    private static final class ViewClaim {
        final View view;
        final int originalVisibility;
        ViewClaim(View view, int originalVisibility) {
            this.view = view;
            this.originalVisibility = originalVisibility;
        }
    }

    private static final class MamlClaim {
        final Object element;
        final boolean originalShow;
        MamlClaim(Object element, boolean originalShow) {
            this.element = element;
            this.originalShow = originalShow;
        }
    }
}
