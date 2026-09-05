package com.hellovoid.liquiddock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/** Unified libxposed hooking and explicit vendor/private reflection boundary. */
public final class HookUtil {

    public enum FailureKind {
        TARGET_NULL,
        CLASS_NOT_FOUND,
        METHOD_NOT_FOUND,
        AMBIGUOUS_METHOD,
        ACCESS_FAILURE,
        INVOCATION_FAILURE
    }

    public static final class Failure {
        private final FailureKind kind;
        private final String targetClassName;
        private final String methodName;
        private final boolean staticInvocation;
        private final List<String> argumentTypes;
        private final Method method;
        private final List<String> candidateSignatures;
        private final Throwable cause;

        private Failure(FailureKind kind, String targetClassName, String methodName,
                        boolean staticInvocation, Object[] args, Method method,
                        List<String> candidateSignatures, Throwable cause) {
            this.kind = kind;
            this.targetClassName = targetClassName;
            this.methodName = methodName;
            this.staticInvocation = staticInvocation;
            this.argumentTypes = argumentTypeLabels(args);
            this.method = method;
            this.candidateSignatures = candidateSignatures == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(candidateSignatures));
            this.cause = cause;
        }

        public FailureKind kind() { return kind; }
        public String targetClassName() { return targetClassName; }
        public String methodName() { return methodName; }
        public boolean staticInvocation() { return staticInvocation; }
        public List<String> argumentTypes() { return argumentTypes; }
        public Method method() { return method; }
        public List<String> candidateSignatures() { return candidateSignatures; }
        public Throwable cause() { return cause; }

        String describe() {
            StringBuilder out = new StringBuilder("vendor reflection ")
                    .append(kind).append(": ")
                    .append(targetClassName).append('#').append(methodName)
                    .append(argumentTypes);
            if (!candidateSignatures.isEmpty()) {
                out.append(" candidates=").append(candidateSignatures);
            }
            if (cause != null) out.append(" cause=").append(cause);
            return out.toString();
        }

        @Override public String toString() { return describe(); }
    }

    public static final class InvocationResult<T> {
        private final T value;
        private final Method method;
        private final Failure failure;

        private InvocationResult(T value, Method method, Failure failure) {
            this.value = value;
            this.method = method;
            this.failure = failure;
        }

        static <T> InvocationResult<T> success(T value, Method method) {
            return new InvocationResult<>(value, method, null);
        }

        static <T> InvocationResult<T> failure(Failure failure) {
            return new InvocationResult<>(null, null, failure);
        }

        public boolean succeeded() { return failure == null; }
        public T value() { return value; }
        public Method method() { return method; }
        public Failure failure() { return failure; }
    }

    private HookUtil() {}

    // ── Hooking ──────────────────────────────────────────────────────

    /** Hook a method. The callback receives the chain directly. */
    public static void hook(Method method, XposedInterface.Hooker callback) {
        method.setAccessible(true);
        Api101Bridge.module().hook(method).intercept(callback);
    }

    /** Hook a constructor. */
    public static void hook(Constructor<?> ctor, XposedInterface.Hooker callback) {
        ctor.setAccessible(true);
        Api101Bridge.module().hook(ctor).intercept(callback);
    }

    /** Find + hook a declared method (exact class only; no superclass walk). */
    public static void hookMethod(Class<?> clazz, String methodName,
                                  Class<?>[] paramTypes,
                                  XposedInterface.Hooker callback) {
        try {
            hook(findMethodExact(clazz, methodName, paramTypes), callback);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(methodName + " on " + clazz.getName(), e);
        }
    }

    /** Find + hook, walking the superclass chain. */
    public static void hookMethod(ClassLoader cl, String className,
                                  String methodName,
                                  XposedInterface.Hooker callback,
                                  Object... paramTypeSpecs) {
        try {
            Class<?> clazz = Class.forName(className, false, cl);
            Class<?>[] types = new Class<?>[paramTypeSpecs.length];
            for (int i = 0; i < paramTypeSpecs.length; i++) {
                Object spec = paramTypeSpecs[i];
                if (spec instanceof Class<?>) {
                    types[i] = (Class<?>) spec;
                } else if (spec instanceof String) {
                    types[i] = Class.forName((String) spec, false, cl);
                }
            }
            hook(findMethodExact(clazz, methodName, types), callback);
        } catch (Exception e) {
            throw new RuntimeException(methodName, e);
        }
    }

    // ── Field reflection ─────────────────────────────────────────────

    public static Field findField(Class<?> clazz, String name) {
        VendorMemberResolver.FieldResolution resolution =
                VendorMemberResolver.resolveField(clazz, name);
        if (resolution.resolved()) return resolution.field();
        if (resolution.status() == VendorMemberResolver.FieldStatus.ACCESS_FAILURE) {
            throw new RuntimeException("field access failed: " + clazz.getName() + "#" + name,
                    resolution.cause());
        }
        throw new RuntimeException("field not found: " + clazz.getName() + "#" + name);
    }

    public static Object getField(Object target, String name) {
        try { return findField(target.getClass(), name).get(target); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static int getIntField(Object target, String name) {
        try { return findField(target.getClass(), name).getInt(target); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static long getLongField(Object target, String name) {
        try { return findField(target.getClass(), name).getLong(target); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static boolean getBooleanField(Object target, String name) {
        try { return findField(target.getClass(), name).getBoolean(target); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void setField(Object target, String name, Object value) {
        try { findField(target.getClass(), name).set(target, value); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void setIntField(Object target, String name, int value) {
        try { findField(target.getClass(), name).setInt(target, value); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    public static void setLongField(Object target, String name, long value) {
        try { findField(target.getClass(), name).setLong(target, value); }
        catch (Throwable e) { throw new RuntimeException(e); }
    }

    // ── Exact method lookup used by hook installation ────────────────

    public static Method findMethodExact(Class<?> clazz, String name, Class<?>[] paramTypes)
            throws NoSuchMethodException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "#" + name);
    }

    // ── Explicit vendor invocation ───────────────────────────────────

    public static InvocationResult<Object> tryInvoke(
            Object target, String methodName, Object... args) {
        if (target == null) {
            return InvocationResult.failure(new Failure(
                    FailureKind.TARGET_NULL, "<null>", methodName, false,
                    args, null, Collections.emptyList(), null));
        }
        return invokeResolved(target.getClass(), target, methodName, false, args);
    }

    public static InvocationResult<Object> tryInvokeStatic(
            Class<?> clazz, String methodName, Object... args) {
        if (clazz == null) {
            return InvocationResult.failure(new Failure(
                    FailureKind.TARGET_NULL, "<null-class>", methodName, true,
                    args, null, Collections.emptyList(), null));
        }
        return invokeResolved(clazz, null, methodName, true, args);
    }

    public static InvocationResult<Object> tryInvokeStatic(
            String className, String methodName, Object... args) {
        try {
            return tryInvokeStatic(Class.forName(className), methodName, args);
        } catch (Throwable error) {
            return InvocationResult.failure(new Failure(
                    FailureKind.CLASS_NOT_FOUND, className, methodName, true,
                    args, null, Collections.emptyList(), error));
        }
    }

    public static Object requireInvoke(Object target, String methodName, Object... args) {
        return require(tryInvoke(target, methodName, args));
    }

    public static Object requireInvokeStatic(
            Class<?> clazz, String methodName, Object... args) {
        return require(tryInvokeStatic(clazz, methodName, args));
    }

    public static Object requireInvokeStatic(
            String className, String methodName, Object... args) {
        return require(tryInvokeStatic(className, methodName, args));
    }

    private static InvocationResult<Object> invokeResolved(
            Class<?> clazz, Object target, String methodName,
            boolean requireStatic, Object[] rawArgs) {
        Object[] args = rawArgs == null ? new Object[0] : rawArgs;
        VendorMemberResolver.MethodResolution resolution =
                VendorMemberResolver.resolveMethod(clazz, methodName, args, requireStatic);
        if (!resolution.resolved()) {
            return InvocationResult.failure(failureForResolution(
                    resolution, clazz, methodName, requireStatic, args));
        }
        Method method = resolution.method();
        try {
            return InvocationResult.success(method.invoke(target, args), method);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            return InvocationResult.failure(new Failure(
                    FailureKind.INVOCATION_FAILURE, clazz.getName(), methodName,
                    requireStatic, args, method, Collections.emptyList(), cause));
        } catch (IllegalAccessException | SecurityException error) {
            return InvocationResult.failure(new Failure(
                    FailureKind.ACCESS_FAILURE, clazz.getName(), methodName,
                    requireStatic, args, method, Collections.emptyList(), error));
        } catch (Throwable error) {
            return InvocationResult.failure(new Failure(
                    FailureKind.INVOCATION_FAILURE, clazz.getName(), methodName,
                    requireStatic, args, method, Collections.emptyList(), error));
        }
    }

    private static Failure failureForResolution(
            VendorMemberResolver.MethodResolution resolution,
            Class<?> clazz, String methodName, boolean requireStatic, Object[] args) {
        FailureKind kind;
        switch (resolution.status()) {
            case AMBIGUOUS:
                kind = FailureKind.AMBIGUOUS_METHOD;
                break;
            case ACCESS_FAILURE:
                kind = FailureKind.ACCESS_FAILURE;
                break;
            case NOT_FOUND:
            default:
                kind = FailureKind.METHOD_NOT_FOUND;
                break;
        }
        return new Failure(kind, clazz.getName(), methodName, requireStatic, args,
                resolution.method(), resolution.candidateSignatures(), resolution.cause());
    }

    private static Object require(InvocationResult<Object> result) {
        if (!result.succeeded()) throw new VendorReflectionException(result.failure());
        return result.value();
    }

    private static List<String> argumentTypeLabels(Object[] rawArgs) {
        Object[] args = rawArgs == null ? new Object[0] : rawArgs;
        ArrayList<String> labels = new ArrayList<>(args.length);
        for (Object arg : args) {
            labels.add(arg == null ? "<null>" : arg.getClass().getName());
        }
        return Collections.unmodifiableList(labels);
    }
}
