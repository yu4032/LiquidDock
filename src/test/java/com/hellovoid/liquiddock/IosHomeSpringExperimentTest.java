package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import org.junit.After;
import org.junit.Test;

/** HOME spring experiment must remain reversible and inert without opt-in. */
public class IosHomeSpringExperimentTest {
    @After public void reset() {
        IosHomeSpringExperiment.configure(false);
    }

    @Test public void disabledKeepsNativeForce() {
        RectAnimation animation = new RectAnimation();
        IosHomeSpringExperiment.configure(false);
        IosHomeSpringExperiment.onHomeStarted(animation);
        assertEquals(300f, animation.x.getSpring().getStiffness(), 0.001f);
        assertEquals(0.85f, animation.x.getSpring().getDampingRatio(), 0.001f);
    }

    @Test public void enabledAdjustsAndRestoresEveryInstanceSpring() {
        RectAnimation animation = new RectAnimation();
        IosHomeSpringExperiment.configure(true);
        IosHomeSpringExperiment.onHomeStarted(animation);
        assertEquals(270f, animation.x.getSpring().getStiffness(), 0.001f);
        assertEquals(0.60f, animation.x.getSpring().getDampingRatio(), 0.001f);
        assertEquals(180f, animation.scale.getSpring().getStiffness(), 0.001f);
        IosHomeSpringExperiment.onHomeStarted(animation); // duplicate listener must not retune
        assertEquals(270f, animation.x.getSpring().getStiffness(), 0.001f);
        IosHomeSpringExperiment.onHomeFinished(animation);
        assertEquals(300f, animation.x.getSpring().getStiffness(), 0.001f);
        assertEquals(0.85f, animation.x.getSpring().getDampingRatio(), 0.001f);
        assertEquals(200f, animation.scale.getSpring().getStiffness(), 0.001f);
    }

    @Test public void liveDisableRestoresAlreadyRunningSpring() {
        RectAnimation animation = new RectAnimation();
        IosHomeSpringExperiment.configure(true);
        IosHomeSpringExperiment.onHomeStarted(animation);
        IosHomeSpringExperiment.configure(false);
        assertEquals(300f, animation.x.getSpring().getStiffness(), 0.001f);
        assertEquals(0.85f, animation.x.getSpring().getDampingRatio(), 0.001f);
    }

    @Test public void hyperOsDirectForceChangesMorphButNotIconHandoffAlpha() {
        HyperRectAnimation animation = new HyperRectAnimation();
        IosHomeSpringExperiment.configure(true);
        IosHomeSpringExperiment.onHomeStarted(animation);
        assertEquals(360f, animation.mRectWidthForce.getStiffness(), 0.001f);
        assertEquals(90f, animation.mRadiusForce.getStiffness(), 0.001f);
        assertEquals(0.60f, animation.mRadiusForce.getDampingRatio(), 0.001f);
        assertEquals(200f, animation.mAlphaForce.getStiffness(), 0.001f);
        assertEquals(0.85f, animation.mAlphaForce.getDampingRatio(), 0.001f);
        IosHomeSpringExperiment.onHomeFinished(animation);
        assertEquals(400f, animation.mRectWidthForce.getStiffness(), 0.001f);
        assertEquals(100f, animation.mRadiusForce.getStiffness(), 0.001f);
        assertEquals(0.85f, animation.mRadiusForce.getDampingRatio(), 0.001f);
    }

    private static final class HyperRectAnimation {
        final SpringForce mRectWidthForce = new SpringForce(400f);
        final SpringForce mRectWidthAliasForce = mRectWidthForce;
        final SpringForce mRadiusForce = new SpringForce(100f);
        final SpringForce mAlphaForce = new SpringForce(200f);
    }

    @Test public void incompatibleInternalsAreFailOpen() {
        IosHomeSpringExperiment.configure(true);
        IosHomeSpringExperiment.onHomeStarted(new Object());
        IosHomeSpringExperiment.onHomeFinished(new Object());
    }

    private static final class RectAnimation {
        final SpringAnimation x = new SpringAnimation(300f);
        final SpringAnimation scale = new SpringAnimation(200f);
    }

    public static final class SpringAnimation {
        private final SpringForce spring;
        SpringAnimation(float stiffness) { spring = new SpringForce(stiffness); }
        public SpringForce getSpring() { return spring; }
    }

    public static final class SpringForce {
        private float stiffness;
        private float damping = 0.85f;
        SpringForce(float stiffness) { this.stiffness = stiffness; }
        public float getStiffness() { return stiffness; }
        public float getDampingRatio() { return damping; }
        public SpringForce setStiffness(float value) { stiffness = value; return this; }
        public SpringForce setDampingRatio(float value) { damping = value; return this; }
    }
}
