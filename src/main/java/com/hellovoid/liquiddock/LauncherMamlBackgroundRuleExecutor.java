package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Executes built-in or exact user-selected MAML hide-element rules. */
final class LauncherMamlBackgroundRuleExecutor {
    private static final String LOG_TAG = "[MamlWidgetBg]";
    private static final String DUMP_LOG_TAG = "[MamlWidgetBgDump]";
    private static final int DUMP_CHUNK_SIZE = 16;
    private static final WidgetBackgroundRuleEngine RULES =
            WidgetBackgroundRuleEngine.loadBundled();

    private static final Map<View, Claim> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> DUMPED_ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherMamlBackgroundRuleExecutor() {}

    static void claim(View host) {
        if (host == null) return;
        claimLoadedRoot(host, readField(host, "mRoot"));
    }

    static void claimLoadedRoot(View host, Object root) {
        if (host == null) return;
        WidgetBackgroundIdentity identity = WidgetBackgroundIdentityReader.maml(host);
        String identityText = describe(identity);

        if (root == null) {
            release(host);
            MainHook.log(LOG_TAG + identityText + " root=null suppressed=false");
            return;
        }

        publishDiscovery(identity, root);

        Set<String> userTargets = new LinkedHashSet<>();
        for (WidgetBackgroundUserRule rule : WidgetBackgroundUserPreferences.loadRules()) {
            if (rule.targetKind() == WidgetBackgroundUserRule.TargetKind.MAML_ELEMENT
                    && rule.matches(identity)) {
                userTargets.add(rule.target());
            }
        }

        String source;
        WidgetBackgroundRule builtIn = null;
        List<String> elementNames;
        if (!userTargets.isEmpty()) {
            source = "user";
            elementNames = new ArrayList<>(userTargets);
            Collections.sort(elementNames);
        } else if (WidgetBackgroundUserPreferences.builtInRulesEnabled()) {
            builtIn = RULES.match(identity);
            if (builtIn == null) {
                release(host);
                MainHook.log(LOG_TAG + identityText
                        + " rule=none rootLoaded=true suppressed=false");
                return;
            }
            source = "builtin:" + builtIn.id();
            elementNames = builtIn.elementNames();
            if (elementNames.isEmpty()) {
                release(host);
                MainHook.log(LOG_TAG + identityText
                        + " rule=" + builtIn.id()
                        + " root=" + root.getClass().getSimpleName()
                        + " diagnosticOnly=true suppressed=false");
                dumpNamedElementsOnce(identity, builtIn.id(), root);
                return;
            }
        } else {
            release(host);
            MainHook.log(LOG_TAG + identityText + " rules=disabled suppressed=false");
            return;
        }

        // Resolve the whole selection before mutating anything. A stale user target or changed
        // provider structure must never leave a partially hidden widget.
        List<Object> resolved = new ArrayList<>(elementNames.size());
        String missingName = null;
        for (String elementName : elementNames) {
            Object target = HookUtil.invoke(root, "findElement", elementName);
            if (target == null) {
                missingName = elementName;
                break;
            }
            resolved.add(target);
        }
        if (resolved.size() != elementNames.size()) {
            release(host);
            MainHook.log(LOG_TAG + identityText
                    + " source=" + source
                    + " root=" + root.getClass().getSimpleName()
                    + " target=" + missingName
                    + " targetFound=false suppressed=false");
            dumpNamedElementsOnce(identity, source, root);
            return;
        }

        Claim previous = CLAIMS.get(host);
        if (previous != null && previous.matches(root, resolved)) {
            for (Object target : resolved) HookUtil.invoke(target, "show", false);
            MainHook.log(LOG_TAG + identityText
                    + " source=" + source
                    + " targets=" + elementNames
                    + " targetFound=true suppressed=" + allHidden(resolved));
            return;
        }
        if (previous != null) {
            restore(previous);
            CLAIMS.remove(host);
        }

        List<ElementClaim> elementClaims = new ArrayList<>(resolved.size());
        for (Object target : resolved) {
            elementClaims.add(new ElementClaim(
                    target, readBooleanField(target, "mShow", true)));
        }
        CLAIMS.put(host, new Claim(root, elementClaims));
        for (Object target : resolved) HookUtil.invoke(target, "show", false);

        MainHook.log(LOG_TAG + identityText
                + " source=" + source
                + " targets=" + elementNames
                + " targetFound=true suppressed=" + allHidden(resolved));
    }

    static void release(View host) {
        if (host == null) return;
        Claim claim = CLAIMS.remove(host);
        if (claim != null) restore(claim);
    }

    private static void publishDiscovery(WidgetBackgroundIdentity identity, Object root) {
        Object value = readField(root, "mElements");
        if (!(value instanceof Map)) return;
        Map<?, ?> elements = (Map<?, ?>) value;
        List<WidgetBackgroundDiscoveryTarget> targets = new ArrayList<>();
        for (Map.Entry<?, ?> entry : elements.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (name == null || name.isEmpty() || "null".equals(name)) continue;
            Object stored = entry.getValue();
            Object element = stored instanceof WeakReference
                    ? ((WeakReference<?>) stored).get() : stored;
            if (element == null) continue;
            targets.add(new WidgetBackgroundDiscoveryTarget(
                    WidgetBackgroundUserRule.TargetKind.MAML_ELEMENT,
                    name, element.getClass().getSimpleName()));
        }
        targets.sort((a, b) -> a.name().compareTo(b.name()));
        WidgetBackgroundDiscoveryStore.publish(new WidgetBackgroundDiscoverySnapshot(
                identity, targets, System.currentTimeMillis()));
    }

    private static boolean allHidden(List<Object> elements) {
        for (Object element : elements) {
            if (readBooleanField(element, "mShow", true)) return false;
        }
        return true;
    }

    private static void restore(Claim claim) {
        for (ElementClaim element : claim.elements) {
            HookUtil.invoke(element.element, "show", element.originalShow);
        }
    }

    /** Dump Launcher 4.50's real ScreenElementRoot registry once per root, diagnostic-only. */
    private static void dumpNamedElementsOnce(
            WidgetBackgroundIdentity identity, String source, Object root) {
        if (root == null) return;
        synchronized (DUMPED_ROOTS) {
            if (DUMPED_ROOTS.containsKey(root)) return;
            DUMPED_ROOTS.put(root, Boolean.TRUE);
        }

        Object value = readField(root, "mElements");
        if (!(value instanceof Map)) {
            MainHook.log(DUMP_LOG_TAG + describe(identity)
                    + " source=" + source + " registry=mElements unavailable");
            return;
        }

        Map<?, ?> elements = (Map<?, ?>) value;
        List<String> names = new ArrayList<>(elements.size());
        for (Map.Entry<?, ?> entry : elements.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Object stored = entry.getValue();
            Object element = stored instanceof WeakReference
                    ? ((WeakReference<?>) stored).get() : stored;
            String type = element != null ? element.getClass().getSimpleName() : "collected";
            names.add(name + ":" + type);
        }
        Collections.sort(names);

        int chunks = Math.max(1, (names.size() + DUMP_CHUNK_SIZE - 1) / DUMP_CHUNK_SIZE);
        if (names.isEmpty()) {
            MainHook.log(DUMP_LOG_TAG + describe(identity)
                    + " source=" + source + " count=0 chunk=1/1 names=[]");
            return;
        }
        for (int chunk = 0; chunk < chunks; chunk++) {
            int from = chunk * DUMP_CHUNK_SIZE;
            int to = Math.min(names.size(), from + DUMP_CHUNK_SIZE);
            MainHook.log(DUMP_LOG_TAG + describe(identity)
                    + " source=" + source
                    + " count=" + names.size()
                    + " chunk=" + (chunk + 1) + "/" + chunks
                    + " names=" + names.subList(from, to));
        }
    }

    private static String describe(WidgetBackgroundIdentity identity) {
        return " productId=" + identity.productId
                + " appPackage=" + identity.appPackage
                + " span=" + identity.spanX + "x" + identity.spanY
                + " configSpan=" + identity.configSpanX + "x" + identity.configSpanY;
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }

    private static boolean readBooleanField(Object target, String name, boolean fallback) {
        if (target == null) return fallback;
        try { return HookUtil.getBooleanField(target, name); }
        catch (Throwable ignored) { return fallback; }
    }

    private static final class Claim {
        final Object root;
        final List<ElementClaim> elements;

        Claim(Object root, List<ElementClaim> elements) {
            this.root = root;
            this.elements = List.copyOf(elements);
        }

        boolean matches(Object candidateRoot, List<Object> candidateElements) {
            if (root != candidateRoot || elements.size() != candidateElements.size()) return false;
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i).element != candidateElements.get(i)) return false;
            }
            return true;
        }
    }

    private static final class ElementClaim {
        final Object element;
        final boolean originalShow;

        ElementClaim(Object element, boolean originalShow) {
            this.element = element;
            this.originalShow = originalShow;
        }
    }
}
