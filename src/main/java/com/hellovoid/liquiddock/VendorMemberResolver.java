package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Pure-Java deterministic resolver for vendor/private reflected members. */
final class VendorMemberResolver {
    enum MethodStatus { RESOLVED, NOT_FOUND, AMBIGUOUS, ACCESS_FAILURE }
    enum FieldStatus { RESOLVED, NOT_FOUND, ACCESS_FAILURE }

    static final class MethodResolution {
        private final MethodStatus status;
        private final Method method;
        private final List<String> candidateSignatures;
        private final Throwable cause;

        private MethodResolution(MethodStatus status, Method method,
                                 List<String> candidateSignatures, Throwable cause) {
            this.status = status;
            this.method = method;
            this.candidateSignatures = candidateSignatures == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(candidateSignatures));
            this.cause = cause;
        }

        static MethodResolution resolved(Method method) {
            return new MethodResolution(MethodStatus.RESOLVED, method,
                    Collections.emptyList(), null);
        }

        static MethodResolution notFound() {
            return new MethodResolution(MethodStatus.NOT_FOUND, null,
                    Collections.emptyList(), null);
        }

        static MethodResolution ambiguous(List<String> signatures) {
            return new MethodResolution(MethodStatus.AMBIGUOUS, null, signatures, null);
        }

        static MethodResolution accessFailure(Method method, Throwable cause) {
            return new MethodResolution(MethodStatus.ACCESS_FAILURE, method,
                    Collections.emptyList(), cause);
        }

        MethodStatus status() { return status; }
        Method method() { return method; }
        List<String> candidateSignatures() { return candidateSignatures; }
        Throwable cause() { return cause; }
        boolean resolved() { return status == MethodStatus.RESOLVED && method != null; }
    }

    static final class FieldResolution {
        private final FieldStatus status;
        private final Field field;
        private final Throwable cause;

        private FieldResolution(FieldStatus status, Field field, Throwable cause) {
            this.status = status;
            this.field = field;
            this.cause = cause;
        }

        static FieldResolution resolved(Field field) {
            return new FieldResolution(FieldStatus.RESOLVED, field, null);
        }

        static FieldResolution notFound() {
            return new FieldResolution(FieldStatus.NOT_FOUND, null, null);
        }

        static FieldResolution accessFailure(Field field, Throwable cause) {
            return new FieldResolution(FieldStatus.ACCESS_FAILURE, field, cause);
        }

        FieldStatus status() { return status; }
        Field field() { return field; }
        Throwable cause() { return cause; }
        boolean resolved() { return status == FieldStatus.RESOLVED && field != null; }
    }

    private static final class NullArgumentMarker {}

    private static final class MethodKey {
        final Class<?> targetClass;
        final String name;
        final boolean requireStatic;
        final List<Class<?>> argumentTypes;

        MethodKey(Class<?> targetClass, String name, boolean requireStatic, Object[] args) {
            this.targetClass = Objects.requireNonNull(targetClass, "targetClass");
            this.name = Objects.requireNonNull(name, "name");
            this.requireStatic = requireStatic;
            ArrayList<Class<?>> types = new ArrayList<>(args.length);
            for (Object arg : args) {
                types.add(arg == null ? NullArgumentMarker.class : arg.getClass());
            }
            argumentTypes = Collections.unmodifiableList(types);
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MethodKey)) return false;
            MethodKey that = (MethodKey) other;
            return targetClass == that.targetClass
                    && requireStatic == that.requireStatic
                    && name.equals(that.name)
                    && argumentTypes.equals(that.argumentTypes);
        }

        @Override public int hashCode() {
            int result = System.identityHashCode(targetClass);
            result = 31 * result + name.hashCode();
            result = 31 * result + Boolean.hashCode(requireStatic);
            return 31 * result + argumentTypes.hashCode();
        }
    }

    private static final class FieldKey {
        final Class<?> targetClass;
        final String name;

        FieldKey(Class<?> targetClass, String name) {
            this.targetClass = Objects.requireNonNull(targetClass, "targetClass");
            this.name = Objects.requireNonNull(name, "name");
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FieldKey)) return false;
            FieldKey that = (FieldKey) other;
            return targetClass == that.targetClass && name.equals(that.name);
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(targetClass) + name.hashCode();
        }
    }

    /** Deduplicates overrides/bridges with the same Java parameter signature. */
    private static final class MethodSignatureKey {
        final Class<?>[] parameterTypes;

        MethodSignatureKey(Method method) {
            parameterTypes = method.getParameterTypes();
        }

        @Override public boolean equals(Object other) {
            return other instanceof MethodSignatureKey
                    && Arrays.equals(parameterTypes,
                    ((MethodSignatureKey) other).parameterTypes);
        }

        @Override public int hashCode() { return Arrays.hashCode(parameterTypes); }
    }

    private static final ConcurrentMap<MethodKey, MethodResolution> METHOD_CACHE =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<FieldKey, FieldResolution> FIELD_CACHE =
            new ConcurrentHashMap<>();

    private VendorMemberResolver() {}

    static MethodResolution resolveMethod(Class<?> targetClass, String name,
                                          Object[] rawArgs, boolean requireStatic) {
        Object[] args = rawArgs == null ? new Object[0] : rawArgs;
        MethodKey key = new MethodKey(targetClass, name, requireStatic, args);
        return METHOD_CACHE.computeIfAbsent(key,
                ignored -> resolveMethodUncached(targetClass, name, args, requireStatic));
    }

    static FieldResolution resolveField(Class<?> targetClass, String name) {
        FieldKey key = new FieldKey(targetClass, name);
        return FIELD_CACHE.computeIfAbsent(key,
                ignored -> resolveFieldUncached(targetClass, name));
    }

    static void clearCachesForTests() {
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
    }

    static int methodCacheSizeForTests() { return METHOD_CACHE.size(); }
    static int fieldCacheSizeForTests() { return FIELD_CACHE.size(); }

    private static MethodResolution resolveMethodUncached(
            Class<?> targetClass, String name, Object[] args, boolean requireStatic) {
        List<Method> candidates = collectApplicableMethods(
                targetClass, name, args, requireStatic);
        if (candidates.isEmpty()) return MethodResolution.notFound();

        List<Method> nonDominated = new ArrayList<>();
        for (Method candidate : candidates) {
            boolean dominated = false;
            for (Method other : candidates) {
                if (candidate == other) continue;
                if (strictlyMoreSpecific(other, candidate, args, targetClass)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) nonDominated.add(candidate);
        }

        if (nonDominated.size() != 1) {
            List<String> signatures = new ArrayList<>(nonDominated.size());
            for (Method method : nonDominated) signatures.add(signature(method));
            signatures.sort(String::compareTo);
            return MethodResolution.ambiguous(signatures);
        }

        Method winner = nonDominated.get(0);
        try {
            winner.setAccessible(true);
            return MethodResolution.resolved(winner);
        } catch (Throwable error) {
            return MethodResolution.accessFailure(winner, error);
        }
    }

    private static List<Method> collectApplicableMethods(
            Class<?> targetClass, String name, Object[] args, boolean requireStatic) {
        Map<MethodSignatureKey, Method> nearestBySignature = new HashMap<>();
        for (Class<?> current = targetClass; current != null;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                if (Modifier.isStatic(method.getModifiers()) != requireStatic) continue;
                if (method.getParameterCount() != args.length) continue;
                if (!parametersApplicable(method.getParameterTypes(), args)) continue;
                MethodSignatureKey key = new MethodSignatureKey(method);
                Method existing = nearestBySignature.get(key);
                if (existing == null
                        || preferEquivalentDeclaration(method, existing, targetClass)) {
                    nearestBySignature.put(key, method);
                }
            }
        }
        return new ArrayList<>(nearestBySignature.values());
    }

    private static boolean preferEquivalentDeclaration(
            Method candidate, Method existing, Class<?> targetClass) {
        int candidateDepth = declaringDepth(targetClass, candidate.getDeclaringClass());
        int existingDepth = declaringDepth(targetClass, existing.getDeclaringClass());
        if (candidateDepth != existingDepth) return candidateDepth < existingDepth;
        boolean candidateReal = !candidate.isBridge() && !candidate.isSynthetic();
        boolean existingReal = !existing.isBridge() && !existing.isSynthetic();
        return candidateReal && !existingReal;
    }

    private static boolean parametersApplicable(Class<?>[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameter = parameters[i];
            Object arg = args[i];
            if (arg == null) {
                if (parameter.isPrimitive()) return false;
                continue;
            }
            Class<?> argumentType = arg.getClass();
            if (parameter.isPrimitive()) {
                if (wrap(parameter) != argumentType) return false;
            } else if (!parameter.isAssignableFrom(argumentType)) {
                return false;
            }
        }
        return true;
    }

    private static boolean strictlyMoreSpecific(
            Method left, Method right, Object[] args, Class<?> targetClass) {
        Class<?>[] leftParameters = left.getParameterTypes();
        Class<?>[] rightParameters = right.getParameterTypes();
        boolean strictlyBetter = false;

        for (int i = 0; i < args.length; i++) {
            int relation = compareParameter(
                    leftParameters[i], rightParameters[i], args[i]);
            if (relation == 1 || relation == 2) return false;
            if (relation == -1) strictlyBetter = true;
        }
        if (strictlyBetter) return true;

        if (Arrays.equals(leftParameters, rightParameters)) {
            int leftDepth = declaringDepth(targetClass, left.getDeclaringClass());
            int rightDepth = declaringDepth(targetClass, right.getDeclaringClass());
            if (leftDepth != rightDepth) return leftDepth < rightDepth;
            boolean leftReal = !left.isBridge() && !left.isSynthetic();
            boolean rightReal = !right.isBridge() && !right.isSynthetic();
            return leftReal && !rightReal;
        }
        return false;
    }

    /** -1 left better, 0 equal, 1 right better, 2 incomparable. */
    private static int compareParameter(Class<?> left, Class<?> right, Object arg) {
        if (left == right) return 0;

        if (arg == null) {
            if (right.isAssignableFrom(left)) return -1;
            if (left.isAssignableFrom(right)) return 1;
            return 2;
        }

        Class<?> argumentType = arg.getClass();
        Match leftMatch = match(left, argumentType);
        Match rightMatch = match(right, argumentType);
        if (leftMatch.category != rightMatch.category) {
            return Integer.compare(leftMatch.category, rightMatch.category);
        }
        if (leftMatch.distance != rightMatch.distance) {
            return Integer.compare(leftMatch.distance, rightMatch.distance);
        }

        Class<?> leftReference = left.isPrimitive() ? wrap(left) : left;
        Class<?> rightReference = right.isPrimitive() ? wrap(right) : right;
        if (rightReference.isAssignableFrom(leftReference)) return -1;
        if (leftReference.isAssignableFrom(rightReference)) return 1;
        return 2;
    }

    private static Match match(Class<?> parameter, Class<?> argumentType) {
        if (!parameter.isPrimitive() && parameter == argumentType) {
            return new Match(0, 0);
        }
        if (parameter.isPrimitive() && wrap(parameter) == argumentType) {
            return new Match(1, 0);
        }
        return new Match(2, inheritanceDistance(argumentType, parameter));
    }

    private static final class Match {
        final int category;
        final int distance;

        Match(int category, int distance) {
            this.category = category;
            this.distance = distance;
        }
    }

    private static int inheritanceDistance(Class<?> source, Class<?> target) {
        if (source == target) return 0;
        ArrayDeque<Class<?>> queue = new ArrayDeque<>();
        ArrayDeque<Integer> depths = new ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        queue.add(source);
        depths.add(0);
        seen.add(source);
        while (!queue.isEmpty()) {
            Class<?> current = queue.removeFirst();
            int depth = depths.removeFirst();
            if (current == target) return depth;
            Class<?> parent = current.getSuperclass();
            if (parent != null && seen.add(parent)) {
                queue.addLast(parent);
                depths.addLast(depth + 1);
            }
            for (Class<?> iface : current.getInterfaces()) {
                if (seen.add(iface)) {
                    queue.addLast(iface);
                    depths.addLast(depth + 1);
                }
            }
        }
        return Integer.MAX_VALUE / 4;
    }

    private static int declaringDepth(Class<?> targetClass, Class<?> declaringClass) {
        int depth = 0;
        for (Class<?> current = targetClass; current != null;
             current = current.getSuperclass()) {
            if (current == declaringClass) return depth;
            depth++;
        }
        return Integer.MAX_VALUE / 4;
    }

    private static FieldResolution resolveFieldUncached(
            Class<?> targetClass, String name) {
        for (Class<?> current = targetClass; current != null;
             current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                try {
                    field.setAccessible(true);
                    return FieldResolution.resolved(field);
                } catch (Throwable error) {
                    return FieldResolution.accessFailure(field, error);
                }
            } catch (NoSuchFieldException ignored) {
                // Continue toward Object.
            }
        }
        return FieldResolution.notFound();
    }

    private static String signature(Method method) {
        StringBuilder out = new StringBuilder();
        out.append(method.getDeclaringClass().getName())
                .append('#').append(method.getName()).append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) out.append(',');
            out.append(parameters[i].getTypeName());
        }
        return out.append(')').toString();
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == void.class) return Void.class;
        return type;
    }
}
