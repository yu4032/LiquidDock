package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SecurityCenterMaterialModePolicyTest {
    @Test
    public void shaderModeUsesNormalRendererWithoutAdvancedCapability() {
        assertTrue(SecurityCenterMaterialModePolicy.canPresentCustom(
                LiquidBlurMode.SHADER, false));
        assertTrue(SecurityCenterMaterialModePolicy.shaderBlurEnabled(LiquidBlurMode.SHADER));
    }

    @Test
    public void advancedModeRequiresCapabilityAndDisablesShaderBlur() {
        assertTrue(SecurityCenterMaterialModePolicy.canPresentCustom(
                LiquidBlurMode.ADVANCED_MATERIAL, true));
        assertFalse(SecurityCenterMaterialModePolicy.shaderBlurEnabled(
                LiquidBlurMode.ADVANCED_MATERIAL));
        assertFalse(SecurityCenterMaterialModePolicy.canPresentCustom(
                LiquidBlurMode.ADVANCED_MATERIAL, false));
    }
}
