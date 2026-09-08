package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WidgetSpecRegistryTest {
    @Test
    public void defaultRegistryContainsExactlyCurrentSpecs() {
        WidgetSpecRegistry registry = WidgetSpecRegistry.DEFAULT;
        assertTrue(registry.supports(1, 1));
        assertTrue(registry.supports(2, 1));
        assertTrue(registry.supports(2, 2));
        assertTrue(registry.supports(4, 2));
        assertFalse(registry.supports(3, 2));
        assertFalse(registry.supports(4, 1));
        assertFalse(registry.supports(1, 2));
    }
}
