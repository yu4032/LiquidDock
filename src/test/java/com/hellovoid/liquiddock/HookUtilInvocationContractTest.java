package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class HookUtilInvocationContractTest {
    static final class Fixture {
        String returnsNull() { return null; }
        String echo(String value) { return value; }
        String explode() { throw new IllegalStateException("boom"); }
        String ambiguous(CharSequence value) { return "chars"; }
        String ambiguous(Number value) { return "number"; }
        static String staticEcho(String value) { return value; }
    }

    @Test public void successfulNullIsNotFailure() {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(new Fixture(), "returnsNull");
        assertTrue(result.succeeded());
        assertNull(result.value());
        assertNull(result.failure());
        assertTrue(result.method() != null);
    }

    @Test public void missingAndAmbiguousMethodsAreStructuredFailures() {
        HookUtil.InvocationResult<Object> missing = HookUtil.tryInvoke(new Fixture(), "missing");
        assertFalse(missing.succeeded());
        assertEquals(HookUtil.FailureKind.METHOD_NOT_FOUND, missing.failure().kind());

        HookUtil.InvocationResult<Object> ambiguous =
                HookUtil.tryInvoke(new Fixture(), "ambiguous", (Object) null);
        assertFalse(ambiguous.succeeded());
        assertEquals(HookUtil.FailureKind.AMBIGUOUS_METHOD, ambiguous.failure().kind());
        assertEquals(2, ambiguous.failure().candidateSignatures().size());
    }

    @Test public void targetThrownExceptionIsPreserved() {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(new Fixture(), "explode");
        assertFalse(result.succeeded());
        assertEquals(HookUtil.FailureKind.INVOCATION_FAILURE, result.failure().kind());
        assertTrue(result.failure().cause() instanceof IllegalStateException);
        assertEquals("boom", result.failure().cause().getMessage());
    }

    @Test public void optionalFailureStringIsDiagnostic() {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(new Fixture(), "missing");
        String text = String.valueOf(result.failure());
        assertTrue(text.contains("METHOD_NOT_FOUND"));
        assertTrue(text.contains("#missing"));
    }

    @Test public void requiredInvocationFailsAtBoundary() {
        try {
            HookUtil.requireInvoke(new Fixture(), "missing");
            fail("required invocation must throw");
        } catch (VendorReflectionException expected) {
            assertEquals(HookUtil.FailureKind.METHOD_NOT_FOUND, expected.failure().kind());
        }
    }

    @Test public void staticClassContractsAreExplicit() {
        HookUtil.InvocationResult<Object> staticResult =
                HookUtil.tryInvokeStatic(Fixture.class, "staticEcho", "ok");
        assertTrue(staticResult.succeeded());
        assertEquals("ok", staticResult.value());
        assertEquals("ok", HookUtil.requireInvokeStatic(Fixture.class, "staticEcho", "ok"));

        HookUtil.InvocationResult<Object> nullClass =
                HookUtil.tryInvokeStatic((Class<?>) null, "x");
        assertFalse(nullClass.succeeded());
        assertEquals(HookUtil.FailureKind.TARGET_NULL, nullClass.failure().kind());
    }

    @Test public void nullTargetIsStructuredFailure() {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(null, "anything");
        assertFalse(result.succeeded());
        assertEquals(HookUtil.FailureKind.TARGET_NULL, result.failure().kind());
    }
}
