package com.hellovoid.liquiddock;

/** Android-free vendor/custom material ownership gate for Security Center glass. */
final class SecurityCenterMaterialOwnershipState {
    enum Owner { VENDOR, CUSTOM }

    private Owner owner = Owner.VENDOR;

    /**
     * Once custom material has been claimed, a newer transition generation must not reopen the
     * vendor background while the previous rendered custom frame is still the authorized fallback.
     */
    boolean canSuppressVendor(long renderedGeneration, long currentGeneration) {
        return owner == Owner.CUSTOM
                && renderedGeneration >= 0L
                && currentGeneration >= renderedGeneration;
    }

    void onCustomClaimed() {
        owner = Owner.CUSTOM;
    }

    void releaseToVendor() {
        owner = Owner.VENDOR;
    }

    Owner owner() {
        return owner;
    }
}
