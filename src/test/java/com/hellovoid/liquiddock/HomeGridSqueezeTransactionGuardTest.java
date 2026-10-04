package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGridSqueezeTransactionGuardTest {
    @Test
    public void onlyExplicitTrueCommitsTheTransaction() {
        assertTrue(HomeGridSqueezeTransactionGuard.succeeded(Boolean.TRUE));
        assertFalse(HomeGridSqueezeTransactionGuard.succeeded(Boolean.FALSE));
        assertFalse(HomeGridSqueezeTransactionGuard.succeeded(null));
        assertFalse(HomeGridSqueezeTransactionGuard.succeeded("true"));
    }
}
