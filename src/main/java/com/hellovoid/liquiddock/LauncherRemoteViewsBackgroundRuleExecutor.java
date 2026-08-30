package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Applies exact user-selected RemoteViews background removals discovered from live widget Views. */
final class LauncherRemoteViewsBackgroundRuleExecutor {
    private static final String LOG_TAG = "[RemoteViewsWidgetBg]";
    private static final Map<View, Claim> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherRemoteViewsBackgroundRuleExecutor() {}

    static void claim(View host) {
        if (!(host instanceof ViewGroup)) return;
        WidgetBackgroundIdentity identity = WidgetBackgroundIdentityReader.remoteViews(host);
        View root = singleRemoteViewsRoot((ViewGroup) host);
        if (root == null) {
            release(host);
            return;
        }

        Map<String, View> namedBackgrounds = new LinkedHashMap<>();
        collectNamedBackgroundViews(root, namedBackgrounds);
        publishDiscovery(identity, namedBackgrounds);

        List<String> selected = new ArrayList<>();
        for (WidgetBackgroundUserRule rule : WidgetBackgroundUserPreferences.loadRules()) {
            if (rule.targetKind() == WidgetBackgroundUserRule.TargetKind.REMOTE_VIEWS_RESOURCE
                    && rule.matches(identity)) {
                selected.add(rule.target());
            }
        }
        Collections.sort(selected);
        if (selected.isEmpty()) {
            release(host);
            return;
        }

        List<View> resolved = new ArrayList<>(selected.size());
        for (String resourceName : selected) {
            View target = namedBackgrounds.get(resourceName);
            if (target == null) {
                release(host);
                MainHook.log(LOG_TAG + " missing selected resource=" + resourceName
                        + " identity=" + describe(identity));
                return;
            }
            resolved.add(target);
        }

        Claim previous = CLAIMS.get(host);
        if (previous != null && previous.matches(root, resolved)) {
            for (View target : resolved) target.setBackground(null);
            return;
        }
        if (previous != null) restore(previous);

        List<ViewClaim> claims = new ArrayList<>(resolved.size());
        for (View target : resolved) claims.add(new ViewClaim(target, target.getBackground()));
        CLAIMS.put(host, new Claim(root, claims));
        for (View target : resolved) target.setBackground(null);
        MainHook.log(LOG_TAG + " user targets hidden=" + selected
                + " identity=" + describe(identity));
    }

    static void release(View host) {
        if (host == null) return;
        Claim claim = CLAIMS.remove(host);
        if (claim != null) restore(claim);
    }

    private static View singleRemoteViewsRoot(ViewGroup host) {
        if (host.getChildCount() != 1) return null;
        return host.getChildAt(0);
    }

    private static void collectNamedBackgroundViews(View view, Map<String, View> out) {
        if (view == null) return;
        if (view.getId() != View.NO_ID && view.getBackground() != null) {
            try {
                String resourceName = view.getResources().getResourceName(view.getId());
                // The generic Launcher 4.50 widget_frame owner is already handled by
                // LauncherGlassVendorMaterialSuppressor. Do not expose the same owner twice.
                if (!"android:id/widget_frame".equals(resourceName)) out.put(resourceName, view);
            } catch (Throwable ignored) {}
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectNamedBackgroundViews(group.getChildAt(i), out);
        }
    }

    private static void publishDiscovery(
            WidgetBackgroundIdentity identity, Map<String, View> namedBackgrounds) {
        List<WidgetBackgroundDiscoveryTarget> targets = new ArrayList<>();
        for (Map.Entry<String, View> entry : namedBackgrounds.entrySet()) {
            View view = entry.getValue();
            targets.add(new WidgetBackgroundDiscoveryTarget(
                    WidgetBackgroundUserRule.TargetKind.REMOTE_VIEWS_RESOURCE,
                    entry.getKey(), view.getClass().getSimpleName()));
        }
        targets.sort((a, b) -> a.name().compareTo(b.name()));
        WidgetBackgroundDiscoveryStore.publish(new WidgetBackgroundDiscoverySnapshot(
                identity, targets, System.currentTimeMillis()));
    }

    private static String describe(WidgetBackgroundIdentity identity) {
        return "type=" + identity.type
                + " appPackage=" + identity.appPackage
                + " span=" + identity.spanX + "x" + identity.spanY
                + " configSpan=" + identity.configSpanX + "x" + identity.configSpanY;
    }

    private static void restore(Claim claim) {
        for (ViewClaim viewClaim : claim.views) {
            if (viewClaim.view.getBackground() == null && viewClaim.original != null) {
                viewClaim.view.setBackground(viewClaim.original);
            }
        }
    }

    private static final class Claim {
        final View root;
        final List<ViewClaim> views;

        Claim(View root, List<ViewClaim> views) {
            this.root = root;
            this.views = List.copyOf(views);
        }

        boolean matches(View candidateRoot, List<View> candidates) {
            if (root != candidateRoot || views.size() != candidates.size()) return false;
            for (int i = 0; i < views.size(); i++) {
                if (views.get(i).view != candidates.get(i)) return false;
            }
            return true;
        }
    }

    private static final class ViewClaim {
        final View view;
        final Drawable original;

        ViewClaim(View view, Drawable original) {
            this.view = view;
            this.original = original;
        }
    }
}
