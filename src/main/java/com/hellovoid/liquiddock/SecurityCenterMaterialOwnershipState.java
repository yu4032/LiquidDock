package com.hellovoid.liquiddock;

/** Android-free vendor/custom material ownership gate for Security Center glass. */
final class SecurityCenterMaterialOwnershipState {
    enum Owner { VENDOR, CUSTOM_PREPARING, CUSTOM, CUSTOM_CLOSING }

    private Owner owner = Owner.VENDOR;
    private boolean vendorSuppressed;

    /**
     * PREPARING keeps vendor presentation authoritative until a current-generation custom frame
     * has been acknowledged by TextureView. CUSTOM_CLOSING preserves whichever presentation
     * authority was active when the vendor close motion started.
     */
    boolean canSuppressVendor(long renderedGeneration, long currentGeneration) {
        return vendorSuppressed
                && (owner == Owner.CUSTOM || owner == Owner.CUSTOM_CLOSING)
                && renderedGeneration >= 0L
                && currentGeneration >= renderedGeneration;
    }

    void onCustomPreparing() {
        if (owner != Owner.VENDOR) return;
        owner = Owner.CUSTOM_PREPARING;
        vendorSuppressed = false;
    }

    void onCustomPresented() {
        if (owner == Owner.CUSTOM_PREPARING) {
            owner = Owner.CUSTOM;
            vendorSuppressed = true;
        }
    }

    void onVendorClosing() {
        if (owner != Owner.VENDOR) owner = Owner.CUSTOM_CLOSING;
    }

    boolean hasSuppressedVendor() {
        return vendorSuppressed;
    }

    void releaseToVendor() {
        owner = Owner.VENDOR;
        vendorSuppressed = false;
    }

    Owner owner() {
        return owner;
    }
}
