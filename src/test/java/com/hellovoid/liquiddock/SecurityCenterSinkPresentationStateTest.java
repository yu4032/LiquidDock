package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SecurityCenterSinkPresentationStateTest {
    @Test
    public void transparentGateKeepsOnlyVisibleMaterialCompositionActive() {
        boolean[] visible = {true, true, false};
        boolean[] authorized = {false, true, true};
        boolean[] expectedCompose = {true, true, false};
        float[] expectedContentAlpha = {0f, 0.72f, 0f};

        for (int i = 0; i < visible.length; i++) {
            assertEquals(expectedCompose[i],
                    SecurityCenterSinkPresentationState.shouldCompose(visible[i]));
            assertEquals(expectedContentAlpha[i], SecurityCenterSinkPresentationState.contentAlpha(
                    visible[i], authorized[i], 0.72f), 0f);
        }
    }
}
