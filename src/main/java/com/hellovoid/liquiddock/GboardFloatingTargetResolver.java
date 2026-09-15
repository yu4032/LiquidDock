package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Resolves the audited Gboard floating-popup provider from its real DEX binary name. */
final class GboardFloatingTargetResolver {
    // Verified directly from the supplied base APK: classes2.dex defines Lpev;.
    private static final String RUNTIME_PROVIDER = "pev";

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
        Class<?> provider = Class.forName(RUNTIME_PROVIDER, false, classLoader);
        Method show = HookUtil.findMethodExact(provider, "b", new Class<?>[0]);
        Method hide = HookUtil.findMethodExact(provider, "a", new Class<?>[0]);
        Field popupView = uniquePopupViewField(provider);
        return new Target(provider, show, hide, popupView, RUNTIME_PROVIDER);
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
}
