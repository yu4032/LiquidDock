package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Executes declarative MAML hide-element rules without widget-specific Java branches. */
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
        Object itemInfo = invokeOptional(host, "getItemInfo");
        WidgetBackgroundIdentity identity = new WidgetBackgroundIdentity(
                "maml",
                readStringField(itemInfo, "productId"),
                readStringField(itemInfo, "appPackage"),
                readIntField(itemInfo, "spanX", -1),
                readIntField(itemInfo, "spanY", -1),
                readIntField(itemInfo, "configSpanX", -1),
                readIntField(itemInfo, "configSpanY", -1));
        WidgetBackgroundRule rule = RULES.match(identity);
        String identityText = describe(identity);

        if (root != null) {
            LauncherWidgetComponentDiscovery.scanMaml(host, identity, root);
        }

        if (rule == null) {
            release(host);
            MainHook.log(LOG_TAG + identityText
                    + " rule=none rootLoaded=" + (root != null)
                    + " suppressed=false");
            return;
        }
        if (root == null) {
            release(host);
            MainHook.log(LOG_TAG + identityText
                    + " rule=" + rule.id()
                    + " root=null targetFound=false suppressed=false");
            return;
        }

        List<String> elementNames = rule.elementNames();
        if (elementNames.isEmpty()) {
            release(host);
            MainHook.log(LOG_TAG + identityText
                    + " rule=" + rule.id()
                    + " root=" + root.getClass().getSimpleName()
                    + " diagnosticOnly=true suppressed=false");
            dumpNamedElementsOnce(identity, rule, root);
            return;
        }

        // Resolve the whole rule before mutating any ScreenElement. A structural mismatch must
        // never leave a partially transparent widget.
        List<Object> resolved = new ArrayList<>(elementNames.size());
        String missingName = null;
        for (String elementName : elementNames) {
            Object target = invokeOptional(root, "findElement", elementName);
            if (target == null) {
                missingName = elementName;
                break;
            }
            resolved.add(target);
        }
        if (resolved.size() != elementNames.size()) {
            release(host);
            MainHook.log(LOG_TAG + identityText
                    + " rule=" + rule.id()
                    + " root=" + root.getClass().getSimpleName()
                    + " target=" + missingName
                    + " targetFound=false suppressed=false");
            dumpNamedElementsOnce(identity, rule, root);
            return;
        }

        Claim previous = CLAIMS.get(host);
        if (previous != null && previous.matches(root, resolved)) {
            for (Object target : resolved) invokeOptional(target, "show", false);
            MainHook.log(LOG_TAG + identityText
                    + " rule=" + rule.id()
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
        for (Object target : resolved) invokeOptional(target, "show", false);

        MainHook.log(LOG_TAG + identityText
                + " rule=" + rule.id()
                + " targets=" + elementNames
                + " targetFound=true suppressed=" + allHidden(resolved));
    }

    static void release(View host) {
        if (host == null) return;
        Claim claim = CLAIMS.remove(host);
        if (claim != null) restore(claim);
    }

    private static Object invokeOptional(Object target, String methodName, Object... args) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(target, methodName, args);
        if (!result.succeeded()) {
            MainHook.log(LOG_TAG + " " + methodName + " unavailable: " + result.failure());
            return null;
        }
        return result.value();
    }

    private static boolean allHidden(List<Object> elements) {
        for (Object element : elements) {
            if (readBooleanField(element, "mShow", true)) return false;
        }
        return true;
    }

    private static void restore(Claim claim) {
        for (ElementClaim element : claim.elements) {
            invokeOptional(element.element, "show", element.originalShow);
        }
    }

    /** Dump Launcher 4.50's real ScreenElementRoot registry once per root, diagnostic-only. */
    private static void dumpNamedElementsOnce(
            WidgetBackgroundIdentity identity, WidgetBackgroundRule rule, Object root) {
        if (root == null) return;
        synchronized (DUMPED_ROOTS) {
            if (DUMPED_ROOTS.containsKey(root)) return;
            DUMPED_ROOTS.put(root, Boolean.TRUE);
        }

        Object value = readField(root, "mElements");
        if (!(value instanceof Map)) {
            MainHook.log(DUMP_LOG_TAG + describe(identity)
                    + " rule=" + rule.id() + " registry=mElements unavailable");
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
                    + " rule=" + rule.id() + " count=0 chunk=1/1 names=[]");
            return;
        }
        for (int chunk = 0; chunk < chunks; chunk++) {
            int from = chunk * DUMP_CHUNK_SIZE;
            int to = Math.min(names.size(), from + DUMP_CHUNK_SIZE);
            MainHook.log(DUMP_LOG_TAG + describe(identity)
                    + " rule=" + rule.id()
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

    private static String readStringField(Object target, String name) {
        Object value = readField(target, name);
        return value instanceof String ? (String) value : null;
    }

    private static int readIntField(Object target, String name, int fallback) {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
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
