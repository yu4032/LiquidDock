package com.hellovoid.liquiddock;

import java.lang.reflect.Executable;
import java.lang.reflect.Constructor;
import java.util.Objects;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

/**
 * API 102 hook ledger. Explicitly named hooks are eligible for future replacement;
 * legacy HookUtil/direct interceptors are counted but never implicitly replaced.
 *
 * <p>Do not infer hot-reload readiness from this registry: native producers, listeners,
 * receivers and long-lived Android callbacks are not owned by this registry. No retained HookHandle
 * or Hooker may be transferred through HotReloadingParam saved instance state.</p>
 */
final class Api102HookRegistry {
    interface Installer {
        XposedInterface.HookHandle install(
                Method method, String id, XposedInterface.Hooker callback);
    }

    /** The shared HookUtil entry point also covers constructors and custom priorities. */
    interface SiteInstaller {
        XposedInterface.HookHandle install(
                Executable executable, String id, int priority, XposedInterface.Hooker callback);
    }

    /** One lexical installation point and one actual target are one replaceable interceptor. */
    private static final class SiteKey {
        final Executable executable;
        final String site;
        final int priority;

        SiteKey(Executable executable, String site, int priority) {
            this.executable = executable;
            this.site = site;
            this.priority = priority;
        }

        @Override public boolean equals(Object value) {
            if (!(value instanceof SiteKey)) return false;
            SiteKey other = (SiteKey) value;
            return priority == other.priority
                    && site.equals(other.site) && executable.equals(other.executable);
        }

        @Override public int hashCode() {
            return Objects.hash(executable, site, priority);
        }
    }

    private static final Api102HookRegistry PROCESS = new Api102HookRegistry(
            (method, id, callback) -> Api101Bridge.module().hook(method)
                    .setId(id)
                    .intercept(callback),
            (executable, id, priority, callback) -> Api101Bridge.module().hook(executable)
                    .setId(id)
                    .setPriority(priority)
                    .intercept(callback));

    private final Installer installer;
    private final SiteInstaller siteInstaller;
    private final Map<String, XposedInterface.HookHandle> handles = new LinkedHashMap<>();
    // Each HookUtil installation site has its own slot, even if other sites hook the same
    // executable. Repeated registration of that *same* slot replaces rather than stacking.
    private final Map<SiteKey, XposedInterface.HookHandle> siteHandles = new LinkedHashMap<>();
    private final Map<SiteKey, String> siteIds = new LinkedHashMap<>();
    // Existing HookUtil callers can install multiple callbacks on the same Method. Retain each
    // returned handle without assigning a fake ID or changing its installation semantics.
    private final List<XposedInterface.HookHandle> unnamedHandles = new ArrayList<>();

    Api102HookRegistry(Installer installer) {
        this(installer, (executable, id, priority, callback) ->
                Api101Bridge.module().hook(executable)
                        .setId(id).setPriority(priority).intercept(callback));
    }

    Api102HookRegistry(Installer installer, SiteInstaller siteInstaller) {
        if (installer == null || siteInstaller == null) {
            throw new IllegalArgumentException("installer/siteInstaller");
        }
        this.installer = installer;
        this.siteInstaller = siteInstaller;
    }

    static Api102HookRegistry processRegistry() {
        return PROCESS;
    }

    static String stableId(String domain, Executable executable) {
        if (domain == null || domain.isBlank() || executable == null) {
            throw new IllegalArgumentException("domain/executable");
        }
        StringBuilder id = new StringBuilder("liquiddock.")
                .append(domain)
                .append(':')
                .append(executable.getDeclaringClass().getName())
                .append('#')
                .append(executable instanceof Method
                        ? executable.getName() : "<init>")
                .append('(');
        Class<?>[] parameters = executable.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) id.append(',');
            id.append(parameters[i].getName());
        }
        return id.append(')').toString();
    }

    static XposedInterface.HookHandle hookIdentified(
            Method method, String id, XposedInterface.Hooker callback) {
        return PROCESS.install(method, id, callback);
    }

    static boolean rollbackIdentified(List<String> ids) {
        return PROCESS.rollback(ids);
    }

    static int installedCount() {
        return PROCESS.count();
    }

    static void registerUnidentified(XposedInterface.HookHandle handle) {
        PROCESS.trackUnidentified(handle);
    }

    static XposedInterface.HookHandle hookSite(Executable executable, String site,
            int priority, XposedInterface.Hooker callback) {
        return PROCESS.installSite(executable, site, priority, callback);
    }

    /**
     * Process-wide API 102 replacement for all HookUtil call sites.
     *
     * Multiple independent call sites targeting the same Method retain separate callback
     * slots and their original priorities. A subsequent registration at the same site
     * atomically updates only its own slot; no gap or duplicate callback is introduced.
     *
     * Use the actual Executable as part of the identity: two class loaders can define
     * distinct Methods with identical textual signatures.
     */
    synchronized XposedInterface.HookHandle installSite(
            Executable executable, String site, int priority, XposedInterface.Hooker callback) {
        if (executable == null || site == null || site.isBlank() || callback == null) {
            throw new IllegalArgumentException("executable/site/callback");
        }
        SiteKey key = new SiteKey(executable, site, priority);
        XposedInterface.HookHandle previous = siteHandles.get(key);
        if (previous != null) {
            XposedInterface.HookHandle replacement = previous.replaceHook(callback);
            if (replacement == null) {
                throw new IllegalStateException("null replacement HookHandle: " + site);
            }
            siteHandles.put(key, replacement);
            return replacement;
        }
        executable.setAccessible(true);
        // Site IDs are unique even for different ClassLoader identities with the same name.
        // The ordinal is only a process-local discriminator: APK hot reload remains disabled.
        String id = "liquiddock.site:" + site + ':' + stableSiteTarget(executable)
                + ":p" + priority + ":n" + (siteIds.size() + 1);
        XposedInterface.HookHandle handle = siteInstaller.install(
                executable, id, priority, callback);
        if (handle == null) throw new IllegalStateException("null HookHandle: " + id);
        siteHandles.put(key, handle);
        siteIds.put(key, id);
        return handle;
    }

    private static String stableSiteTarget(Executable executable) {
        StringBuilder builder = new StringBuilder(executable.getDeclaringClass().getName())
                .append('#').append(executable instanceof Constructor ? "<init>" : executable.getName())
                .append('(');
        Class<?>[] types = executable.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(types[i].getName());
        }
        return builder.append(')').toString();
    }

    synchronized XposedInterface.HookHandle install(
            Method method, String id, XposedInterface.Hooker callback) {
        if (method == null || callback == null || id == null || id.isBlank()) {
            throw new IllegalArgumentException("method/id/callback");
        }
        if (handles.containsKey(id)) {
            throw new IllegalStateException("duplicate API 102 hook id: " + id);
        }
        method.setAccessible(true);
        XposedInterface.HookHandle handle = installer.install(method, id, callback);
        if (handle == null) throw new IllegalStateException("null API 102 HookHandle: " + id);
        handles.put(id, handle);
        return handle;
    }

    /**
     * API 102 in-process atomic callback replacement. The framework preserves the existing
     * hook's id, priority and exception mode. Only the latest returned handle remains owned;
     * a failed replacement must leave the previous handle untouched.
     *
     * This is independent of module hot reload and does not release Android/native resources.
     */
    synchronized XposedInterface.HookHandle replaceIdentified(
            String id, XposedInterface.Hooker next) {
        if (id == null || id.isBlank() || next == null) {
            throw new IllegalArgumentException("id/next");
        }
        XposedInterface.HookHandle previous = handles.get(id);
        if (previous == null) throw new IllegalStateException("unregistered hook id: " + id);
        XposedInterface.HookHandle replacement = previous.replaceHook(next);
        if (replacement == null) {
            throw new IllegalStateException("null replacement HookHandle: " + id);
        }
        handles.put(id, replacement);
        return replacement;
    }

    synchronized int count() {
        return handles.size() + siteHandles.size() + unnamedHandles.size();
    }

    synchronized int unidentifiedCount() {
        return unnamedHandles.size();
    }

    synchronized void trackUnidentified(XposedInterface.HookHandle handle) {
        if (handle == null) throw new IllegalArgumentException("HookHandle");
        unnamedHandles.add(handle);
    }

    synchronized List<String> idSnapshot() {
        return new ArrayList<>(handles.keySet());
    }

    /** Reverse order matches stack-like installation and safely cleans partial installs. */
    synchronized boolean rollback(List<String> ids) {
        if (ids == null) return true;
        boolean complete = true;
        for (int i = ids.size() - 1; i >= 0; i--) {
            XposedInterface.HookHandle handle = handles.get(ids.get(i));
            if (handle == null) continue;
            try {
                handle.unhook();
                handles.remove(ids.get(i));
            } catch (Throwable error) {
                // Keep the handle in the ledger and the feature in installed state. Retrying
                // registration after a failed unhook would duplicate callbacks.
                complete = false;
                try {
                    Api101Bridge.log("[DC][API102] identified-hook rollback failed id="
                            + ids.get(i), error);
                } catch (Throwable ignored) {
                    // Logging must never abort the rest of a multi-hook rollback.
                }
            }
        }
        return complete;
    }
}
