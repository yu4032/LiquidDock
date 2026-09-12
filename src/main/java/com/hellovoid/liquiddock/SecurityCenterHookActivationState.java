package com.hellovoid.liquiddock;

/** Fail-closed activation gate for Security Center mutation callbacks. */
final class SecurityCenterHookActivationState {
    private boolean callbacksRegistered;
    private boolean validationCommitted;
    private boolean validationFailed;

    SecurityCenterHookActivationState() {}

    synchronized void onCallbacksRegistered() {
        callbacksRegistered = true;
    }

    synchronized void onValidationCommitted() {
        if (validationFailed) return;
        validationCommitted = true;
    }

    synchronized void onValidationFailed() {
        validationFailed = true;
        validationCommitted = false;
    }

    synchronized boolean allowsMutation() {
        return callbacksRegistered && validationCommitted && !validationFailed;
    }
}
