package com.hellovoid.liquiddock;

enum SecurityCenterVendorMaterialCapability {
    BACKGROUND_BLUR,
    SOFT_LIGHT_GLASS;

    static SecurityCenterVendorMaterialCapability resolve(
            SecurityCenterVendorGeneration generation, boolean nativeSoftLightActive) {
        return generation == SecurityCenterVendorGeneration.OS4_SOFT_LIGHT_CAPABLE
                && nativeSoftLightActive
                ? SOFT_LIGHT_GLASS
                : BACKGROUND_BLUR;
    }
}
