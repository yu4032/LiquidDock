package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regression coverage for the ART stack shape.
 *
 * <p>On ART, {@code Thread#getStackTrace()} starts with {@code dalvik.system.VMStack} and
 * {@code java.lang.Thread} frames whose line number is negative. A registration-site resolver that
 * returns on the first frame without line information therefore never sees the caller, and every
 * hook in every process collapses onto one synthetic site id. The JVM does not produce those
 * frames, so the existing suite passed while device behaviour was wrong; these cases feed a
 * captured stack directly instead of relying on the host VM's frame layout.</p>
 */
public class HookUtilSiteIdentityTest {

    private static StackTraceElement frame(String owner, String method, int line) {
        return new StackTraceElement(owner, method, "X.java", line);
    }

    @Test
    public void artVendorFramesAreSkippedInsteadOfBecomingTheSite() {
        StackTraceElement[] stack = {
                frame("dalvik.system.VMStack", "getThreadStackTrace", -2),
                frame("java.lang.Thread", "getStackTrace", -1),
                frame(HookUtil.class.getName(), "hookAtSite", 128),
                frame("com.hellovoid.liquiddock.MiuixFolderGlassHook", "install", 140),
        };
        assertEquals("com.hellovoid.liquiddock.MiuixFolderGlassHook#install:140",
                HookUtil.registrationSite(stack));
    }

    @Test
    public void distinctSourceLinesProduceDistinctSites() {
        StackTraceElement[] first = {
                frame("dalvik.system.VMStack", "getThreadStackTrace", -2),
                frame(HookUtil.class.getName(), "hookAtSite", 128),
                frame("com.hellovoid.liquiddock.DockCustomizationHook", "install", 60),
        };
        StackTraceElement[] second = {
                frame("dalvik.system.VMStack", "getThreadStackTrace", -2),
                frame(HookUtil.class.getName(), "hookAtSite", 128),
                frame("com.hellovoid.liquiddock.DockCustomizationHook", "install", 61),
        };
        assertNotEquals(HookUtil.registrationSite(first), HookUtil.registrationSite(second));
    }

    @Test
    public void sameLineOfDifferentClassesNeverShareASite() {
        StackTraceElement[] a = {frame("com.hellovoid.liquiddock.A", "install", 10)};
        StackTraceElement[] b = {frame("com.hellovoid.liquiddock.B", "install", 10)};
        assertNotEquals(HookUtil.registrationSite(a), HookUtil.registrationSite(b));
    }

    @Test
    public void fullyUnlocatedStackStillYieldsAUniqueSite() {
        StackTraceElement[] stack = {
                frame("dalvik.system.VMStack", "getThreadStackTrace", -2),
                frame("java.lang.Thread", "getStackTrace", -1),
        };
        String first = HookUtil.registrationSite(stack);
        String second = HookUtil.registrationSite(stack);
        assertTrue(first.startsWith("unknown:"));
        assertNotEquals("a line-less stack must not hand out a shared slot", first, second);
    }

    @Test
    public void zeroLineFramesAreSkipped() {
        StackTraceElement[] stack = {
                frame("com.hellovoid.liquiddock.Synthetic", "bridge", 0),
                frame("com.hellovoid.liquiddock.RealOwner", "install", 42),
        };
        assertEquals("com.hellovoid.liquiddock.RealOwner#install:42", HookUtil.registrationSite(stack));
    }
}
