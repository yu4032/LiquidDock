package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;

/** Typed contract for the semantic Security Center assistant / All Apps lifecycle. */
public class SecurityCenterHookLifecycleContractTest {
    private static boolean hasMethod(Class<?> type, String name, int parameterCount) {
        return Arrays.stream(type.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals(name)
                        && method.getParameterCount() == parameterCount);
    }

    @Test
    public void assistantContractIsResolvedBeforeMutationActivation() {
        assertTrue(hasMethod(SecurityCenterSemanticContractResolver.class, "resolveForTest", 3));
        assertTrue(hasMethod(SecurityCenterHookActivationState.class, "allowsMutation", 0));
        assertTrue(hasMethod(SecurityCenterHookActivationState.class, "onCallbacksRegistered", 0));
        assertTrue(hasMethod(SecurityCenterHookActivationState.class, "onValidationCommitted", 0));
        assertTrue(hasMethod(SecurityCenterHookActivationState.class, "onValidationFailed", 0));
    }

    @Test
    public void dockBoxAndAllAppsAreLateBoundIntoOneCoordinator() {
        assertTrue("assistant binding must include Dock, abstract Dock, material box, motion box and type",
                hasMethod(SecurityCenterGlassCoordinator.class, "bindAssistant", 6));
        assertTrue(hasMethod(SecurityCenterGlassCoordinator.class, "updateAllAppsLayout", 2));
        assertTrue(hasMethod(SecurityCenterGlassCoordinator.class, "refreshTransitionFrame", 1));
    }


}
