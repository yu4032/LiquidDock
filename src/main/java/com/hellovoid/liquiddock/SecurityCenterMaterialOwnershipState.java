package com.hellovoid.liquiddock;

/** Android-free vendor/custom material ownership gate for Security Center glass. */
final class SecurityCenterMaterialOwnershipState {
    enum Owner { VENDOR, CUSTOM }

    private Owner owner = Owner.VENDOR;

    boolean canSuppressVendor(long renderedGeneration, long currentGeneration) {
        return renderedGeneration >= 0L && renderedGeneration == currentGeneration;
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
