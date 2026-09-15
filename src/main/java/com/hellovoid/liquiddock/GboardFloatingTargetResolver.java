package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Resolves the one audited Gboard floating-popup provider across DEX/JADX binary-name views. */
final class GboardFloatingTargetResolver {
    /**
     * JADX renders classes from the DEX default package under a synthetic `defpackage` source
     * package. Runtime reflection still needs the real binary name, so probe that first.
     */
    private static final String[] PROVIDER_BINARY_NAMES = {"pev", "defpackage.pev"};

    static final class Target {
        final Class<?> providerClass;
        final Method showMethod;
        final Method hideMethod;
        final Field popupViewField;
        final String binaryName;

        Target(
                Class<?> providerClass,
                Method showMethod,
                Method hideMethod,
                Field popupViewField,
                String binaryName) {
            this.providerClass = providerClass;
            this.showMethod = showMethod;
            this.hideMethod = hideMethod;
            this.popupViewField = popupViewField;
            this.binaryName = binaryName;
        }
    }

    private GboardFloatingTargetResolver() {}

    static Target resolve(ClassLoader classLoader) throws Exception {
        if (classLoader == null) throw new IllegalArgumentException("classLoader == null");
        List<String> failures = new ArrayList<>();
        Throwable firstCause = null;
        for (String binaryName : PROVIDER_BINARY_NAMES) {
            try {
                Class<?> provider = Class.forName(binaryName, false, classLoader);
                Method show = HookUtil.findMethodExact(provider, "b", new Class<?>[0]);
                Method hide = HookUtil.findMethodExact(provider, "a", new Class<?>[0]);
                Field popupView = uniquePopupViewField(provider);
                return new Target(provider, show, hide, popupView, binaryName);
            } catch (Throwable error) {
                if (firstCause == null) firstCause = error;
                failures.add(binaryName + "=" + summarize(error));
            }
        }
        IllegalStateException unresolved = new IllegalStateException(
                "popup provider unresolved: " + String.join(", ", failures));
        if (firstCause != null) unresolved.initCause(firstCause);
        throw unresolved;
    }

    private static Field uniquePopupViewField(Class<?> provider) throws Exception {
        Field resolved = null;
        for (Field field : provider.getDeclaredFields()) {
            if (!View.class.isAssignableFrom(field.getType())) continue;
            if (resolved != null) {
                throw new NoSuchFieldException(
                        "ambiguous View fields: " + resolved.getName() + ", " + field.getName());
            }
            resolved = field;
        }
        if (resolved == null) throw new NoSuchFieldException("unique popup View field missing");
        resolved.setAccessible(true);
        return resolved;
    }

    private static String summarize(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : ":" + message);
    }
}
