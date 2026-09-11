package com.hellovoid.liquiddock;

/** Android-free vendor/custom material ownership gate for Security Center glass. */
final class SecurityCenterMaterialOwnershipState {
    enum Owner { VENDOR, CUSTOM_PREPARING, CUSTOM }

    private Owner owner = Owner.VENDOR;

    /**
     * CUSTOM_PREPARING means vendor material has already been physically stripped while custom
     * sinks remain hidden waiting for a post-claim fresh frame. Vendor final-background callbacks
     * must therefore stay suppressed even though no custom frame is visible yet.
     */
    boolean canSuppressVendor(long renderedGeneration, long currentGeneration) {
        if (owner == Owner.CUSTOM_PREPARING) return currentGeneration >= 0L;
        return owner == Owner.CUSTOM
                && renderedGeneration >= 0L
                && currentGeneration >= renderedGeneration;
    }

    void onCustomPreparing() {
        owner = Owner.CUSTOM_PREPARING;
    }

    void onCustomPresented() {
        if (owner != Owner.VENDOR) owner = Owner.CUSTOM;
    }

    void onCustomClaimed() {
        onCustomPreparing();
        onCustomPresented();
    }

    void releaseToVendor() {
        owner = Owner.VENDOR;
    }

    Owner owner() {
        return owner;
    }
}
