package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Starts the current-source Security Center material pipeline at configure completion. */
final class SecurityCenterEarlyPrepareHook {
    private static final Object LOCK = new Object();
    private static final Set<Method> INSTALLED = new HashSet<>();
    private static final int ASSISTANT_GAME = 1;
    private static final int ASSISTANT_VIDEO = 3;
    private static final int ASSISTANT_GLOBAL_DOCK = 4;

    private SecurityCenterEarlyPrepareHook() {}

    static void install(
            SecurityCenterSemanticContractResolver.ResolvedContract contract,
            int videoMainContentResId) {
        if (contract == null || videoMainContentResId == 0) return;
        Method configure = contract.configure();
        synchronized (LOCK) {
            if (!INSTALLED.add(configure)) return;
        }
        HookUtil.hook(configure, chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            if (!SecurityCenterGlassRuntimeState.isEnabled()) return result;
            Object turboObject = chain.getThisObject();
            Object typeArg = chain.getArgs().size() > 4 ? chain.getArgs().get(4) : null;
            if (!(turboObject instanceof View) || typeArg == null) return result;
            try {
                int type = assistantType(typeArg, contract);
                if (type == 0) return result;
                View turbo = (View) turboObject;
                Object dockObject = invoke(contract.dockGetter(), turbo);
                if (!(dockObject instanceof View)) return result;
                View boxMaterial = resolveBoxMaterial(
                        turbo, type, contract, videoMainContentResId);
                if ((type == ASSISTANT_GAME || type == ASSISTANT_VIDEO) && boxMaterial == null) {
                    return result;
                }
                SecurityCenterGlassRuntimeState.bindAssistant(
                        turbo, (View) dockObject, boxMaterial, type);
            } catch (Throwable error) {
                try {
                    Api101Bridge.log("[DC][SecurityCenterGlass] early prepare deferred: " + error);
                } catch (Throwable ignored) {}
            }
            return result;
        });
    }

    private static int assistantType(
            Object typeArg, SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        Object value = invoke(contract.assistantTypeDiscriminator(), typeArg);
        if (!(value instanceof Number)) return 0;
        int type = ((Number) value).intValue();
        return type == ASSISTANT_GAME || type == ASSISTANT_VIDEO || type == ASSISTANT_GLOBAL_DOCK
                ? type : 0;
    }

    private static View resolveBoxMaterial(
            View turbo,
            int type,
            SecurityCenterSemanticContractResolver.ResolvedContract contract,
            int videoMainContentResId) {
        if (type == ASSISTANT_GLOBAL_DOCK) return null;
        Object boxObject = invoke(contract.boxGetter(), turbo);
        if (!(boxObject instanceof View)) return null;
        View box = (View) boxObject;
        if (type == ASSISTANT_GAME) {
            if (!contract.gameBoxClass().isInstance(box)) return null;
            Object material = invoke(contract.gameMaterialGetter(), box);
            return material instanceof View && contract.gameMaterialClass().isInstance(material)
                    ? (View) material : null;
        }
        View material = box.findViewById(videoMainContentResId);
        return material != null && material.getId() == videoMainContentResId ? material : null;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Security Center early prepare invocation failed", cause);
        } catch (Throwable error) {
            throw new IllegalStateException("Security Center early prepare invocation failed", error);
        }
    }
}
