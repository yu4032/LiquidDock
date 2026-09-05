package com.hellovoid.liquiddock;

/** Thrown when a required vendor/private reflective operation cannot be completed. */
public final class VendorReflectionException extends RuntimeException {
    private final HookUtil.Failure failure;

    VendorReflectionException(HookUtil.Failure failure) {
        super(failure != null ? failure.describe() : "vendor reflection failed",
                failure != null ? failure.cause() : null);
        this.failure = failure;
    }

    public HookUtil.Failure failure() { return failure; }
}
