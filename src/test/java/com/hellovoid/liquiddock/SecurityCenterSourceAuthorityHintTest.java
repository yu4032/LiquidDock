package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Runtime source authority is an optional producer-rebind hint, not a material enable gate. */
public class SecurityCenterSourceAuthorityHintTest {
    @Test
    public void materialRuntimeDoesNotRequireSourceAuthorityHint() {
        SecurityCenterGlassRuntimeState.initialize(null, true, true, true);

        assertTrue("carrier/root lifecycle must be able to start glass before an activity hint arrives",
                SecurityCenterGlassRuntimeState.isEnabled());
    }
}
