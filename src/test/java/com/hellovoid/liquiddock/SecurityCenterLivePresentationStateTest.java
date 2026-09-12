package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SecurityCenterLivePresentationStateTest {
    @Test
    public void firstStrictPresentationNeverEntersLive() {
        SecurityCenterLivePresentationState state = new SecurityCenterLivePresentationState();

        state.onStrictPresentation(7L, true);

        assertFalse(state.isLive(7L));
    }

    @Test
    public void reboundStrictPresentationEntersSameGenerationLive() {
        SecurityCenterLivePresentationState state = new SecurityCenterLivePresentationState();

        state.onStrictPresentation(7L, true);
        state.onStrictPresentation(7L, false);

        assertTrue(state.isLive(7L));
        assertFalse(state.isLive(8L));
    }

    @Test
    public void differentGenerationOrInvalidationCannotInheritLive() {
        SecurityCenterLivePresentationState state = new SecurityCenterLivePresentationState();

        state.onStrictPresentation(7L, true);
        state.onStrictPresentation(8L, false);
        assertFalse(state.isLive(8L));

        state.onStrictPresentation(7L, false);
        assertFalse(state.isLive(7L));

        state.onStrictPresentation(9L, true);
        state.onStrictPresentation(9L, false);
        assertTrue(state.isLive(9L));

        state.invalidate();
        assertFalse(state.isLive(9L));
    }
}
