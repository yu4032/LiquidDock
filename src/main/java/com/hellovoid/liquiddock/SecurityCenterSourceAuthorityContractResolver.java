package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Resolves Security Center's process activity-change authority by exact Binder callback shape. */
final class SecurityCenterSourceAuthorityContractResolver {
    static final class Contract {
        private final Field listenerField;
        private final Class<?> componentNameClass;

        Contract(Field listenerField, Class<?> componentNameClass) {
            this.listenerField = listenerField;
            this.componentNameClass = componentNameClass;
        }

        Class<?> listenerClass() { return listenerField.getType(); }

        Method resolveConcreteCallback(Object service) {
            if (service == null || !listenerField.getDeclaringClass().isInstance(service)) {
                throw reject("activity-change listener owner changed");
            }
            final Object listener;
            try {
                listenerField.setAccessible(true);
                listener = listenerField.get(service);
            } catch (Throwable error) {
                throw reject("activity-change listener field inaccessible", error);
            }
            if (listener == null || !listenerField.getType().isInstance(listener)) {
                throw reject("activity-change listener instance unavailable");
            }
            List<Method> callbacks = exactDeclaredCallbacks(listener.getClass(), componentNameClass);
            if (callbacks.size() != 1) {
                throw reject("concrete activity-change callback missing or ambiguous");
            }
            Method callback = callbacks.get(0);
            callback.setAccessible(true);
            return callback;
        }

        Method resolveConcreteCallbackForTest(Object service) {
            return resolveConcreteCallback(service);
        }
    }

    private SecurityCenterSourceAuthorityContractResolver() {}

    static Contract resolveForTest(Class<?> serviceClass, Class<?> componentNameClass) {
        return resolve(serviceClass, componentNameClass);
    }

    static Contract resolve(Class<?> serviceClass, Class<?> componentNameClass) {
        if (serviceClass == null || componentNameClass == null) {
            throw reject("missing service/ComponentName authority types");
        }
        List<Field> matches = new ArrayList<>();
        for (Field field : serviceClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Class<?> listenerClass = field.getType();
            if (listenerClass.isPrimitive() || listenerClass == Object.class) continue;
            if (inheritedCallbackShapeCount(listenerClass, componentNameClass) == 1) {
                matches.add(field);
            }
        }
        if (matches.size() != 1) {
            throw reject("activity-change authority field missing or ambiguous");
        }
        Field listenerField = matches.get(0);
        listenerField.setAccessible(true);
        return new Contract(listenerField, componentNameClass);
    }

    private static int inheritedCallbackShapeCount(Class<?> listenerClass, Class<?> componentNameClass) {
        int matches = 0;
        for (Method method : listenerClass.getMethods()) {
            if (matchesCallback(method, componentNameClass)) matches++;
        }
        return matches;
    }

    private static List<Method> exactDeclaredCallbacks(
            Class<?> concreteClass, Class<?> componentNameClass) {
        List<Method> callbacks = new ArrayList<>();
        for (Method method : concreteClass.getDeclaredMethods()) {
            if (matchesCallback(method, componentNameClass)) callbacks.add(method);
        }
        return callbacks;
    }

    private static boolean matchesCallback(Method method, Class<?> componentNameClass) {
        Class<?>[] parameters = method.getParameterTypes();
        return !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() == void.class
                && parameters.length == 2
                && parameters[0] == componentNameClass
                && parameters[1] == componentNameClass;
    }

    private static IllegalStateException reject(String reason) {
        return new IllegalStateException("Security Center semantic contract rejected: " + reason);
    }

    private static IllegalStateException reject(String reason, Throwable cause) {
        return new IllegalStateException(
                "Security Center semantic contract rejected: " + reason, cause);
    }
}
