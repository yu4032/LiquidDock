package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Resolves Security Center's process activity-change authority by exact Binder callback shape. */
final class SecurityCenterSourceAuthorityContractResolver {
    static final class Contract {
        private final Class<?> listenerClass;
        private final Method onActivityChanged;

        Contract(Class<?> listenerClass, Method onActivityChanged) {
            this.listenerClass = listenerClass;
            this.onActivityChanged = onActivityChanged;
        }

        Class<?> listenerClass() { return listenerClass; }
        Method onActivityChanged() { return onActivityChanged; }
    }

    private SecurityCenterSourceAuthorityContractResolver() {}

    static Contract resolveForTest(Class<?> serviceClass, Class<?> componentNameClass) {
        return resolve(serviceClass, componentNameClass);
    }

    static Contract resolve(Class<?> serviceClass, Class<?> componentNameClass) {
        if (serviceClass == null || componentNameClass == null) {
            throw reject("missing service/ComponentName authority types");
        }
        List<Contract> matches = new ArrayList<>();
        for (Field field : serviceClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Class<?> listenerClass = field.getType();
            if (listenerClass.isPrimitive() || listenerClass == Object.class) continue;
            List<Method> callbacks = new ArrayList<>();
            for (Method method : listenerClass.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers())
                        && method.getName().equals("onActivityChanged")
                        && method.getReturnType() == void.class
                        && parameters.length == 2
                        && parameters[0] == componentNameClass
                        && parameters[1] == componentNameClass) {
                    callbacks.add(method);
                }
            }
            if (callbacks.size() == 1) {
                Method callback = callbacks.get(0);
                callback.setAccessible(true);
                matches.add(new Contract(listenerClass, callback));
            } else if (callbacks.size() > 1) {
                throw reject("activity-change callback ambiguous inside listener");
            }
        }
        if (matches.size() != 1) {
            throw reject("activity-change authority missing or ambiguous");
        }
        return matches.get(0);
    }

    private static IllegalStateException reject(String reason) {
        return new IllegalStateException("Security Center semantic contract rejected: " + reason);
    }
}
