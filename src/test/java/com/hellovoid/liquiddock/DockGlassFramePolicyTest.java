package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockGlassFramePolicyTest {
    @Test
    public void sceneAnimationReusesPreparedBackdrop() {
        DockGlassFramePolicy policy = new DockGlassFramePolicy();
        policy.requestSource();
        DockGlassFramePolicy.Work initial = policy.consume();
        assertTrue(initial.prepareBackdrop);
        assertTrue(initial.renderScene);

        policy.requestScene();
        DockGlassFramePolicy.Work animation = policy.consume();
        assertFalse(animation.prepareBackdrop);
        assertTrue(animation.renderScene);
    }

    @Test
    public void mappingChangeRebuildsBackdrop() {
        DockGlassFramePolicy policy = new DockGlassFramePolicy();
        policy.requestMapping();

        DockGlassFramePolicy.Work work = policy.consume();
        assertTrue(work.prepareBackdrop);
        assertTrue(work.renderScene);
    }

    @Test
    public void producerFrameRebuildsBackdrop() {
        DockGlassFramePolicy policy = new DockGlassFramePolicy();
        policy.requestSource();

        DockGlassFramePolicy.Work work = policy.consume();
        assertTrue(work.prepareBackdrop);
        assertTrue(work.renderScene);
    }

    @Test
    public void idleConsumeDoesNothing() {
        DockGlassFramePolicy policy = new DockGlassFramePolicy();
        DockGlassFramePolicy.Work work = policy.consume();
        assertFalse(work.prepareBackdrop);
        assertFalse(work.renderScene);
    }
}
