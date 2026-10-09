package com.hellovoid.liquiddock;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

/**
 * Stage-one owner for API 102 hook IDs and handles. It owns only explicitly enrolled hooks.
 *
 * <p>Do not infer hot-reload readiness from this registry: native producers, listeners,
 * receivers and other HookUtil/direct-hook callers are not enrolled yet. No retained HookHandle
 * or Hooker may be transferred through HotReloadingParam saved instance state.</p>
 */
final class Api102HookRegistry {
    interface Installer {
        XposedInterface.HookHandle install(
                Method method, String id, XposedInterface.Hooker callback);
    }

    private static final Api102HookRegistry PROCESS = new Api102HookRegistry(
            (method, id, callback) -> Api101Bridge.module().hook(method)
                    .setId(id)
                    .intercept(callback));

    private final Installer installer;
    private final Map<String, XposedInterface.HookHandle> handles = new LinkedHashMap<>();

    Api102HookRegistry(Installer installer) {
        if (installer == null) throw new IllegalArgumentException("installer");
        this.installer = installer;
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

    static void rollbackIdentified(List<String> ids) {
        PROCESS.rollback(ids);
    }

    static int installedCount() {
        return PROCESS.count();
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

    synchronized int count() {
        return handles.size();
    }

    synchronized List<String> idSnapshot() {
        return new ArrayList<>(handles.keySet());
    }

    /** Reverse order matches stack-like installation and safely cleans partial installs. */
    synchronized void rollback(List<String> ids) {
        if (ids == null) return;
        for (int i = ids.size() - 1; i >= 0; i--) {
            XposedInterface.HookHandle handle = handles.get(ids.get(i));
            if (handle == null) continue;
            try {
                handle.unhook();
                handles.remove(ids.get(i));
            } catch (Throwable error) {
                // A failed removal must not mask the initial vendor-install failure.
                Api101Bridge.log("[DC][API102] identified-hook rollback failed id="
                        + ids.get(i), error);
            }
        }
    }
}
